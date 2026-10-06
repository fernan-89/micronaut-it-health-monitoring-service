package com.thinklab.infrastructure.adapter.out.probe;

import com.thinklab.application.config.HealthMonitoringProperties;
import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.HealthCheck.CheckType;
import com.thinklab.domain.model.ProbeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.UnresolvedAddressException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ProbeAdaptersTest {

    private final Instant now = Instant.parse("2026-10-06T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final UUID org = UUID.randomUUID();
    private final HealthMonitoringProperties properties = new HealthMonitoringProperties();

    private AddressGuard guardResolvingTo(String address) {
        return new AddressGuard(properties, host -> new InetAddress[]{InetAddress.getByName(address)});
    }

    private HealthCheck http(Integer expectedStatus) {
        return HealthCheck.createNew(UUID.randomUUID(), org, "API", CheckType.HTTP, "https://api.acme.test/health", null, 10, 1000, expectedStatus, null, null, "op");
    }

    private HealthCheck tcp() {
        return HealthCheck.createNew(UUID.randomUUID(), org, "SSH", CheckType.TCP, "db.internal:5432", null, 10, 1000, null, null, null, "op");
    }

    private HttpClient clientAnswering(int status) {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(response));
        return client;
    }

    private HttpClient clientFailing(Throwable failure) {
        HttpClient client = mock(HttpClient.class);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.failedFuture(failure));
        return client;
    }

    // ------------------------------------------------------------------ AddressGuard

    @Test
    @DisplayName("the guard returns the first address when every address is acceptable, and refuses the name if any one is not")
    void guard() throws Exception {
        AddressGuard fine = new AddressGuard(properties, host -> new InetAddress[]{InetAddress.getByName("10.0.0.5"), InetAddress.getByName("10.0.0.6")});
        assertEquals("10.0.0.5", fine.resolveAllowed("db.internal").getHostAddress());

        AddressGuard mixed = new AddressGuard(properties, host -> new InetAddress[]{InetAddress.getByName("10.0.0.5"), InetAddress.getByName("169.254.169.254")});
        assertThrows(AddressNotAllowedException.class, () -> mixed.resolveAllowed("sneaky.example"));
        assertThrows(AddressNotAllowedException.class, () -> guardResolvingTo("127.0.0.1").resolveAllowed("x"));
        properties.setAllowLoopback(true);
        assertEquals("127.0.0.1", guardResolvingTo("127.0.0.1").resolveAllowed("x").getHostAddress());
    }

    @Test
    @DisplayName("the default guard resolves real names, and an unresolvable one is an unknown host")
    void defaultGuard() {
        AddressGuard guard = new AddressGuard(properties);
        properties.setAllowLoopback(true);

        assertDoesNotThrowResolving(guard);
        assertThrows(UnknownHostException.class, () -> guard.resolveAllowed("no-such-host.invalid"));
    }

    private static void assertDoesNotThrowResolving(AddressGuard guard) {
        try {
            assertTrue(guard.resolveAllowed("localhost").isLoopbackAddress());
        } catch (UnknownHostException e) {
            throw new AssertionError(e);
        }
    }

    // ------------------------------------------------------------------ Failure vocabulary

    @Test
    @DisplayName("every failure becomes one fixed code, never a message")
    void failures() {
        assertEquals(ProbeResult.BLOCKED, ProbeFailures.toResult(new AddressNotAllowedException(), now, 1).error());
        assertEquals(ProbeResult.TIMEOUT, ProbeFailures.toResult(new HttpConnectTimeoutException("secret detail"), now, 1).error());
        assertEquals(ProbeResult.TIMEOUT, ProbeFailures.toResult(new SocketTimeoutException("secret detail"), now, 1).error());
        assertEquals(ProbeResult.REFUSED, ProbeFailures.toResult(new ConnectException("secret detail"), now, 1).error());
        assertEquals(ProbeResult.DNS, ProbeFailures.toResult(new UnknownHostException("secret detail"), now, 1).error());
        assertEquals(ProbeResult.DNS, ProbeFailures.toResult(new UnresolvedAddressException(), now, 1).error());
        assertEquals(ProbeResult.FAILED, ProbeFailures.toResult(new IOException("secret detail"), now, 1).error());
        assertEquals(ProbeResult.REFUSED, ProbeFailures.toResult(new CompletionException(new ConnectException("x")), now, 1).error());
        assertEquals(ProbeResult.FAILED, ProbeFailures.toResult(new CompletionException(null), now, 1).error());
        ProbeResult result = ProbeFailures.toResult(new IOException(), now, 42);
        assertFalse(result.ok());
        assertEquals(42, result.latencyMillis());
        assertNull(result.statusCode());
    }

    // ------------------------------------------------------------------ HTTP

    @Test
    @DisplayName("HTTP: 200 to 399 is up when no status is expected; the expected status is exact when one is set")
    void httpStatuses() {
        for (int status : new int[]{200, 204, 301, 399}) {
            StepVerifier.create(new HttpProbeAdapter(clientAnswering(status), guardResolvingTo("10.0.0.5"), clock).probe(http(null)))
                    .assertNext(result -> assertTrue(result.ok())).verifyComplete();
        }
        for (int status : new int[]{199, 400, 503}) {
            StepVerifier.create(new HttpProbeAdapter(clientAnswering(status), guardResolvingTo("10.0.0.5"), clock).probe(http(null)))
                    .assertNext(result -> {
                        assertFalse(result.ok());
                        assertEquals(ProbeResult.UNEXPECTED_STATUS, result.error());
                        assertEquals(status, result.statusCode());
                    }).verifyComplete();
        }
        StepVerifier.create(new HttpProbeAdapter(clientAnswering(204), guardResolvingTo("10.0.0.5"), clock).probe(http(204))).assertNext(result -> assertTrue(result.ok())).verifyComplete();
        StepVerifier.create(new HttpProbeAdapter(clientAnswering(200), guardResolvingTo("10.0.0.5"), clock).probe(http(204)))
                .assertNext(result -> assertEquals(ProbeResult.UNEXPECTED_STATUS, result.error())).verifyComplete();
        assertEquals(CheckType.HTTP, new HttpProbeAdapter(clientAnswering(200), guardResolvingTo("10.0.0.5"), clock).type());
    }

    @Test
    @DisplayName("HTTP: the request is a GET with the check's timeout, redirects are not followed by the client the adapter builds")
    void httpRequest() {
        AtomicReference<HttpRequest> sent = new AtomicReference<>();
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            sent.set(call.getArgument(0));
            return CompletableFuture.completedFuture(response);
        });

        StepVerifier.create(new HttpProbeAdapter(client, guardResolvingTo("10.0.0.5"), clock).probe(http(null))).expectNextCount(1).verifyComplete();

        assertEquals("GET", sent.get().method());
        assertEquals(1000, sent.get().timeout().orElseThrow().toMillis());
        assertEquals("https://api.acme.test/health", sent.get().uri().toString());
        assertTrue(sent.get().headers().map().isEmpty());
        assertEquals(CheckType.HTTP, new HttpProbeAdapter(new AddressGuard(properties), clock).type());
    }

    @Test
    @DisplayName("HTTP: a forbidden resolved address is blocked before any request, and network failures become their codes")
    void httpFailures() {
        HttpClient neverCalled = mock(HttpClient.class);
        StepVerifier.create(new HttpProbeAdapter(neverCalled, guardResolvingTo("169.254.169.254"), clock).probe(http(null)))
                .assertNext(result -> assertEquals(ProbeResult.BLOCKED, result.error())).verifyComplete();
        StepVerifier.create(new HttpProbeAdapter(clientFailing(new HttpConnectTimeoutException("x")), guardResolvingTo("10.0.0.5"), clock).probe(http(null)))
                .assertNext(result -> assertEquals(ProbeResult.TIMEOUT, result.error())).verifyComplete();
        StepVerifier.create(new HttpProbeAdapter(clientFailing(new ConnectException("x")), guardResolvingTo("10.0.0.5"), clock).probe(http(null)))
                .assertNext(result -> assertEquals(ProbeResult.REFUSED, result.error())).verifyComplete();
        AddressGuard unresolvable = new AddressGuard(properties, host -> {
            throw new UnknownHostException("x");
        });
        StepVerifier.create(new HttpProbeAdapter(neverCalled, unresolvable, clock).probe(http(null)))
                .assertNext(result -> assertEquals(ProbeResult.DNS, result.error())).verifyComplete();
    }

    // ------------------------------------------------------------------ TCP

    @Test
    @DisplayName("TCP: connects to exactly the vetted address and port, with the check's timeout, and is up when it connects")
    void tcpUp() {
        AtomicReference<InetSocketAddress> connected = new AtomicReference<>();
        AtomicReference<Integer> timeout = new AtomicReference<>();
        TcpConnector connector = (address, millis) -> {
            connected.set(address);
            timeout.set(millis);
        };
        TcpProbeAdapter adapter = new TcpProbeAdapter(guardResolvingTo("10.0.0.5"), connector, clock);

        StepVerifier.create(adapter.probe(tcp())).assertNext(result -> {
            assertTrue(result.ok());
            assertNull(result.statusCode());
        }).verifyComplete();

        assertEquals("10.0.0.5", connected.get().getAddress().getHostAddress());
        assertEquals(5432, connected.get().getPort());
        assertEquals(1000, timeout.get());
        assertEquals(CheckType.TCP, adapter.type());
    }

    @Test
    @DisplayName("TCP: a refused, timed out, blocked or failing connection becomes its fixed code")
    void tcpDown() {
        StepVerifier.create(new TcpProbeAdapter(guardResolvingTo("10.0.0.5"), (a, t) -> { throw new ConnectException("x"); }, clock).probe(tcp()))
                .assertNext(result -> assertEquals(ProbeResult.REFUSED, result.error())).verifyComplete();
        StepVerifier.create(new TcpProbeAdapter(guardResolvingTo("10.0.0.5"), (a, t) -> { throw new SocketTimeoutException("x"); }, clock).probe(tcp()))
                .assertNext(result -> assertEquals(ProbeResult.TIMEOUT, result.error())).verifyComplete();
        StepVerifier.create(new TcpProbeAdapter(guardResolvingTo("169.254.169.254"), (a, t) -> { throw new AssertionError("must not connect"); }, clock).probe(tcp()))
                .assertNext(result -> assertEquals(ProbeResult.BLOCKED, result.error())).verifyComplete();
        StepVerifier.create(new TcpProbeAdapter(guardResolvingTo("10.0.0.5"), (a, t) -> { throw new IOException("x"); }, clock).probe(tcp()))
                .assertNext(result -> assertEquals(ProbeResult.FAILED, result.error())).verifyComplete();
    }
}

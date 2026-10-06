package com.thinklab.infrastructure.adapter.out.probe;

import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.HealthCheck.CheckType;
import com.thinklab.domain.model.ProbeResult;
import com.thinklab.domain.port.ProbePort;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * HTTP probe: one GET with the check's timeout. Redirects are NOT followed (a redirect is an answer, and following one could lead
 * anywhere), the body is discarded unread, and no header from the check is sent (no credentials exist to send, ADR-031). The check is up
 * when the status is the expected one, or 200-399 when none is set.
 */
@Singleton
public class HttpProbeAdapter implements ProbePort {

    private final HttpClient client;
    private final AddressGuard guard;
    private final Clock clock;

    @Inject
    public HttpProbeAdapter(AddressGuard guard, Clock clock) {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build(), guard, clock);
    }

    HttpProbeAdapter(HttpClient client, AddressGuard guard, Clock clock) {
        this.client = client;
        this.guard = guard;
        this.clock = clock;
    }

    @Override
    public CheckType type() {
        return CheckType.HTTP;
    }

    @Override
    public Mono<ProbeResult> probe(HealthCheck check) {
        Instant started = clock.instant();
        URI uri = URI.create(check.getTarget());
        return Mono.fromCallable(() -> {
                    guard.resolveAllowed(uri.getHost());
                    return HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(check.getTimeoutMillis())).GET().build();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(request -> Mono.fromFuture(() -> client.sendAsync(request, HttpResponse.BodyHandlers.discarding())))
                .map(response -> evaluate(check, response.statusCode(), started))
                .onErrorResume(error -> Mono.just(ProbeFailures.toResult(error, started, elapsed(started))));
    }

    private ProbeResult evaluate(HealthCheck check, int status, Instant started) {
        boolean ok = check.getExpectedStatus() != null ? status == check.getExpectedStatus() : status >= 200 && status < 400;
        long latency = elapsed(started);
        return ok ? ProbeResult.up(started, latency, status) : ProbeResult.down(started, latency, ProbeResult.UNEXPECTED_STATUS, status);
    }

    private long elapsed(Instant started) {
        return Duration.between(started, clock.instant()).toMillis();
    }
}

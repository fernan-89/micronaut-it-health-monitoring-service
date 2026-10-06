package com.thinklab.infrastructure.adapter.out.probe;

import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.HealthCheck.CheckType;
import com.thinklab.domain.model.ProbeResult;
import com.thinklab.domain.port.ProbePort;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** TCP probe: resolves the host, refuses an address the policy forbids, and connects to exactly the address that was vetted. */
@Singleton
public class TcpProbeAdapter implements ProbePort {

    private final AddressGuard guard;
    private final TcpConnector connector;
    private final Clock clock;

    public TcpProbeAdapter(AddressGuard guard, TcpConnector connector, Clock clock) {
        this.guard = guard;
        this.connector = connector;
        this.clock = clock;
    }

    @Override
    public CheckType type() {
        return CheckType.TCP;
    }

    @Override
    public Mono<ProbeResult> probe(HealthCheck check) {
        Instant started = clock.instant();
        String target = check.getTarget();
        int colon = target.lastIndexOf(':');
        String host = target.substring(0, colon);
        int port = Integer.parseInt(target.substring(colon + 1));
        return Mono.fromCallable(() -> {
                    InetAddress address = guard.resolveAllowed(host);
                    connector.connect(new InetSocketAddress(address, port), check.getTimeoutMillis());
                    return ProbeResult.up(started, Duration.between(started, clock.instant()).toMillis(), null);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> Mono.just(ProbeFailures.toResult(error, started, Duration.between(started, clock.instant()).toMillis())));
    }
}

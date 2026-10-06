package com.thinklab.application.usecase;

import com.thinklab.application.config.HealthMonitoringProperties;
import com.thinklab.domain.repository.HealthCheckRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;

/** One tick of the monitor: claims the checks that are due and probes them, a bounded number at once. One check failing never stops the round. */
@Singleton
public class ProbeRoundUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProbeRoundUseCase.class);

    private final HealthCheckRepository repository;
    private final ProbeRecorder recorder;
    private final HealthMonitoringProperties properties;
    private final Clock clock;

    public ProbeRoundUseCase(HealthCheckRepository repository, ProbeRecorder recorder, HealthMonitoringProperties properties, Clock clock) {
        this.repository = repository;
        this.recorder = recorder;
        this.properties = properties;
        this.clock = clock;
    }

    /** Emits how many checks were probed. */
    public Mono<Long> execute() {
        return repository.claimDue(clock.instant(), properties.getClaimBatch())
                .flatMap(check -> recorder.probeAndRecord(check, HealthAccess.SYSTEM_EXECUTOR)
                        .onErrorResume(error -> {
                            log.warn("[PROBE ROUND] Recording the result of {} failed: {}", check.getId(), error.getClass().getSimpleName());
                            return Mono.empty();
                        })
                        .then(Mono.just(1)), properties.getConcurrency())
                .count();
    }
}

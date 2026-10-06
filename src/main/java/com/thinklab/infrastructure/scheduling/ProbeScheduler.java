package com.thinklab.infrastructure.scheduling;

import com.thinklab.application.usecase.ProbeRoundUseCase;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs a probe round every few seconds (ADR-030). A round that is still running when the next tick comes is not doubled up: the tick is skipped.
 * Off with {@code thinklab.health-monitoring.scheduler-enabled=false} (a service that only serves the API, or a test).
 */
@Singleton
@Requires(property = "thinklab.health-monitoring.scheduler-enabled", notEquals = "false")
public class ProbeScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProbeScheduler.class);

    private final ProbeRoundUseCase round;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ProbeScheduler(ProbeRoundUseCase round) {
        this.round = round;
    }

    @Scheduled(fixedDelay = "${thinklab.health-monitoring.tick:5s}", initialDelay = "10s")
    void tick() {
        if (!running.compareAndSet(false, true)) {
            log.debug("[PROBE ROUND] The previous round is still running; this tick is skipped.");
            return;
        }
        round.execute()
                .doFinally(signal -> running.set(false))
                .subscribe(count -> log.debug("[PROBE ROUND] Probed {} check(s).", count),
                        error -> log.error("[PROBE ROUND] The round failed: {}", error.getClass().getSimpleName()));
    }
}

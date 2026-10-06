package com.thinklab.application.usecase;

import com.thinklab.domain.exception.InvalidHealthCheckStatusException;
import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.ProbeResult;
import com.thinklab.domain.repository.HealthCheckRepository;
import com.thinklab.domain.repository.ProbeResultRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;

/**
 * Probes a check and records what it saw: the scheduler and a manual run both come through here. The result goes into the expiring
 * history first (a failure there is only logged); then the check is loaded again FRESH and the result applied to it, with the version
 * guard, retrying a few times when a person changed the check in between (ADR-033). A check deleted meanwhile is simply skipped.
 */
@Singleton
public class ProbeRecorder {

    private static final Logger log = LoggerFactory.getLogger(ProbeRecorder.class);
    static final int MAX_ATTEMPTS = 3;

    private final ProbeRegistry probes;
    private final HealthCheckRepository checkRepository;
    private final ProbeResultRepository resultRepository;
    private final Clock clock;

    public ProbeRecorder(ProbeRegistry probes, HealthCheckRepository checkRepository, ProbeResultRepository resultRepository, Clock clock) {
        this.probes = probes;
        this.checkRepository = checkRepository;
        this.resultRepository = resultRepository;
        this.clock = clock;
    }

    /** Emits the check as it was saved, or nothing when it no longer exists or the writes kept losing the race. */
    public Mono<HealthCheck> probeAndRecord(HealthCheck check, String executor) {
        return probes.of(check.getType()).probe(check)
                .onErrorResume(error -> {
                    log.warn("[PROBE] The probe of {} failed unexpectedly: {}", check.getId(), error.getClass().getSimpleName());
                    return Mono.just(ProbeResult.down(clock.instant(), 0, ProbeResult.FAILED, null));
                })
                .flatMap(result -> resultRepository.add(check.getId(), check.getOrganisationId(), result)
                        .onErrorResume(error -> {
                            log.warn("[PROBE] The result of {} could not be kept: {}", check.getId(), error.getClass().getSimpleName());
                            return Mono.empty();
                        })
                        .then(Mono.defer(() -> apply(check, result, executor, 1))));
    }

    private Mono<HealthCheck> apply(HealthCheck check, ProbeResult result, String executor, int attempt) {
        return checkRepository.findById(check.getId(), check.getOrganisationId())
                .flatMap(current -> {
                    long expected = current.getVersion();
                    var entry = current.recordResult(result, executor).orElse(null);
                    return checkRepository.save(current, expected, entry).thenReturn(current);
                })
                .onErrorResume(InvalidHealthCheckStatusException.class, lost -> {
                    if (attempt >= MAX_ATTEMPTS) {
                        log.warn("[PROBE] The result of {} lost the race {} times and was dropped.", check.getId(), attempt);
                        return Mono.empty();
                    }
                    return apply(check, result, executor, attempt + 1);
                });
    }
}

package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.HealthCheckResponse;
import com.thinklab.application.mapper.HealthCheckMapper;
import com.thinklab.domain.exception.HealthCheckNotFoundException;
import com.thinklab.domain.repository.HealthCheckRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for probing right now (BIAN Behavior Qualifier: {@code check/execute}): the same probe and the same recording as the scheduler, whatever the status of the check. */
@Singleton
public class RunHealthCheckUseCase {

    private static final Logger log = LoggerFactory.getLogger(RunHealthCheckUseCase.class);

    private final HealthCheckRepository repository;
    private final ProbeRecorder recorder;

    public RunHealthCheckUseCase(HealthCheckRepository repository, ProbeRecorder recorder) {
        this.repository = repository;
        this.recorder = recorder;
    }

    public Mono<HealthCheckResponse> execute(UUID id, UUID organisationId, String executor, String role) {
        log.info("[USE CASE] Probing HealthCheck ID: {} now", id);

        return Mono.fromRunnable(() -> HealthAccess.requireStaff(role, "run a health check"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new HealthCheckNotFoundException("HealthCheck " + id + " not found.")))
                .flatMap(check -> recorder.probeAndRecord(check, executor))
                .switchIfEmpty(Mono.defer(() -> repository.findById(id, organisationId)))
                .map(HealthCheckMapper::toResponse);
    }
}

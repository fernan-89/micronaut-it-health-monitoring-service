package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ProbeResultResponse;
import com.thinklab.application.mapper.HealthCheckMapper;
import com.thinklab.domain.exception.HealthCheckNotFoundException;
import com.thinklab.domain.repository.HealthCheckRepository;
import com.thinklab.domain.repository.ProbeResultRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/** Use Case for the recent probes of a check, newest first (BIAN Behavior Qualifier: {@code results/retrieve}). The check must be the tenant's. */
@Singleton
public class RetrieveProbeResultsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveProbeResultsUseCase.class);
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 500;

    private final HealthCheckRepository checkRepository;
    private final ProbeResultRepository resultRepository;

    public RetrieveProbeResultsUseCase(HealthCheckRepository checkRepository, ProbeResultRepository resultRepository) {
        this.checkRepository = checkRepository;
        this.resultRepository = resultRepository;
    }

    public Mono<List<ProbeResultResponse>> execute(UUID id, UUID organisationId, Integer limit, String role) {
        log.info("[USE CASE] Retrieving the probe results of HealthCheck ID: {}", id);

        int bounded = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        return Mono.fromRunnable(() -> HealthAccess.requireStaff(role, "read probe results"))
                .then(Mono.defer(() -> checkRepository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new HealthCheckNotFoundException("HealthCheck " + id + " not found.")))
                .flatMap(check -> resultRepository.findRecent(id, organisationId, bounded).map(HealthCheckMapper::toResponse).collectList());
    }
}

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

/** Use Case for reading one check of the tenant (BIAN Behavior Qualifier: {@code retrieve}). Staff only. */
@Singleton
public class RetrieveHealthCheckUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveHealthCheckUseCase.class);

    private final HealthCheckRepository repository;

    public RetrieveHealthCheckUseCase(HealthCheckRepository repository) {
        this.repository = repository;
    }

    public Mono<HealthCheckResponse> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving HealthCheck by ID: {}", id);

        return Mono.fromRunnable(() -> HealthAccess.requireStaff(role, "read a health check"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new HealthCheckNotFoundException("HealthCheck " + id + " not found.")))
                .map(HealthCheckMapper::toResponse);
    }
}

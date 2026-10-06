package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.HealthCheckResponse;
import com.thinklab.application.mapper.HealthCheckMapper;
import com.thinklab.domain.repository.HealthCheckRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for listing the tenant's checks, filterable by health, status and asset (BIAN Behavior Qualifier: {@code retrieve}, collection). */
@Singleton
public class RetrieveHealthChecksUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveHealthChecksUseCase.class);

    private final HealthCheckRepository repository;

    public RetrieveHealthChecksUseCase(HealthCheckRepository repository) {
        this.repository = repository;
    }

    public Flux<HealthCheckResponse> execute(UUID organisationId, HealthCheckRepository.Filter filter, String role) {
        log.info("[USE CASE] Retrieving HealthChecks for organisation: {}", organisationId);

        return Mono.fromRunnable(() -> HealthAccess.requireStaff(role, "list health checks"))
                .thenMany(Flux.defer(() -> repository.findAll(organisationId, filter)))
                .map(HealthCheckMapper::toResponse);
    }
}

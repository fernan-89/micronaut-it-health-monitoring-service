package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateHealthCheckRequest;
import com.thinklab.domain.exception.HealthCheckNotFoundException;
import com.thinklab.domain.repository.HealthCheckRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for changing a check (BIAN Behavior Qualifier: {@code update}). The new target is checked against the policy first. */
@Singleton
public class UpdateHealthCheckUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateHealthCheckUseCase.class);

    private final HealthCheckWorkflow workflow;
    private final HealthCheckRepository repository;
    private final TargetPolicy targetPolicy;

    public UpdateHealthCheckUseCase(HealthCheckWorkflow workflow, HealthCheckRepository repository, TargetPolicy targetPolicy) {
        this.workflow = workflow;
        this.repository = repository;
        this.targetPolicy = targetPolicy;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, UpdateHealthCheckRequest request, String executor, String role) {
        log.info("[USE CASE] Updating HealthCheck ID: {}", id);

        String target = request.target().trim();
        return Mono.fromRunnable(() -> HealthAccess.requireStaff(role, "change a health check"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new HealthCheckNotFoundException("HealthCheck " + id + " not found.")))
                .doOnNext(check -> targetPolicy.check(check.getType(), target))
                .then(Mono.defer(() -> workflow.apply(id, organisationId, role, "change a health check", check -> check.update(request.name(), target, request.assetId(),
                        request.intervalSeconds(), request.timeoutMillis(), request.expectedStatus(), request.failureThreshold(), request.successThreshold(), executor))));
    }
}

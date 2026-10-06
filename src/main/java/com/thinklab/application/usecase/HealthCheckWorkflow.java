package com.thinklab.application.usecase;

import com.thinklab.domain.exception.HealthCheckNotFoundException;
import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.HealthCheck.HealthAuditEntry;
import com.thinklab.domain.repository.HealthCheckRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

/** The shape every staff change to a check shares: staff only, load (a 404 if it is not the tenant's), apply, save with the version loaded. */
@Singleton
public class HealthCheckWorkflow {

    private final HealthCheckRepository repository;

    public HealthCheckWorkflow(HealthCheckRepository repository) {
        this.repository = repository;
    }

    public Mono<Void> apply(UUID id, UUID organisationId, String role, String operation, Function<HealthCheck, HealthAuditEntry> action) {
        return Mono.fromRunnable(() -> HealthAccess.requireStaff(role, operation))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new HealthCheckNotFoundException("HealthCheck " + id + " not found.")))
                .flatMap(check -> {
                    long expected = check.getVersion();
                    HealthAuditEntry entry = action.apply(check);
                    return repository.save(check, expected, entry);
                });
    }
}

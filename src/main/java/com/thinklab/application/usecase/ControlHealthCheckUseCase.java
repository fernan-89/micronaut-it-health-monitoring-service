package com.thinklab.application.usecase;

import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.HealthCheck.HealthAuditEntry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for pausing and resuming a check (BIAN Behavior Qualifier: {@code control/*}). */
@Singleton
public class ControlHealthCheckUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlHealthCheckUseCase.class);

    /** What a person can ask for; each constant says how the aggregate performs it. */
    public enum Action {
        PAUSE {
            @Override HealthAuditEntry apply(HealthCheck check, String executor) { return check.pause(executor); }
        },
        RESUME {
            @Override HealthAuditEntry apply(HealthCheck check, String executor) { return check.resume(executor); }
        };

        abstract HealthAuditEntry apply(HealthCheck check, String executor);
    }

    private final HealthCheckWorkflow workflow;

    public ControlHealthCheckUseCase(HealthCheckWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, Action action, String executor, String role) {
        log.info("[USE CASE] {} on HealthCheck ID: {}", action, id);

        return workflow.apply(id, organisationId, role, "pause or resume a health check", check -> action.apply(check, executor));
    }
}

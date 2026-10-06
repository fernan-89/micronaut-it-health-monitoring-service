package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.mapper.HealthCheckMapper;
import com.thinklab.domain.exception.HealthCheckNotFoundException;
import com.thinklab.domain.repository.HealthCheckRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Use Case for the forensic ledger of a check (BIAN Behavior Qualifier: {@code audit-log/retrieve}): the changes of health and every staff action. Staff only. */
@Singleton
public class RetrieveHealthCheckAuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveHealthCheckAuditLogUseCase.class);

    private final HealthCheckRepository repository;

    public RetrieveHealthCheckAuditLogUseCase(HealthCheckRepository repository) {
        this.repository = repository;
    }

    public Mono<List<AuditEntryResponse>> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving the audit log of HealthCheck ID: {}", id);

        return Mono.fromRunnable(() -> HealthAccess.requireStaff(role, "read the audit trail of a health check"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new HealthCheckNotFoundException("HealthCheck " + id + " not found.")))
                .map(check -> check.getAuditTrail().stream().map(HealthCheckMapper::toResponse).collect(Collectors.toList()));
    }
}

package com.thinklab.domain.repository;

import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.HealthCheck.CheckStatus;
import com.thinklab.domain.model.HealthCheck.Health;
import com.thinklab.domain.model.HealthCheck.HealthAuditEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound Port for HealthCheck persistence. {@link #save} is an optimistic write guarded by the {@code version} the check was loaded
 * with (ADR-033); every lookup is tenant-scoped except {@link #claimDue}, which the scheduler runs across tenants.
 */
public interface HealthCheckRepository {

    /** Inserts the check; a name already used in the organisation is {@link com.thinklab.domain.exception.DuplicateHealthCheckException}. */
    Mono<HealthCheck> create(HealthCheck check);

    Mono<HealthCheck> findById(UUID id, UUID organisationId);

    Flux<HealthCheck> findAll(UUID organisationId, Filter filter);

    /**
     * Persists the state the check reached, with its audit entry when there is one, only while the stored version is still
     * {@code expectedVersion}; a lost race is {@link com.thinklab.domain.exception.InvalidHealthCheckStatusException}.
     */
    Mono<Void> save(HealthCheck check, long expectedVersion, HealthAuditEntry auditEntry);

    /**
     * Atomically takes up to {@code limit} ACTIVE checks that are due at {@code now}, leasing each one (it is not due again until the lease
     * ends, or the result is recorded), so several instances never probe the same check at once.
     */
    Flux<HealthCheck> claimDue(Instant now, int limit);

    record Filter(Health health, CheckStatus status, UUID assetId) {
    }
}

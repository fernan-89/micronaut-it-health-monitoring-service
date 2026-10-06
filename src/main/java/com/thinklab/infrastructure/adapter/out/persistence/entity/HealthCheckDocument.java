package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.HealthCheck.CheckStatus;
import com.thinklab.domain.model.HealthCheck.CheckType;
import com.thinklab.domain.model.HealthCheck.Health;
import com.thinklab.domain.model.HealthCheck.HealthAuditEntry;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Infrastructure-specific representation of the HealthCheck Aggregate for MongoDB. A target never holds credentials, a query or a fragment (the domain refuses them). */
@Introspected
public class HealthCheckDocument {

    @BsonId
    private UUID id;

    private UUID organisationId;
    private String name;
    private String type;
    private String target;
    private UUID assetId;
    private int intervalSeconds;
    private int timeoutMillis;
    private Integer expectedStatus;
    private int failureThreshold;
    private int successThreshold;
    private String status;
    private String health;
    private int consecutiveFailures;
    private int consecutiveSuccesses;
    private Instant lastCheckedAt;
    private Long lastLatencyMillis;
    private String lastError;
    private Instant lastStateChangeAt;
    private Instant nextDueAt;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
    public int getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(int timeoutMillis) { this.timeoutMillis = timeoutMillis; }
    public Integer getExpectedStatus() { return expectedStatus; }
    public void setExpectedStatus(Integer expectedStatus) { this.expectedStatus = expectedStatus; }
    public int getFailureThreshold() { return failureThreshold; }
    public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
    public int getSuccessThreshold() { return successThreshold; }
    public void setSuccessThreshold(int successThreshold) { this.successThreshold = successThreshold; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHealth() { return health; }
    public void setHealth(String health) { this.health = health; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
    public int getConsecutiveSuccesses() { return consecutiveSuccesses; }
    public void setConsecutiveSuccesses(int consecutiveSuccesses) { this.consecutiveSuccesses = consecutiveSuccesses; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(Instant lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public Long getLastLatencyMillis() { return lastLatencyMillis; }
    public void setLastLatencyMillis(Long lastLatencyMillis) { this.lastLatencyMillis = lastLatencyMillis; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getLastStateChangeAt() { return lastStateChangeAt; }
    public void setLastStateChangeAt(Instant lastStateChangeAt) { this.lastStateChangeAt = lastStateChangeAt; }
    public Instant getNextDueAt() { return nextDueAt; }
    public void setNextDueAt(Instant nextDueAt) { this.nextDueAt = nextDueAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<AuditEntryDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditEntryDocument> auditTrail) { this.auditTrail = auditTrail; }

    @Introspected
    public record AuditEntryDocument(Instant occurredAt, String action, String executor, String from, String to, String detail) {

        public static AuditEntryDocument fromDomain(HealthAuditEntry entry) {
            return new AuditEntryDocument(entry.occurredAt(), entry.action(), entry.executor(), entry.from(), entry.to(), entry.detail());
        }

        HealthAuditEntry toDomain() {
            return new HealthAuditEntry(occurredAt, action, executor, from, to, detail);
        }
    }

    public static final class HealthCheckPersistenceMapper {

        private HealthCheckPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static HealthCheckDocument toDocument(HealthCheck check) {
            HealthCheckDocument doc = new HealthCheckDocument();
            doc.setId(check.getId());
            doc.setOrganisationId(check.getOrganisationId());
            doc.setName(check.getName());
            doc.setType(check.getType().name());
            doc.setTarget(check.getTarget());
            doc.setAssetId(check.getAssetId());
            doc.setIntervalSeconds(check.getIntervalSeconds());
            doc.setTimeoutMillis(check.getTimeoutMillis());
            doc.setExpectedStatus(check.getExpectedStatus());
            doc.setFailureThreshold(check.getFailureThreshold());
            doc.setSuccessThreshold(check.getSuccessThreshold());
            doc.setStatus(check.getStatus().name());
            doc.setHealth(check.getHealth().name());
            doc.setConsecutiveFailures(check.getConsecutiveFailures());
            doc.setConsecutiveSuccesses(check.getConsecutiveSuccesses());
            doc.setLastCheckedAt(check.getLastCheckedAt());
            doc.setLastLatencyMillis(check.getLastLatencyMillis());
            doc.setLastError(check.getLastError());
            doc.setLastStateChangeAt(check.getLastStateChangeAt());
            doc.setNextDueAt(check.getNextDueAt());
            doc.setVersion(check.getVersion());
            doc.setCreatedAt(check.getCreatedAt());
            doc.setUpdatedAt(check.getUpdatedAt());
            doc.setAuditTrail(check.getAuditTrail().stream().map(AuditEntryDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static HealthCheck toDomain(HealthCheckDocument doc) {
            return HealthCheck.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getName(), CheckType.valueOf(doc.getType()), doc.getTarget(), doc.getAssetId(),
                    doc.getIntervalSeconds(), doc.getTimeoutMillis(), doc.getExpectedStatus(), doc.getFailureThreshold(), doc.getSuccessThreshold(),
                    CheckStatus.valueOf(doc.getStatus()), Health.valueOf(doc.getHealth()), doc.getConsecutiveFailures(), doc.getConsecutiveSuccesses(),
                    doc.getLastCheckedAt(), doc.getLastLatencyMillis(), doc.getLastError(), doc.getLastStateChangeAt(), doc.getNextDueAt(), doc.getVersion(),
                    doc.getCreatedAt(), doc.getUpdatedAt(), doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList()));
        }
    }
}

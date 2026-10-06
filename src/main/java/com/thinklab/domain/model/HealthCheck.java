package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidHealthCheckStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Aggregate Root (BIAN Control Record) of the IT Health Monitoring Service Domain: one thing watched on a schedule, an HTTP address or a
 * TCP host and port, optionally tied to an asset by id.
 *
 * <p>Two independent dimensions: the lifecycle {@link CheckStatus} (ACTIVE or PAUSED: is it being probed) and the observed {@link Health}
 * (UNKNOWN, UP, DOWN). Health does not flip on one probe: it goes DOWN after {@code failureThreshold} failures in a row and UP after
 * {@code successThreshold} successes in a row, so a single dropped packet is not an outage (ADR-030). Only a CHANGE of health, and the
 * lifecycle actions, enter the audit trail, never every probe (the results have their own expiring history), and the trail keeps its
 * last 200 entries. {@code version} is the optimistic-concurrency token of every write (ADR-033).
 */
public class HealthCheck {

    public static final int DEFAULT_INTERVAL_SECONDS = 60;
    public static final int DEFAULT_TIMEOUT_MILLIS = 5000;
    public static final int DEFAULT_FAILURE_THRESHOLD = 3;
    public static final int DEFAULT_SUCCESS_THRESHOLD = 1;
    public static final int MIN_INTERVAL_SECONDS = 10;
    public static final int MAX_INTERVAL_SECONDS = 86_400;
    public static final int MIN_TIMEOUT_MILLIS = 100;
    public static final int MAX_TIMEOUT_MILLIS = 30_000;
    public static final int MAX_THRESHOLD = 10;
    public static final int MAX_COUNTER = 1_000_000;
    public static final int MAX_AUDIT_ENTRIES = 200;
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?|\\[[0-9A-Fa-f:]{2,45}\\]");

    private final UUID id;
    private final UUID organisationId;
    private String name;
    private final CheckType type;
    private String target;
    private UUID assetId;
    private int intervalSeconds;
    private int timeoutMillis;
    private Integer expectedStatus;
    private int failureThreshold;
    private int successThreshold;
    private CheckStatus status;
    private Health health;
    private int consecutiveFailures;
    private int consecutiveSuccesses;
    private Instant lastCheckedAt;
    private Long lastLatencyMillis;
    private String lastError;
    private Instant lastStateChangeAt;
    private Instant nextDueAt;
    private final long version;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<HealthAuditEntry> auditTrail;

    private HealthCheck(UUID id, UUID organisationId, String name, CheckType type, String target, UUID assetId, int intervalSeconds, int timeoutMillis,
                        Integer expectedStatus, int failureThreshold, int successThreshold, String executor) {
        this.id = id;
        this.organisationId = organisationId;
        this.name = name;
        this.type = type;
        this.target = target;
        this.assetId = assetId;
        this.intervalSeconds = intervalSeconds;
        this.timeoutMillis = timeoutMillis;
        this.expectedStatus = expectedStatus;
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.status = CheckStatus.ACTIVE;
        this.health = Health.UNKNOWN;
        this.version = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.nextDueAt = this.createdAt;
        this.auditTrail = new ArrayList<>();
        this.auditTrail.add(new HealthAuditEntry(this.createdAt, "INITIATED", executor, null, Health.UNKNOWN.name(), "Watching " + type + " " + target + "."));
    }

    private HealthCheck(UUID id, UUID organisationId, String name, CheckType type, String target, UUID assetId, int intervalSeconds, int timeoutMillis,
                        Integer expectedStatus, int failureThreshold, int successThreshold, CheckStatus status, Health health, int consecutiveFailures,
                        int consecutiveSuccesses, Instant lastCheckedAt, Long lastLatencyMillis, String lastError, Instant lastStateChangeAt,
                        Instant nextDueAt, long version, Instant createdAt, Instant updatedAt, List<HealthAuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.name = name;
        this.type = type;
        this.target = target;
        this.assetId = assetId;
        this.intervalSeconds = intervalSeconds;
        this.timeoutMillis = timeoutMillis;
        this.expectedStatus = expectedStatus;
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.status = status != null ? status : CheckStatus.ACTIVE;
        this.health = health != null ? health : Health.UNKNOWN;
        this.consecutiveFailures = consecutiveFailures;
        this.consecutiveSuccesses = consecutiveSuccesses;
        this.lastCheckedAt = lastCheckedAt;
        this.lastLatencyMillis = lastLatencyMillis;
        this.lastError = lastError;
        this.lastStateChangeAt = lastStateChangeAt;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.nextDueAt = nextDueAt != null ? nextDueAt : this.createdAt;
        this.version = version;
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    /** Any of {@code interval}, {@code timeout}, {@code failureThreshold}, {@code successThreshold} left {@code null} takes its default. {@code expectedStatus} only means something for HTTP. */
    public static HealthCheck createNew(UUID id, UUID organisationId, String name, CheckType type, String target, UUID assetId, Integer intervalSeconds,
                                        Integer timeoutMillis, Integer expectedStatus, Integer failureThreshold, Integer successThreshold, String executor) {
        if (id == null || organisationId == null) {
            throw new IllegalArgumentException("ID and Organisation ID are mandatory for HealthCheck creation.");
        }
        if (type == null) {
            throw new IllegalArgumentException("The type of check (HTTP or TCP) is mandatory.");
        }
        int interval = intervalSeconds != null ? intervalSeconds : DEFAULT_INTERVAL_SECONDS;
        int timeout = timeoutMillis != null ? timeoutMillis : DEFAULT_TIMEOUT_MILLIS;
        int failures = failureThreshold != null ? failureThreshold : DEFAULT_FAILURE_THRESHOLD;
        int successes = successThreshold != null ? successThreshold : DEFAULT_SUCCESS_THRESHOLD;
        validate(name, type, target, interval, timeout, expectedStatus, failures, successes);
        requireExecutor(executor);
        return new HealthCheck(id, organisationId, name, type, target, assetId, interval, timeout, type == CheckType.HTTP ? expectedStatus : null, failures, successes, executor);
    }

    public static HealthCheck reconstitute(UUID id, UUID organisationId, String name, CheckType type, String target, UUID assetId, int intervalSeconds,
                                           int timeoutMillis, Integer expectedStatus, int failureThreshold, int successThreshold, CheckStatus status,
                                           Health health, int consecutiveFailures, int consecutiveSuccesses, Instant lastCheckedAt, Long lastLatencyMillis,
                                           String lastError, Instant lastStateChangeAt, Instant nextDueAt, long version, Instant createdAt, Instant updatedAt,
                                           List<HealthAuditEntry> auditTrail) {
        if (id == null || organisationId == null || name == null || type == null || target == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Name, Type and Target are mandatory to reconstitute a HealthCheck.");
        }
        return new HealthCheck(id, organisationId, name, type, target, assetId, intervalSeconds, timeoutMillis, expectedStatus, failureThreshold,
                successThreshold, status, health, consecutiveFailures, consecutiveSuccesses, lastCheckedAt, lastLatencyMillis, lastError, lastStateChangeAt,
                nextDueAt, version, createdAt, updatedAt, auditTrail);
    }

    // --- Domain Behaviors ---

    /**
     * Behavior Qualifier: {@code update}. Everything but the type. A different target is a different thing: the observed health starts again
     * from UNKNOWN and the check is due at once.
     */
    public HealthAuditEntry update(String newName, String newTarget, UUID newAssetId, Integer newIntervalSeconds, Integer newTimeoutMillis, Integer newExpectedStatus,
                                   Integer newFailureThreshold, Integer newSuccessThreshold, String executor) {
        int interval = newIntervalSeconds != null ? newIntervalSeconds : DEFAULT_INTERVAL_SECONDS;
        int timeout = newTimeoutMillis != null ? newTimeoutMillis : DEFAULT_TIMEOUT_MILLIS;
        int failures = newFailureThreshold != null ? newFailureThreshold : DEFAULT_FAILURE_THRESHOLD;
        int successes = newSuccessThreshold != null ? newSuccessThreshold : DEFAULT_SUCCESS_THRESHOLD;
        validate(newName, type, newTarget, interval, timeout, newExpectedStatus, failures, successes);
        requireExecutor(executor);
        boolean retargeted = !newTarget.equals(this.target);
        this.name = newName;
        this.target = newTarget;
        this.assetId = newAssetId;
        this.intervalSeconds = interval;
        this.timeoutMillis = timeout;
        this.expectedStatus = type == CheckType.HTTP ? newExpectedStatus : null;
        this.failureThreshold = failures;
        this.successThreshold = successes;
        this.updatedAt = Instant.now();
        String detail = "Settings updated.";
        if (retargeted) {
            this.health = Health.UNKNOWN;
            this.consecutiveFailures = 0;
            this.consecutiveSuccesses = 0;
            this.lastCheckedAt = null;
            this.lastLatencyMillis = null;
            this.lastError = null;
            this.lastStateChangeAt = null;
            this.nextDueAt = this.updatedAt;
            detail = "Settings updated; the target changed, so the health starts again from UNKNOWN.";
        }
        return record("UPDATED", executor, this.status.name(), this.status.name(), detail);
    }

    /** Behavior Qualifier: {@code control/pause}. ACTIVE -&gt; PAUSED: the check is no longer probed on its own (a manual run still works). */
    public HealthAuditEntry pause(String executor) {
        requireStatus(CheckStatus.ACTIVE);
        requireExecutor(executor);
        this.status = CheckStatus.PAUSED;
        return record("PAUSED", executor, CheckStatus.ACTIVE.name(), CheckStatus.PAUSED.name(), "Paused: no longer probed.");
    }

    /** Behavior Qualifier: {@code control/resume}. PAUSED -&gt; ACTIVE, due at once. */
    public HealthAuditEntry resume(String executor) {
        requireStatus(CheckStatus.PAUSED);
        requireExecutor(executor);
        this.status = CheckStatus.ACTIVE;
        HealthAuditEntry entry = record("RESUMED", executor, CheckStatus.PAUSED.name(), CheckStatus.ACTIVE.name(), "Resumed: probed again.");
        this.nextDueAt = this.updatedAt;
        return entry;
    }

    /**
     * Records what a probe saw. The health changes only when a threshold is crossed; the entry (empty when nothing changed) names the
     * change. An active check is next due one interval after the probe; a paused one keeps no schedule.
     */
    public Optional<HealthAuditEntry> recordResult(ProbeResult result, String executor) {
        requireExecutor(executor);
        this.lastCheckedAt = result.at();
        this.lastLatencyMillis = result.latencyMillis();
        this.lastError = result.ok() ? null : result.error();
        if (result.ok()) {
            this.consecutiveSuccesses = Math.min(this.consecutiveSuccesses + 1, MAX_COUNTER);
            this.consecutiveFailures = 0;
        } else {
            this.consecutiveFailures = Math.min(this.consecutiveFailures + 1, MAX_COUNTER);
            this.consecutiveSuccesses = 0;
        }
        Health next = this.health;
        if (result.ok() && this.consecutiveSuccesses >= this.successThreshold) {
            next = Health.UP;
        } else if (!result.ok() && this.consecutiveFailures >= this.failureThreshold) {
            next = Health.DOWN;
        }
        if (this.status == CheckStatus.ACTIVE) {
            this.nextDueAt = result.at().plusSeconds(this.intervalSeconds);
        }
        this.updatedAt = Instant.now();
        if (next == this.health) {
            return Optional.empty();
        }
        Health previous = this.health;
        this.health = next;
        this.lastStateChangeAt = result.at();
        HealthAuditEntry entry = new HealthAuditEntry(this.updatedAt, next == Health.UP ? "BECAME_UP" : "WENT_DOWN", executor, previous.name(), next.name(),
                next == Health.UP ? "Answering again." : "Failed " + this.consecutiveFailures + " time(s) in a row: " + result.error() + ".");
        addAudit(entry);
        return Optional.of(entry);
    }

    // --- Internal helpers ---

    private HealthAuditEntry record(String action, String executor, String from, String to, String detail) {
        this.updatedAt = Instant.now();
        HealthAuditEntry entry = new HealthAuditEntry(this.updatedAt, action, executor, from, to, detail);
        addAudit(entry);
        return entry;
    }

    private void addAudit(HealthAuditEntry entry) {
        this.auditTrail.add(entry);
        while (this.auditTrail.size() > MAX_AUDIT_ENTRIES) {
            this.auditTrail.remove(0);
        }
    }

    private void requireStatus(CheckStatus expected) {
        if (this.status != expected) {
            throw new InvalidHealthCheckStatusException(String.format("Illegal transition: HealthCheck is [%s], expected [%s].", this.status, expected));
        }
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable HealthCheck mutations.");
        }
    }

    private static void validate(String name, CheckType type, String target, int interval, int timeout, Integer expectedStatus, int failures, int successes) {
        if (name == null || name.isBlank() || name.length() > 80) {
            throw new IllegalArgumentException("Name is mandatory for a HealthCheck (up to 80 characters).");
        }
        if (interval < MIN_INTERVAL_SECONDS || interval > MAX_INTERVAL_SECONDS) {
            throw new IllegalArgumentException("The interval is between " + MIN_INTERVAL_SECONDS + " and " + MAX_INTERVAL_SECONDS + " seconds.");
        }
        if (timeout < MIN_TIMEOUT_MILLIS || timeout > MAX_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException("The timeout is between " + MIN_TIMEOUT_MILLIS + " and " + MAX_TIMEOUT_MILLIS + " milliseconds.");
        }
        if (timeout > interval * 1000L) {
            throw new IllegalArgumentException("The timeout cannot be longer than the interval.");
        }
        if (failures < 1 || failures > MAX_THRESHOLD || successes < 1 || successes > MAX_THRESHOLD) {
            throw new IllegalArgumentException("The thresholds are between 1 and " + MAX_THRESHOLD + ".");
        }
        if (expectedStatus != null && (expectedStatus < 100 || expectedStatus > 599)) {
            throw new IllegalArgumentException("The expected status is an HTTP status code (100 to 599).");
        }
        if (type == CheckType.HTTP) {
            validateUrl(target);
        } else {
            validateHostAndPort(target);
        }
    }

    /** An http(s) address of a host with no credentials, query or fragment: those are where secrets end up, and they would be stored. */
    private static void validateUrl(String target) {
        URI uri;
        try {
            uri = URI.create(target == null ? "" : target.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The target is not a valid URL.");
        }
        boolean web = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
        if (!web || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null || target.length() > 300) {
            throw new IllegalArgumentException("An HTTP target is an http(s) address of a host (up to 300 characters), without credentials, query or fragment.");
        }
    }

    private static void validateHostAndPort(String target) {
        int colon = target == null ? -1 : target.lastIndexOf(':');
        if (colon < 1 || !HOST.matcher(target.substring(0, colon)).matches()) {
            throw new IllegalArgumentException("A TCP target is host:port.");
        }
        try {
            int port = Integer.parseInt(target.substring(colon + 1));
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("The port is between 1 and 65535.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("A TCP target is host:port.");
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getName() { return name; }
    public CheckType getType() { return type; }
    public String getTarget() { return target; }
    public UUID getAssetId() { return assetId; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public int getTimeoutMillis() { return timeoutMillis; }
    public Integer getExpectedStatus() { return expectedStatus; }
    public int getFailureThreshold() { return failureThreshold; }
    public int getSuccessThreshold() { return successThreshold; }
    public CheckStatus getStatus() { return status; }
    public Health getHealth() { return health; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public int getConsecutiveSuccesses() { return consecutiveSuccesses; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public Long getLastLatencyMillis() { return lastLatencyMillis; }
    public String getLastError() { return lastError; }
    public Instant getLastStateChangeAt() { return lastStateChangeAt; }
    public Instant getNextDueAt() { return nextDueAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<HealthAuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }

    // --- Nested Value Objects ---

    public enum CheckType { HTTP, TCP }

    /** Is it being probed on its own. */
    public enum CheckStatus { ACTIVE, PAUSED }

    /** What the probes say. */
    public enum Health { UNKNOWN, UP, DOWN }

    /** Immutable forensic ledger entry; {@code from} and {@code to} are a status or a health, whichever the action moved. */
    public record HealthAuditEntry(Instant occurredAt, String action, String executor, String from, String to, String detail) {}
}

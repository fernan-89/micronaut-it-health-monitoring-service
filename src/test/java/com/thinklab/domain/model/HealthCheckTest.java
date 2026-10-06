package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidHealthCheckStatusException;
import com.thinklab.domain.model.HealthCheck.CheckStatus;
import com.thinklab.domain.model.HealthCheck.CheckType;
import com.thinklab.domain.model.HealthCheck.Health;
import com.thinklab.domain.model.HealthCheck.HealthAuditEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthCheckTest {

    private final UUID org = UUID.randomUUID();
    private final Instant t0 = Instant.parse("2026-10-06T10:00:00Z");

    private HealthCheck http() {
        return HealthCheck.createNew(UUID.randomUUID(), org, "API", CheckType.HTTP, "https://api.acme.test/health", null, null, null, null, null, null, "op");
    }

    private HealthCheck tcp() {
        return HealthCheck.createNew(UUID.randomUUID(), org, "SSH", CheckType.TCP, "10.0.0.5:22", UUID.randomUUID(), 30, 1000, 204, 2, 2, "op");
    }

    private static void rejects(Runnable action) {
        assertThrows(IllegalArgumentException.class, action::run);
    }

    private ProbeResult up(Instant at) {
        return ProbeResult.up(at, 12, 200);
    }

    private ProbeResult down(Instant at) {
        return ProbeResult.down(at, 5000, ProbeResult.TIMEOUT, null);
    }

    @Test
    @DisplayName("a new HTTP check takes the defaults, starts ACTIVE and UNKNOWN, due at once, with an INITIATED entry")
    void createHttp() {
        HealthCheck check = http();

        assertEquals(60, check.getIntervalSeconds());
        assertEquals(5000, check.getTimeoutMillis());
        assertEquals(3, check.getFailureThreshold());
        assertEquals(1, check.getSuccessThreshold());
        assertNull(check.getExpectedStatus());
        assertEquals(CheckStatus.ACTIVE, check.getStatus());
        assertEquals(Health.UNKNOWN, check.getHealth());
        assertEquals(check.getCreatedAt(), check.getNextDueAt());
        assertEquals(0, check.getVersion());
        assertEquals("INITIATED", check.getAuditTrail().get(0).action());
        assertNull(check.getAuditTrail().get(0).from());
        assertEquals("op", check.getAuditTrail().get(0).executor());
        assertEquals(check.getCreatedAt(), check.getUpdatedAt());
        assertEquals(CheckType.HTTP, check.getType());
        assertEquals(org, check.getOrganisationId());
        assertNull(check.getAssetId());
        assertNull(check.getLastCheckedAt());
        assertNull(check.getLastLatencyMillis());
        assertNull(check.getLastError());
        assertNull(check.getLastStateChangeAt());
        assertEquals(0, check.getConsecutiveFailures());
        assertEquals(0, check.getConsecutiveSuccesses());
    }

    @Test
    @DisplayName("a TCP check keeps its own settings and ignores an expected status, which means nothing for it")
    void createTcp() {
        HealthCheck check = tcp();

        assertEquals(30, check.getIntervalSeconds());
        assertEquals(1000, check.getTimeoutMillis());
        assertEquals(2, check.getFailureThreshold());
        assertEquals(2, check.getSuccessThreshold());
        assertNull(check.getExpectedStatus());
        assertEquals("10.0.0.5:22", check.getTarget());
        HealthCheck httpWith = HealthCheck.createNew(UUID.randomUUID(), org, "n", CheckType.HTTP, "http://host", null, 10, 100, 301, 1, 1, "op");
        assertEquals(301, httpWith.getExpectedStatus());
    }

    @Test
    @DisplayName("creation guards: ids, type, name, target, ranges, thresholds, expected status and executor")
    void createGuards() {
        UUID id = UUID.randomUUID();
        rejects(() -> HealthCheck.createNew(null, org, "n", CheckType.HTTP, "http://h", null, null, null, null, null, null, "op"));
        rejects(() -> HealthCheck.createNew(id, null, "n", CheckType.HTTP, "http://h", null, null, null, null, null, null, "op"));
        rejects(() -> HealthCheck.createNew(id, org, "n", null, "http://h", null, null, null, null, null, null, "op"));
        rejects(() -> HealthCheck.createNew(id, org, null, CheckType.HTTP, "http://h", null, null, null, null, null, null, "op"));
        rejects(() -> HealthCheck.createNew(id, org, " ", CheckType.HTTP, "http://h", null, null, null, null, null, null, "op"));
        rejects(() -> HealthCheck.createNew(id, org, "x".repeat(81), CheckType.HTTP, "http://h", null, null, null, null, null, null, "op"));
        rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.HTTP, "http://h", null, 9, null, null, null, null, "op"));
        rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.HTTP, "http://h", null, 86_401, null, null, null, null, "op"));
        rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.HTTP, "http://h", null, null, 99, null, null, null, "op"));
        rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.HTTP, "http://h", null, null, 30_001, null, null, null, "op"));
        rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.HTTP, "http://h", null, 10, 10_001, null, null, null, "op"));
        rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.HTTP, "http://h", null, null, null, null, 0, null, "op"));
        rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.HTTP, "http://h", null, null, null, null, 11, null, "op"));
        rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.HTTP, "http://h", null, null, null, null, null, 0, "op"));
        rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.HTTP, "http://h", null, null, null, null, null, 11, "op"));
        rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.HTTP, "http://h", null, null, null, 99, null, null, "op"));
        rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.HTTP, "http://h", null, null, null, 600, null, null, "op"));
        rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.HTTP, "http://h", null, null, null, null, null, null, null));
        rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.HTTP, "http://h", null, null, null, null, null, null, " "));
    }

    @Test
    @DisplayName("an HTTP target is an http(s) address of a host with no credentials, query or fragment")
    void httpTargets() {
        UUID id = UUID.randomUUID();
        for (String bad : new String[]{null, "", "not a url", "ftp://host", "/relative", "https:///no-host", "https://user:pw@host", "https://host?token=1", "https://host#frag",
                "https://host/" + "a".repeat(300)}) {
            rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.HTTP, bad, null, null, null, null, null, null, "op"));
        }
        HealthCheck.createNew(id, org, "n", CheckType.HTTP, "http://10.0.0.5:8080/health", null, null, null, null, null, null, "op");
    }

    @Test
    @DisplayName("a TCP target is host:port, with a port from 1 to 65535")
    void tcpTargets() {
        UUID id = UUID.randomUUID();
        for (String bad : new String[]{null, "", "host", ":22", "host:", "host:abc", "host:0", "host:65536", "ho st:22", "-host:22"}) {
            rejects(() -> HealthCheck.createNew(id, org, "n", CheckType.TCP, bad, null, null, null, null, null, null, "op"));
        }
        HealthCheck.createNew(id, org, "n", CheckType.TCP, "db.internal:5432", null, null, null, null, null, null, "op");
        HealthCheck.createNew(id, org, "n", CheckType.TCP, "[fd00::1]:22", null, null, null, null, null, null, "op");
    }

    @Test
    @DisplayName("health flips DOWN only after the failure threshold in a row, and UP after the success threshold; a success in between starts the count again")
    void thresholds() {
        HealthCheck check = HealthCheck.createNew(UUID.randomUUID(), org, "n", CheckType.TCP, "h:22", null, null, null, null, 3, 2, "op");

        assertTrue(check.recordResult(down(t0), "m").isEmpty());
        assertTrue(check.recordResult(down(t0.plusSeconds(60)), "m").isEmpty());
        assertEquals(2, check.getConsecutiveFailures());
        assertEquals(Health.UNKNOWN, check.getHealth());
        assertTrue(check.recordResult(up(t0.plusSeconds(120)), "m").isEmpty());
        assertEquals(0, check.getConsecutiveFailures());
        assertEquals(1, check.getConsecutiveSuccesses());
        assertTrue(check.recordResult(down(t0.plusSeconds(180)), "m").isEmpty());
        assertTrue(check.recordResult(down(t0.plusSeconds(240)), "m").isEmpty());
        HealthAuditEntry wentDown = check.recordResult(down(t0.plusSeconds(300)), "m").orElseThrow();

        assertEquals(Health.DOWN, check.getHealth());
        assertEquals("WENT_DOWN", wentDown.action());
        assertEquals("UNKNOWN", wentDown.from());
        assertEquals("DOWN", wentDown.to());
        assertTrue(wentDown.detail().contains("3 time(s) in a row") && wentDown.detail().contains("timeout"));
        assertEquals(t0.plusSeconds(300), check.getLastStateChangeAt());
        assertTrue(check.recordResult(down(t0.plusSeconds(360)), "m").isEmpty());
        assertTrue(check.recordResult(up(t0.plusSeconds(420)), "m").isEmpty());
        HealthAuditEntry recovered = check.recordResult(up(t0.plusSeconds(480)), "m").orElseThrow();

        assertEquals(Health.UP, check.getHealth());
        assertEquals("BECAME_UP", recovered.action());
        assertEquals("DOWN", recovered.from());
        assertTrue(check.recordResult(up(t0.plusSeconds(540)), "m").isEmpty());
        assertEquals(3, check.getAuditTrail().size());
    }

    @Test
    @DisplayName("a probe records when it ran, how long it took and what went wrong, and the next one is due an interval later")
    void recordsTheProbe() {
        HealthCheck check = http();

        check.recordResult(ProbeResult.down(t0, 77, ProbeResult.UNEXPECTED_STATUS, 503), "m");

        assertEquals(t0, check.getLastCheckedAt());
        assertEquals(77L, check.getLastLatencyMillis());
        assertEquals("unexpected status", check.getLastError());
        assertEquals(t0.plusSeconds(60), check.getNextDueAt());
        check.recordResult(up(t0.plusSeconds(60)), "m");
        assertNull(check.getLastError());
        assertEquals(Health.UP, check.getHealth());
        assertThrows(IllegalArgumentException.class, () -> check.recordResult(up(t0), null));
    }

    @Test
    @DisplayName("a paused check records a manual probe but keeps no schedule")
    void pausedKeepsNoSchedule() {
        HealthCheck check = http();
        check.pause("op");
        Instant due = check.getNextDueAt();

        check.recordResult(up(t0), "op");

        assertEquals(due, check.getNextDueAt());
        assertEquals(CheckStatus.PAUSED, check.getStatus());
    }

    @Test
    @DisplayName("counters never overflow")
    void countersAreCapped() {
        HealthCheck check = HealthCheck.reconstitute(UUID.randomUUID(), org, "n", CheckType.TCP, "h:22", null, 60, 5000, null, 1_000_001, 1_000_001, CheckStatus.ACTIVE, Health.UNKNOWN,
                HealthCheck.MAX_COUNTER, HealthCheck.MAX_COUNTER, null, null, null, null, null, 0, null, null, null);

        check.recordResult(down(t0), "m");
        assertEquals(HealthCheck.MAX_COUNTER, check.getConsecutiveFailures());
        check.recordResult(up(t0), "m");
        check.recordResult(up(t0), "m");
        assertEquals(2, check.getConsecutiveSuccesses());
        HealthCheck full = HealthCheck.reconstitute(UUID.randomUUID(), org, "n", CheckType.TCP, "h:22", null, 60, 5000, null, 1_000_001, 1_000_001, CheckStatus.ACTIVE, Health.UNKNOWN,
                0, HealthCheck.MAX_COUNTER, null, null, null, null, null, 0, null, null, null);
        full.recordResult(up(t0), "m");
        assertEquals(HealthCheck.MAX_COUNTER, full.getConsecutiveSuccesses());
    }

    @Test
    @DisplayName("the audit trail keeps its last 200 entries, so a flapping check never grows it for ever")
    void auditTrailIsBounded() {
        HealthCheck check = HealthCheck.createNew(UUID.randomUUID(), org, "n", CheckType.TCP, "h:22", null, null, null, null, 1, 1, "op");

        for (int i = 0; i < 250; i++) {
            check.recordResult(i % 2 == 0 ? up(t0.plusSeconds(i)) : down(t0.plusSeconds(i)), "m");
        }

        assertEquals(HealthCheck.MAX_AUDIT_ENTRIES, check.getAuditTrail().size());
        assertNotEquals("INITIATED", check.getAuditTrail().get(0).action());
    }

    private static void assertNotEquals(String unexpected, String actual) {
        assertFalse(unexpected.equals(actual));
    }

    @Test
    @DisplayName("update changes the definition and keeps the health; a new target starts it again from UNKNOWN and makes the check due at once")
    void update() {
        HealthCheck check = http();
        check.recordResult(up(t0), "m");

        HealthAuditEntry same = check.update("API v2", "https://api.acme.test/health", UUID.randomUUID(), 120, 2000, 204, 5, 2, "op-2");

        assertEquals("UPDATED", same.action());
        assertEquals("ACTIVE", same.from());
        assertEquals(Health.UP, check.getHealth());
        assertEquals("API v2", check.getName());
        assertEquals(120, check.getIntervalSeconds());
        assertEquals(204, check.getExpectedStatus());
        assertTrue(same.detail().equals("Settings updated."));

        HealthAuditEntry moved = check.update("API v2", "https://other.acme.test/health", null, null, null, null, null, null, "op-2");

        assertEquals(Health.UNKNOWN, check.getHealth());
        assertNull(check.getLastCheckedAt());
        assertNull(check.getLastLatencyMillis());
        assertNull(check.getLastError());
        assertNull(check.getLastStateChangeAt());
        assertEquals(0, check.getConsecutiveSuccesses());
        assertEquals(check.getUpdatedAt(), check.getNextDueAt());
        assertEquals(60, check.getIntervalSeconds());
        assertTrue(moved.detail().contains("starts again from UNKNOWN"));

        HealthCheck tcpCheck = tcp();
        tcpCheck.update("SSH", "10.0.0.5:22", null, null, null, 200, null, null, "op");
        assertNull(tcpCheck.getExpectedStatus());
        rejects(() -> check.update("", "https://x.test", null, null, null, null, null, null, "op"));
        rejects(() -> check.update("n", "https://x.test", null, null, null, null, null, null, " "));
    }

    @Test
    @DisplayName("pause then resume follow the lifecycle, each with its entry; resume makes the check due at once")
    void lifecycle() {
        HealthCheck check = http();

        assertThrows(InvalidHealthCheckStatusException.class, () -> check.resume("op"));
        HealthAuditEntry paused = check.pause("op");
        assertEquals(CheckStatus.PAUSED, check.getStatus());
        assertEquals("ACTIVE", paused.from());
        assertEquals("PAUSED", paused.to());
        assertThrows(InvalidHealthCheckStatusException.class, () -> check.pause("op"));
        HealthAuditEntry resumed = check.resume("op-2");
        assertEquals(CheckStatus.ACTIVE, check.getStatus());
        assertEquals("RESUMED", resumed.action());
        assertEquals(check.getUpdatedAt(), check.getNextDueAt());
        assertEquals(3, check.getAuditTrail().size());
        assertThrows(IllegalArgumentException.class, () -> check.pause(null));
        check.pause("op");
        assertThrows(IllegalArgumentException.class, () -> check.resume(" "));
    }

    @Test
    @DisplayName("reconstitute keeps what was stored, defaults what is missing, and refuses a missing identity")
    void reconstitute() {
        UUID id = UUID.randomUUID();
        HealthCheckAuditSample sample = new HealthCheckAuditSample();
        HealthCheck full = HealthCheck.reconstitute(id, org, "n", CheckType.HTTP, "http://h", null, 60, 5000, 200, 3, 1, CheckStatus.PAUSED, Health.DOWN, 4, 0, t0, 9L, "timeout",
                t0, t0, 7, t0, t0, List.of(sample.entry));
        assertEquals(CheckStatus.PAUSED, full.getStatus());
        assertEquals(Health.DOWN, full.getHealth());
        assertEquals(7, full.getVersion());
        assertEquals(1, full.getAuditTrail().size());

        HealthCheck bare = HealthCheck.reconstitute(id, org, "n", CheckType.HTTP, "http://h", null, 60, 5000, null, 3, 1, null, null, 0, 0, null, null, null, null, null, 0, null, null, null);
        assertEquals(CheckStatus.ACTIVE, bare.getStatus());
        assertEquals(Health.UNKNOWN, bare.getHealth());
        assertTrue(bare.getAuditTrail().isEmpty());
        assertEquals(bare.getCreatedAt(), bare.getUpdatedAt());
        assertEquals(bare.getCreatedAt(), bare.getNextDueAt());

        rejects(() -> HealthCheck.reconstitute(null, org, "n", CheckType.HTTP, "t", null, 60, 5000, null, 3, 1, null, null, 0, 0, null, null, null, null, null, 0, null, null, null));
        rejects(() -> HealthCheck.reconstitute(id, null, "n", CheckType.HTTP, "t", null, 60, 5000, null, 3, 1, null, null, 0, 0, null, null, null, null, null, 0, null, null, null));
        rejects(() -> HealthCheck.reconstitute(id, org, null, CheckType.HTTP, "t", null, 60, 5000, null, 3, 1, null, null, 0, 0, null, null, null, null, null, 0, null, null, null));
        rejects(() -> HealthCheck.reconstitute(id, org, "n", null, "t", null, 60, 5000, null, 3, 1, null, null, 0, 0, null, null, null, null, null, 0, null, null, null));
        rejects(() -> HealthCheck.reconstitute(id, org, "n", CheckType.HTTP, null, null, 60, 5000, null, 3, 1, null, null, 0, 0, null, null, null, null, null, 0, null, null, null));
    }

    private static final class HealthCheckAuditSample {
        final HealthAuditEntry entry = new HealthAuditEntry(Instant.parse("2026-10-06T10:00:00Z"), "INITIATED", "op", null, "UNKNOWN", "d");
    }
}

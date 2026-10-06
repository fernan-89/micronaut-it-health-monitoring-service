package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.exception.DuplicateHealthCheckException;
import com.thinklab.domain.exception.InvalidHealthCheckStatusException;
import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.HealthCheck.CheckStatus;
import com.thinklab.domain.model.HealthCheck.CheckType;
import com.thinklab.domain.model.HealthCheck.Health;
import com.thinklab.domain.model.ProbeResult;
import com.thinklab.domain.repository.HealthCheckRepository;
import com.thinklab.domain.repository.HealthCheckRepository.Filter;
import com.thinklab.domain.repository.ProbeResultRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The monitor's persistence against a real MongoDB: the unique name, the version-guarded save (a second writer from the same state
 * loses), the lease that makes two schedulers never claim the same check, the expiring result history, and the indexes created at startup.
 * The scheduler is switched off here: these tests drive the repositories themselves.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HealthMonitoringPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "health_monitoring_it";
    private static final String EXECUTOR = "op-1";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE), "thinklab.health-monitoring.scheduler-enabled", "false");
    }

    @Inject HealthCheckRepository checks;
    @Inject ProbeResultRepository results;
    @Inject MongoClient mongoClient;

    private HealthCheck newCheck(UUID organisation, String name) {
        return HealthCheck.createNew(UUID.randomUUID(), organisation, name, CheckType.TCP, "db.internal:5432", UUID.randomUUID(), 30, 1000, null, 2, 1, EXECUTOR);
    }

    @Test
    @DisplayName("a check is read back whole, only for its own organisation, and the list filters work")
    void roundTripAndFilters() {
        UUID organisation = UUID.randomUUID();
        UUID asset = UUID.randomUUID();
        HealthCheck created = checks.create(HealthCheck.createNew(UUID.randomUUID(), organisation, "API", CheckType.HTTP, "https://api.acme.test/health", asset, null, null, 204, null, null, EXECUTOR)).block();

        HealthCheck found = checks.findById(created.getId(), organisation).block();

        assertEquals(204, found.getExpectedStatus());
        assertEquals(asset, found.getAssetId());
        assertEquals(Health.UNKNOWN, found.getHealth());
        assertEquals(1, found.getAuditTrail().size());
        assertNull(checks.findById(created.getId(), UUID.randomUUID()).block());
        assertEquals(Set.of(created.getId()), ids(checks.findAll(organisation, new Filter(Health.UNKNOWN, CheckStatus.ACTIVE, asset)).collectList().block()));
        assertTrue(checks.findAll(organisation, new Filter(Health.UP, null, null)).collectList().block().isEmpty());
        assertTrue(checks.findAll(UUID.randomUUID(), new Filter(null, null, null)).collectList().block().isEmpty());
    }

    private static Set<UUID> ids(List<HealthCheck> found) {
        return found.stream().map(HealthCheck::getId).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("two checks with the same name in one organisation: the second loses; in another organisation it is fine; a rename onto a taken name loses too")
    void nameIsUnique() {
        UUID organisation = UUID.randomUUID();
        checks.create(newCheck(organisation, "Same")).block();

        assertThrows(DuplicateHealthCheckException.class, () -> checks.create(newCheck(organisation, "Same")).block());
        checks.create(newCheck(UUID.randomUUID(), "Same")).block();
        HealthCheck other = checks.create(newCheck(organisation, "Other")).block();
        HealthCheck loaded = checks.findById(other.getId(), organisation).block();
        long version = loaded.getVersion();
        var entry = loaded.update("Same", loaded.getTarget(), null, 30, 1000, null, 2, 1, EXECUTOR);

        assertThrows(DuplicateHealthCheckException.class, () -> checks.save(loaded, version, entry).block());
    }

    @Test
    @DisplayName("a save is guarded by the version loaded: the second writer from the same state loses, and the version moves on")
    void versionGuard() {
        UUID organisation = UUID.randomUUID();
        HealthCheck created = checks.create(newCheck(organisation, "Guarded")).block();
        HealthCheck first = checks.findById(created.getId(), organisation).block();
        HealthCheck second = checks.findById(created.getId(), organisation).block();

        var pause = first.pause(EXECUTOR);
        checks.save(first, first.getVersion(), pause).block();
        var down = second.recordResult(ProbeResult.down(Instant.now(), 5, ProbeResult.TIMEOUT, null), "m");
        assertThrows(InvalidHealthCheckStatusException.class, () -> checks.save(second, second.getVersion(), down.orElse(null)).block());

        HealthCheck stored = checks.findById(created.getId(), organisation).block();
        assertEquals(CheckStatus.PAUSED, stored.getStatus());
        assertEquals(1, stored.getVersion());
        assertEquals(2, stored.getAuditTrail().size());
    }

    @Test
    @DisplayName("a result that changes nothing is saved without an audit entry; one that crosses a threshold adds exactly one")
    void auditOnlyOnChange() {
        UUID organisation = UUID.randomUUID();
        HealthCheck created = checks.create(newCheck(organisation, "Flap")).block();
        for (int i = 0; i < 3; i++) {
            HealthCheck loaded = checks.findById(created.getId(), organisation).block();
            long version = loaded.getVersion();
            var entry = loaded.recordResult(ProbeResult.down(Instant.now(), 5, ProbeResult.REFUSED, null), "m").orElse(null);
            checks.save(loaded, version, entry).block();
        }

        HealthCheck stored = checks.findById(created.getId(), organisation).block();

        assertEquals(Health.DOWN, stored.getHealth());
        assertEquals(3, stored.getConsecutiveFailures());
        assertEquals(2, stored.getAuditTrail().size());
        assertEquals("WENT_DOWN", stored.getAuditTrail().get(1).action());
    }

    @Test
    @DisplayName("claiming takes only due active checks, leases them so a second claim finds none, and 20 simultaneous claims never share one")
    void claimingIsAtomic() {
        UUID organisation = UUID.randomUUID();
        HealthCheck due = checks.create(newCheck(organisation, "Due")).block();
        HealthCheck paused = checks.create(newCheck(organisation, "Paused")).block();
        HealthCheck loaded = checks.findById(paused.getId(), organisation).block();
        checks.save(loaded, loaded.getVersion(), loaded.pause(EXECUTOR)).block();
        Instant now = Instant.now().plusSeconds(5);

        Set<UUID> first = ids(checks.claimDue(now, 1000).collectList().block());
        Set<UUID> second = ids(checks.claimDue(now, 1000).collectList().block());

        assertTrue(first.contains(due.getId()));
        assertTrue(!first.contains(paused.getId()));
        assertTrue(!second.contains(due.getId()));
        List<HealthCheck> many = Flux.range(0, 10).map(i -> newCheck(organisation, "Race " + i)).flatMap(checks::create).collectList().block();
        List<UUID> claimed = Flux.range(0, 20).flatMap(i -> checks.claimDue(now, 5)).map(HealthCheck::getId).collectList().block();
        assertEquals(claimed.size(), Set.copyOf(claimed).size());
        assertTrue(claimed.containsAll(many.stream().map(HealthCheck::getId).toList()));
    }

    @Test
    @DisplayName("results come back newest first, limited, and only for the check and organisation asked")
    void results() {
        UUID organisation = UUID.randomUUID();
        UUID check = UUID.randomUUID();
        Instant base = Instant.now();
        results.add(check, organisation, ProbeResult.up(base.minusSeconds(120), 10, 200)).block();
        results.add(check, organisation, ProbeResult.down(base.minusSeconds(60), 20, ProbeResult.TIMEOUT, null)).block();
        results.add(check, organisation, ProbeResult.up(base, 30, 200)).block();
        results.add(UUID.randomUUID(), organisation, ProbeResult.up(base, 1, 200)).block();

        List<ProbeResult> recent = results.findRecent(check, organisation, 2).collectList().block();

        assertEquals(2, recent.size());
        assertEquals(30L, recent.get(0).latencyMillis());
        assertEquals("timeout", recent.get(1).error());
        assertNull(recent.get(1).statusCode());
        assertTrue(results.findRecent(check, UUID.randomUUID(), 10).collectList().block().isEmpty());
    }

    @Test
    @DisplayName("startup created the unique name index, the claim index and the TTL on the results")
    void indexesExist() {
        Set<String> checkIndexes = indexNames("health_checks");
        assertTrue(checkIndexes.contains("organisationId_1_name_1"));
        assertTrue(checkIndexes.contains("status_1_nextDueAt_1"));
        Set<String> resultIndexes = indexNames("probe_results");
        assertTrue(resultIndexes.contains("checkId_1_at_-1"));
        assertTrue(resultIndexes.contains("at_ttl"));
    }

    private Set<String> indexNames(String collection) {
        return Flux.from(mongoClient.getDatabase(DATABASE).getCollection(collection).listIndexes()).map(index -> ((Document) index).getString("name"))
                .collect(Collectors.toSet()).block();
    }
}

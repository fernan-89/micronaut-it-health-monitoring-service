package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.MongoTimeoutException;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.DuplicateHealthCheckException;
import com.thinklab.domain.exception.InvalidHealthCheckStatusException;
import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.HealthCheck.CheckStatus;
import com.thinklab.domain.model.HealthCheck.CheckType;
import com.thinklab.domain.model.HealthCheck.Health;
import com.thinklab.domain.model.ProbeResult;
import com.thinklab.domain.repository.HealthCheckRepository.Filter;
import com.thinklab.infrastructure.adapter.out.persistence.entity.HealthCheckDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.HealthCheckDocument.HealthCheckPersistenceMapper;
import com.thinklab.infrastructure.adapter.out.persistence.repository.HealthCheckMongoRepositoryAdapter;
import com.thinklab.infrastructure.adapter.out.persistence.repository.ProbeResultMongoRepositoryAdapter;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class HealthPersistenceTest {

    private static final String URI = "mongodb://localhost:27017/hlm_test";

    private final UUID org = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-06T12:00:00Z");
    private MongoClient client;
    private MongoCollection<HealthCheckDocument> checks;
    private MongoCollection<Document> results;

    @BeforeEach
    void setUp() {
        client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        checks = mock(MongoCollection.class);
        results = mock(MongoCollection.class);
        when(client.getDatabase("hlm_test")).thenReturn(database);
        when(client.getDatabase("thinklab_it_health_monitoring_db")).thenReturn(database);
        when(database.getCollection("health_checks", HealthCheckDocument.class)).thenReturn(checks);
        when(database.getCollection("probe_results")).thenReturn(results);
        when(checks.withCodecRegistry(any())).thenReturn(checks);
    }

    private HealthCheck check() {
        HealthCheck check = HealthCheck.createNew(UUID.randomUUID(), org, "API", CheckType.HTTP, "https://api.acme.test/health", UUID.randomUUID(), 30, 1000, 204, 2, 2, "op");
        check.recordResult(ProbeResult.down(now, 7, ProbeResult.TIMEOUT, null), "m");
        check.recordResult(ProbeResult.down(now, 7, ProbeResult.TIMEOUT, null), "m");
        return check;
    }

    private static MongoWriteException writeError(int code) {
        return new MongoWriteException(new WriteError(code, "write error", new BsonDocument()), new ServerAddress());
    }

    private <T> void finds(MongoCollection<T> collection, java.util.function.Function<Object, T> toDocument, Object... found) {
        FindPublisher<T> publisher = mock(FindPublisher.class);
        when(collection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        when(publisher.sort(any(Bson.class))).thenReturn(publisher);
        when(publisher.limit(anyInt())).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<T> subscriber = invocation.getArgument(0);
            Flux.fromArray(found).map(toDocument).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
    }

    // ------------------------------------------------------------------ Document

    @Test
    @DisplayName("a check survives the round trip, with its thresholds, counters, health, version and audit trail")
    void roundTrip() {
        HealthCheck check = check();

        HealthCheckDocument document = HealthCheckPersistenceMapper.toDocument(check);
        HealthCheck back = HealthCheckPersistenceMapper.toDomain(document);

        assertEquals(Health.DOWN, back.getHealth());
        assertEquals(2, back.getConsecutiveFailures());
        assertEquals(204, back.getExpectedStatus());
        assertEquals(check.getAssetId(), back.getAssetId());
        assertEquals(CheckStatus.ACTIVE, back.getStatus());
        assertEquals(now, back.getLastCheckedAt());
        assertEquals(7L, back.getLastLatencyMillis());
        assertEquals("timeout", back.getLastError());
        assertEquals(now, back.getLastStateChangeAt());
        assertEquals(check.getNextDueAt(), back.getNextDueAt());
        assertEquals(2, back.getAuditTrail().size());
        assertEquals("WENT_DOWN", back.getAuditTrail().get(1).action());
        assertEquals(check.getName(), document.getName());
        assertEquals("HTTP", document.getType());
        assertEquals(check.getTarget(), document.getTarget());
        assertEquals(30, document.getIntervalSeconds());
        assertEquals(1000, document.getTimeoutMillis());
        assertEquals(2, document.getFailureThreshold());
        assertEquals(2, document.getSuccessThreshold());
        assertEquals(0, document.getConsecutiveSuccesses());
        assertEquals(check.getVersion(), document.getVersion());
        assertEquals(check.getCreatedAt(), document.getCreatedAt());
        assertEquals(check.getUpdatedAt(), document.getUpdatedAt());
        assertEquals(org, document.getOrganisationId());
        assertEquals(check.getId(), document.getId());
    }

    @Test
    @DisplayName("the persistence mapper is a utility class")
    void mapperIsUtility() throws ReflectiveOperationException {
        Constructor<HealthCheckPersistenceMapper> constructor = HealthCheckPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    // ------------------------------------------------------------------ Checks

    @Test
    @DisplayName("create, lookups scoped to the organisation, and the filters of the list")
    void lookups() {
        HealthCheck check = check();
        when(checks.insertOne(any(HealthCheckDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));
        finds(checks, found -> HealthCheckPersistenceMapper.toDocument((HealthCheck) found), check);
        HealthCheckMongoRepositoryAdapter adapter = new HealthCheckMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.create(check)).expectNext(check).verifyComplete();
        StepVerifier.create(adapter.findById(check.getId(), org)).assertNext(found -> assertEquals(check.getId(), found.getId())).verifyComplete();
        StepVerifier.create(adapter.findAll(org, new Filter(Health.DOWN, CheckStatus.ACTIVE, UUID.randomUUID()))).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAll(org, new Filter(null, null, null))).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(checks, times(3)).find(filter.capture());
        assertTrue(filter.getAllValues().get(0).toString().contains("organisationId"));
        String full = filter.getAllValues().get(1).toString();
        assertTrue(full.contains("health") && full.contains("status") && full.contains("assetId") && full.contains("DOWN"));
        String bare = filter.getAllValues().get(2).toString();
        assertTrue(bare.contains("organisationId") && !bare.contains("health") && !bare.contains("assetId"));
    }

    @Test
    @DisplayName("a duplicate name is a 409 of the domain, whether on create or on a rename; any other write error passes through; the URI may omit the database")
    void duplicates() {
        HealthCheck check = check();
        when(checks.insertOne(any(HealthCheckDocument.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(1)));
        when(checks.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(2)));
        HealthCheckMongoRepositoryAdapter adapter = new HealthCheckMongoRepositoryAdapter(client, "mongodb://localhost:27017");

        StepVerifier.create(adapter.create(check)).expectError(DuplicateHealthCheckException.class).verify();
        StepVerifier.create(adapter.create(check)).expectError(MongoWriteException.class).verify();
        StepVerifier.create(adapter.save(check, 0, null)).expectError(DuplicateHealthCheckException.class).verify();
        StepVerifier.create(adapter.save(check, 0, null)).expectError(MongoWriteException.class).verify();
    }

    @Test
    @DisplayName("save is guarded by the version loaded; a lost race is a conflict; the audit entry is pushed only when there is one, capped")
    void save() {
        HealthCheck check = check();
        when(checks.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));
        HealthCheckMongoRepositoryAdapter adapter = new HealthCheckMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.save(check, 4, check.getAuditTrail().get(1))).verifyComplete();
        StepVerifier.create(adapter.save(check, 4, null)).verifyComplete();
        StepVerifier.create(adapter.save(check, 4, null)).expectError(InvalidHealthCheckStatusException.class).verify();

        ArgumentCaptor<Bson> guard = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(checks, times(3)).updateOne(guard.capture(), update.capture());
        assertTrue(guard.getAllValues().get(0).toString().contains("version") && guard.getAllValues().get(0).toString().contains("organisationId"));
        assertTrue(update.getAllValues().get(0).toString().contains("auditTrail"));
        assertTrue(!update.getAllValues().get(1).toString().contains("auditTrail"));
        assertTrue(update.getAllValues().get(0).toString().contains("version"));
    }

    @Test
    @DisplayName("claiming takes due active checks one by one, leasing each, and stops at the first nothing is due or at the limit")
    void claim() {
        HealthCheck a = check();
        HealthCheck b = check();
        when(checks.findOneAndUpdate(any(Bson.class), any(Bson.class), any()))
                .thenReturn(Mono.just(HealthCheckPersistenceMapper.toDocument(a)))
                .thenReturn(Mono.just(HealthCheckPersistenceMapper.toDocument(b)))
                .thenReturn(Mono.empty());
        HealthCheckMongoRepositoryAdapter adapter = new HealthCheckMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.claimDue(now, 5))
                .assertNext(claimed -> assertEquals(a.getId(), claimed.getId()))
                .assertNext(claimed -> assertEquals(b.getId(), claimed.getId()))
                .verifyComplete();

        verify(checks, times(3)).findOneAndUpdate(any(Bson.class), any(Bson.class), any());
    }

    // ------------------------------------------------------------------ Results

    @Test
    @DisplayName("a result is one small document; recent results come back newest first as results again, nulls kept null")
    void results() {
        UUID checkId = UUID.randomUUID();
        when(results.insertOne(any(Document.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));
        Document stored = new Document("checkId", checkId).append("organisationId", org).append("at", Date.from(now)).append("ok", false).append("statusCode", null)
                .append("latencyMillis", 12L).append("error", "timeout");
        finds(results, found -> (Document) found, stored);
        ProbeResultMongoRepositoryAdapter adapter = new ProbeResultMongoRepositoryAdapter(client, "mongodb://localhost:27017");

        StepVerifier.create(adapter.add(checkId, org, ProbeResult.down(now, 12, ProbeResult.TIMEOUT, null))).verifyComplete();
        new ProbeResultMongoRepositoryAdapter(client, URI);
        StepVerifier.create(adapter.findRecent(checkId, org, 10)).assertNext(result -> {
            assertEquals(now, result.at());
            assertEquals(false, result.ok());
            assertNull(result.statusCode());
            assertEquals(12L, result.latencyMillis());
            assertEquals("timeout", result.error());
        }).verifyComplete();

        ArgumentCaptor<Document> inserted = ArgumentCaptor.forClass(Document.class);
        verify(results).insertOne(inserted.capture());
        assertEquals(checkId, inserted.getValue().get("checkId"));
        assertEquals("timeout", inserted.getValue().get("error"));
        assertTrue(inserted.getValue().get("at") instanceof Date);
    }

    // ------------------------------------------------------------------ Indexes

    @Test
    @DisplayName("startup creates the unique name, the claim, the filter, the history and the TTL indexes")
    void indexes() {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> checkIndexes = mock(MongoCollection.class);
        MongoCollection<Document> resultIndexes = mock(MongoCollection.class);
        when(client.getDatabase("tenant_hlm")).thenReturn(database);
        when(database.getCollection("health_checks")).thenReturn(checkIndexes);
        when(database.getCollection("probe_results")).thenReturn(resultIndexes);
        when(checkIndexes.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));
        when(resultIndexes.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new com.thinklab.infrastructure.adapter.out.persistence.repository.HealthMonitoringIndexInitializer(client, "mongodb://mongo:27017/tenant_hlm")
                .onApplicationEvent(mock(io.micronaut.context.event.StartupEvent.class));

        ArgumentCaptor<IndexOptions> checkOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(checkIndexes, times(4)).createIndex(any(), checkOptions.capture());
        assertTrue(checkOptions.getAllValues().get(0).isUnique());
        assertEquals("organisationId_1_name_1", checkOptions.getAllValues().get(0).getName());
        ArgumentCaptor<IndexOptions> resultOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(resultIndexes, times(2)).createIndex(any(), resultOptions.capture());
        assertEquals(7, resultOptions.getAllValues().get(1).getExpireAfter(java.util.concurrent.TimeUnit.DAYS));
    }

    @Test
    @DisplayName("fail-open: an unreachable server or a rejected index is logged, never propagated; the URI may omit the database; arguments are null-checked")
    void indexesFailOpen() {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase("thinklab_it_health_monitoring_db")).thenReturn(database);
        when(database.getCollection(any(String.class))).thenReturn(collection);
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.error(new MongoTimeoutException("no server")))
                .thenReturn(Mono.error(new IllegalStateException("rejected"))).thenReturn(Mono.just("ok"));
        var initializer = new com.thinklab.infrastructure.adapter.out.persistence.repository.HealthMonitoringIndexInitializer(client, "mongodb://mongo:27017");

        initializer.onApplicationEvent(mock(io.micronaut.context.event.StartupEvent.class));

        verify(collection, times(6)).createIndex(any(), any(IndexOptions.class));
        assertThrows(NullPointerException.class, () -> initializer.onApplicationEvent(null));
        assertThrows(NullPointerException.class, () -> new com.thinklab.infrastructure.adapter.out.persistence.repository.HealthMonitoringIndexInitializer(null, URI));
        assertThrows(NullPointerException.class, () -> new com.thinklab.infrastructure.adapter.out.persistence.repository.HealthMonitoringIndexInitializer(client, null));
    }
}

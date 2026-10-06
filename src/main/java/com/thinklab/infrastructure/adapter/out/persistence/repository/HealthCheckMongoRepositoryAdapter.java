package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.PushOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.DuplicateHealthCheckException;
import com.thinklab.domain.exception.InvalidHealthCheckStatusException;
import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.HealthCheck.HealthAuditEntry;
import com.thinklab.domain.repository.HealthCheckRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.HealthCheckDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.HealthCheckDocument.AuditEntryDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.HealthCheckDocument.HealthCheckPersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter for the HealthCheck aggregate, raw reactive-streams driver. Every save is one atomic update guarded
 * by the version loaded (ADR-033) that also bumps it and appends the audit entry (capped at its last 200). The scheduler's claim is
 * one atomic find-and-update per check that leases it, so two instances never take the same one.
 */
@Singleton
public class HealthCheckMongoRepositoryAdapter implements HealthCheckRepository {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckMongoRepositoryAdapter.class);
    static final String DEFAULT_DATABASE = "thinklab_it_health_monitoring_db";
    static final String COLLECTION_NAME = "health_checks";
    static final int DUPLICATE_KEY = 11000;
    /** A claimed check is not due again for this long unless its result is recorded sooner; longer than the longest probe. */
    static final long LEASE_SECONDS = 60;
    private static final String FIELD_ID = "_id";
    private static final String FIELD_ORGANISATION = "organisationId";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public HealthCheckMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : DEFAULT_DATABASE;
    }

    private MongoCollection<HealthCheckDocument> getCollection() {
        return mongoClient.getDatabase(database).getCollection(COLLECTION_NAME, HealthCheckDocument.class).withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<HealthCheck> create(HealthCheck check) {
        log.debug("[PERSISTENCE] Monolithic create for HealthCheck Aggregate: {}", check.getId());

        return Mono.from(getCollection().insertOne(HealthCheckPersistenceMapper.toDocument(check)))
                .map(result -> check)
                .onErrorMap(MongoWriteException.class, error -> error.getError().getCode() == DUPLICATE_KEY
                        ? new DuplicateHealthCheckException("A health check named '" + check.getName() + "' already exists in this organisation.") : error);
    }

    @Override
    public Mono<HealthCheck> findById(UUID id, UUID organisationId) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId))).first())
                .map(HealthCheckPersistenceMapper::toDomain);
    }

    @Override
    public Flux<HealthCheck> findAll(UUID organisationId, Filter filter) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq(FIELD_ORGANISATION, organisationId));
        if (filter.health() != null) {
            filters.add(Filters.eq("health", filter.health().name()));
        }
        if (filter.status() != null) {
            filters.add(Filters.eq("status", filter.status().name()));
        }
        if (filter.assetId() != null) {
            filters.add(Filters.eq("assetId", filter.assetId()));
        }
        return Flux.from(getCollection().find(Filters.and(filters)).sort(Sorts.ascending("name"))).map(HealthCheckPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> save(HealthCheck check, long expectedVersion, HealthAuditEntry auditEntry) {
        Bson guard = Filters.and(Filters.eq(FIELD_ID, check.getId()), Filters.eq(FIELD_ORGANISATION, check.getOrganisationId()), Filters.eq("version", expectedVersion));
        HealthCheckDocument state = HealthCheckPersistenceMapper.toDocument(check);
        List<Bson> updates = new ArrayList<>(List.of(
                Updates.set("name", state.getName()),
                Updates.set("target", state.getTarget()),
                Updates.set("assetId", state.getAssetId()),
                Updates.set("intervalSeconds", state.getIntervalSeconds()),
                Updates.set("timeoutMillis", state.getTimeoutMillis()),
                Updates.set("expectedStatus", state.getExpectedStatus()),
                Updates.set("failureThreshold", state.getFailureThreshold()),
                Updates.set("successThreshold", state.getSuccessThreshold()),
                Updates.set("status", state.getStatus()),
                Updates.set("health", state.getHealth()),
                Updates.set("consecutiveFailures", state.getConsecutiveFailures()),
                Updates.set("consecutiveSuccesses", state.getConsecutiveSuccesses()),
                Updates.set("lastCheckedAt", state.getLastCheckedAt()),
                Updates.set("lastLatencyMillis", state.getLastLatencyMillis()),
                Updates.set("lastError", state.getLastError()),
                Updates.set("lastStateChangeAt", state.getLastStateChangeAt()),
                Updates.set("nextDueAt", state.getNextDueAt()),
                Updates.set("updatedAt", Instant.now()),
                Updates.inc("version", 1L)));
        Optional.ofNullable(auditEntry).ifPresent(entry -> updates.add(
                Updates.pushEach("auditTrail", List.of(AuditEntryDocument.fromDomain(entry)), new PushOptions().slice(-HealthCheck.MAX_AUDIT_ENTRIES))));
        return Mono.from(getCollection().updateOne(guard, Updates.combine(updates)))
                .flatMap(result -> result.getMatchedCount() == 0
                        ? Mono.error(new InvalidHealthCheckStatusException("HealthCheck was changed by someone else while this change was being recorded; read it again and retry."))
                        : Mono.<Void>empty())
                .onErrorMap(MongoWriteException.class, error -> error.getError().getCode() == DUPLICATE_KEY
                        ? new DuplicateHealthCheckException("A health check named '" + check.getName() + "' already exists in this organisation.") : error);
    }

    @Override
    public Flux<HealthCheck> claimDue(Instant now, int limit) {
        return Flux.range(0, limit)
                .concatMap(i -> claimOne(now).map(Optional::of).defaultIfEmpty(Optional.empty()))
                .takeWhile(Optional::isPresent)
                .map(Optional::get);
    }

    private Mono<HealthCheck> claimOne(Instant now) {
        Bson due = Filters.and(Filters.eq("status", "ACTIVE"), Filters.lte("nextDueAt", now));
        return Mono.from(getCollection().findOneAndUpdate(due, Updates.set("nextDueAt", now.plusSeconds(LEASE_SECONDS)),
                        new FindOneAndUpdateOptions().sort(Sorts.ascending("nextDueAt")).returnDocument(ReturnDocument.AFTER)))
                .map(HealthCheckPersistenceMapper::toDomain);
    }
}

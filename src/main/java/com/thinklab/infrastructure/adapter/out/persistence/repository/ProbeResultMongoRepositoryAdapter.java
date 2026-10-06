package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.model.ProbeResult;
import com.thinklab.domain.repository.ProbeResultRepository;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/** The expiring history of probe results: one small document per probe (a TTL index on {@code at} removes it), read newest first. */
@Singleton
public class ProbeResultMongoRepositoryAdapter implements ProbeResultRepository {

    static final String COLLECTION_NAME = "probe_results";

    private final MongoClient mongoClient;
    private final String database;

    public ProbeResultMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : HealthCheckMongoRepositoryAdapter.DEFAULT_DATABASE;
    }

    private MongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(database).getCollection(COLLECTION_NAME);
    }

    @Override
    public Mono<Void> add(UUID checkId, UUID organisationId, ProbeResult result) {
        Document entry = new Document("checkId", checkId).append("organisationId", organisationId).append("at", Date.from(result.at()))
                .append("ok", result.ok()).append("statusCode", result.statusCode()).append("latencyMillis", result.latencyMillis()).append("error", result.error());
        return Mono.from(getCollection().insertOne(entry)).then();
    }

    @Override
    public Flux<ProbeResult> findRecent(UUID checkId, UUID organisationId, int limit) {
        return Flux.from(getCollection().find(Filters.and(Filters.eq("checkId", checkId), Filters.eq("organisationId", organisationId)))
                        .sort(Sorts.descending("at")).limit(limit))
                .map(doc -> new ProbeResult(doc.getDate("at").toInstant(), doc.getBoolean("ok"), doc.getInteger("statusCode"), doc.getLong("latencyMillis"), doc.getString("error")));
    }
}

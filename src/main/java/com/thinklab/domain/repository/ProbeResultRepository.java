package com.thinklab.domain.repository;

import com.thinklab.domain.model.ProbeResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound Port for the history of probe results. Entries expire by themselves (a TTL index), so the collection does not grow for ever. */
public interface ProbeResultRepository {

    Mono<Void> add(UUID checkId, UUID organisationId, ProbeResult result);

    /** Newest first. */
    Flux<ProbeResult> findRecent(UUID checkId, UUID organisationId, int limit);
}

package com.thinklab.domain.port;

import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.HealthCheck.CheckType;
import com.thinklab.domain.model.ProbeResult;
import reactor.core.publisher.Mono;

/**
 * Outbound Port for one way of probing (HTTP or TCP, ADR-031). A probe NEVER fails as a stream: whatever goes wrong is a down
 * {@link ProbeResult} carrying one of its fixed error codes.
 */
public interface ProbePort {

    CheckType type();

    Mono<ProbeResult> probe(HealthCheck check);
}

package com.thinklab.application.dto.response;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record HealthCheckResponse(
        UUID id,
        UUID organisationId,
        String name,
        String type,
        String target,
        @Nullable UUID assetId,
        int intervalSeconds,
        int timeoutMillis,
        @Nullable Integer expectedStatus,
        int failureThreshold,
        int successThreshold,
        String status,
        String health,
        int consecutiveFailures,
        @Nullable Instant lastCheckedAt,
        @Nullable Long lastLatencyMillis,
        @Nullable String lastError,
        @Nullable Instant lastStateChangeAt,
        Instant createdAt,
        Instant updatedAt
) {}

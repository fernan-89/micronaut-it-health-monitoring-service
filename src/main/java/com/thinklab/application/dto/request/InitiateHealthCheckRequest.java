package com.thinklab.application.dto.request;

import com.thinklab.domain.model.HealthCheck.CheckType;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** DTO for {@code initiate}. Everything after the target is optional and takes its default (every 60 s, 5 s timeout, down after 3 failures, up after 1 success). */
@Serdeable
public record InitiateHealthCheckRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must not exceed 80 characters")
        String name,
        @NotNull(message = "Type is required")
        CheckType type,
        @NotBlank(message = "Target is required")
        @Size(max = 300, message = "Target must not exceed 300 characters")
        String target,
        @Nullable UUID assetId,
        @Nullable Integer intervalSeconds,
        @Nullable Integer timeoutMillis,
        @Nullable Integer expectedStatus,
        @Nullable Integer failureThreshold,
        @Nullable Integer successThreshold
) {}

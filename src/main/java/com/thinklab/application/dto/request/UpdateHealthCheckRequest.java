package com.thinklab.application.dto.request;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** DTO for {@code update}: the whole definition again (the type cannot change). What is left out takes its default. */
@Serdeable
public record UpdateHealthCheckRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must not exceed 80 characters")
        String name,
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

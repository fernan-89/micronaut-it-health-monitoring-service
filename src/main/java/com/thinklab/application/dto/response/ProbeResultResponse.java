package com.thinklab.application.dto.response;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
public record ProbeResultResponse(Instant at, boolean ok, @Nullable Integer statusCode, long latencyMillis, @Nullable String error) {}

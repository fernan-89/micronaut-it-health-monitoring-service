package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

/** How many checks of the tenant are in each state; {@code paused} counts the ones not being probed, whatever their last health. */
@Serdeable
public record HealthSummaryResponse(int total, int up, int down, int unknown, int paused) {}

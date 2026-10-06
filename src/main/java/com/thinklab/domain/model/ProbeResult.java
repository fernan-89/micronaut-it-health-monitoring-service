package com.thinklab.domain.model;

import java.time.Instant;

/**
 * What one probe saw. {@code error} is ALWAYS one of the short codes below, never a message taken from the target or the network stack
 * (those can carry addresses, tokens or banners): what is stored and shown is a fixed vocabulary (ADR-031).
 */
public record ProbeResult(Instant at, boolean ok, Integer statusCode, long latencyMillis, String error) {

    public static final String TIMEOUT = "timeout";
    public static final String REFUSED = "connection refused";
    public static final String DNS = "dns failure";
    public static final String UNEXPECTED_STATUS = "unexpected status";
    public static final String BLOCKED = "address not allowed";
    public static final String FAILED = "probe failed";

    public static ProbeResult up(Instant at, long latencyMillis, Integer statusCode) {
        return new ProbeResult(at, true, statusCode, latencyMillis, null);
    }

    public static ProbeResult down(Instant at, long latencyMillis, String error, Integer statusCode) {
        return new ProbeResult(at, false, statusCode, latencyMillis, error);
    }
}

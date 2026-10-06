package com.thinklab.application.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Settings of the monitor (ADR-031). {@code allowLoopback} lets checks watch this very host (a test double, a local stack); real
 * deployments leave it off. The scheduler claims at most {@code claimBatch} due checks per tick and probes up to {@code concurrency} at once.
 */
@ConfigurationProperties("thinklab.health-monitoring")
public class HealthMonitoringProperties {

    private boolean allowLoopback = false;
    private int claimBatch = 20;
    private int concurrency = 10;

    public boolean isAllowLoopback() {
        return allowLoopback;
    }

    public void setAllowLoopback(boolean allowLoopback) {
        this.allowLoopback = allowLoopback;
    }

    public int getClaimBatch() {
        return claimBatch;
    }

    public void setClaimBatch(int claimBatch) {
        this.claimBatch = claimBatch;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }
}

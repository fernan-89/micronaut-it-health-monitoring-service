package com.thinklab.application.mapper;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.HealthCheckResponse;
import com.thinklab.application.dto.response.ProbeResultResponse;
import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.HealthCheck.HealthAuditEntry;
import com.thinklab.domain.model.ProbeResult;

public final class HealthCheckMapper {

    private HealthCheckMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static HealthCheckResponse toResponse(HealthCheck check) {
        return new HealthCheckResponse(check.getId(), check.getOrganisationId(), check.getName(), check.getType().name(), check.getTarget(), check.getAssetId(),
                check.getIntervalSeconds(), check.getTimeoutMillis(), check.getExpectedStatus(), check.getFailureThreshold(), check.getSuccessThreshold(),
                check.getStatus().name(), check.getHealth().name(), check.getConsecutiveFailures(), check.getLastCheckedAt(), check.getLastLatencyMillis(),
                check.getLastError(), check.getLastStateChangeAt(), check.getCreatedAt(), check.getUpdatedAt());
    }

    public static ProbeResultResponse toResponse(ProbeResult result) {
        return new ProbeResultResponse(result.at(), result.ok(), result.statusCode(), result.latencyMillis(), result.error());
    }

    public static AuditEntryResponse toResponse(HealthAuditEntry entry) {
        return new AuditEntryResponse(entry.occurredAt(), entry.action(), entry.executor(), entry.from(), entry.to(), entry.detail());
    }
}

package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.InitiateHealthCheckRequest;
import com.thinklab.application.dto.request.UpdateHealthCheckRequest;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.HealthCheckResponse;
import com.thinklab.application.dto.response.HealthSummaryResponse;
import com.thinklab.application.dto.response.ProbeResultResponse;
import com.thinklab.application.usecase.ControlHealthCheckUseCase;
import com.thinklab.application.usecase.InitiateHealthCheckUseCase;
import com.thinklab.application.usecase.RetrieveHealthCheckAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveHealthCheckUseCase;
import com.thinklab.application.usecase.RetrieveHealthChecksUseCase;
import com.thinklab.application.usecase.RetrieveHealthSummaryUseCase;
import com.thinklab.application.usecase.RetrieveProbeResultsUseCase;
import com.thinklab.application.usecase.RunHealthCheckUseCase;
import com.thinklab.application.usecase.UpdateHealthCheckUseCase;
import com.thinklab.domain.model.HealthCheck.CheckStatus;
import com.thinklab.domain.model.HealthCheck.CheckType;
import com.thinklab.domain.model.HealthCheck.Health;
import com.thinklab.domain.repository.HealthCheckRepository;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** The controller only reads headers and delegates: tenant, executor and role always travel to the use case. */
@ExtendWith(MockitoExtension.class)
class HealthMonitoringControllerTest {

    private static final String EXECUTOR = "op-1";
    private final UUID tenant = UUID.randomUUID();
    private final String tenantHeader = tenant.toString();
    private final UUID id = UUID.randomUUID();

    @Mock private InitiateHealthCheckUseCase initiate;
    @Mock private RetrieveHealthCheckUseCase retrieve;
    @Mock private RetrieveHealthChecksUseCase retrieveAll;
    @Mock private UpdateHealthCheckUseCase update;
    @Mock private ControlHealthCheckUseCase control;
    @Mock private RunHealthCheckUseCase run;
    @Mock private RetrieveProbeResultsUseCase results;
    @Mock private RetrieveHealthCheckAuditLogUseCase auditLog;
    @Mock private RetrieveHealthSummaryUseCase summary;

    private HealthMonitoringController controller;

    @BeforeEach
    void setUp() {
        controller = new HealthMonitoringController(initiate, retrieve, retrieveAll, update, control, run, results, auditLog, summary);
    }

    private HealthCheckResponse response() {
        return new HealthCheckResponse(id, tenant, "API", "HTTP", "https://x.test", null, 60, 5000, null, 3, 1, "ACTIVE", "UP", 0, null, null, null, null, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("the write routes delegate with the tenant, the executor and the role")
    void writes() {
        var initiateRequest = new InitiateHealthCheckRequest("API", CheckType.HTTP, "https://x.test", null, null, null, null, null, null);
        var updateRequest = new UpdateHealthCheckRequest("API", "https://x.test", null, null, null, null, null, null);
        when(initiate.execute(tenant, initiateRequest, EXECUTOR, "AGENT")).thenReturn(Mono.just(response()));
        when(update.execute(id, tenant, updateRequest, EXECUTOR, "AGENT")).thenReturn(Mono.empty());
        when(control.execute(id, tenant, ControlHealthCheckUseCase.Action.PAUSE, EXECUTOR, "AGENT")).thenReturn(Mono.empty());
        when(control.execute(id, tenant, ControlHealthCheckUseCase.Action.RESUME, EXECUTOR, "AGENT")).thenReturn(Mono.empty());
        when(run.execute(id, tenant, EXECUTOR, "AGENT")).thenReturn(Mono.just(response()));

        StepVerifier.create(controller.initiate(tenantHeader, EXECUTOR, "AGENT", initiateRequest)).assertNext(r -> assertEquals(HttpStatus.CREATED, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.update(id, tenantHeader, EXECUTOR, "AGENT", updateRequest)).assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.pause(id, tenantHeader, EXECUTOR, "AGENT")).assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.resume(id, tenantHeader, EXECUTOR, "AGENT")).assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.execute(id, tenantHeader, EXECUTOR, "AGENT")).assertNext(r -> assertEquals(HttpStatus.OK, r.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("the read routes delegate with the tenant and the role, and the filters of the list travel together")
    void reads() {
        UUID asset = UUID.randomUUID();
        var filter = new HealthCheckRepository.Filter(Health.DOWN, CheckStatus.ACTIVE, asset);
        when(retrieve.execute(id, tenant, "AGENT")).thenReturn(Mono.just(response()));
        when(retrieveAll.execute(tenant, filter, "AGENT")).thenReturn(Flux.just(response()));
        when(summary.execute(tenant, "AGENT")).thenReturn(Mono.just(new HealthSummaryResponse(1, 1, 0, 0, 0)));
        when(results.execute(id, tenant, 20, "AGENT")).thenReturn(Mono.just(List.of(new ProbeResultResponse(Instant.now(), true, 200, 5, null))));
        when(auditLog.execute(id, tenant, "AGENT")).thenReturn(Mono.just(List.of(new AuditEntryResponse(Instant.now(), "INITIATED", "op", null, "UNKNOWN", "d"))));

        StepVerifier.create(controller.retrieveById(id, tenantHeader, "AGENT")).assertNext(r -> assertEquals(HttpStatus.OK, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.retrieveAll(tenantHeader, "AGENT", Health.DOWN, CheckStatus.ACTIVE, asset)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
        StepVerifier.create(controller.summary(tenantHeader, "AGENT")).assertNext(s -> assertEquals(1, s.total())).verifyComplete();
        StepVerifier.create(controller.retrieveResults(id, tenantHeader, "AGENT", 20)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
        StepVerifier.create(controller.retrieveAuditLog(id, tenantHeader, "AGENT")).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }
}

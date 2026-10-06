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
import com.thinklab.domain.model.HealthCheck.Health;
import com.thinklab.domain.repository.HealthCheckRepository;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code it-health-monitoring} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.HealthCheck} is the Control Record. Every route follows
 * {@code /it-health-monitoring/v1/{control-record-id}/{behavior-qualifier}}. There is no {@code DELETE}: a check is paused.
 *
 * <p><b>Tenant on every route, staff only (ADR-032):</b> {@code X-Tenant-Id} scopes every lookup (another tenant's check is a 404) and
 * {@code X-Role}, set by the kit's {@code SecurityFilter} from the verified token when security is on, makes every route refuse a
 * {@code REQUESTER} with 403 {@code ERR-HLM-00403}.
 */
@Controller("/it-health-monitoring/v1")
public class HealthMonitoringController {

    private static final Logger log = LoggerFactory.getLogger(HealthMonitoringController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";
    static final String ROLE_HEADER = "X-Role";

    private final InitiateHealthCheckUseCase initiate;
    private final RetrieveHealthCheckUseCase retrieve;
    private final RetrieveHealthChecksUseCase retrieveAll;
    private final UpdateHealthCheckUseCase update;
    private final ControlHealthCheckUseCase control;
    private final RunHealthCheckUseCase run;
    private final RetrieveProbeResultsUseCase results;
    private final RetrieveHealthCheckAuditLogUseCase auditLog;
    private final RetrieveHealthSummaryUseCase summary;

    public HealthMonitoringController(InitiateHealthCheckUseCase initiate, RetrieveHealthCheckUseCase retrieve, RetrieveHealthChecksUseCase retrieveAll,
                                      UpdateHealthCheckUseCase update, ControlHealthCheckUseCase control, RunHealthCheckUseCase run,
                                      RetrieveProbeResultsUseCase results, RetrieveHealthCheckAuditLogUseCase auditLog, RetrieveHealthSummaryUseCase summary) {
        this.initiate = initiate;
        this.retrieve = retrieve;
        this.retrieveAll = retrieveAll;
        this.update = update;
        this.control = control;
        this.run = run;
        this.results = results;
        this.auditLog = auditLog;
        this.summary = summary;
    }

    /** Behavior Qualifier: {@code initiate}. Starts watching an HTTP address or a TCP host and port. */
    @Post("/initiate")
    public Mono<HttpResponse<HealthCheckResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role,
            @Body @Valid InitiateHealthCheckRequest request
    ) {
        log.info("[ACTION: INITIATE_HEALTH_CHECK] [EXECUTOR: {}] Received request for organisation: {}", executor, tenantId);

        return initiate.execute(UUID.fromString(tenantId), request, executor, role).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. One check of the tenant. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<HealthCheckResponse>> retrieveById(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrieve.execute(id, UUID.fromString(tenantId), role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection), by health, status and the asset it watches. */
    @Get("/retrieve")
    public Mono<List<HealthCheckResponse>> retrieveAll(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(ROLE_HEADER) @Nullable String role,
            @QueryValue @Nullable Health health,
            @QueryValue @Nullable CheckStatus status,
            @QueryValue @Nullable UUID assetId
    ) {
        return Mono.defer(() -> retrieveAll.execute(UUID.fromString(tenantId), new HealthCheckRepository.Filter(health, status, assetId), role).collectList());
    }

    /** Behavior Qualifier: {@code summary/retrieve}. How many checks are up, down, not known yet, and paused. */
    @Get("/summary/retrieve")
    public Mono<HealthSummaryResponse> summary(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> summary.execute(UUID.fromString(tenantId), role));
    }

    /** Behavior Qualifier: {@code update}. The definition again; the type cannot change, and a new target starts the health from UNKNOWN. */
    @Put("/{id}/update")
    public Mono<HttpResponse<Void>> update(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid UpdateHealthCheckRequest request
    ) {
        return Mono.defer(() -> update.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/pause}. ACTIVE -&gt; PAUSED: no longer probed on its own. */
    @Put("/{id}/control/pause")
    public Mono<HttpResponse<Void>> pause(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                          @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlHealthCheckUseCase.Action.PAUSE, executor, role);
    }

    /** Behavior Qualifier: {@code control/resume}. PAUSED -&gt; ACTIVE, probed at once. */
    @Put("/{id}/control/resume")
    public Mono<HttpResponse<Void>> resume(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                           @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlHealthCheckUseCase.Action.RESUME, executor, role);
    }

    /** Behavior Qualifier: {@code check/execute}. Probes right now, whatever the status, and answers the check as it is after. */
    @Put("/{id}/check/execute")
    public Mono<HttpResponse<HealthCheckResponse>> execute(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                           @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> run.execute(id, UUID.fromString(tenantId), executor, role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code results/retrieve}. The recent probes, newest first (they expire after 7 days). */
    @Get("/{id}/results/retrieve")
    public Mono<List<ProbeResultResponse>> retrieveResults(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                           @Header(ROLE_HEADER) @Nullable String role, @QueryValue @Nullable Integer limit) {
        return Mono.defer(() -> results.execute(id, UUID.fromString(tenantId), limit, role));
    }

    /** Behavior Qualifier: {@code audit-log/retrieve}. Changes of health and staff actions. */
    @Get("/{id}/audit-log/retrieve")
    public Mono<List<AuditEntryResponse>> retrieveAuditLog(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> auditLog.execute(id, UUID.fromString(tenantId), role));
    }

    private Mono<HttpResponse<Void>> control(UUID id, String tenantId, ControlHealthCheckUseCase.Action action, String executor, String role) {
        log.info("[ACTION: CONTROL_HEALTH_CHECK] [EXECUTOR: {}] {} for ID: {}", executor, action, id);

        return Mono.defer(() -> control.execute(id, UUID.fromString(tenantId), action, executor, role)).thenReturn(HttpResponse.noContent());
    }
}

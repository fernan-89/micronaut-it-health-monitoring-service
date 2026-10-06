package com.thinklab.application.usecase;

import com.thinklab.application.config.HealthMonitoringProperties;
import com.thinklab.application.dto.request.InitiateHealthCheckRequest;
import com.thinklab.application.dto.request.UpdateHealthCheckRequest;
import com.thinklab.application.mapper.HealthCheckMapper;
import com.thinklab.domain.exception.HealthAccessDeniedException;
import com.thinklab.domain.exception.HealthCheckNotFoundException;
import com.thinklab.domain.exception.InvalidHealthCheckStatusException;
import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.HealthCheck.CheckStatus;
import com.thinklab.domain.model.HealthCheck.CheckType;
import com.thinklab.domain.model.HealthCheck.Health;
import com.thinklab.domain.model.ProbeResult;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.port.ProbePort;
import com.thinklab.domain.repository.HealthCheckRepository;
import com.thinklab.domain.repository.ProbeResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthUseCaseTest {

    @Mock private HealthCheckRepository repository;
    @Mock private ProbeResultRepository results;
    @Mock private HashServicePort hashService;
    @Mock private ProbePort httpProbe;

    private final UUID org = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-06T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final HealthMonitoringProperties properties = new HealthMonitoringProperties();
    private TargetPolicy policy;
    private ProbeRecorder recorder;

    @BeforeEach
    void setUp() {
        policy = new TargetPolicy(properties);
        lenient().when(httpProbe.type()).thenReturn(CheckType.HTTP);
        lenient().when(repository.save(any(), anyLong(), any())).thenReturn(Mono.empty());
        recorder = new ProbeRecorder(new ProbeRegistry(List.of(httpProbe)), repository, results, clock);
    }

    private HealthCheck check() {
        return HealthCheck.createNew(UUID.randomUUID(), org, "API", CheckType.HTTP, "https://api.acme.test/health", null, null, null, null, 1, 1, "op");
    }

    private InitiateHealthCheckRequest request(CheckType type, String target) {
        return new InitiateHealthCheckRequest("API", type, target, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("the properties hold what they are given")
    void properties() {
        properties.setClaimBatch(5);
        properties.setConcurrency(2);

        assertEquals(5, properties.getClaimBatch());
        assertEquals(2, properties.getConcurrency());
        assertFalse(new HealthMonitoringProperties().isAllowLoopback());
    }

    // ------------------------------------------------------------------ Initiate / update / control

    @Test
    @DisplayName("initiate checks the policy, takes a sovereign id and saves the check")
    void initiate() {
        when(hashService.generateSovereignId("health-check-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(repository.create(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(new InitiateHealthCheckUseCase(hashService, repository, policy).execute(org, request(CheckType.HTTP, " https://api.acme.test/health "), "op", "AGENT"))
                .assertNext(response -> {
                    assertEquals("https://api.acme.test/health", response.target());
                    assertEquals("UNKNOWN", response.health());
                    assertEquals("ACTIVE", response.status());
                    assertEquals(60, response.intervalSeconds());
                }).verifyComplete();
    }

    @Test
    @DisplayName("initiate refuses a requester and a forbidden target before taking an id")
    void initiateRefusals() {
        InitiateHealthCheckUseCase useCase = new InitiateHealthCheckUseCase(hashService, repository, policy);

        StepVerifier.create(useCase.execute(org, request(CheckType.HTTP, "https://x.test"), "op", "REQUESTER")).expectError(HealthAccessDeniedException.class).verify();
        StepVerifier.create(useCase.execute(org, request(CheckType.HTTP, "http://169.254.169.254/"), "op", null)).expectError(IllegalArgumentException.class).verify();
        verify(hashService, never()).generateSovereignId(any());
    }

    @Test
    @DisplayName("update checks the new target against the policy, then saves with the version loaded")
    void update() {
        HealthCheck check = check();
        when(repository.findById(check.getId(), org)).thenReturn(Mono.just(check));
        UpdateHealthCheckUseCase useCase = new UpdateHealthCheckUseCase(new HealthCheckWorkflow(repository), repository, policy);

        StepVerifier.create(useCase.execute(check.getId(), org, new UpdateHealthCheckRequest("API 2", " https://api2.acme.test/health ", null, 120, null, null, null, null), "op", "AGENT"))
                .verifyComplete();

        verify(repository).save(eq(check), eq(0L), any());
        assertEquals("https://api2.acme.test/health", check.getTarget());
    }

    @Test
    @DisplayName("update refuses a requester, an unknown check and a forbidden new target")
    void updateRefusals() {
        UpdateHealthCheckUseCase useCase = new UpdateHealthCheckUseCase(new HealthCheckWorkflow(repository), repository, policy);
        UUID id = UUID.randomUUID();
        UpdateHealthCheckRequest request = new UpdateHealthCheckRequest("n", "http://169.254.169.254/", null, null, null, null, null, null);

        StepVerifier.create(useCase.execute(id, org, request, "op", "REQUESTER")).expectError(HealthAccessDeniedException.class).verify();
        when(repository.findById(id, org)).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(id, org, request, "op", null)).expectError(HealthCheckNotFoundException.class).verify();
        HealthCheck check = check();
        when(repository.findById(check.getId(), org)).thenReturn(Mono.just(check));
        StepVerifier.create(useCase.execute(check.getId(), org, request, "op", null)).expectError(IllegalArgumentException.class).verify();
    }

    @Test
    @DisplayName("pause and resume go through the workflow; a requester and an unknown check are refused")
    void control() {
        HealthCheck check = check();
        when(repository.findById(check.getId(), org)).thenReturn(Mono.just(check));
        ControlHealthCheckUseCase useCase = new ControlHealthCheckUseCase(new HealthCheckWorkflow(repository));

        StepVerifier.create(useCase.execute(check.getId(), org, ControlHealthCheckUseCase.Action.PAUSE, "op", "AGENT")).verifyComplete();
        assertEquals(CheckStatus.PAUSED, check.getStatus());
        StepVerifier.create(useCase.execute(check.getId(), org, ControlHealthCheckUseCase.Action.RESUME, "op", null)).verifyComplete();
        assertEquals(CheckStatus.ACTIVE, check.getStatus());
        StepVerifier.create(useCase.execute(check.getId(), org, ControlHealthCheckUseCase.Action.PAUSE, "op", "REQUESTER")).expectError(HealthAccessDeniedException.class).verify();
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing, org)).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(missing, org, ControlHealthCheckUseCase.Action.PAUSE, "op", null)).expectError(HealthCheckNotFoundException.class).verify();
    }

    // ------------------------------------------------------------------ Probing

    @Test
    @DisplayName("probing records the result in the history and applies it to a fresh copy of the check, saving with the version it had")
    void probeAndRecord() {
        HealthCheck loaded = check();
        HealthCheck fresh = check();
        when(httpProbe.probe(loaded)).thenReturn(Mono.just(ProbeResult.down(now, 5, ProbeResult.REFUSED, null)));
        when(results.add(eq(loaded.getId()), eq(org), any())).thenReturn(Mono.empty());
        when(repository.findById(loaded.getId(), org)).thenReturn(Mono.just(fresh));

        StepVerifier.create(recorder.probeAndRecord(loaded, "system:health-monitor")).expectNext(fresh).verifyComplete();

        assertEquals(Health.DOWN, fresh.getHealth());
        verify(repository).save(eq(fresh), eq(0L), any());
    }

    @Test
    @DisplayName("a result that changes nothing is saved without an audit entry")
    void noEntryWhenNothingChanged() {
        HealthCheck loaded = check();
        loaded.recordResult(ProbeResult.up(now.minusSeconds(60), 1, 200), "m");
        when(httpProbe.probe(loaded)).thenReturn(Mono.just(ProbeResult.up(now, 5, 200)));
        when(results.add(any(), any(), any())).thenReturn(Mono.empty());
        when(repository.findById(loaded.getId(), org)).thenReturn(Mono.just(loaded));

        StepVerifier.create(recorder.probeAndRecord(loaded, "m")).expectNext(loaded).verifyComplete();

        verify(repository).save(eq(loaded), eq(0L), eq(null));
    }

    @Test
    @DisplayName("a probe that errors unexpectedly is a down result, and a history that cannot be written does not stop the recording")
    void defensive() {
        HealthCheck loaded = check();
        when(httpProbe.probe(loaded)).thenReturn(Mono.error(new IllegalStateException("boom")));
        when(results.add(any(), any(), any())).thenReturn(Mono.error(new IllegalStateException("mongo down")));
        when(repository.findById(loaded.getId(), org)).thenReturn(Mono.just(loaded));

        StepVerifier.create(recorder.probeAndRecord(loaded, "m")).expectNext(loaded).verifyComplete();

        assertEquals(ProbeResult.FAILED, loaded.getLastError());
        assertEquals(Health.DOWN, loaded.getHealth());
    }

    @Test
    @DisplayName("a check deleted meanwhile is skipped; a lost race is retried, and dropped after three attempts")
    void raceAndDeletion() {
        HealthCheck loaded = check();
        when(httpProbe.probe(loaded)).thenReturn(Mono.just(ProbeResult.up(now, 5, 200)));
        when(results.add(any(), any(), any())).thenReturn(Mono.empty());
        when(repository.findById(loaded.getId(), org)).thenReturn(Mono.empty());
        StepVerifier.create(recorder.probeAndRecord(loaded, "m")).verifyComplete();

        when(repository.findById(loaded.getId(), org)).thenAnswer(call -> Mono.just(check()));
        when(repository.save(any(), anyLong(), any()))
                .thenReturn(Mono.error(new InvalidHealthCheckStatusException("lost")))
                .thenReturn(Mono.empty());
        StepVerifier.create(recorder.probeAndRecord(loaded, "m")).expectNextCount(1).verifyComplete();

        when(repository.save(any(), anyLong(), any())).thenReturn(Mono.error(new InvalidHealthCheckStatusException("lost")));
        StepVerifier.create(recorder.probeAndRecord(loaded, "m")).verifyComplete();
    }

    @Test
    @DisplayName("a probe round claims the due checks, probes them and counts them; one failing never stops the others")
    void probeRound() {
        properties.setClaimBatch(3);
        HealthCheck a = check();
        HealthCheck b = check();
        when(repository.claimDue(now, 3)).thenReturn(Flux.just(a, b));
        when(httpProbe.probe(a)).thenReturn(Mono.just(ProbeResult.up(now, 1, 200)));
        when(httpProbe.probe(b)).thenReturn(Mono.just(ProbeResult.up(now, 1, 200)));
        when(results.add(any(), any(), any())).thenReturn(Mono.empty());
        when(repository.findById(a.getId(), org)).thenReturn(Mono.error(new IllegalStateException("mongo down")));
        when(repository.findById(b.getId(), org)).thenReturn(Mono.just(b));

        StepVerifier.create(new ProbeRoundUseCase(repository, recorder, properties, clock).execute()).expectNext(2L).verifyComplete();

        assertEquals(Health.UP, b.getHealth());
    }

    @Test
    @DisplayName("run now probes whatever the status, answers the check after the probe, and falls back to reading it when the write lost the race")
    void runNow() {
        HealthCheck loaded = check();
        HealthCheck fresh = check();
        when(repository.findById(loaded.getId(), org)).thenReturn(Mono.just(loaded)).thenReturn(Mono.just(fresh));
        when(httpProbe.probe(loaded)).thenReturn(Mono.just(ProbeResult.up(now, 3, 200)));
        when(results.add(any(), any(), any())).thenReturn(Mono.empty());
        RunHealthCheckUseCase useCase = new RunHealthCheckUseCase(repository, recorder);

        StepVerifier.create(useCase.execute(loaded.getId(), org, "op", "AGENT")).assertNext(response -> assertEquals("UP", response.health())).verifyComplete();

        when(repository.findById(loaded.getId(), org)).thenReturn(Mono.just(loaded)).thenReturn(Mono.empty()).thenReturn(Mono.just(fresh));
        StepVerifier.create(useCase.execute(loaded.getId(), org, "op", null)).expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(loaded.getId(), org, "op", "REQUESTER")).expectError(HealthAccessDeniedException.class).verify();
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing, org)).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(missing, org, "op", null)).expectError(HealthCheckNotFoundException.class).verify();
    }

    // ------------------------------------------------------------------ Reads

    @Test
    @DisplayName("retrieve, list, results, audit log and summary are for staff, scoped to the tenant, and 404 on an unknown check")
    void reads() {
        HealthCheck up = check();
        up.recordResult(ProbeResult.up(now, 1, 200), "m");
        HealthCheck down = check();
        down.recordResult(ProbeResult.down(now, 1, ProbeResult.TIMEOUT, null), "m");
        HealthCheck unknown = HealthCheck.createNew(UUID.randomUUID(), org, "U", CheckType.TCP, "h:22", null, null, null, null, null, null, "op");
        HealthCheck paused = check();
        paused.pause("op");
        HealthCheckRepository.Filter filter = new HealthCheckRepository.Filter(null, null, null);
        when(repository.findById(up.getId(), org)).thenReturn(Mono.just(up));
        when(repository.findAll(org, filter)).thenReturn(Flux.just(up, down, unknown, paused));
        when(results.findRecent(up.getId(), org, 50)).thenReturn(Flux.just(ProbeResult.up(now, 1, 200)));
        when(results.findRecent(up.getId(), org, 500)).thenReturn(Flux.empty());
        when(results.findRecent(up.getId(), org, 1)).thenReturn(Flux.empty());

        StepVerifier.create(new RetrieveHealthCheckUseCase(repository).execute(up.getId(), org, "AGENT")).assertNext(r -> assertEquals("UP", r.health())).verifyComplete();
        StepVerifier.create(new RetrieveHealthChecksUseCase(repository).execute(org, filter, "AGENT")).expectNextCount(4).verifyComplete();
        StepVerifier.create(new RetrieveHealthSummaryUseCase(repository).execute(org, "AGENT"))
                .assertNext(s -> assertEquals("4/1/1/1/1", s.total() + "/" + s.up() + "/" + s.down() + "/" + s.unknown() + "/" + s.paused())).verifyComplete();
        StepVerifier.create(new RetrieveProbeResultsUseCase(repository, results).execute(up.getId(), org, null, "AGENT")).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
        StepVerifier.create(new RetrieveProbeResultsUseCase(repository, results).execute(up.getId(), org, 9999, "AGENT")).assertNext(list -> assertTrue(list.isEmpty())).verifyComplete();
        StepVerifier.create(new RetrieveProbeResultsUseCase(repository, results).execute(up.getId(), org, -5, "AGENT")).assertNext(list -> assertTrue(list.isEmpty())).verifyComplete();
        StepVerifier.create(new RetrieveHealthCheckAuditLogUseCase(repository).execute(up.getId(), org, "AGENT"))
                .assertNext(entries -> assertEquals(List.of("INITIATED", "BECAME_UP"), entries.stream().map(e -> e.action()).toList())).verifyComplete();

        StepVerifier.create(new RetrieveHealthCheckUseCase(repository).execute(up.getId(), org, "REQUESTER")).expectError(HealthAccessDeniedException.class).verify();
        StepVerifier.create(new RetrieveHealthChecksUseCase(repository).execute(org, filter, "REQUESTER")).expectError(HealthAccessDeniedException.class).verify();
        StepVerifier.create(new RetrieveHealthSummaryUseCase(repository).execute(org, "REQUESTER")).expectError(HealthAccessDeniedException.class).verify();
        StepVerifier.create(new RetrieveProbeResultsUseCase(repository, results).execute(up.getId(), org, null, "REQUESTER")).expectError(HealthAccessDeniedException.class).verify();
        StepVerifier.create(new RetrieveHealthCheckAuditLogUseCase(repository).execute(up.getId(), org, "REQUESTER")).expectError(HealthAccessDeniedException.class).verify();
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing, org)).thenReturn(Mono.empty());
        StepVerifier.create(new RetrieveHealthCheckUseCase(repository).execute(missing, org, null)).expectError(HealthCheckNotFoundException.class).verify();
        StepVerifier.create(new RetrieveProbeResultsUseCase(repository, results).execute(missing, org, null, null)).expectError(HealthCheckNotFoundException.class).verify();
        StepVerifier.create(new RetrieveHealthCheckAuditLogUseCase(repository).execute(missing, org, null)).expectError(HealthCheckNotFoundException.class).verify();
    }

    @Test
    @DisplayName("the mapper turns a result and an entry into responses, keeping a missing status code missing")
    void mapper() {
        var result = HealthCheckMapper.toResponse(ProbeResult.down(now, 9, ProbeResult.DNS, null));
        assertNull(result.statusCode());
        assertEquals("dns failure", result.error());
        var entry = HealthCheckMapper.toResponse(check().getAuditTrail().get(0));
        assertNull(entry.fromStatus());
        assertEquals("UNKNOWN", entry.toStatus());
        verify(results, times(0)).add(any(), any(), any());
    }
}

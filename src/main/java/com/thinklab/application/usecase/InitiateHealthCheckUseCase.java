package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateHealthCheckRequest;
import com.thinklab.application.dto.response.HealthCheckResponse;
import com.thinklab.application.mapper.HealthCheckMapper;
import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.HealthCheckRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for starting to watch something (BIAN Behavior Qualifier: {@code initiate}). The target is checked against the policy before anything else. */
@Singleton
public class InitiateHealthCheckUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateHealthCheckUseCase.class);

    private final HashServicePort hashServicePort;
    private final HealthCheckRepository repository;
    private final TargetPolicy targetPolicy;

    public InitiateHealthCheckUseCase(HashServicePort hashServicePort, HealthCheckRepository repository, TargetPolicy targetPolicy) {
        this.hashServicePort = hashServicePort;
        this.repository = repository;
        this.targetPolicy = targetPolicy;
    }

    public Mono<HealthCheckResponse> execute(UUID organisationId, InitiateHealthCheckRequest request, String executor, String role) {
        log.info("[USE CASE] Watching {} {} for organisation: {}", request.type(), request.name(), organisationId);

        return Mono.fromRunnable(() -> {
                    HealthAccess.requireStaff(role, "start monitoring something");
                    targetPolicy.check(request.type(), request.target().trim());
                })
                .then(Mono.defer(() -> hashServicePort.generateSovereignId("health-check-creation")))
                .map(id -> HealthCheck.createNew(id, organisationId, request.name(), request.type(), request.target().trim(), request.assetId(),
                        request.intervalSeconds(), request.timeoutMillis(), request.expectedStatus(), request.failureThreshold(), request.successThreshold(), executor))
                .flatMap(repository::create)
                .map(HealthCheckMapper::toResponse);
    }
}

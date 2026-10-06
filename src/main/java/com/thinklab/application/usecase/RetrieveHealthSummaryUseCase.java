package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.HealthSummaryResponse;
import com.thinklab.domain.model.HealthCheck;
import com.thinklab.domain.model.HealthCheck.CheckStatus;
import com.thinklab.domain.model.HealthCheck.Health;
import com.thinklab.domain.repository.HealthCheckRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/** Use Case for the one-glance count (BIAN Behavior Qualifier: {@code summary/retrieve}): of the checks being probed, how many are up, down or not known yet; and how many are paused. */
@Singleton
public class RetrieveHealthSummaryUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveHealthSummaryUseCase.class);

    private final HealthCheckRepository repository;

    public RetrieveHealthSummaryUseCase(HealthCheckRepository repository) {
        this.repository = repository;
    }

    public Mono<HealthSummaryResponse> execute(UUID organisationId, String role) {
        log.info("[USE CASE] Summarising the health of organisation: {}", organisationId);

        return Mono.fromRunnable(() -> HealthAccess.requireStaff(role, "read the health summary"))
                .thenMany(Flux.defer(() -> repository.findAll(organisationId, new HealthCheckRepository.Filter(null, null, null))))
                .collectList()
                .map(checks -> {
                    int paused = (int) checks.stream().filter(c -> c.getStatus() == CheckStatus.PAUSED).count();
                    var active = checks.stream().filter(c -> c.getStatus() == CheckStatus.ACTIVE).map(HealthCheck::getHealth).toList();
                    return new HealthSummaryResponse(checks.size(), count(active, Health.UP), count(active, Health.DOWN), count(active, Health.UNKNOWN), paused);
                });
    }

    private static int count(List<Health> healths, Health wanted) {
        return (int) healths.stream().filter(h -> h == wanted).count();
    }
}

package com.thinklab.infrastructure.scheduling;

import com.thinklab.application.usecase.ProbeRoundUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProbeSchedulerTest {

    @Test
    @DisplayName("a tick runs a round; a tick that arrives while the round still runs is skipped; the next one runs once it ended")
    void ticks() {
        ProbeRoundUseCase round = mock(ProbeRoundUseCase.class);
        Sinks.One<Long> running = Sinks.one();
        AtomicInteger started = new AtomicInteger();
        when(round.execute()).thenAnswer(call -> Mono.defer(() -> {
            started.incrementAndGet();
            return running.asMono();
        }));
        ProbeScheduler scheduler = new ProbeScheduler(round);

        scheduler.tick();
        scheduler.tick();
        assertEquals(1, started.get());
        running.tryEmitValue(3L);
        scheduler.tick();

        assertEquals(2, started.get());
    }

    @Test
    @DisplayName("a round that fails is logged and the scheduler is free for the next tick")
    void failingRound() {
        ProbeRoundUseCase round = mock(ProbeRoundUseCase.class);
        AtomicInteger started = new AtomicInteger();
        when(round.execute()).thenAnswer(call -> Mono.defer(() -> {
            started.incrementAndGet();
            return Mono.<Long>error(new IllegalStateException("mongo down"));
        }));
        ProbeScheduler scheduler = new ProbeScheduler(round);

        scheduler.tick();
        scheduler.tick();

        assertEquals(2, started.get());
    }
}

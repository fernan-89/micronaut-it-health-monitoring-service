package com.thinklab.application.usecase;

import com.thinklab.domain.model.HealthCheck.CheckType;
import com.thinklab.domain.port.ProbePort;
import jakarta.inject.Singleton;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Finds the probe of a type of check. */
@Singleton
public class ProbeRegistry {

    private final Map<CheckType, ProbePort> byType = new EnumMap<>(CheckType.class);

    public ProbeRegistry(List<ProbePort> probes) {
        probes.forEach(probe -> byType.put(probe.type(), probe));
    }

    public ProbePort of(CheckType type) {
        ProbePort probe = byType.get(type);
        if (probe == null) {
            throw new IllegalStateException("No probe for type " + type);
        }
        return probe;
    }
}

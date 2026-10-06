package com.thinklab.infrastructure.adapter.out.probe;

import com.thinklab.application.config.HealthMonitoringProperties;
import com.thinklab.application.usecase.TargetPolicy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Holds a probe to the rules of {@link TargetPolicy} on what the name RESOLVES to at probe time, not only on what was written: a name
 * that now points at the cloud metadata address is refused. When EVERY address is acceptable the first is returned, so a probe that
 * connects by address (TCP) connects to exactly the address that was vetted. (HTTP connects by name again: a name that changes between
 * the check and the connection is a known, documented limit.)
 */
@Singleton
public class AddressGuard {

    /** Name resolution, replaceable in tests. */
    @FunctionalInterface
    interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final HealthMonitoringProperties properties;
    private final Resolver resolver;

    @Inject
    public AddressGuard(HealthMonitoringProperties properties) {
        this(properties, InetAddress::getAllByName);
    }

    AddressGuard(HealthMonitoringProperties properties, Resolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
    }

    InetAddress resolveAllowed(String host) throws UnknownHostException {
        InetAddress[] addresses = resolver.resolve(host);
        for (InetAddress address : addresses) {
            if (TargetPolicy.isForbidden(address, properties.isAllowLoopback())) {
                throw new AddressNotAllowedException();
            }
        }
        return addresses[0];
    }
}

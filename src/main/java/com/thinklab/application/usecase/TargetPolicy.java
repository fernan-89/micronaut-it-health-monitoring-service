package com.thinklab.application.usecase;

import com.thinklab.application.config.HealthMonitoringProperties;
import com.thinklab.domain.model.HealthCheck.CheckType;
import jakarta.inject.Singleton;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Where a check may point (ADR-031). A monitor exists to watch internal infrastructure, so PRIVATE networks are allowed; what is never
 * allowed is where an internal caller must not be pointed: link-local addresses (the cloud metadata service lives at 169.254.169.254),
 * the unspecified address, multicast and the well-known metadata names. Loopback is refused unless the deployment allows it. This is the
 * literal check made when a check is written; the same rules run again on the resolved address at every probe (see {@link #isForbidden}).
 */
@Singleton
public class TargetPolicy {

    private final HealthMonitoringProperties properties;

    public TargetPolicy(HealthMonitoringProperties properties) {
        this.properties = properties;
    }

    public void check(CheckType type, String target) {
        String host = hostOf(type, target).toLowerCase(Locale.ROOT);
        if (host.equals("metadata.google.internal") || host.equals("metadata") || host.equals("instance-data")) {
            throw new IllegalArgumentException("That target is a cloud metadata address and cannot be monitored.");
        }
        if (host.equals("localhost") || host.endsWith(".localhost")) {
            if (!properties.isAllowLoopback()) {
                throw new IllegalArgumentException("A loopback target cannot be monitored here.");
            }
            return;
        }
        InetAddress literal = literal(host);
        if (literal != null && isForbidden(literal, properties.isAllowLoopback())) {
            throw new IllegalArgumentException("That address cannot be monitored (link-local, unspecified, multicast or loopback).");
        }
    }

    /** The rules every address is held to, at write time (literals) and at probe time (what the name resolved to). */
    public static boolean isForbidden(InetAddress address, boolean allowLoopback) {
        if (address.isLoopbackAddress()) {
            return !allowLoopback;
        }
        return address.isLinkLocalAddress() || address.isAnyLocalAddress() || address.isMulticastAddress() || isAwsIpv6Metadata(address);
    }

    private static boolean isAwsIpv6Metadata(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xff) == 0xfd && (bytes[1] & 0xff) == 0x00 && (bytes[2] & 0xff) == 0x0e && (bytes[3] & 0xff) == 0xc2;
    }

    private static String hostOf(CheckType type, String target) {
        if (type == CheckType.HTTP) {
            return URI.create(target.trim()).getHost();
        }
        return target.substring(0, target.lastIndexOf(':'));
    }

    /** An address literal (dotted IPv4, bracketed IPv6, or a plain decimal integer), or {@code null} for a name. No DNS is done here. */
    private static InetAddress literal(String host) {
        try {
            if (host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
                return InetAddress.getByName(host);
            }
            if (host.startsWith("[") && host.endsWith("]")) {
                return InetAddress.getByName(host);
            }
            if (host.matches("[0-9]{1,10}")) {
                long value = Long.parseLong(host);
                return InetAddress.getByAddress(new byte[]{(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value});
            }
        } catch (UnknownHostException | RuntimeException e) {
            throw new IllegalArgumentException("The target is not a valid address.");
        }
        return null;
    }
}

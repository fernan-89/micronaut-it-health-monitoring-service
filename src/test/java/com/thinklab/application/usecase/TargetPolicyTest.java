package com.thinklab.application.usecase;

import com.thinklab.application.config.HealthMonitoringProperties;
import com.thinklab.domain.model.HealthCheck.CheckType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetPolicyTest {

    private final HealthMonitoringProperties properties = new HealthMonitoringProperties();
    private final TargetPolicy policy = new TargetPolicy(properties);

    private void refusesHttp(List<String> targets) {
        for (String target : targets) {
            assertThrows(IllegalArgumentException.class, () -> policy.check(CheckType.HTTP, target), target);
        }
    }

    @Test
    @DisplayName("private networks and ordinary names are allowed: watching internal infrastructure is what a monitor is for")
    void allowed() {
        policy.check(CheckType.HTTP, "https://api.acme.test/health");
        policy.check(CheckType.HTTP, "http://10.0.0.5:8080/health");
        policy.check(CheckType.HTTP, "http://192.168.1.10");
        policy.check(CheckType.TCP, "172.16.0.9:22");
        policy.check(CheckType.TCP, "[fd00::1]:22");
        policy.check(CheckType.TCP, "db.internal:5432");
        policy.check(CheckType.TCP, "[2001:db8::1]:22");
        policy.check(CheckType.TCP, "[fd01::1]:22");
        policy.check(CheckType.TCP, "[fd00:e01::1]:22");
        policy.check(CheckType.TCP, "[abc:22");
    }

    @Test
    @DisplayName("link-local, unspecified and multicast addresses are refused, in every spelling")
    void forbiddenAddresses() {
        refusesHttp(List.of("http://169.254.169.254/", "http://169.254.0.1", "http://0.0.0.0", "http://224.0.0.1", "http://[fe80::1]/", "http://[ff02::1]/",
                "http://[fd00:ec2::254]/", "http://2852039166/"));
    }

    @Test
    @DisplayName("the well-known metadata names are refused")
    void metadataNames() {
        refusesHttp(List.of("http://metadata.google.internal/", "http://METADATA/", "http://instance-data/"));
    }

    @Test
    @DisplayName("loopback is refused unless the deployment allows it, for HTTP and for TCP")
    void loopback() {
        refusesHttp(List.of("http://localhost/", "http://127.0.0.1/", "http://[::1]/", "http://2130706433/"));
        for (String target : List.of("localhost:22", "127.0.0.1:22", "app.localhost:22")) {
            assertThrows(IllegalArgumentException.class, () -> policy.check(CheckType.TCP, target), target);
        }
        assertThrows(IllegalArgumentException.class, () -> policy.check(CheckType.TCP, "169.254.169.254:80"));

        properties.setAllowLoopback(true);
        policy.check(CheckType.HTTP, "http://localhost:9100/jira");
        policy.check(CheckType.TCP, "app.localhost:22");
        policy.check(CheckType.TCP, "127.0.0.1:22");
        refusesHttp(List.of("http://169.254.169.254/"));
        assertTrue(TargetPolicy.isForbidden(InetAddress.getLoopbackAddress(), false));
        assertFalse(TargetPolicy.isForbidden(InetAddress.getLoopbackAddress(), true));
    }

    @Test
    @DisplayName("a malformed target is left for the domain to refuse, never a crash of the policy")
    void malformedTargets() {
        policy.check(CheckType.TCP, "db.internal");
        policy.check(CheckType.HTTP, "https:///no-host");
        policy.check(CheckType.HTTP, "not a url");
    }

    @Test
    @DisplayName("a malformed literal is refused as a bad address")
    void malformedLiteral() {
        assertThrows(IllegalArgumentException.class, () -> policy.check(CheckType.TCP, "999.999.999.999:22"));
        assertThrows(IllegalArgumentException.class, () -> policy.check(CheckType.TCP, "[zz]:22"));
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
}

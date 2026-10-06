package com.thinklab.application.usecase;

import com.thinklab.application.mapper.HealthCheckMapper;
import com.thinklab.domain.exception.HealthAccessDeniedException;
import com.thinklab.domain.model.HealthCheck.CheckType;
import com.thinklab.domain.port.ProbePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The guard rails of the small helpers: who is refused, which probe answers which type, and that the helpers cannot be instantiated. */
class UtilityClassesTest {

    @Test
    @DisplayName("a requester is refused, anyone else passes")
    void access() {
        HealthAccess.requireStaff(null, "x");
        HealthAccess.requireStaff("AGENT", "x");
        assertThrows(HealthAccessDeniedException.class, () -> HealthAccess.requireStaff("REQUESTER", "x"));
    }

    @Test
    @DisplayName("the registry answers the probe of a type and refuses one it has none for")
    void registry() {
        ProbePort http = mock(ProbePort.class);
        when(http.type()).thenReturn(CheckType.HTTP);
        ProbeRegistry registry = new ProbeRegistry(List.of(http));

        assertEquals(http, registry.of(CheckType.HTTP));
        assertThrows(IllegalStateException.class, () -> registry.of(CheckType.TCP));
    }

    @Test
    @DisplayName("the helpers are utility classes")
    void notInstantiable() throws ReflectiveOperationException {
        for (Class<?> type : List.of(HealthAccess.class, HealthCheckMapper.class)) {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
        }
    }
}

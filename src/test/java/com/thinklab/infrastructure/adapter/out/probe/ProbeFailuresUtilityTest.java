package com.thinklab.infrastructure.adapter.out.probe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ProbeFailuresUtilityTest {

    @Test
    @DisplayName("the failure vocabulary is a utility class")
    void notInstantiable() throws ReflectiveOperationException {
        var constructor = ProbeFailures.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }
}

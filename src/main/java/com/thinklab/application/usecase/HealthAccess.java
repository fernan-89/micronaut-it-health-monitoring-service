package com.thinklab.application.usecase;

import com.thinklab.domain.exception.HealthAccessDeniedException;

/** Health monitoring is for IT staff (ADR-032): a REQUESTER is refused everywhere. */
final class HealthAccess {

    static final String REQUESTER_ROLE = "REQUESTER";
    static final String SYSTEM_EXECUTOR = "system:health-monitor";

    private HealthAccess() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    static void requireStaff(String role, String operation) {
        if (REQUESTER_ROLE.equals(role)) {
            throw new HealthAccessDeniedException("A requester cannot " + operation + ": health monitoring is handled by IT staff.");
        }
    }
}

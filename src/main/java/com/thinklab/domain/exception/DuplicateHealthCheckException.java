package com.thinklab.domain.exception;

/**
 * Domain Exception: Thrown when an HealthCheck is initiated with a serial number that already exists
 * within the same Organisation scope.
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict.
 */
public class DuplicateHealthCheckException extends BusinessException {

    private static final String ERROR_CODE = "ERR-HLM-00409";

    public DuplicateHealthCheckException(String message) {
        super(ERROR_CODE, message);
    }
}

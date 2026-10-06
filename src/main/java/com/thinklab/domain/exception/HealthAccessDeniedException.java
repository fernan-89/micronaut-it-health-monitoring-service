package com.thinklab.domain.exception;

/** Domain Exception: a REQUESTER tried to use health monitoring (staff only, ADR-032). RFC 7807 mapping: HTTP 403 Forbidden. */
public class HealthAccessDeniedException extends BusinessException {

    private static final String ERROR_CODE = "ERR-HLM-00403";

    public HealthAccessDeniedException(String message) {
        super(ERROR_CODE, message);
    }
}

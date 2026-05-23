package com.massimotter.weave.backend.audit;

public class AuditRequiredException extends RuntimeException {

    public AuditRequiredException(String message) {
        super(message);
    }
}

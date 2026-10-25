package com.massimotter.weave.backend.audit;

/**
 * Fail-closed helper for future connector/assistant write paths.
 */
public final class AuditWriteGate {

    private AuditWriteGate() {
    }

    public static void publishRequired(AuditEventPublisher publisher, AuditEvent event) {
        if (publisher == null) {
            throw new AuditRequiredException("audit publisher is required before connector or assistant writes are allowed");
        }
        publisher.publish(event);
    }
}

package com.massimotter.weave.backend.audit;

/**
 * Shared fail-closed publication guard for auditable product writes.
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

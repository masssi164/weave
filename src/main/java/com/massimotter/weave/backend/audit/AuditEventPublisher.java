package com.massimotter.weave.backend.audit;

public interface AuditEventPublisher {

    void publish(AuditEvent event);
}

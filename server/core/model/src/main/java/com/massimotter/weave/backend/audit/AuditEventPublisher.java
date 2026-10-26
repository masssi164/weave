package com.massimotter.weave.backend.audit;

import java.util.List;

public interface AuditEventPublisher {

    void publish(AuditEvent event);

    /** Newest occurrence first; the database applies organization, envelope filters and pagination. */
    List<AuditEvent> events(AuditEventQuery query);
}

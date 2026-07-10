package com.massimotter.weave.backend.audit;

import java.util.List;

public interface AuditEventPublisher {

    void publish(AuditEvent event);

    List<AuditEvent> events();
}

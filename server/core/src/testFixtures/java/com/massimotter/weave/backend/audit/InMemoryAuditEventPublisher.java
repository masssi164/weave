package com.massimotter.weave.backend.audit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Objects.requireNonNull;

/**
 * Test/support publisher that only appends. There is intentionally no update or delete API.
 */
public final class InMemoryAuditEventPublisher implements AuditEventPublisher {

    private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void publish(AuditEvent event) {
        events.add(requireNonNull(event, "event must not be null"));
    }

    public List<AuditEvent> events() {
        return List.copyOf(events);
    }
}

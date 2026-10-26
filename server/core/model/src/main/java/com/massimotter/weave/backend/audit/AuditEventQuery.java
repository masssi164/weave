package com.massimotter.weave.backend.audit;

import java.util.Arrays;
import java.util.List;

/** Organization-scoped, bounded audit read. Empty filters mean all known envelope values. */
public record AuditEventQuery(
        String organizationId,
        List<AuditAction> actions,
        List<AuditRedactionLevel> redactionLevels,
        int page,
        int size) {
    public static final int MAX_PAGE_SIZE = 200;

    public AuditEventQuery {
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException("Audit reads require an organization id");
        }
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Audit reads require a non-negative page and size from 1 to 200");
        }
        actions = actions == null || actions.isEmpty()
                ? List.copyOf(Arrays.asList(AuditAction.values())) : List.copyOf(actions);
        redactionLevels = redactionLevels == null || redactionLevels.isEmpty()
                ? List.copyOf(Arrays.asList(AuditRedactionLevel.values())) : List.copyOf(redactionLevels);
    }

    public static AuditEventQuery recent(String organizationId) {
        return new AuditEventQuery(organizationId, List.of(), List.of(), 0, MAX_PAGE_SIZE);
    }

    public boolean selects(AuditEvent event) {
        return event != null && organizationId.equals(event.tenantId())
                && actions.contains(event.action()) && redactionLevels.contains(event.redactionLevel());
    }
}

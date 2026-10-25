package com.massimotter.weave.backend.context.authz;

/**
 * Product-facing Context membership roles. Team/channel are projections; roles are evaluated against contexts.
 */
public enum ContextRole {
    OWNER,
    ADMIN,
    MEMBER,
    GUEST,
    VIEWER,
    CONNECTOR,
    ASSISTANT;

    boolean grants(ContextPermission permission) {
        return switch (this) {
            case OWNER, ADMIN -> true;
            case MEMBER -> permission == ContextPermission.VIEW || permission == ContextPermission.EDIT;
            case GUEST, VIEWER -> permission == ContextPermission.VIEW;
            case CONNECTOR, ASSISTANT -> false;
        };
    }
}

package com.massimotter.weave.backend.context.authz;

import java.util.Optional;

/**
 * Known ReBAC tuple relations for Context Graph authorization.
 * Unknown relation strings deliberately fail closed in the adapter.
 */
public enum ContextRelation {
    CONTEXT_VIEWER("context_viewer", ContextPermission.VIEW),
    CONTEXT_EDITOR("context_editor", ContextPermission.EDIT),
    CONTEXT_ADMIN("context_admin", ContextPermission.ADMIN),
    CONTEXT_MEMBER("context_member", ContextPermission.EDIT),
    CONTEXT_OWNER("context_owner", ContextPermission.ADMIN);

    private final String wireValue;
    private final ContextPermission permission;

    ContextRelation(String wireValue, ContextPermission permission) {
        this.wireValue = wireValue;
        this.permission = permission;
    }

    public String wireValue() {
        return wireValue;
    }

    boolean grants(ContextPermission requested) {
        return switch (permission) {
            case ADMIN -> true;
            case EDIT -> requested == ContextPermission.VIEW || requested == ContextPermission.EDIT;
            case VIEW -> requested == ContextPermission.VIEW;
        };
    }

    static Optional<ContextRelation> fromWireValue(String wireValue) {
        for (ContextRelation relation : values()) {
            if (relation.wireValue.equals(wireValue)) {
                return Optional.of(relation);
            }
        }
        return Optional.empty();
    }
}

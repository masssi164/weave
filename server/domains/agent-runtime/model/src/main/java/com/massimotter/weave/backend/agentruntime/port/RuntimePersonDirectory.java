package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;

/** Resolves an opaque Weave person reference through the authoritative IDM organization. */
public interface RuntimePersonDirectory {
    ResolvedRuntimePerson resolve(ResolveRuntimePersonCommand command);

    record ResolveRuntimePersonCommand(String organizationRef, String personRef, String auditRef) {
        public ResolveRuntimePersonCommand {
            requireText(organizationRef, "organizationRef");
            requireText(personRef, "personRef");
            requireText(auditRef, "auditRef");
        }
    }

    record ResolvedRuntimePerson(
            String organizationRef,
            String personRef,
            RuntimeMemberBinding memberBinding) {
        public ResolvedRuntimePerson {
            requireText(organizationRef, "organizationRef");
            requireText(personRef, "personRef");
            if (memberBinding == null) {
                throw new IllegalArgumentException("memberBinding is required");
            }
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}

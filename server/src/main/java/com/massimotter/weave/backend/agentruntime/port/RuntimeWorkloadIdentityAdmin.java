package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import java.util.Objects;

public interface RuntimeWorkloadIdentityAdmin {
    RuntimeWorkloadBinding ensureBinding(EnsureBindingCommand command);

    record EnsureBindingCommand(
            String organizationRef,
            String personRef,
            String cellRef,
            String clientId,
            RuntimeWorkloadBinding.AuthenticationMethod authenticationMethod,
            String auditRef) {
        public EnsureBindingCommand {
            requireText(organizationRef, "organizationRef");
            requireText(personRef, "personRef");
            requireText(cellRef, "cellRef");
            requireText(clientId, "clientId");
            Objects.requireNonNull(authenticationMethod, "authenticationMethod");
            requireText(auditRef, "auditRef");
        }

        private static void requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " is required");
            }
        }
    }
}

package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import java.util.Objects;

public interface RuntimeWorkloadIdentityAdmin {
    RuntimeWorkloadBinding ensureBinding(EnsureBindingCommand command);

    RuntimeWorkloadBinding rotateBinding(RotateBindingCommand command);

    RuntimeWorkloadBinding retirePreviousCredential(RetireCredentialCommand command);

    void disableBinding(DisableBindingCommand command);

    void deleteBinding(DeleteBindingCommand command);

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

    record DisableBindingCommand(
            String organizationRef,
            String personRef,
            String cellRef,
            RuntimeWorkloadBinding binding,
            String auditRef) {
        public DisableBindingCommand {
            EnsureBindingCommand.requireText(organizationRef, "organizationRef");
            EnsureBindingCommand.requireText(personRef, "personRef");
            EnsureBindingCommand.requireText(cellRef, "cellRef");
            Objects.requireNonNull(binding, "binding");
            EnsureBindingCommand.requireText(auditRef, "auditRef");
        }
    }

    record RotateBindingCommand(
            String organizationRef,
            String personRef,
            String cellRef,
            RuntimeWorkloadBinding binding,
            String rotationRef,
            String auditRef) {
        public RotateBindingCommand {
            EnsureBindingCommand.requireText(organizationRef, "organizationRef");
            EnsureBindingCommand.requireText(personRef, "personRef");
            EnsureBindingCommand.requireText(cellRef, "cellRef");
            Objects.requireNonNull(binding, "binding");
            EnsureBindingCommand.requireText(rotationRef, "rotationRef");
            EnsureBindingCommand.requireText(auditRef, "auditRef");
        }
    }

    record RetireCredentialCommand(
            String organizationRef,
            String personRef,
            String cellRef,
            RuntimeWorkloadBinding binding,
            String rotationRef,
            String auditRef) {
        public RetireCredentialCommand {
            EnsureBindingCommand.requireText(organizationRef, "organizationRef");
            EnsureBindingCommand.requireText(personRef, "personRef");
            EnsureBindingCommand.requireText(cellRef, "cellRef");
            Objects.requireNonNull(binding, "binding");
            EnsureBindingCommand.requireText(rotationRef, "rotationRef");
            EnsureBindingCommand.requireText(auditRef, "auditRef");
        }
    }

    record DeleteBindingCommand(
            String organizationRef,
            String personRef,
            String cellRef,
            RuntimeWorkloadBinding binding,
            String auditRef) {
        public DeleteBindingCommand {
            EnsureBindingCommand.requireText(organizationRef, "organizationRef");
            EnsureBindingCommand.requireText(personRef, "personRef");
            EnsureBindingCommand.requireText(cellRef, "cellRef");
            Objects.requireNonNull(binding, "binding");
            EnsureBindingCommand.requireText(auditRef, "auditRef");
        }
    }
}

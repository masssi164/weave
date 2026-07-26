package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadCredentialState;
import java.util.Objects;
import java.util.Optional;

/** Secret-manager boundary. Implementations never return private key or client-secret values. */
public interface RuntimeWorkloadCredentialStore {
    Optional<RuntimeWorkloadCredentialState> find(String clientId);

    RuntimeWorkloadCredentialState create(CreateCredentialCommand command);

    RuntimeWorkloadCredentialState prepareRotation(RotateCredentialCommand command);

    RuntimeWorkloadCredentialState activateRotation(RotateCredentialCommand command);

    RuntimeWorkloadCredentialState prepareRetirement(RetireCredentialCommand command);

    RuntimeWorkloadCredentialState completeRetirement(RetireCredentialCommand command);

    void delete(DeleteCredentialCommand command);

    record CreateCredentialCommand(
            String clientId,
            String ownerFingerprint,
            RuntimeWorkloadBinding.AuthenticationMethod authenticationMethod) {
        public CreateCredentialCommand {
            requireClientId(clientId);
            requireText(ownerFingerprint, "ownerFingerprint");
            Objects.requireNonNull(authenticationMethod, "authenticationMethod");
        }
    }

    record RotateCredentialCommand(String clientId, String ownerFingerprint, String rotationRef) {
        public RotateCredentialCommand {
            requireClientId(clientId);
            requireText(ownerFingerprint, "ownerFingerprint");
            requireText(rotationRef, "rotationRef");
        }
    }

    record RetireCredentialCommand(String clientId, String ownerFingerprint, String rotationRef) {
        public RetireCredentialCommand {
            requireClientId(clientId);
            requireText(ownerFingerprint, "ownerFingerprint");
            requireText(rotationRef, "rotationRef");
        }
    }

    record DeleteCredentialCommand(String clientId, String ownerFingerprint) {
        public DeleteCredentialCommand {
            requireClientId(clientId);
            requireText(ownerFingerprint, "ownerFingerprint");
        }
    }

    private static void requireClientId(String value) {
        if (value == null || !value.matches("weaver-cell-[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("clientId must use the weaver-cell-{id} namespace");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}

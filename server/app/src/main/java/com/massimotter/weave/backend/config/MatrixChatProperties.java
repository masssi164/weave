package com.massimotter.weave.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.matrix")
public record MatrixChatProperties(
        boolean federationEnabled,
        E2ee e2ee,
        BackendBoundary backendBoundary) {

    public MatrixChatProperties {
        e2ee = e2ee == null ? new E2ee(false, false, false, false, false, false, null) : e2ee;
        backendBoundary = backendBoundary == null
                ? new BackendBoundary(null, null)
                : backendBoundary;
    }

    public record E2ee(
            boolean encryptedRoomsValidated,
            boolean deviceVerificationValidated,
            boolean keyBackupValidated,
            boolean lostDeviceRecoveryValidated,
            boolean multiDeviceValidated,
            boolean accessibilityReviewed,
            String statusSource) {

        public E2ee {
            statusSource = defaultIfBlank(statusSource, "backend_runtime_flags_only");
        }

        public boolean fullyValidated() {
            return encryptedRoomsValidated
                    && deviceVerificationValidated
                    && keyBackupValidated
                    && lostDeviceRecoveryValidated
                    && multiDeviceValidated
                    && accessibilityReviewed;
        }
    }

    public record BackendBoundary(String agentParticipation, String connectorWritePolicy) {

        public BackendBoundary {
            agentParticipation = defaultIfBlank(
                    agentParticipation,
                    "blocked_until_explicit_consent_audit_and_matrix_device_trust_are_implemented");
            connectorWritePolicy = defaultIfBlank(
                    connectorWritePolicy,
                    "fail_closed_until_audit_consent_and_matrix_e2ee_client_identity_are_implemented");
        }
    }

    private static String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}

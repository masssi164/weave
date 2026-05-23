package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Backend-owned status snapshot for admin diagnostics and smoke tests.")
public record PlatformStatusResponse(
        @Schema(description = "Correlation identifier also returned in the X-Request-Id response header.")
        String requestId,
        DiagnosticStatus backend,
        DiagnosticStatus auth,
        MatrixStatus matrix,
        DiagnosticStatus files,
        DiagnosticStatus calendar,
        DiagnosticStatus nextcloud,
        @Schema(description = "Normalized non-secret checks that admin UI, setup scripts, and smoke tests can render.")
        List<DiagnosticCheck> checks,
        @Schema(description = "Distinct operator actions for checks that are not ready.")
        List<String> actions) {

    public boolean ready() {
        return checks.stream()
                .filter(check -> !"unavailable".equals(check.readiness()))
                .allMatch(check -> "ready".equals(check.readiness()));
    }

    public record DiagnosticStatus(
            @Schema(description = "Short machine-readable status: up, degraded, blocked, or disabled.")
            String status,
            @Schema(description = "Normalized readiness: ready, degraded, blocked, or unavailable.")
            String readiness,
            @Schema(description = "Plain-language, support-safe reason for the current state.")
            String message,
            @Schema(description = "Recommended non-secret operator action when not ready.")
            String action) {
    }

    public record MatrixStatus(
            String status,
            String readiness,
            String message,
            String action,
            boolean federationEnabled,
            @Schema(description = "True only after all active Matrix E2EE implementation and accessibility gates are validated.")
            boolean e2eeEnabled,
            E2eeStatus e2ee,
            MatrixBackendBoundary backendBoundary) {
    }

    public record E2eeStatus(
            @Schema(description = "validated only when encrypted rooms, device verification, recovery, lost-device, multi-device, and accessibility gates are complete.")
            String status,
            @Schema(description = "Support-safe source of this status; never a room message-body inspection source.")
            String source,
            boolean encryptedRoomsValidated,
            boolean deviceVerificationValidated,
            boolean keyBackupValidated,
            boolean lostDeviceRecoveryValidated,
            boolean multiDeviceValidated,
            boolean accessibilityReviewed,
            String action) {
    }

    public record MatrixBackendBoundary(
            @Schema(description = "False for E2EE-compatible backend behavior; encrypted message bodies are client-readable only.")
            boolean serverReadableMessageContent,
            @Schema(description = "Support-safe metadata classes backend diagnostics may use without implying message body access.")
            List<String> metadataReadable,
            String messageContentPolicy,
            String agentParticipation,
            String connectorWritePolicy) {
    }

    public record DiagnosticCheck(
            @Schema(description = "Stable check identifier.", example = "auth")
            String key,
            @Schema(description = "Human-readable check label.", example = "Keycloak auth")
            String label,
            String status,
            String readiness,
            String message,
            String action) {
    }
}

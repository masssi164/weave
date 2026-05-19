package com.massimotter.weave.backend.boards.openproject;

import com.massimotter.weave.backend.boards.support.BoardsErrorCode;
import com.massimotter.weave.backend.boards.support.BoardsException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fail-closed promotion gate for the OpenProject Boards read-sync seam. The
 * gate documents the minimum runtime conditions before OpenProject can back the
 * provider-neutral Boards facade without making provider auth, raw IDs, or
 * write behavior a product contract.
 */
public record OpenProjectBoardsRuntimeGate(
        boolean providerRuntimeEnabled,
        boolean readSyncEnabled,
        boolean contextAuthorizationEnabled,
        boolean auditConsentEnabled,
        boolean providerWritesEnabled,
        String authMode) {

    public static OpenProjectBoardsRuntimeGate disabled() {
        return new OpenProjectBoardsRuntimeGate(false, false, false, false, false, "disabled");
    }

    public void requireReadSyncAllowed(String operation) {
        var missing = missingReadGates();
        if (!missing.isEmpty()) {
            throw new BoardsException(
                    BoardsErrorCode.PROVIDER_UNAVAILABLE,
                    "OpenProject Boards read-sync remains disabled and fail-closed until provider runtime, read-sync, and Context/Space authorization gates are enabled.",
                    details(operation, missing, "read_sync"));
        }
    }

    public void requireWriteAllowed(String operation) {
        if (!providerWritesEnabled) {
            throw new BoardsException(
                    BoardsErrorCode.UNSUPPORTED_CAPABILITY,
                    "OpenProject Boards writes remain disabled until audit, consent, and provider write promotion are explicitly enabled.",
                    details(operation, "provider_writes_disabled", "write"));
        }
        var missing = missingWriteGates();
        if (!missing.isEmpty()) {
            throw new BoardsException(
                    BoardsErrorCode.PROVIDER_UNAVAILABLE,
                    "OpenProject Boards writes remain fail-closed until audit/consent and Context/Space authorization gates are enabled.",
                    details(operation, missing, "write"));
        }
    }

    private java.util.List<String> missingReadGates() {
        var missing = new ArrayList<String>();
        if (!providerRuntimeEnabled) {
            missing.add("provider_runtime");
        }
        if (!readSyncEnabled) {
            missing.add("read_sync");
        }
        if (!contextAuthorizationEnabled) {
            missing.add("context_authorization");
        }
        if (authMode == null || authMode.isBlank() || "disabled".equalsIgnoreCase(authMode)) {
            missing.add("provider_auth_mode");
        }
        return missing;
    }

    private java.util.List<String> missingWriteGates() {
        var missing = new ArrayList<String>();
        if (!contextAuthorizationEnabled) {
            missing.add("context_authorization");
        }
        if (!auditConsentEnabled) {
            missing.add("audit_consent");
        }
        if (authMode == null || authMode.isBlank() || "disabled".equalsIgnoreCase(authMode)) {
            missing.add("provider_auth_mode");
        }
        return missing;
    }

    private Map<String, String> details(String operation, java.util.List<String> missing, String mode) {
        return details(operation, String.join(",", missing), mode);
    }

    private Map<String, String> details(String operation, String missing, String mode) {
        var details = new LinkedHashMap<String, String>();
        details.put("provider", "openproject");
        details.put("operation", operation);
        details.put("mode", mode);
        details.put("missingGates", missing);
        details.put("providerWritesEnabled", Boolean.toString(providerWritesEnabled));
        details.put("supportSafe", "true");
        return details;
    }
}

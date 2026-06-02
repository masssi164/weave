package com.massimotter.weave.backend.weaver;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record WeaverToolInvocationRequest(
        String toolName,
        String userRef,
        String runtimeProfileHash,
        String runtimeProfileUserRef,
        String runtimeProfileSignature,
        boolean runtimeProfileRevoked,
        String runtimeTokenExpiresAt,
        boolean consentGranted,
        List<String> grantedCapabilities,
        List<String> scopedToolGrants,
        Map<String, Object> input,
        String approvalReceiptRef) {

    public WeaverToolInvocationRequest(
            String toolName,
            String userRef,
            String runtimeProfileHash,
            List<String> grantedCapabilities,
            Map<String, Object> input,
            String approvalReceiptRef) {
        this(
                toolName,
                userRef,
                runtimeProfileHash,
                userRef,
                "weave-signature:v1:test-support-safe",
                false,
                Instant.now().plusSeconds(300).toString(),
                true,
                grantedCapabilities,
                List.of(toolName == null ? "" : toolName),
                input,
                approvalReceiptRef);
    }

    public WeaverToolInvocationRequest {
        if (runtimeProfileHash == null || runtimeProfileHash.isBlank()) {
            throw new IllegalArgumentException("runtimeProfileHash is required for Weaver tool audit correlation");
        }
        runtimeProfileUserRef = runtimeProfileUserRef == null || runtimeProfileUserRef.isBlank()
                ? "user:unknown"
                : runtimeProfileUserRef;
        runtimeProfileSignature = runtimeProfileSignature == null ? "" : runtimeProfileSignature;
        runtimeTokenExpiresAt = runtimeTokenExpiresAt == null ? "" : runtimeTokenExpiresAt;
        grantedCapabilities = List.copyOf(grantedCapabilities == null ? List.of() : grantedCapabilities);
        scopedToolGrants = List.copyOf(scopedToolGrants == null ? List.of() : scopedToolGrants);
        input = Map.copyOf(input == null ? Map.of() : input);
    }
}

package com.massimotter.weave.backend.calls.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record JoinGrant(
        String grantId,
        String meetingId,
        String roomName,
        String personRef,
        CallRole role,
        List<String> permissions,
        String mediaUrl,
        String token,
        Instant issuedAt,
        Instant expiresAt,
        Map<String, Object> supportSafeDiagnostics) {

    public JoinGrant {
        grantId = requireText(grantId, "grantId");
        meetingId = requireText(meetingId, "meetingId");
        roomName = requireText(roomName, "roomName");
        personRef = requireText(personRef, "personRef");
        role = java.util.Objects.requireNonNull(role, "role must not be null");
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        mediaUrl = requireText(mediaUrl, "mediaUrl");
        token = requireText(token, "token");
        issuedAt = java.util.Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        expiresAt = java.util.Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        supportSafeDiagnostics = supportSafeDiagnostics == null ? Map.of() : Map.copyOf(supportSafeDiagnostics);
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    public JoinGrant redacted() {
        return new JoinGrant(
                grantId,
                meetingId,
                roomName,
                personRef,
                role,
                permissions,
                mediaUrl,
                "redacted",
                issuedAt,
                expiresAt,
                supportSafeDiagnostics);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

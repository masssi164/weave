package com.massimotter.weave.backend.agentruntime.domain;

import java.time.Instant;
import java.util.regex.Pattern;

public record SignedRuntimeProfile(
        String protectedHeader,
        String payload,
        String signature,
        String profileHash,
        String profileId,
        String cellRef,
        String keyId,
        Instant issuedAt,
        Instant expiresAt) {

    private static final Pattern BASE64URL = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern HASH = Pattern.compile("sha256:[a-f0-9]{64}");

    public SignedRuntimeProfile {
        requireBase64Url(protectedHeader, "protectedHeader", 1);
        requireBase64Url(payload, "payload", 1);
        requireBase64Url(signature, "signature", 80);
        if (profileHash == null || !HASH.matcher(profileHash).matches()) {
            throw new IllegalArgumentException("profileHash must be a SHA-256 reference");
        }
        if (profileId == null || cellRef == null || keyId == null || issuedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("signed RuntimeProfile metadata is required");
        }
    }

    private static void requireBase64Url(String value, String field, int minimumLength) {
        if (value == null || value.length() < minimumLength || !BASE64URL.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be unpadded base64url");
        }
    }
}

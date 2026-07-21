package com.massimotter.weave.backend.agentruntime.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable one-way ownership markers shared by the control store and identity adapter. */
public final class RuntimeWorkloadOwnership {
    private RuntimeWorkloadOwnership() {}

    public static String ownerFingerprint(
            String organizationRef,
            String personRef,
            String cellRef,
            String clientId) {
        return fingerprint(organizationRef + "\u0000" + personRef + "\u0000" + cellRef + "\u0000" + clientId);
    }

    public static String fingerprint(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A workload ownership reference is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK SHA-256 support is unavailable", impossible);
        }
    }
}

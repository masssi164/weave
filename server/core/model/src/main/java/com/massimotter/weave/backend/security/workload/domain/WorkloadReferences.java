package com.massimotter.weave.backend.security.workload.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable one-way references for workload authorization and IDM evidence. */
public final class WorkloadReferences {
    private WorkloadReferences() {}

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

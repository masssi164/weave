package com.massimotter.weave.backend.files.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Canonical digest helpers shared by Files use cases and storage adapters. */
public final class FilesDigests {

    private FilesDigests() {
    }

    public static String sha256(byte[] value) {
        byte[] content = value == null ? new byte[0] : value;
        return "sha256:" + HexFormat.of().formatHex(newSha256().digest(content));
    }

    public static String sha256(String value) {
        return sha256((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}

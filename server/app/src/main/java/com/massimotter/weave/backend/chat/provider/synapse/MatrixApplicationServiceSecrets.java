package com.massimotter.weave.backend.chat.provider.synapse;

import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public final class MatrixApplicationServiceSecrets {

    private static final int MAX_TOKEN_BYTES = 16_384;

    private final byte[] asToken;
    private final byte[] hsToken;

    public MatrixApplicationServiceSecrets(ChatRuntimeProperties.Matrix properties) {
        this(readToken(properties.requiredAsTokenFile()), readToken(properties.requiredHsTokenFile()));
    }

    MatrixApplicationServiceSecrets(byte[] asToken, byte[] hsToken) {
        this.asToken = validatedCopy(asToken, "as_token");
        this.hsToken = validatedCopy(hsToken, "hs_token");
        if (MessageDigest.isEqual(this.asToken, this.hsToken)) {
            throw new IllegalStateException("Matrix Application Service tokens must be distinct.");
        }
    }

    public String asBearerValue() {
        return new String(asToken, StandardCharsets.UTF_8);
    }

    public boolean matchesHomeserverToken(byte[] candidate) {
        return candidate != null && MessageDigest.isEqual(hsToken, candidate);
    }

    public boolean matchesAnyToken(byte[] candidate) {
        return candidate != null
                && (MessageDigest.isEqual(asToken, candidate) || MessageDigest.isEqual(hsToken, candidate));
    }

    private static byte[] readToken(Path path) {
        try {
            long size = Files.size(path);
            if (size < 16 || size > MAX_TOKEN_BYTES) {
                throw new IllegalStateException("Matrix Application Service token file has an invalid size.");
            }
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Matrix Application Service token file could not be read.", exception);
        }
    }

    private static byte[] validatedCopy(byte[] token, String label) {
        if (token == null) {
            throw new IllegalStateException("Matrix Application Service " + label + " is missing.");
        }
        String normalized = new String(token, StandardCharsets.UTF_8).trim();
        if (normalized.length() < 16 || normalized.length() > MAX_TOKEN_BYTES
                || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalStateException("Matrix Application Service " + label + " is invalid.");
        }
        return normalized.getBytes(StandardCharsets.UTF_8);
    }
}

package com.massimotter.weave.backend.chat.provider.synapse;

import com.massimotter.weave.backend.chat.config.ChatRuntimeProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public final class MatrixApplicationServiceSecrets {

    private static final int MAX_TOKEN_BYTES = 16_384;

    private final ChatRuntimeProperties.Matrix properties;
    private volatile Tokens loaded;

    /** Graph construction is offline; real startup and first use validate the mounted tokens. */
    public MatrixApplicationServiceSecrets(ChatRuntimeProperties.Matrix properties) {
        this.properties = java.util.Objects.requireNonNull(properties);
    }

    MatrixApplicationServiceSecrets(byte[] asToken, byte[] hsToken) {
        this.properties = null;
        this.loaded = validated(asToken, hsToken);
    }

    public void requireReady() {
        tokens();
    }

    public String asBearerValue() {
        return new String(tokens().asToken, StandardCharsets.UTF_8);
    }

    public boolean matchesHomeserverToken(byte[] candidate) {
        return candidate != null && MessageDigest.isEqual(tokens().hsToken, candidate);
    }

    public boolean matchesAnyToken(byte[] candidate) {
        Tokens tokens = tokens();
        return candidate != null
                && (MessageDigest.isEqual(tokens.asToken, candidate) || MessageDigest.isEqual(tokens.hsToken, candidate));
    }

    private Tokens tokens() {
        Tokens current = loaded;
        if (current != null) return current;
        synchronized (this) {
            if (loaded == null) {
                loaded = validated(readToken(properties.requiredAsTokenFile()),
                        readToken(properties.requiredHsTokenFile()));
            }
            return loaded;
        }
    }

    private static Tokens validated(byte[] asToken, byte[] hsToken) {
        byte[] as = validatedCopy(asToken, "as_token");
        byte[] hs = validatedCopy(hsToken, "hs_token");
        if (MessageDigest.isEqual(as, hs)) {
            throw new IllegalStateException("Matrix Application Service tokens must be distinct.");
        }
        return new Tokens(as, hs);
    }

    private static final class Tokens {
        private final byte[] asToken;
        private final byte[] hsToken;

        private Tokens(byte[] asToken, byte[] hsToken) {
            this.asToken = asToken;
            this.hsToken = hsToken;
        }
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

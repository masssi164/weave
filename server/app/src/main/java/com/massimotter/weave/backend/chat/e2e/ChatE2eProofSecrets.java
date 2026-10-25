package com.massimotter.weave.backend.chat.e2e;

import com.massimotter.weave.backend.config.ChatE2eProofProperties;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceSecrets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;

public final class ChatE2eProofSecrets {

    private static final int MAX_TOKEN_BYTES = 16_384;
    private final byte[] token;

    public ChatE2eProofSecrets(ChatE2eProofProperties properties) {
        this(readToken(properties));
    }

    ChatE2eProofSecrets(byte[] token) {
        String normalized = token == null ? "" : new String(token, StandardCharsets.UTF_8).trim();
        if (normalized.length() < 16 || normalized.length() > MAX_TOKEN_BYTES
                || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalStateException("The isolated Chat E2E proof token is invalid.");
        }
        this.token = normalized.getBytes(StandardCharsets.UTF_8);
    }

    public boolean matches(byte[] candidate) {
        return candidate != null && MessageDigest.isEqual(token, candidate);
    }

    public byte[] hmacKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("weave-chat-e2e-proof-v1\u0000".getBytes(StandardCharsets.UTF_8));
            return digest.digest(token);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Chat E2E proof hashing is unavailable.", exception);
        }
    }

    public boolean conflictsWith(MatrixApplicationServiceSecrets applicationServiceSecrets) {
        return applicationServiceSecrets.matchesAnyToken(token);
    }

    private static byte[] readToken(ChatE2eProofProperties properties) {
        try {
            long size = Files.size(properties.requiredTokenFile());
            if (size < 16 || size > MAX_TOKEN_BYTES) {
                throw new IllegalStateException("The isolated Chat E2E proof token file has an invalid size.");
            }
            return Files.readAllBytes(properties.requiredTokenFile());
        } catch (IOException exception) {
            throw new IllegalStateException("The isolated Chat E2E proof token file could not be read.", exception);
        }
    }
}

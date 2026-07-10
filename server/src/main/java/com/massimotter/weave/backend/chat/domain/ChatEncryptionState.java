package com.massimotter.weave.backend.chat.domain;

import java.util.List;

public record ChatEncryptionState(
        String mode,
        boolean encrypted,
        boolean serverMayReadContent,
        boolean clientVerificationRequired,
        List<String> supportSafeBlockers) {

    public ChatEncryptionState {
        mode = mode == null || mode.isBlank() ? "unencrypted" : mode.trim();
        supportSafeBlockers = supportSafeBlockers == null ? List.of() : List.copyOf(supportSafeBlockers);
        if (encrypted && serverMayReadContent) {
            throw new IllegalArgumentException("encrypted Chat content cannot be marked server-readable");
        }
    }

    public static ChatEncryptionState unencrypted() {
        return new ChatEncryptionState("unencrypted", false, true, false, List.of());
    }

    public static ChatEncryptionState matrixMegolm() {
        return new ChatEncryptionState(
                ChatEncryptedEnvelope.MEGOLM_V1,
                true,
                false,
                true,
                List.of());
    }
}

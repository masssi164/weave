package com.massimotter.weave.backend.chat.application;

import java.util.List;

/** Distinct existing adapter declarations: runtime diagnostics and future replacement preflight. */
public final class ChatProviderCatalog {
    private ChatProviderCatalog() {}

    public static List<String> registryCandidates() {
        return List.of("weave-native", "matrix-synapse", "synapse-homeserver", "slack", "microsoft-teams");
    }

    public static List<String> replacementCandidates() {
        return List.of("matrix-synapse", "synapse-homeserver", "microsoft-teams", "slack", "nextcloud-talk");
    }
}

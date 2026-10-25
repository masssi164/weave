package com.massimotter.weave.backend.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Isolated-stack-only proof endpoint contract. Disabled in every persistent environment. */
@ConfigurationProperties(prefix = "weave.chat.e2e-proof")
public record ChatE2eProofProperties(
        boolean enabled,
        String tokenFile,
        String runId,
        String stackScope) {

    public ChatE2eProofProperties {
        tokenFile = normalize(tokenFile);
        runId = normalize(runId);
        stackScope = normalize(stackScope);
    }

    public Path requiredTokenFile() {
        if (!enabled || tokenFile.isBlank()) {
            throw new IllegalStateException("The isolated Chat E2E proof token file is not configured.");
        }
        return Path.of(tokenFile).toAbsolutePath().normalize();
    }

    public String requiredRunId() {
        if (!enabled || !"isolated".equals(stackScope) || runId.isBlank() || runId.length() > 160
                || !runId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,159}")) {
            throw new IllegalStateException("The isolated Chat E2E proof scope or run identifier is not configured.");
        }
        return runId;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

package com.massimotter.weave.backend.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderRedactorTest {

    @Test
    void redactsSecretsCredentialUrlsBearerTokensAndSensitiveQueries() {
        String raw = "GET https://gitlab.example.local/api/v4/projects?access_token=abc123 "
                + "Authorization=Bearer super.secret.token password=hunter2 "
                + "https://user:app-password@files.example.local/remote.php/dav";

        String redacted = ProviderRedactor.redact(raw);

        assertThat(redacted)
                .doesNotContain("abc123")
                .doesNotContain("super.secret.token")
                .doesNotContain("hunter2")
                .doesNotContain("app-password")
                .contains("[redacted]");
    }
}

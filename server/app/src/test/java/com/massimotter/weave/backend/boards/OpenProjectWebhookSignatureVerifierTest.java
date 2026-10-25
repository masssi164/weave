package com.massimotter.weave.backend.boards;

import com.massimotter.weave.backend.boards.openproject.OpenProjectWebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenProjectWebhookSignatureVerifierTest {

    private final OpenProjectWebhookSignatureVerifier verifier = new OpenProjectWebhookSignatureVerifier();

    @Test
    void acceptsOpenProjectSha1HmacSignatureForRawBody() {
        byte[] rawBody = "{\"action\":\"work_package:updated\",\"work_package\":{\"id\":99}}"
                .getBytes(StandardCharsets.UTF_8);
        String header = verifier.signForTest(rawBody, "webhook-secret");

        assertThat(header).startsWith("sha1=");
        assertThat(verifier.verify(header, rawBody, "webhook-secret")).isTrue();
    }

    @Test
    void rejectsMissingMalformedOrMismatchedSignaturesWithoutLeakingSecretDetails() {
        byte[] rawBody = "{\"action\":\"project:updated\"}".getBytes(StandardCharsets.UTF_8);
        String validHeader = verifier.signForTest(rawBody, "webhook-secret");

        assertThat(verifier.verify(null, rawBody, "webhook-secret")).isFalse();
        assertThat(verifier.verify("sha256=abc", rawBody, "webhook-secret")).isFalse();
        assertThat(verifier.verify("sha1=not-hex", rawBody, "webhook-secret")).isFalse();
        assertThat(verifier.verify(validHeader, "{}".getBytes(StandardCharsets.UTF_8), "webhook-secret")).isFalse();
        assertThat(verifier.verify(validHeader, rawBody, "other-secret")).isFalse();
        assertThat(verifier.verify(validHeader, rawBody, " ")).isFalse();
    }
}

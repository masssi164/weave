package com.massimotter.weave.backend.chat.e2e;

import com.massimotter.weave.backend.config.ChatE2eProofProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatE2eCallbackReplayTapTest {

    @Test
    void capturesOnlyTheFirstCallbackAndDefensivelyCopiesItsPayload() {
        ChatE2eCallbackReplayTap tap = new ChatE2eCallbackReplayTap(properties());
        byte[] firstPayload = "{\"events\":[{\"type\":\"m.room.encrypted\"}]}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(tap.captureFirst("homeserver-transaction-1", firstPayload)).isTrue();
        firstPayload[0] = 'x';
        assertThat(tap.captureFirst(
                "homeserver-transaction-2",
                "{\"events\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8))).isFalse();

        ChatE2eCallbackReplayTap.CapturedCallback captured = tap.captured().orElseThrow();
        assertThat(captured.transactionId()).isEqualTo("homeserver-transaction-1");
        assertThat(new String(captured.payload(), java.nio.charset.StandardCharsets.UTF_8))
                .startsWith("{\"events\"");
        byte[] returned = captured.payload();
        returned[0] = 'y';
        assertThat(new String(captured.payload(), java.nio.charset.StandardCharsets.UTF_8))
                .startsWith("{\"events\"");
    }

    private ChatE2eProofProperties properties() {
        return new ChatE2eProofProperties(true, "/private/proof-token", "isolated-run-1234", "isolated");
    }
}

package com.massimotter.weave.backend.service.interop;

import com.massimotter.weave.backend.config.InteropGatewayProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.interop.CanonicalBridgeEventResponse;
import com.massimotter.weave.backend.model.interop.SlackEventRequest;
import com.massimotter.weave.backend.model.interop.SlackOutboundMessageRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InteropCoreServiceTest {

    @Test
    void idempotencyKeysAreStableAndNamespaced() {
        IdempotencyKeyService service = new IdempotencyKeyService();

        assertThat(service.key("slack:event", "T1:E1"))
                .isEqualTo(service.key("slack:event", "T1:E1"))
                .startsWith("idem_");
        assertThat(service.key("teams:event", "T1:E1"))
                .isNotEqualTo(service.key("slack:event", "T1:E1"));
    }

    @Test
    void enabledSlackSandboxMapsTextEventWithoutSecretOrProductionDelivery() {
        InteropGatewayService service = enabledService();

        CanonicalBridgeEventResponse event = service.ingestSlackEvent(
                new SlackEventRequest("E123", "T123", "C123", "U123", "hello", null, null));

        assertThat(event.provider()).isEqualTo("slack");
        assertThat(event.roomRef()).isEqualTo("room-123");
        assertThat(event.dryRunOnly()).isTrue();
        assertThat(event.payload()).containsEntry("text", "hello");
        assertThat(event.toString()).doesNotContain("secret://");
    }

    @Test
    void outboundSlackSandboxMapsMessageWithoutProviderDelivery() {
        InteropGatewayService service = enabledService();

        var response = service.sendSlackMessage(new SlackOutboundMessageRequest("W1", "hello from Weave", null));

        assertThat(response.provider()).isEqualTo("slack");
        assertThat(response.channelRef()).isEqualTo("C123");
        assertThat(response.deliveryStatus()).isEqualTo("sandbox-not-delivered");
        assertThat(response.dryRunOnly()).isTrue();
        assertThat(response.productionCallAttempted()).isFalse();
        assertThat(response.toString()).doesNotContain("secret://");
    }

    @Test
    void slackSandboxPreventsBotMessageLoopsBeforeMapping() {
        InteropGatewayService service = enabledService();

        assertThatThrownBy(() -> service.ingestSlackEvent(
                        new SlackEventRequest("E123", "T123", "C123", "U123", "loop", null, "bot_message")))
                .isInstanceOf(ApiErrorException.class)
                .extracting("code")
                .isEqualTo("slack-loop-prevented");
    }

    private InteropGatewayService enabledService() {
        InteropGatewayProperties properties = new InteropGatewayProperties(
                true,
                new InteropGatewayProperties.Provider(
                        true,
                        "client-id",
                        "secret://slack/client",
                        "secret://slack/signing",
                        "secret://slack/token",
                        "T123",
                        "C123",
                        "room-123"),
                null,
                "support-safe-redacted");
        return new InteropGatewayService(
                properties,
                new IdempotencyKeyService(),
                new SlackSignatureVerifier(),
                ignored -> java.util.Optional.of("signing-secret"));
    }

    @Test
    void slackSignatureVerifierMatchesKnownHmac() {
        SlackSignatureVerifier verifier = new SlackSignatureVerifier();
        String secret = "8f742231b10e8888abcd99yyyzzz85a5";
        String timestamp = "1531420618";
        String body = "token=xyzz&team_id=T1&team_domain=example&channel_id=C1&channel_name=test&user_id=U1&user_name=sam&command=/weather&text=94070&response_url=https://hooks.slack.com/commands/1234/5678";
        String signature = "v0=3c70f074de800658cbf1e6d468ee3919304a7459c8eead796123476f04c2b243";

        assertThat(verifier.verify(secret, timestamp, body, signature)).isTrue();
        assertThat(verifier.verify(secret, timestamp, body, signature.replace('a', 'b'))).isFalse();
    }
}

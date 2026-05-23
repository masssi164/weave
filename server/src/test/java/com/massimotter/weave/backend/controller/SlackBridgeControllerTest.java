package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.service.interop.SlackSecretResolver;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
        "weave.interop.enabled=true",
        "weave.interop.slack.enabled=true",
        "weave.interop.slack.client-id=client-id",
        "weave.interop.slack.client-secret-ref=secret://slack/client-secret",
        "weave.interop.slack.signing-secret-ref=secret://slack/signing-secret",
        "weave.interop.slack.token-ref=secret://slack/bot-token",
        "weave.interop.slack.workspace-id=T123",
        "weave.interop.slack.channel-id=C123",
        "weave.interop.slack.room-id=!room:weave.local"
})
@AutoConfigureMockMvc
class SlackBridgeControllerTest {

    private static final String SIGNING_SECRET = "test-signing-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private SlackSecretResolver slackSecretResolver;

    @Test
    void rejectsInboundSlackEventWithoutVerifiableSignature() throws Exception {
        when(slackSecretResolver.resolveSigningSecret(eq("secret://slack/signing-secret")))
                .thenReturn(java.util.Optional.of(SIGNING_SECRET));

        mockMvc.perform(post("/api/interop/slack/events")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slackEventBody(null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("slack-signature-invalid"));
    }

    @Test
    void mapsSignedInboundSlackTextEventToCanonicalSandboxEvent() throws Exception {
        when(slackSecretResolver.resolveSigningSecret(eq("secret://slack/signing-secret")))
                .thenReturn(java.util.Optional.of(SIGNING_SECRET));
        String body = slackEventBody(null);
        String timestamp = "1710000000";

        mockMvc.perform(post("/api/interop/slack/events")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Slack-Request-Timestamp", timestamp)
                        .header("X-Slack-Signature", slackSignature(timestamp, body))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("slack"))
                .andExpect(jsonPath("$.roomRef").value("!room:weave.local"))
                .andExpect(jsonPath("$.dryRunOnly").value(true))
                .andExpect(content().string(not(containsString("secret://slack"))));
    }

    @Test
    void preventsSlackBotMessageLoopBeforeMapping() throws Exception {
        when(slackSecretResolver.resolveSigningSecret(eq("secret://slack/signing-secret")))
                .thenReturn(java.util.Optional.of(SIGNING_SECRET));
        String body = slackEventBody("bot_message");
        String timestamp = "1710000001";

        mockMvc.perform(post("/api/interop/slack/events")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Slack-Request-Timestamp", timestamp)
                        .header("X-Slack-Signature", slackSignature(timestamp, body))
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("slack-loop-prevented"));
    }

    @Test
    void outboundSlackMessageStaysSandboxOnlyAndIdempotent() throws Exception {
        mockMvc.perform(post("/api/interop/slack/messages")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "weave-message-1",
                                  "text": "hello from Weave"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("slack"))
                .andExpect(jsonPath("$.channelRef").value("C123"))
                .andExpect(jsonPath("$.deliveryStatus").value("sandbox-not-delivered"))
                .andExpect(jsonPath("$.dryRunOnly").value(true))
                .andExpect(jsonPath("$.productionCallAttempted").value(false))
                .andExpect(content().string(not(containsString("secret://slack"))));
    }

    private String slackEventBody(String subtype) {
        String subtypeField = subtype == null ? "" : ",\n  \"subtype\": \"" + subtype + "\"";
        return """
                {
                  "eventId": "E123",
                  "teamId": "T123",
                  "channelId": "C123",
                  "userId": "U123",
                  "text": "hello from Slack",
                  "threadTs": null%s
                }
                """.formatted(subtypeField);
    }

    private String slackSignature(String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(("v0:" + timestamp + ":" + body).getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder("v0=");
        for (byte b : digest) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor workspaceJwt() {
        return jwt().jwt(jwt -> jwt
                        .issuer("https://auth.example.invalid/realms/weave")
                        .subject("owner-123")
                        .claim("workspace_id", "weave-dev"))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}

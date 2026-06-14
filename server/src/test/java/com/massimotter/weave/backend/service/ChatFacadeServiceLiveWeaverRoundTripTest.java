package com.massimotter.weave.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WeaverPaChatProperties;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.chat.ChatSendMessageRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "WEAVE_WEAVER_PA_CHAT_LIVE", matches = "(?i:true|1|yes|on)")
class ChatFacadeServiceLiveWeaverRoundTripTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void sendsWeaveChatMessageThroughWeaverHarnessToLiveLmStudioAndReturnsAssistantResponse() throws Exception {
        try (LiveWeaverBridge bridge = LiveWeaverBridge.start()) {
            InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
            WorkspaceCapabilityProperties properties = workspaceCapabilityProperties();
            ChatFacadeService service = new ChatFacadeService(
                    properties,
                    workspaceCapabilityService(properties),
                    request -> ContextAuthorizationDecision.allow("live harness context authorization"),
                    new ContextAuthorizationProperties(null, null, null, null, null, null, null, null),
                    auditPublisher,
                    new HttpWeaverPaChatClient(new WeaverPaChatProperties(
                            true,
                            bridge.url(),
                            Duration.ofSeconds(60),
                            "credentialref://weave/channels/weave-chat/runtime-token")));

            Jwt member = jwt(List.of("member"), List.of("weave-weaver-runtime"));
            var sent = service.sendMessage(member, "pa-weaver", new ChatSendMessageRequest(
                    "Ask PA Weaver through Weave Chat to prove the live LM Studio round trip.",
                    List.of()));
            var messages = service.messages(member, "pa-weaver").messages();

            assertThat(bridge.requestBody()).contains("channels.weave-chat")
                    .contains("credentialref://weave/channels/weave-chat/runtime-token")
                    .doesNotContain("Authorization", "Bearer ", "access_token", "refresh_token", "localhost");
            assertThat(sent.deliveryEvidence())
                    .containsEntry("channelId", "channels.weave-chat")
                    .containsEntry("weaverReceived", true)
                    .containsEntry("lmStudioResponseReceived", true)
                    .containsEntry("supportSafe", true);
            assertThat(messages)
                    .filteredOn(message -> "pa-weaver-to-lmstudio".equals(message.deliveryEvidence().get("route")))
                    .singleElement()
                    .satisfies(message -> {
                        assertThat(message.text()).isNotBlank();
                        assertThat(message.deliveryEvidence())
                                .containsEntry("route", "pa-weaver-to-lmstudio")
                                .containsEntry("channelId", "channels.weave-chat")
                                .containsEntry("modelRef", "lmstudio/qwen/qwen3.5-9b")
                                .containsEntry("source", "lmstudio-openai-compatible")
                                .containsEntry("liveCall", "completed")
                                .containsEntry("rawProviderDiagnosticsExposed", false)
                                .containsEntry("supportSafe", true);
                    });
            assertThat(auditPublisher.events())
                    .extracting(event -> event.action())
                    .contains(AuditAction.CHAT_MESSAGE_SENT, AuditAction.WEAVER_PA_CHAT_TURN_COMPLETED);
            assertThat(auditPublisher.events().toString())
                    .doesNotContain("Authorization", "Bearer ", "access_token", "refresh_token", "localhost");
        }
    }

    private static final class LiveWeaverBridge implements AutoCloseable {
        private final HttpServer server;
        private final AtomicReference<String> requestBody = new AtomicReference<>("");

        private LiveWeaverBridge(HttpServer server) {
            this.server = server;
        }

        static LiveWeaverBridge start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            LiveWeaverBridge bridge = new LiveWeaverBridge(server);
            server.createContext("/pa-chat", exchange -> {
                try {
                    bridge.requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    JsonNode evidence = runLiveWeaverHarness();
                    byte[] response = OBJECT_MAPPER.writeValueAsBytes(Map.of(
                            "weaverReceived", true,
                            "lmStudioResponseReceived", true,
                            "answer", evidence.path("outbound").path("text").asText(),
                            "modelRef", evidence.path("lmStudio").path("modelRef").asText(),
                            "providerRef", "provider:model:lmstudio",
                            "auditRef", "audit://weaver/pa-chat/live-roundtrip",
                            "supportSafeEvidence", Map.of(
                                    "channelId", "channels.weave-chat",
                                    "modelRef", evidence.path("lmStudio").path("modelRef").asText(),
                                    "source", evidence.path("modelResponse").path("source").asText(),
                                    "liveCall", evidence.path("lmStudio").path("liveCall").asText(),
                                    "approvedReplyTool", evidence.path("runtimeProfilePolicy").path("approvedReplyTool").asText(),
                                    "unsafeExecTool", evidence.path("runtimeProfilePolicy").path("unsafeExecTool").asText(),
                                    "rawProviderDiagnosticsExposed", false,
                                    "supportSafe", true)));
                    exchange.getResponseHeaders().add("content-type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                } catch (Exception exception) {
                    byte[] response = ("{\"error\":\"live Weaver harness failed\"}").getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(503, response.length);
                    exchange.getResponseBody().write(response);
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return bridge;
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/pa-chat";
        }

        String requestBody() {
            return requestBody.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static JsonNode runLiveWeaverHarness() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "node",
                "--import",
                "tsx",
                "scripts/weaver/weave-chat-roundtrip.ts")
                .directory(resolveWeaverCheckout().toFile())
                .redirectErrorStream(true);
        processBuilder.environment().put("WEAVER_WEAVE_CHAT_ROUNDTRIP_LIVE", "true");
        Process process = processBuilder.start();
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Live Weaver harness failed with exit " + exitCode + ": " + output);
        }
        JsonNode evidence = OBJECT_MAPPER.readTree(output);
        if (!"live".equals(evidence.path("mode").asText())
                || !"completed".equals(evidence.path("lmStudio").path("liveCall").asText())
                || !"lmstudio-openai-compatible".equals(evidence.path("modelResponse").path("source").asText())) {
            throw new IllegalStateException("Live Weaver harness did not return completed LM Studio evidence: " + output);
        }
        return evidence;
    }

    private static Path resolveWeaverCheckout() {
        Path siblingFromRepoRoot = Path.of("../weaver").toAbsolutePath().normalize();
        Path siblingFromServerProject = Path.of("../../weaver").toAbsolutePath().normalize();
        if (Files.exists(siblingFromRepoRoot.resolve("scripts/weaver/weave-chat-roundtrip.ts"))) {
            return siblingFromRepoRoot;
        }
        if (Files.exists(siblingFromServerProject.resolve("scripts/weaver/weave-chat-roundtrip.ts"))) {
            return siblingFromServerProject;
        }
        throw new IllegalStateException("Expected sibling weaver checkout with scripts/weaver/weave-chat-roundtrip.ts");
    }

    private WorkspaceCapabilityProperties workspaceCapabilityProperties() {
        return new WorkspaceCapabilityProperties(
                null,
                new WorkspaceCapabilityProperties.Capability(true, null, WorkspaceCapabilityReadiness.READY),
                null,
                null,
                null,
                null);
    }

    private WorkspaceCapabilityService workspaceCapabilityService(WorkspaceCapabilityProperties properties) {
        OAuth2ResourceServerProperties resourceServerProperties = new OAuth2ResourceServerProperties();
        resourceServerProperties.getJwt().setIssuerUri("https://auth.example.invalid/realms/acme");
        return new WorkspaceCapabilityService(
                resourceServerProperties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                properties,
                new WeaverRuntimeProperties(
                        true,
                        "weaver-governed-baseline",
                        "ghcr.io/masssi164/weaver-openclaw:policy-generated",
                        "/var/lib/weave/weaver/{userId}",
                        ".weaver/agents",
                        "weave-runtime-net",
                        List.of("weave-weaver-runtime"),
                        List.of("weaver.files_read", "weaver.boards_read", "weaver.exec_disabled"),
                        List.of("weave-chat"),
                        List.of("message.send"),
                        false,
                        false,
                        true,
                        true));
    }

    private Jwt jwt(List<String> roles, List<String> groups) {
        Instant now = Instant.parse("2026-05-25T12:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("weave_tenant_id", "tenant-default")
                .claim("realm_access", Map.of("roles", roles))
                .claim("groups", groups)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}

package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.WeaverPaChatProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpWeaverPaChatClientTest {

    @Test
    void callsConfiguredWeaverBridgeAndReturnsOnlySupportSafeEvidence() throws Exception {
        try (BridgeServer bridge = BridgeServer.start()) {
            HttpWeaverPaChatClient client = new HttpWeaverPaChatClient(new WeaverPaChatProperties(
                    true,
                    bridge.url(),
                    Duration.ofSeconds(2),
                    "credentialref://weave/channels/weave-chat/runtime-token"));

            WeaverPaChatTurnResult result = client.completeTurn(new WeaverPaChatTurnRequest(
                    "pa-weaver",
                    "msg-1",
                    "user:member",
                    "Ping through Weave Chat",
                    "channels.weave-chat",
                    "provider:chat:selected-by-admin",
                    "lmstudio/qwen/qwen3.5-9b",
                    Map.of("supportSafe", true)));

            assertThat(bridge.requestBody()).contains("channels.weave-chat")
                    .contains("credentialref://weave/channels/weave-chat/runtime-token")
                    .doesNotContain("Bearer ", "access_token", "localhost");
            assertThat(result.weaverReceived()).isTrue();
            assertThat(result.lmStudioResponseReceived()).isTrue();
            assertThat(result.answer()).contains("LM Studio live bridge answer");
            assertThat(result.modelRef()).isEqualTo("lmstudio/qwen/qwen3.5-9b");
            assertThat(result.supportSafeEvidence()).containsEntry("rawProviderDiagnosticsExposed", false);
        }
    }

    @Test
    void failsClosedWhenBridgeIsNotConfigured() {
        HttpWeaverPaChatClient client = new HttpWeaverPaChatClient(new WeaverPaChatProperties(
                false,
                "",
                Duration.ofSeconds(2),
                "credentialref://weave/channels/weave-chat/runtime-token"));

        assertThatThrownBy(() -> client.completeTurn(new WeaverPaChatTurnRequest(
                "pa-weaver",
                "msg-1",
                "user:member",
                "Ping",
                "channels.weave-chat",
                "provider:chat:selected-by-admin",
                "lmstudio/qwen/qwen3.5-9b",
                Map.of("supportSafe", true))))
                .isInstanceOf(WeaverPaChatUnavailableException.class)
                .hasMessageContaining("refusing to synthesize LM Studio evidence");
    }

    private static final class BridgeServer implements AutoCloseable {
        private final HttpServer server;
        private String requestBody = "";

        private BridgeServer(HttpServer server) {
            this.server = server;
        }

        static BridgeServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            BridgeServer bridge = new BridgeServer(server);
            server.createContext("/pa-chat", exchange -> {
                bridge.requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] body = ("{"
                        + "\"weaverReceived\":true,"
                        + "\"lmStudioResponseReceived\":true,"
                        + "\"answer\":\"LM Studio live bridge answer through Weaver weave-chat.\","
                        + "\"modelRef\":\"lmstudio/qwen/qwen3.5-9b\","
                        + "\"providerRef\":\"provider:model:lmstudio\","
                        + "\"auditRef\":\"audit://weaver/pa-chat/test-bridge\","
                        + "\"supportSafeEvidence\":{\"rawProviderDiagnosticsExposed\":false,\"supportSafe\":true}"
                        + "}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("content-type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return bridge;
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/pa-chat";
        }

        String requestBody() {
            return requestBody;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}

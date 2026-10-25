package com.massimotter.weave.backend.chat.provider.synapse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.domain.ChatEncryptedEnvelope;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SynapseApplicationServiceClientTest {

    private static final String AS_TOKEN = "as-token-value-0123456789";
    private static final String HS_TOKEN = "hs-token-value-0123456789";
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC);

    private final ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
    private final List<ObservedRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicBoolean throttleSend = new AtomicBoolean();
    private final AtomicReference<String> retryAfter = new AtomicReference<>("120");
    private HttpServer server;
    private SynapseApplicationServiceClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        ChatRuntimeProperties.Matrix properties = new ChatRuntimeProperties.Matrix(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "matrix.internal",
                "weave-chat-synapse",
                "_weave_",
                "/private/as-token",
                "/private/hs-token",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofSeconds(60),
                1_048_576,
                100);
        MatrixApplicationServiceSecrets secrets = new MatrixApplicationServiceSecrets(
                AS_TOKEN.getBytes(StandardCharsets.UTF_8),
                HS_TOKEN.getBytes(StandardCharsets.UTF_8));
        client = new SynapseApplicationServiceClient(
                properties.requiredInternalBaseUri(),
                properties.requiredServerName(),
                secrets.asBearerValue(),
                properties.requestTimeout(),
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                objectMapper,
                FIXED);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void usesOnlyApplicationServiceIdentityAndForwardsOpaqueEncryptedEnvelope() throws Exception {
        String author = "@_weave_opaqueauthor:matrix.internal";
        String collaborator = "@_weave_opaquecollaborator:matrix.internal";
        client.ensureVirtualUser(author);
        var room = client.createRoom(
                author,
                "#_weave_opaqueroom:matrix.internal",
                "Collaboration",
                List.of(collaborator),
                ChatEncryptedEnvelope.MEGOLM_V1);
        Map<String, Object> envelope = Map.of(
                "algorithm", ChatEncryptedEnvelope.MEGOLM_V1,
                "ciphertext", "opaque-ciphertext",
                "sender_key", "curve25519:opaque",
                "session_id", "opaque-session",
                "device_id", "OPAQUEDEVICE");
        var event = client.sendEvent(
                author,
                room.providerRef(),
                "weave_opaque_txn",
                ChatEventContent.encrypted(envelope),
                null);

        assertThat(event.providerRef()).isEqualTo("$opaque-event:matrix.internal");
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.authorization()).isEqualTo("Bearer " + AS_TOKEN);
            assertThat(request.authorization()).doesNotContain(HS_TOKEN, "member-token", "keycloak");
        });
        ObservedRequest registration = requests.stream()
                .filter(request -> request.path().endsWith("/register"))
                .findFirst().orElseThrow();
        JsonNode registrationBody = objectMapper.readTree(registration.body());
        assertThat(registrationBody.path("type").asString()).isEqualTo("m.login.application_service");
        assertThat(registrationBody.path("inhibit_login").asBoolean()).isTrue();
        assertThat(registrationBody.has("password")).isFalse();
        assertThat(registrationBody.has("access_token")).isFalse();

        ObservedRequest roomCreate = requests.stream()
                .filter(request -> request.path().endsWith("/createRoom"))
                .findFirst().orElseThrow();
        JsonNode roomCreateBody = objectMapper.readTree(roomCreate.body());
        assertThat(roomCreateBody.path("invite").get(0).asString()).isEqualTo(collaborator);
        assertThat(roomCreateBody.path("initial_state").get(0).path("type").asString())
                .isEqualTo("m.room.encryption");
        assertThat(roomCreateBody.path("initial_state").get(0).path("state_key").asString()).isEmpty();
        assertThat(roomCreateBody.path("initial_state").get(0).path("content").path("algorithm").asString())
                .isEqualTo(ChatEncryptedEnvelope.MEGOLM_V1);

        ObservedRequest encryptedSend = requests.stream()
                .filter(request -> request.path().contains("/send/m.room.encrypted/"))
                .findFirst().orElseThrow();
        assertThat(encryptedSend.query()).contains("user_id=");
        JsonNode providerEnvelope = objectMapper.readTree(encryptedSend.body());
        assertThat(providerEnvelope.path("ciphertext").asString()).isEqualTo("opaque-ciphertext");
        assertThat(providerEnvelope.toString()).doesNotContain("plaintext-sentinel", "formatted_body", "msgtype");
    }

    @Test
    void mapsThrottleToSupportSafeRetryAfterWithoutReturningProviderBody() {
        throttleSend.set(true);
        Map<String, Object> envelope = Map.of(
                "algorithm", ChatEncryptedEnvelope.MEGOLM_V1,
                "ciphertext", "opaque-ciphertext",
                "sender_key", "curve25519:opaque",
                "session_id", "opaque-session",
                "device_id", "OPAQUEDEVICE");

        assertThatThrownBy(() -> client.sendEvent(
                "@_weave_opaqueauthor:matrix.internal",
                "!opaque-room:matrix.internal",
                "weave_opaque_txn",
                ChatEventContent.encrypted(envelope),
                null))
                .isInstanceOfSatisfying(SynapseProviderException.class, exception -> {
                    assertThat(exception.supportSafeCode()).isEqualTo("chat-provider-throttled");
                    assertThat(exception.retryAt()).isEqualTo(FIXED.instant().plusSeconds(120));
                    assertThat(exception.getMessage()).doesNotContain("opaque provider failure body");
                });
    }

    @Test
    void parsesRfc1123RetryAfterDates() {
        throttleSend.set(true);
        retryAfter.set("Wed, 15 Jul 2026 10:03:00 GMT");

        assertThatThrownBy(() -> client.sendEvent(
                "@_weave_opaqueauthor:matrix.internal",
                "!opaque-room:matrix.internal",
                "weave_opaque_txn",
                ChatEventContent.encrypted(Map.of(
                        "algorithm", ChatEncryptedEnvelope.MEGOLM_V1,
                        "ciphertext", "opaque-ciphertext",
                        "sender_key", "curve25519:opaque",
                        "session_id", "opaque-session",
                        "device_id", "OPAQUEDEVICE")),
                null))
                .isInstanceOfSatisfying(SynapseProviderException.class, exception ->
                        assertThat(exception.retryAt()).isEqualTo(FIXED.instant().plusSeconds(180)));
    }

    @Test
    void provesExactMembershipEncryptionEventMappingAndCiphertextCorrelation() {
        String author = "@_weave_opaqueauthor:matrix.internal";
        String collaborator = "@_weave_opaquecollaborator:matrix.internal";
        String outsider = "@_weave_opaqueoutsider:matrix.internal";

        SynapseApplicationServiceClient.ProviderRoomEvidence evidence = client.readRoomEvidence(
                author,
                "!opaque-room:matrix.internal",
                List.of(author, collaborator),
                List.of("$opaque-event-1:matrix.internal", "$opaque-event-2:matrix.internal"),
                List.of(sha256("opaque-ciphertext-1"), sha256("opaque-ciphertext-2")),
                outsider);

        assertThat(evidence.authorizedMembershipExact()).isTrue();
        assertThat(evidence.outsiderAbsent()).isTrue();
        assertThat(evidence.outsiderReadDenied()).isTrue();
        assertThat(evidence.encryptionStateVerified()).isTrue();
        assertThat(evidence.encryptedEventRefsExact()).isTrue();
        assertThat(evidence.ciphertextHashesExact()).isTrue();
        assertThat(evidence.encryptedEventCount()).isEqualTo(2);
        assertThat(evidence.plaintextEventCount()).isZero();

        SynapseApplicationServiceClient.ProviderRoomEvidence incompleteExpectation = client.readRoomEvidence(
                author,
                "!opaque-room:matrix.internal",
                List.of(author),
                List.of("$opaque-event-1:matrix.internal"),
                List.of(sha256("opaque-ciphertext-1")),
                outsider);
        assertThat(incompleteExpectation.authorizedMembershipExact()).isFalse();
        assertThat(incompleteExpectation.encryptedEventRefsExact()).isFalse();
        assertThat(incompleteExpectation.ciphertextHashesExact()).isFalse();
    }

    @Test
    void treatsAnUnmappedOutsiderAsFailClosedWithoutAProviderRead() {
        String author = "@_weave_opaqueauthor:matrix.internal";
        String collaborator = "@_weave_opaquecollaborator:matrix.internal";

        SynapseApplicationServiceClient.ProviderRoomEvidence evidence = client.readRoomEvidence(
                author,
                "!opaque-room:matrix.internal",
                List.of(author, collaborator),
                List.of("$opaque-event-1:matrix.internal", "$opaque-event-2:matrix.internal"),
                List.of(sha256("opaque-ciphertext-1"), sha256("opaque-ciphertext-2")),
                null);

        assertThat(evidence.authorizedMembershipExact()).isTrue();
        assertThat(evidence.outsiderAbsent()).isTrue();
        assertThat(evidence.outsiderReadDenied()).isTrue();
        assertThat(requests.stream()
                .filter(request -> request.path().endsWith("/messages"))
                .count()).isEqualTo(1);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new ObservedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                body));
        int status = 200;
        String response;
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/register")) {
            response = "{\"user_id\":\"@_weave_opaqueauthor:matrix.internal\"}";
        } else if (path.endsWith("/createRoom")) {
            response = "{\"room_id\":\"!opaque-room:matrix.internal\"}";
        } else if (path.contains("/send/") && throttleSend.get()) {
            status = 429;
            exchange.getResponseHeaders().add("Retry-After", retryAfter.get());
            response = "{\"errcode\":\"M_LIMIT_EXCEEDED\",\"error\":\"opaque provider failure body\"}";
        } else if (path.contains("/send/")) {
            response = "{\"event_id\":\"$opaque-event:matrix.internal\"}";
        } else if (path.endsWith("/joined_members")) {
            response = "{\"joined\":{"
                    + "\"@_weave_opaqueauthor:matrix.internal\":{},"
                    + "\"@_weave_opaquecollaborator:matrix.internal\":{}}}";
        } else if (path.endsWith("/state/m.room.encryption/")) {
            response = "{\"algorithm\":\"m.megolm.v1.aes-sha2\"}";
        } else if (path.endsWith("/messages") && exchange.getRequestURI().getRawQuery().contains("opaqueoutsider")) {
            status = 403;
            response = "{\"errcode\":\"M_FORBIDDEN\"}";
        } else if (path.endsWith("/messages")) {
            response = "{\"chunk\":["
                    + "{\"event_id\":\"$opaque-event-2:matrix.internal\",\"type\":\"m.room.encrypted\","
                    + "\"content\":{\"ciphertext\":\"opaque-ciphertext-2\"}},"
                    + "{\"event_id\":\"$opaque-event-1:matrix.internal\",\"type\":\"m.room.encrypted\","
                    + "\"content\":{\"ciphertext\":\"opaque-ciphertext-1\"}}]}";
        } else {
            status = 404;
            response = "{}";
        }
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record ObservedRequest(
            String method,
            String path,
            String query,
            String authorization,
            String body) {
    }
}

package com.massimotter.weave.backend.chat.provider.synapse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.domain.ChatEncryptedEnvelope;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatEventKind;
import com.massimotter.weave.backend.chat.domain.ChatRelation;
import com.massimotter.weave.backend.chat.port.ChatSouthboundProvider;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.util.UriUtils;

final class SynapseApplicationServiceClient {

    private static final int MAX_RESPONSE_BYTES = 1_048_576;

    private final URI baseUri;
    private final String serverName;
    private final String asToken;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    SynapseApplicationServiceClient(
            ChatRuntimeProperties.Matrix properties,
            MatrixApplicationServiceSecrets secrets,
            ObjectMapper objectMapper,
            Clock clock) {
        this(
                properties.requiredInternalBaseUri(),
                properties.requiredServerName(),
                secrets.asBearerValue(),
                properties.requestTimeout(),
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build(),
                objectMapper,
                clock);
    }

    SynapseApplicationServiceClient(
            URI baseUri,
            String serverName,
            String asToken,
            Duration timeout,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Clock clock) {
        this.baseUri = baseUri;
        this.serverName = serverName;
        this.asToken = asToken;
        this.timeout = timeout;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    ChatSouthboundProvider.ProviderAck ensureVirtualUser(String providerUserRef) {
        String localpart = localpart(providerUserRef);
        JsonNode response = request(
                "POST",
                "/_matrix/client/v3/register",
                null,
                Map.of(
                        "type", "m.login.application_service",
                        "username", localpart,
                        "inhibit_login", true),
                Set.of(200, 400));
        if (response.path("errcode").asText("").equals("M_USER_IN_USE")) {
            return new ChatSouthboundProvider.ProviderAck(providerUserRef, "existing");
        }
        if (response.hasNonNull("errcode")) {
            throw new SynapseProviderException("chat-provider-registration-rejected", null);
        }
        return new ChatSouthboundProvider.ProviderAck(providerUserRef, "registered");
    }

    ChatSouthboundProvider.ProviderAck createRoom(
            String providerActorRef,
            String providerAliasIntent,
            String title,
            List<String> invitedProviderActors,
            String initialEncryptionAlgorithm) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("visibility", "private");
        body.put("preset", "private_chat");
        body.put("name", title);
        body.put("room_alias_name", aliasLocalpart(providerAliasIntent));
        body.put("invite", List.copyOf(invitedProviderActors));
        if (initialEncryptionAlgorithm != null && !"unencrypted".equals(initialEncryptionAlgorithm)) {
            if (!com.massimotter.weave.backend.chat.domain.ChatEncryptedEnvelope.MEGOLM_V1
                    .equals(initialEncryptionAlgorithm)) {
                throw new IllegalArgumentException("initial Chat encryption algorithm is unsupported");
            }
            body.put("initial_state", List.of(Map.of(
                    "type", "m.room.encryption",
                    "state_key", "",
                    "content", Map.of("algorithm", initialEncryptionAlgorithm))));
        }
        try {
            JsonNode response = request("POST", "/_matrix/client/v3/createRoom", providerActorRef, body, Set.of(200));
            return requiredAck(response, "room_id");
        } catch (SynapseProviderException exception) {
            if (!"M_ROOM_IN_USE".equals(exception.matrixErrcode())) {
                throw exception;
            }
            JsonNode resolved = request(
                    "GET",
                    "/_matrix/client/v3/directory/room/" + path(providerAliasIntent),
                    providerActorRef,
                    null,
                    Set.of(200, 404));
            if (resolved.hasNonNull("room_id")) {
                return requiredAck(resolved, "room_id");
            }
            throw exception;
        }
    }

    ChatSouthboundProvider.ProviderAck createRoom(
            String providerActorRef,
            String providerAliasIntent,
            String title,
            List<String> invitedProviderActors) {
        return createRoom(providerActorRef, providerAliasIntent, title, invitedProviderActors, null);
    }

    ChatSouthboundProvider.ProviderAck joinRoom(String providerActorRef, String providerRoomRef) {
        JsonNode response = request("POST", "/_matrix/client/v3/join/" + path(providerRoomRef),
                providerActorRef, Map.of(), Set.of(200));
        return response.hasNonNull("room_id")
                ? requiredAck(response, "room_id")
                : new ChatSouthboundProvider.ProviderAck(providerRoomRef, "joined");
    }

    ChatSouthboundProvider.ProviderAck leaveRoom(String providerActorRef, String providerRoomRef) {
        request("POST", "/_matrix/client/v3/rooms/" + path(providerRoomRef) + "/leave",
                providerActorRef, Map.of(), Set.of(200));
        return new ChatSouthboundProvider.ProviderAck(providerRoomRef, "left");
    }

    ChatSouthboundProvider.ProviderAck enableEncryption(
            String providerActorRef,
            String providerRoomRef,
            String algorithm) {
        JsonNode response = request("PUT",
                "/_matrix/client/v3/rooms/" + path(providerRoomRef) + "/state/m.room.encryption/",
                providerActorRef,
                Map.of("algorithm", algorithm),
                Set.of(200));
        return requiredAck(response, "event_id");
    }

    ChatSouthboundProvider.ProviderAck sendEvent(
            String providerActorRef,
            String providerRoomRef,
            String providerTransactionId,
            ChatEventContent content,
            String providerRelationTargetRef) {
        String eventType = switch (content.kind()) {
            case MESSAGE -> "m.room.message";
            case REACTION -> "m.reaction";
            case ENCRYPTED -> "m.room.encrypted";
        };
        JsonNode response = request("PUT",
                "/_matrix/client/v3/rooms/" + path(providerRoomRef) + "/send/" + eventType + "/"
                        + path(providerTransactionId),
                providerActorRef,
                providerContent(content, providerRelationTargetRef),
                Set.of(200));
        return requiredAck(response, "event_id");
    }

    ChatSouthboundProvider.ProviderAck redactEvent(
            String providerActorRef,
            String providerRoomRef,
            String providerEventRef,
            String providerTransactionId) {
        JsonNode response = request("PUT",
                "/_matrix/client/v3/rooms/" + path(providerRoomRef) + "/redact/" + path(providerEventRef)
                        + "/" + path(providerTransactionId),
                providerActorRef,
                Map.of(),
                Set.of(200));
        return requiredAck(response, "event_id");
    }

    ChatSouthboundProvider.ProviderAck markRead(
            String providerActorRef,
            String providerRoomRef,
            String providerEventRef) {
        request("POST",
                "/_matrix/client/v3/rooms/" + path(providerRoomRef) + "/receipt/m.read/" + path(providerEventRef),
                providerActorRef,
                Map.of(),
                Set.of(200));
        return new ChatSouthboundProvider.ProviderAck(providerEventRef, "read");
    }

    void setTyping(String providerActorRef, String providerRoomRef, boolean typing, int timeoutMilliseconds) {
        request("PUT",
                "/_matrix/client/v3/rooms/" + path(providerRoomRef) + "/typing/" + path(providerActorRef),
                providerActorRef,
                typing
                        ? Map.of("typing", true, "timeout", Math.max(0, Math.min(timeoutMilliseconds, 120_000)))
                        : Map.of("typing", false),
                Set.of(200));
    }

    boolean authenticatedReadiness(String senderLocalpart) {
        String sender = "@" + senderLocalpart + ":" + serverName;
        JsonNode response = request("GET", "/_matrix/client/v3/account/whoami", sender, null, Set.of(200));
        return sender.equals(response.path("user_id").asText());
    }

    ProviderRoomEvidence readRoomEvidence(
            String providerActorRef,
            String providerRoomRef,
            List<String> expectedJoinedActors,
            List<String> expectedEncryptedEventRefs,
            List<String> expectedCiphertextHashes,
            String outsiderActorRef) {
        JsonNode joined = request("GET",
                "/_matrix/client/v3/rooms/" + path(providerRoomRef) + "/joined_members",
                providerActorRef,
                null,
                Set.of(200));
        JsonNode messages = request("GET",
                "/_matrix/client/v3/rooms/" + path(providerRoomRef) + "/messages?dir=b&limit=100",
                providerActorRef,
                null,
                Set.of(200));
        JsonNode encryption = request("GET",
                "/_matrix/client/v3/rooms/" + path(providerRoomRef) + "/state/m.room.encryption/",
                providerActorRef,
                null,
                Set.of(200));
        int encrypted = 0;
        int plaintext = 0;
        List<String> encryptedEventRefs = new ArrayList<>();
        List<String> ciphertextHashes = new ArrayList<>();
        for (JsonNode event : messages.path("chunk")) {
            String type = event.path("type").asText();
            if ("m.room.encrypted".equals(type)) {
                encrypted++;
                encryptedEventRefs.add(event.path("event_id").asText(""));
                JsonNode ciphertext = event.path("content").path("ciphertext");
                ciphertextHashes.add(sha256(ciphertext.isTextual() ? ciphertext.textValue() : ciphertext.toString()));
            } else if ("m.room.message".equals(type)) {
                plaintext++;
            }
        }
        boolean outsiderDenied = outsiderActorRef == null;
        if (outsiderActorRef != null) {
            try {
                request("GET",
                        "/_matrix/client/v3/rooms/" + path(providerRoomRef) + "/messages?dir=b&limit=1",
                        outsiderActorRef,
                        null,
                        Set.of(403));
                outsiderDenied = true;
            } catch (SynapseProviderException exception) {
                outsiderDenied = false;
            }
        }
        List<String> joinedIds = new ArrayList<>();
        joined.path("joined").propertyNames().forEach(joinedIds::add);
        boolean membershipExact = exactSet(joinedIds, expectedJoinedActors);
        return new ProviderRoomEvidence(
                membershipExact,
                outsiderActorRef == null || !joinedIds.contains(outsiderActorRef),
                outsiderDenied,
                ChatEncryptedEnvelope.MEGOLM_V1.equals(encryption.path("algorithm").asText()),
                exactSet(encryptedEventRefs, expectedEncryptedEventRefs),
                exactSet(ciphertextHashes, expectedCiphertextHashes),
                encrypted,
                plaintext);
    }

    private boolean exactSet(List<String> actual, List<String> expected) {
        return actual != null
                && expected != null
                && actual.size() == expected.size()
                && new HashSet<>(actual).size() == actual.size()
                && new HashSet<>(expected).size() == expected.size()
                && new HashSet<>(actual).equals(new HashSet<>(expected));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Map<String, Object> providerContent(ChatEventContent content, String providerRelationTargetRef) {
        if (content.kind() == ChatEventKind.ENCRYPTED) {
            return content.encryptedEnvelope().content();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (content.kind() == ChatEventKind.MESSAGE) {
            result.putAll(plainMessageContent(content));
        } else {
            result.put("m.relates_to", relation(
                    content.relation(), content.reactionKey(), providerRelationTargetRef));
        }
        if (content.relation() != null && content.kind() == ChatEventKind.MESSAGE) {
            result.put("m.relates_to", relation(
                    content.relation(), content.reactionKey(), providerRelationTargetRef));
            if ("replace".equals(content.relation().kind())) {
                result.put("m.new_content", plainMessageContent(content));
            }
        }
        result.putAll(content.presentationExtensions());
        return Map.copyOf(result);
    }

    private Map<String, Object> relation(
            ChatRelation relation,
            String reactionKey,
            String providerRelationTargetRef) {
        if (relation == null) {
            return Map.of();
        }
        Map<String, Object> value = new LinkedHashMap<>();
        if (providerRelationTargetRef == null || providerRelationTargetRef.isBlank()) {
            throw new IllegalArgumentException("provider relation target is required");
        }
        if ("reply".equals(relation.kind())) {
            value.put("m.in_reply_to", Map.of("event_id", providerRelationTargetRef));
            return Map.copyOf(value);
        }
        value.put("rel_type", switch (relation.kind()) {
            case "reaction" -> "m.annotation";
            case "replace" -> "m.replace";
            case "thread" -> "m.thread";
            default -> throw new IllegalArgumentException("provider relation kind is unsupported");
        });
        value.put("event_id", providerRelationTargetRef);
        if (reactionKey != null) {
            value.put("key", reactionKey);
        }
        return Map.copyOf(value);
    }

    private Map<String, Object> plainMessageContent(ChatEventContent content) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("msgtype", content.messageType() == null ? "m.text" : content.messageType());
        value.put("body", content.body());
        if (content.format() != null) {
            value.put("format", content.format());
        }
        if (content.formattedBody() != null) {
            value.put("formatted_body", content.formattedBody());
        }
        return Map.copyOf(value);
    }

    private ChatSouthboundProvider.ProviderAck requiredAck(JsonNode response, String field) {
        String providerRef = response.path(field).asText("");
        if (providerRef.isBlank()) {
            throw new SynapseProviderException("chat-provider-ack-invalid", null);
        }
        return new ChatSouthboundProvider.ProviderAck(providerRef, providerRef);
    }

    private JsonNode request(
            String method,
            String path,
            String assertedUser,
            Object body,
            Set<Integer> allowedStatuses) {
        URI uri = requestUri(path, assertedUser);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + asToken)
                .header("Accept", "application/json");
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json(body), StandardCharsets.UTF_8));
        }
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SynapseProviderException("chat-provider-interrupted", clock.instant().plusSeconds(5));
        } catch (IOException exception) {
            throw new SynapseProviderException("chat-provider-unavailable", clock.instant().plusSeconds(5));
        }
        byte[] bytes;
        try (InputStream input = response.body()) {
            bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
        } catch (IOException exception) {
            throw new SynapseProviderException("chat-provider-response-unreadable", clock.instant().plusSeconds(5));
        }
        if (bytes.length > MAX_RESPONSE_BYTES) {
            throw new SynapseProviderException("chat-provider-response-too-large", null);
        }
        if (!allowedStatuses.contains(response.statusCode())) {
            Instant retryAt = response.statusCode() == 429
                    ? retryAt(response.headers().firstValue("Retry-After").orElse(null))
                    : response.statusCode() >= 500 ? clock.instant().plusSeconds(5) : null;
            String code = response.statusCode() == 429
                    ? "chat-provider-throttled"
                    : response.statusCode() >= 500
                            ? "chat-provider-unavailable"
                            : "chat-provider-operation-rejected";
            throw new SynapseProviderException(code, retryAt, matrixErrcode(bytes));
        }
        if (bytes.length == 0) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(bytes);
        } catch (JacksonException exception) {
            throw new SynapseProviderException("chat-provider-response-invalid", null);
        }
    }

    private String matrixErrcode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            JsonNode value = objectMapper.readTree(bytes);
            String errcode = value.path("errcode").asText(null);
            return errcode != null && errcode.matches("M_[A-Z0-9_]{1,80}") ? errcode : null;
        } catch (JacksonException exception) {
            return null;
        }
    }

    private URI requestUri(String rawPath, String assertedUser) {
        String separator = rawPath.contains("?") ? "&" : "?";
        String query = assertedUser == null
                ? ""
                : separator + "user_id=" + URLEncoder.encode(assertedUser, StandardCharsets.UTF_8);
        return baseUri.resolve(rawPath.replaceFirst("^/", "") + query);
    }

    private String path(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private String localpart(String userId) {
        if (userId == null || !userId.startsWith("@") || !userId.endsWith(":" + serverName)) {
            throw new IllegalArgumentException("provider user reference is invalid");
        }
        return userId.substring(1, userId.length() - serverName.length() - 1);
    }

    private String aliasLocalpart(String alias) {
        if (alias == null || !alias.startsWith("#") || !alias.endsWith(":" + serverName)) {
            throw new IllegalArgumentException("provider alias intent is invalid");
        }
        return alias.substring(1, alias.length() - serverName.length() - 1);
    }

    private Instant retryAt(String value) {
        if (value == null || value.isBlank()) {
            return clock.instant().plusSeconds(60);
        }
        try {
            return clock.instant().plusSeconds(Math.max(1, Math.min(Long.parseLong(value.trim()), 3600)));
        } catch (NumberFormatException ignored) {
            try {
                return ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            } catch (DateTimeParseException invalidDate) {
                return clock.instant().plusSeconds(60);
            }
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("provider request could not be serialized", exception);
        }
    }

    record ProviderRoomEvidence(
            boolean authorizedMembershipExact,
            boolean outsiderAbsent,
            boolean outsiderReadDenied,
            boolean encryptionStateVerified,
            boolean encryptedEventRefsExact,
            boolean ciphertextHashesExact,
            int encryptedEventCount,
            int plaintextEventCount) {
    }
}

package com.massimotter.weave.backend.chat.provider.synapse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.chat.e2e.ChatE2eCallbackReplayTap;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
@RequestMapping(MatrixApplicationServiceController.BASE_PATH)
@ConditionalOnProperty(name = "weave.chat.provider", havingValue = ChatRuntimeProperties.MATRIX_SYNAPSE_PROVIDER)
public final class MatrixApplicationServiceController {

    static final String BASE_PATH = "/api/internal/chat/matrix/appservice";

    private final CanonicalChatStore store;
    private final MatrixSynapseChatSouthboundAdapter provider;
    private final ChatRuntimeProperties properties;
    private final ObjectMapper objectMapper;
    private final ChatE2eCallbackReplayTap callbackReplayTap;

    @Autowired
    public MatrixApplicationServiceController(
            CanonicalChatStore store,
            MatrixSynapseChatSouthboundAdapter provider,
            ChatRuntimeProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<ChatE2eCallbackReplayTap> callbackReplayTapProvider) {
        this(store, provider, properties, objectMapper, callbackReplayTapProvider.getIfAvailable());
    }

    MatrixApplicationServiceController(
            CanonicalChatStore store,
            MatrixSynapseChatSouthboundAdapter provider,
            ChatRuntimeProperties properties,
            ObjectMapper objectMapper) {
        this(store, provider, properties, objectMapper, (ChatE2eCallbackReplayTap) null);
    }

    MatrixApplicationServiceController(
            CanonicalChatStore store,
            MatrixSynapseChatSouthboundAdapter provider,
            ChatRuntimeProperties properties,
            ObjectMapper objectMapper,
            ChatE2eCallbackReplayTap callbackReplayTap) {
        this.store = store;
        this.provider = provider;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.callbackReplayTap = callbackReplayTap;
    }

    @PutMapping({"/transactions/{transactionId}", "/_matrix/app/v1/transactions/{transactionId}"})
    public ResponseEntity<Map<String, Object>> transaction(
            @PathVariable String transactionId,
            HttpServletRequest request) {
        try {
            return processTransaction(required(transactionId, 255), boundedBody(request));
        } catch (IOException exception) {
            return matrixError(HttpStatus.PAYLOAD_TOO_LARGE, "M_TOO_LARGE", "Application Service transaction is too large.");
        }
    }

    /**
     * Re-enters the same callback processor for an isolated E2E proof. The raw
     * provider payload remains in process and is never returned northbound.
     */
    public ResponseEntity<Map<String, Object>> replayCapturedForIsolatedProof(
            ChatE2eCallbackReplayTap.CapturedCallback captured) {
        if (captured == null) {
            return matrixError(HttpStatus.SERVICE_UNAVAILABLE, "M_UNAVAILABLE", "Application Service replay is unavailable.");
        }
        return processTransaction(captured.transactionId(), captured.payload());
    }

    private ResponseEntity<Map<String, Object>> processTransaction(String transactionId, byte[] payload) {
        try {
            String safeTransactionId = required(transactionId, 255);
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject() || !root.path("events").isArray()) {
                return matrixError(HttpStatus.BAD_REQUEST, "M_BAD_JSON", "Application Service transaction is invalid.");
            }
            JsonNode events = root.path("events");
            if (events.size() > properties.matrix().callbackMaxEvents()) {
                return matrixError(HttpStatus.PAYLOAD_TOO_LARGE, "M_TOO_LARGE", "Application Service transaction is too large.");
            }
            CanonicalChatStore.CallbackStart start = store.beginCallback(
                    provider.providerKey(), safeTransactionId, semanticPayloadDigest(root), events.size());
            if (start == CanonicalChatStore.CallbackStart.SEMANTIC_MISMATCH) {
                return matrixError(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "M_UNAVAILABLE",
                        "Application Service transaction semantics are inconsistent.");
            }
            if (start == CanonicalChatStore.CallbackStart.DUPLICATE) {
                return ResponseEntity.ok(Map.of());
            }
            int duplicates = 0;
            int index = 0;
            boolean successfulEncryptedEventProcessed = false;
            for (JsonNode eventNode : events) {
                try {
                    CanonicalChatStore.ProviderCallbackEvent callback = callbackEvent(
                            safeTransactionId, eventNode);
                    CanonicalChatStore.CallbackEventResult result = store.recordCallbackEvent(
                            provider.providerKey(), callback);
                    if (result.state().contains("duplicate")) {
                        duplicates++;
                    }
                    if ("m.room.encrypted".equals(callback.eventType())
                            && (result.state().equals("accepted")
                            || result.state().equals("deduplicated")
                            || result.state().equals("acknowledged-echo"))) {
                        successfulEncryptedEventProcessed = true;
                    }
                } catch (IllegalArgumentException exception) {
                    store.recordMalformedCallbackEvent(
                            provider.providerKey(),
                            safeTransactionId,
                            sha256((safeTransactionId + "\u0000" + index + "\u0000" + eventNode)
                                    .getBytes(StandardCharsets.UTF_8)),
                            "callback-event-malformed");
                }
                index++;
            }
            store.completeCallback(provider.providerKey(), safeTransactionId, duplicates);
            if (successfulEncryptedEventProcessed && callbackReplayTap != null) {
                callbackReplayTap.captureFirst(safeTransactionId, payload);
            }
            return ResponseEntity.ok(Map.of());
        } catch (IllegalArgumentException | IOException exception) {
            return matrixError(HttpStatus.BAD_REQUEST, "M_BAD_JSON", "Application Service transaction is invalid.");
        } catch (RuntimeException exception) {
            return matrixError(HttpStatus.SERVICE_UNAVAILABLE, "M_UNAVAILABLE", "Application Service processing is unavailable.");
        }
    }

    @GetMapping({"/users/{userId:.+}", "/_matrix/app/v1/users/{userId:.+}"})
    public ResponseEntity<Map<String, Object>> userExists(@PathVariable String userId) {
        boolean exists = store.mappingByProviderRef(provider.providerKey(), "actor", required(userId, 768))
                .filter(mapping -> "acknowledged".equals(mapping.state()))
                .isPresent();
        return exists ? ResponseEntity.ok(Map.of()) : ResponseEntity.notFound().build();
    }

    @GetMapping({"/rooms/{roomAlias:.+}", "/_matrix/app/v1/rooms/{roomAlias:.+}"})
    public ResponseEntity<Map<String, Object>> roomExists(@PathVariable String roomAlias) {
        boolean exists = store.mappingByIntent(provider.providerKey(), "conversation", required(roomAlias, 768))
                .filter(mapping -> "acknowledged".equals(mapping.state()) || "degraded".equals(mapping.state()))
                .filter(mapping -> mapping.providerRef() != null && !mapping.providerRef().isBlank())
                .isPresent();
        return exists ? ResponseEntity.ok(Map.of()) : ResponseEntity.notFound().build();
    }

    private CanonicalChatStore.ProviderCallbackEvent callbackEvent(String transactionId, JsonNode value) {
        if (value == null || !value.isObject() || !value.path("content").isObject()) {
            throw new IllegalArgumentException("callback event is invalid");
        }
        Map<String, Object> content = new java.util.LinkedHashMap<>(
                objectMapper.convertValue(value.path("content"), new TypeReference<>() { }));
        String providerTransaction = value.path("unsigned").path("transaction_id").asText(null);
        String topLevelRedacts = optional(value.path("redacts").asText(null), 768);
        Object contentRedactsValue = content.remove("redacts");
        String contentRedacts = contentRedactsValue == null
                ? null
                : optional(contentRedactsValue instanceof String text ? text : "", 768);
        if (contentRedactsValue != null && contentRedacts == null) {
            throw new IllegalArgumentException("private callback redaction target is invalid");
        }
        if (topLevelRedacts != null && contentRedacts != null
                && !topLevelRedacts.equals(contentRedacts)) {
            throw new IllegalArgumentException("private callback redaction targets conflict");
        }
        return new CanonicalChatStore.ProviderCallbackEvent(
                transactionId,
                optional(providerTransaction, 255),
                required(value.path("event_id").asText(null), 768),
                required(value.path("room_id").asText(null), 768),
                required(value.path("sender").asText(null), 768),
                required(value.path("type").asText(null), 255),
                optionalStateKey(value),
                topLevelRedacts == null ? contentRedacts : topLevelRedacts,
                content,
                Long.toString(value.path("origin_server_ts").asLong(0)),
                value.path("unsigned").path("redacted_because").isObject());
    }

    private byte[] boundedBody(HttpServletRequest request) throws IOException {
        int maximum = properties.matrix().callbackMaxBytes();
        if (request.getContentLengthLong() > maximum) {
            throw new IOException("body too large");
        }
        try (InputStream input = request.getInputStream()) {
            byte[] bytes = input.readNBytes(maximum + 1);
            if (bytes.length > maximum) {
                throw new IOException("body too large");
            }
            return bytes;
        }
    }

    private String required(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException("private callback value is invalid");
        }
        return value.trim();
    }

    private String optional(String value, int maximum) {
        return value == null || value.isBlank() ? null : required(value, maximum);
    }

    private String optionalStateKey(JsonNode value) {
        if (!value.has("state_key")) {
            return null;
        }
        JsonNode stateKey = value.get("state_key");
        if (stateKey == null || !stateKey.isTextual() || stateKey.textValue().length() > 768
                || stateKey.textValue().chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("private callback state key is invalid");
        }
        return stateKey.textValue();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * Synapse reconstructs a queued Application Service transaction on every
     * delivery attempt. Presentation fields such as age, unsigned metadata, and
     * serialization order can therefore differ while provider event identity
     * and semantic content remain unchanged. The homeserver transaction is an
     * event set: order and presentation envelopes are excluded, while identity,
     * routing, state, sender, type, redaction target, and content stay binding.
     */
    static String semanticPayloadDigest(JsonNode root) {
        ArrayList<String> semanticEvents = new ArrayList<>();
        for (JsonNode event : root.path("events")) {
            ObjectNode semanticEvent = JsonNodeFactory.instance.objectNode();
            copySemanticField(event, semanticEvent, "event_id");
            copySemanticField(event, semanticEvent, "room_id");
            copySemanticField(event, semanticEvent, "sender");
            copySemanticField(event, semanticEvent, "type");
            copySemanticField(event, semanticEvent, "state_key");
            copySemanticField(event, semanticEvent, "content");
            copySemanticField(event, semanticEvent, "redacts");
            if (event.path("unsigned").path("redacted_because").isObject()) {
                semanticEvent.put("provider_redacted", true);
            }
            StringBuilder canonicalEvent = new StringBuilder();
            appendCanonicalJson(semanticEvent, canonicalEvent);
            semanticEvents.add(canonicalEvent.toString());
        }
        Collections.sort(semanticEvents);
        StringBuilder canonical = new StringBuilder();
        canonical.append('[');
        for (int index = 0; index < semanticEvents.size(); index++) {
            if (index > 0) {
                canonical.append(',');
            }
            canonical.append(semanticEvents.get(index));
        }
        canonical.append(']');
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void copySemanticField(JsonNode source, ObjectNode target, String field) {
        if (source.has(field)) {
            target.set(field, source.get(field));
        }
    }

    private static void appendCanonicalJson(JsonNode value, StringBuilder target) {
        if (value.isObject()) {
            ArrayList<String> fields = new ArrayList<>();
            value.fieldNames().forEachRemaining(fields::add);
            Collections.sort(fields);
            target.append('{');
            for (int index = 0; index < fields.size(); index++) {
                if (index > 0) {
                    target.append(',');
                }
                String field = fields.get(index);
                target.append(TextNode.valueOf(field)).append(':');
                appendCanonicalJson(value.get(field), target);
            }
            target.append('}');
            return;
        }
        if (value.isArray()) {
            target.append('[');
            for (int index = 0; index < value.size(); index++) {
                if (index > 0) {
                    target.append(',');
                }
                appendCanonicalJson(value.get(index), target);
            }
            target.append(']');
            return;
        }
        target.append(value);
    }

    private ResponseEntity<Map<String, Object>> matrixError(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("errcode", code, "error", message));
    }

}

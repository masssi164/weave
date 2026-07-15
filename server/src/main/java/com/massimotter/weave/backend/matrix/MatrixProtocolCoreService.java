package com.massimotter.weave.backend.matrix;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MatrixProtocolCoreService {

    public static final String PROTOCOL_SURFACE = "matrix-client-server-facade";
    public static final String OIDC_GATEKEEPER = "spring-boot-resource-server";
    public static final String RUST_PROTOCOL_CORE = "ruma-serde-serde_json-thiserror-tracing";
    public static final String SERVER_JNI_BOUNDARY = "server-jni-wrapper";
    public static final String FLUTTER_BRIDGE_BOUNDARY = "flutter-rust-bridge";
    public static final String NATIVE_LIBRARY = NativeMatrixCore.LIBRARY_NAME;
    public static final String NATIVE_METHOD = NativeMatrixCore.JNI_METHOD;

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final String serverName;

    public MatrixProtocolCoreService(
            ObjectMapper objectMapper,
            @Value("${weave.matrix.facade.server-name:api.weave.test}") String serverName) {
        this.objectMapper = objectMapper;
        this.serverName = requireText(serverName, "Matrix facade server name");
        NativeMatrixCore.ensureLoaded();
        descriptor();
    }

    public Map<String, Object> versions() {
        return project("versions", Map.of());
    }

    public Map<String, Object> descriptor() {
        return project("descriptor", Map.of());
    }

    public Map<String, Object> whoami(String subject, String deviceId) {
        return project("whoami", Map.of(
                "subject", subject == null ? "" : subject,
                "deviceId", deviceId == null ? "" : deviceId));
    }

    public Map<String, Object> sync(
            String subject,
            String cursor,
            String since,
            List<CanonicalConversation> conversations) {
        return sync(subject, cursor, since, conversations, Map.of());
    }

    public Map<String, Object> sync(
            String subject,
            String cursor,
            String since,
            List<CanonicalConversation> conversations,
            Map<String, Object> accountData) {
        return sync(subject, cursor, since, conversations, accountData, MatrixSyncCrypto.empty());
    }

    public Map<String, Object> sync(
            String subject,
            String cursor,
            String since,
            List<CanonicalConversation> conversations,
            Map<String, Object> accountData,
            MatrixSyncCrypto crypto) {
        return project("sync", new CanonicalProjection(
                subject,
                cursor,
                since,
                conversations,
                accountData,
                crypto.toDeviceEvents(),
                crypto.deviceListsChanged(),
                crypto.deviceListsLeft(),
                crypto.oneTimeKeyCounts(),
                crypto.unusedFallbackKeyTypes()));
    }

    public void validateSyncToken(String since) {
        project("validate-sync-token", new CanonicalProjection("", "", since, List.of(), Map.of()));
    }

    public String decodeSyncCursor(String since) {
        Object cursor = project("decode-sync-token", new CanonicalProjection("", "", since, List.of(), Map.of()))
                .get("cursor");
        return cursor instanceof String value ? value : "";
    }

    public Map<String, Object> joinedRooms(List<CanonicalConversation> conversations) {
        return project("joined-rooms", new CanonicalProjection("", "", null, conversations, Map.of()));
    }

    public Map<String, Object> messages(
            String cursor,
            String from,
            CanonicalConversation conversation) {
        return project("messages", new CanonicalProjection("", cursor, from, List.of(conversation), Map.of()));
    }

    public Map<String, Object> members(CanonicalConversation conversation) {
        return project("members", new CanonicalProjection("", "", null, List.of(conversation), Map.of()));
    }

    public String parseSendBody(String requestJson) {
        Object body = projectRaw("parse-send", requestJson).get("body");
        if (!(body instanceof String value) || value.isBlank()) {
            throw new MatrixProtocolException("M_BAD_JSON", "Matrix message body must not be blank.");
        }
        return value;
    }

    public Map<String, Object> parseObject(String requestJson) {
        Object value = projectRaw("parse-object", requestJson).get("value");
        if (!(value instanceof Map<?, ?> object)) {
            throw new MatrixProtocolException("M_BAD_JSON", "Matrix request body must be a JSON object.");
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        object.forEach((key, nested) -> {
            if (!(key instanceof String text)) {
                throw new MatrixProtocolException("M_BAD_JSON", "Matrix request body keys must be strings.");
            }
            result.put(text, nested);
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    public ParsedEventContent parseEvent(String eventType, String requestJson) {
        try {
            Object content = objectMapper.readValue(requestJson, Object.class);
            Map<String, Object> parsed = project("parse-event", Map.of(
                    "eventType", requireText(eventType, "Matrix event type"),
                    "content", content));
            return objectMapper.convertValue(parsed, ParsedEventContent.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new MatrixProtocolException("M_BAD_JSON", "Matrix event content is invalid.");
        }
    }

    public Map<String, Object> sendResponse(String messageId) {
        return project("send-response", Map.of("messageId", requireText(messageId, "message id")));
    }

    public String decodeRoomId(String matrixRoomId) {
        Object conversationId = project("decode-room", Map.of("roomId", matrixRoomId == null ? "" : matrixRoomId))
                .get("conversationId");
        if (!(conversationId instanceof String value) || value.isBlank()) {
            throw new MatrixProtocolException("M_INVALID_PARAM", "Matrix room identifier is invalid.");
        }
        return value;
    }

    public String decodeEventId(String matrixEventId) {
        Object eventId = project("decode-event", Map.of("eventId", matrixEventId == null ? "" : matrixEventId))
                .get("eventId");
        if (!(eventId instanceof String value) || value.isBlank()) {
            throw new MatrixProtocolException("M_INVALID_PARAM", "Matrix event identifier is invalid.");
        }
        return value;
    }

    public String roomId(String conversationId) {
        Object roomId = project("room-id", Map.of("conversationId", requireText(conversationId, "conversation id")))
                .get("roomId");
        if (!(roomId instanceof String value) || value.isBlank()) {
            throw new MatrixProtocolException("M_WEAVE_MATRIX_CORE_ERROR", "Matrix room identifier could not be projected.");
        }
        return value;
    }

    public String userId(String memberRef) {
        Object userId = project("user-id", Map.of("memberRef", requireText(memberRef, "member reference")))
                .get("userId");
        if (!(userId instanceof String value) || value.isBlank()) {
            throw new MatrixProtocolException("M_WEAVE_MATRIX_CORE_ERROR", "Matrix user identifier could not be projected.");
        }
        return value;
    }

    public Map<String, Object> error(String errcode, String message) {
        try {
            String input = objectMapper.writeValueAsString(Map.of(
                    "errcode", requireText(errcode, "Matrix error code"),
                    "error", requireText(message, "Matrix error message")));
            return readOutput(NativeMatrixCore.projectJson("error", input, serverName), false);
        } catch (JsonProcessingException exception) {
            throw new MatrixProtocolException("M_WEAVE_MATRIX_CORE_ERROR", "Matrix error input could not be serialized.");
        }
    }

    public String serverName() {
        return serverName;
    }

    private Map<String, Object> project(String operation, Object input) {
        try {
            return projectRaw(operation, objectMapper.writeValueAsString(input));
        } catch (JsonProcessingException exception) {
            throw new MatrixProtocolException("M_WEAVE_MATRIX_CORE_ERROR", "Canonical Chat input could not be serialized.");
        }
    }

    private Map<String, Object> projectRaw(String operation, String inputJson) {
        String output = NativeMatrixCore.projectJson(operation, inputJson, serverName);
        return readOutput(output, true);
    }

    private Map<String, Object> readOutput(String output, boolean rejectMatrixError) {
        try {
            Map<String, Object> response = objectMapper.readValue(output, JSON_OBJECT);
            if (rejectMatrixError && response.get("errcode") instanceof String errcode) {
                String message = response.get("error") instanceof String error
                        ? error
                        : "The Matrix protocol core rejected the request.";
                throw new MatrixProtocolException(errcode, message);
            }
            return response;
        } catch (JsonProcessingException exception) {
            throw new MatrixProtocolException(
                    "M_WEAVE_MATRIX_CORE_ERROR",
                    "The Rust/Ruma Matrix protocol core returned an invalid payload.");
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public record CanonicalProjection(
            String subject,
            String cursor,
            String since,
            List<CanonicalConversation> conversations,
            Map<String, Object> accountData,
            List<Map<String, Object>> toDeviceEvents,
            List<String> deviceListsChanged,
            List<String> deviceListsLeft,
            Map<String, Long> deviceOneTimeKeysCount,
            List<String> deviceUnusedFallbackKeyTypes) {

        public CanonicalProjection(
                String subject,
                String cursor,
                String since,
                List<CanonicalConversation> conversations,
                Map<String, Object> accountData) {
            this(subject, cursor, since, conversations, accountData, List.of(), List.of(), List.of(), Map.of(), List.of());
        }

        public CanonicalProjection {
            subject = subject == null ? "" : subject;
            cursor = cursor == null ? "" : cursor;
            conversations = conversations == null ? List.of() : List.copyOf(conversations);
            accountData = accountData == null ? Map.of() : Map.copyOf(accountData);
            toDeviceEvents = toDeviceEvents == null ? List.of() : List.copyOf(toDeviceEvents);
            deviceListsChanged = deviceListsChanged == null ? List.of() : List.copyOf(deviceListsChanged);
            deviceListsLeft = deviceListsLeft == null ? List.of() : List.copyOf(deviceListsLeft);
            deviceOneTimeKeysCount = deviceOneTimeKeysCount == null ? Map.of() : Map.copyOf(deviceOneTimeKeysCount);
            deviceUnusedFallbackKeyTypes = deviceUnusedFallbackKeyTypes == null
                    ? List.of()
                    : List.copyOf(deviceUnusedFallbackKeyTypes);
        }
    }

    public record MatrixSyncCrypto(
            List<Map<String, Object>> toDeviceEvents,
            List<String> deviceListsChanged,
            List<String> deviceListsLeft,
            Map<String, Long> oneTimeKeyCounts,
            List<String> unusedFallbackKeyTypes,
            long nextSequence) {

        public MatrixSyncCrypto {
            toDeviceEvents = toDeviceEvents == null ? List.of() : List.copyOf(toDeviceEvents);
            deviceListsChanged = deviceListsChanged == null ? List.of() : List.copyOf(deviceListsChanged);
            deviceListsLeft = deviceListsLeft == null ? List.of() : List.copyOf(deviceListsLeft);
            oneTimeKeyCounts = oneTimeKeyCounts == null ? Map.of() : Map.copyOf(oneTimeKeyCounts);
            unusedFallbackKeyTypes = unusedFallbackKeyTypes == null ? List.of() : List.copyOf(unusedFallbackKeyTypes);
            if (nextSequence < 0) {
                throw new IllegalArgumentException("Matrix E2EE sequence must not be negative.");
            }
        }

        public static MatrixSyncCrypto empty() {
            return new MatrixSyncCrypto(List.of(), List.of(), List.of(), Map.of(), List.of(), 0);
        }
    }

    public record CanonicalConversation(
            String conversationId,
            String title,
            long updatedAtEpochMillis,
            long unreadCount,
            String encryptionAlgorithm,
            List<CanonicalMembership> memberships,
            List<CanonicalMessage> messages) {

        public CanonicalConversation {
            memberships = memberships == null ? List.of() : List.copyOf(memberships);
            messages = messages == null ? List.of() : List.copyOf(messages);
            encryptionAlgorithm = encryptionAlgorithm == null || encryptionAlgorithm.isBlank()
                    ? null
                    : encryptionAlgorithm.trim();
        }

        public CanonicalConversation(
                String conversationId,
                String title,
                long updatedAtEpochMillis,
                long unreadCount,
                List<CanonicalMembership> memberships,
                List<CanonicalMessage> messages) {
            this(
                    conversationId,
                    title,
                    updatedAtEpochMillis,
                    unreadCount,
                    null,
                    memberships,
                    messages);
        }

        public CanonicalConversation(
                String conversationId,
                String title,
                long updatedAtEpochMillis,
                long unreadCount,
                List<CanonicalMessage> messages) {
            this(conversationId, title, updatedAtEpochMillis, unreadCount, null, List.of(), messages);
        }
    }

    public record CanonicalMembership(String memberRef, String state) {
    }

    public record CanonicalMessage(
            String messageId,
            String senderRef,
            long sentAtEpochMillis,
            String kind,
            String messageType,
            String body,
            String format,
            String formattedBody,
            String relationKind,
            String relationTargetEventId,
            String replyToEventId,
            String reactionKey,
            Map<String, Object> presentationExtensions,
            String deliveryState,
            Map<String, Object> encryptedContent,
            boolean redacted) {

        public CanonicalMessage {
            presentationExtensions = presentationExtensions == null
                    ? Map.of()
                    : Map.copyOf(presentationExtensions);
            encryptedContent = encryptedContent == null ? null : Map.copyOf(encryptedContent);
        }
    }

    public record ParsedEventContent(
            String kind,
            String messageType,
            String body,
            String format,
            String formattedBody,
            String relationKind,
            String relationTargetEventId,
            String replyToEventId,
            String reactionKey,
            Map<String, Object> presentationExtensions,
            Map<String, Object> encryptedContent) {

        public ParsedEventContent {
            presentationExtensions = presentationExtensions == null
                    ? Map.of()
                    : Map.copyOf(presentationExtensions);
            encryptedContent = encryptedContent == null ? null : Map.copyOf(encryptedContent);
        }
    }
}

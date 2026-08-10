package com.massimotter.weave.backend.matrix;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class MatrixProtocolCoreService implements MatrixProtocolCodec {

    public static final String PROTOCOL_SURFACE = "matrix-client-server-facade";
    public static final String OIDC_GATEKEEPER = "spring-boot-resource-server";
    public static final String RUST_PROTOCOL_CORE = "ruma-serde-serde_json-thiserror-tracing";
    public static final String SERVER_JNI_BOUNDARY = "server-jni-wrapper";
    public static final String FLUTTER_BRIDGE_TARGET = "flutter-rust-bridge";
    public static final String NATIVE_LIBRARY = NativeMatrixCore.LIBRARY_NAME;
    public static final String NATIVE_METHOD = NativeMatrixCore.JNI_METHOD;

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final String serverName;

    @Autowired
    public MatrixProtocolCoreService(
            ObjectMapper objectMapper,
            @Value("${weave.matrix.facade.server-name:api.weave.test}") String serverName) {
        this.objectMapper = objectMapper;
        this.serverName = requireText(serverName, "Matrix facade server name");
    }

    @PostConstruct
    void verifyRequiredProtocolRuntime() { NativeMatrixCore.ensureLoaded(); }

    public Map<String, Object> versions() { return project(MatrixProtocolOperation.VERSIONS, Map.of()); }
    public Map<String, Object> descriptor() { return project(MatrixProtocolOperation.DESCRIPTOR, Map.of()); }

    public Map<String, Object> whoami(String subject, String deviceId) {
        return project(MatrixProtocolOperation.WHOAMI, Map.of(
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
            Map<String, Map<String, Object>> accountData) {
        return sync(subject, cursor, since, conversations, accountData, MatrixSyncCrypto.empty());
    }

    public Map<String, Object> sync(
            String subject,
            String cursor,
            String since,
            List<CanonicalConversation> conversations,
            Map<String, Map<String, Object>> accountData,
            MatrixSyncCrypto crypto) {
        return project(MatrixProtocolOperation.SYNC, new CanonicalProjection(
                subject, cursor, since, conversations, accountData,
                crypto.toDeviceEvents(), crypto.deviceListsChanged(), crypto.deviceListsLeft(),
                crypto.oneTimeKeyCounts(), crypto.unusedFallbackKeyTypes()));
    }

    public void validateSyncToken(String since) {
        project(MatrixProtocolOperation.VALIDATE_SYNC_TOKEN,
                new CanonicalProjection("", "", since, List.of(), Map.of()));
    }

    public String decodeSyncCursor(String since) {
        Object cursor = project(MatrixProtocolOperation.DECODE_SYNC_TOKEN,
                new CanonicalProjection("", "", since, List.of(), Map.of())).get("cursor");
        return cursor instanceof String value ? value : "";
    }

    public Map<String, Object> joinedRooms(List<CanonicalConversation> conversations) {
        return project(MatrixProtocolOperation.JOINED_ROOMS,
                new CanonicalProjection("", "", null, conversations, Map.of()));
    }

    public Map<String, Object> messages(String cursor, String from, CanonicalConversation conversation) {
        return project(MatrixProtocolOperation.MESSAGES,
                new CanonicalProjection("", cursor, from, List.of(conversation), Map.of()));
    }

    public Map<String, Object> members(CanonicalConversation conversation) {
        return project(MatrixProtocolOperation.MEMBERS,
                new CanonicalProjection("", "", null, List.of(conversation), Map.of()));
    }

    public String parseSendBody(String requestJson) {
        Object body = project(MatrixProtocolOperation.PARSE_SEND, requestJson).get("body");
        if (!(body instanceof String value) || value.isBlank()) {
            throw new MatrixProtocolException("M_BAD_JSON", "Matrix message body must not be blank.");
        }
        return value;
    }

    public Map<String, Object> parseObject(String requestJson) {
        Object value = project(MatrixProtocolOperation.PARSE_OBJECT, requestJson).get("value");
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
            Map<String, Object> parsed = project(MatrixProtocolOperation.PARSE_EVENT, Map.of(
                    "eventType", requireText(eventType, "Matrix event type"),
                    "content", content));
            return objectMapper.convertValue(parsed, ParsedEventContent.class);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new MatrixProtocolException("M_BAD_JSON", "Matrix event content is invalid.");
        }
    }

    public Map<String, Object> sendResponse(String messageId) {
        return project(MatrixProtocolOperation.SEND_RESPONSE,
                Map.of("messageId", requireText(messageId, "message id")));
    }

    public String decodeRoomId(String matrixRoomId) {
        Object conversationId = project(MatrixProtocolOperation.DECODE_ROOM,
                Map.of("roomId", matrixRoomId == null ? "" : matrixRoomId)).get("conversationId");
        if (!(conversationId instanceof String value) || value.isBlank()) {
            throw new MatrixProtocolException("M_INVALID_PARAM", "Matrix room identifier is invalid.");
        }
        return value;
    }

    public String decodeEventId(String matrixEventId) {
        Object eventId = project(MatrixProtocolOperation.DECODE_EVENT,
                Map.of("eventId", matrixEventId == null ? "" : matrixEventId)).get("eventId");
        if (!(eventId instanceof String value) || value.isBlank()) {
            throw new MatrixProtocolException("M_INVALID_PARAM", "Matrix event identifier is invalid.");
        }
        return value;
    }

    public String roomId(String conversationId) {
        Object roomId = project(MatrixProtocolOperation.ROOM_ID,
                Map.of("conversationId", requireText(conversationId, "conversation id"))).get("roomId");
        if (!(roomId instanceof String value) || value.isBlank()) {
            throw new MatrixProtocolException("M_WEAVE_MATRIX_CORE_ERROR", "Matrix room identifier could not be projected.");
        }
        return value;
    }

    public String userId(String memberRef) {
        Object userId = project(MatrixProtocolOperation.USER_ID,
                Map.of("memberRef", requireText(memberRef, "member reference"))).get("userId");
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
            return project(MatrixProtocolOperation.ERROR, input, false);
        } catch (JacksonException exception) {
            throw new MatrixProtocolException("M_WEAVE_MATRIX_CORE_ERROR", "Matrix error input could not be serialized.");
        }
    }

    public String serverName() { return serverName; }

    @Override
    public Map<String, Object> project(MatrixProtocolOperation operation, String inputJson) {
        return project(operation, inputJson, true);
    }

    private Map<String, Object> project(MatrixProtocolOperation operation, Object input) {
        try {
            return project(operation, objectMapper.writeValueAsString(input));
        } catch (JacksonException exception) {
            throw new MatrixProtocolException("M_WEAVE_MATRIX_CORE_ERROR", "Canonical Chat input could not be serialized.");
        }
    }

    private Map<String, Object> project(MatrixProtocolOperation operation, String inputJson, boolean rejectMatrixError) {
        if (operation == null) throw new IllegalArgumentException("Matrix protocol operation is required.");
        NativeMatrixCore.ensureLoaded();
        return readOutput(NativeMatrixCore.projectJson(operation.wireName(), inputJson, serverName), rejectMatrixError);
    }

    private Map<String, Object> readOutput(String output, boolean rejectMatrixError) {
        try {
            Map<String, Object> response = objectMapper.readValue(output, JSON_OBJECT);
            if (rejectMatrixError && response.get("errcode") instanceof String errcode) {
                String message = response.get("error") instanceof String error ? error : "Matrix protocol operation failed.";
                throw new MatrixProtocolException(errcode, message);
            }
            return response;
        } catch (MatrixProtocolException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new MatrixProtocolException("M_WEAVE_MATRIX_CORE_ERROR", "Matrix protocol output could not be decoded.");
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    public record CanonicalProjection(
            String subject,
            String cursor,
            String since,
            List<CanonicalConversation> conversations,
            Map<String, Map<String, Object>> accountData,
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
                Map<String, Map<String, Object>> accountData) {
            this(subject, cursor, since, conversations, accountData, List.of(), List.of(), List.of(), Map.of(), List.of());
        }
    }

    public record CanonicalConversation(
            String conversationId,
            String title,
            long updatedAtEpochMillis,
            int unreadCount,
            String encryptionAlgorithm,
            List<CanonicalMembership> memberships,
            List<CanonicalMessage> messages) {}

    public record CanonicalMembership(String memberRef, String state) {}

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
            String body,
            String messageType,
            String format,
            String formattedBody,
            String relationKind,
            String relationTargetEventId,
            String replyToEventId,
            String reactionKey,
            Map<String, Object> encryptedContent,
            Map<String, Object> presentationExtensions) {}

    public record MatrixSyncCrypto(
            List<Map<String, Object>> toDeviceEvents,
            List<String> deviceListsChanged,
            List<String> deviceListsLeft,
            Map<String, Long> oneTimeKeyCounts,
            List<String> unusedFallbackKeyTypes,
            long nextSequence,
            long deviceListSequence) {
        public MatrixSyncCrypto(
                List<Map<String, Object>> toDeviceEvents,
                List<String> deviceListsChanged,
                List<String> deviceListsLeft,
                Map<String, Long> oneTimeKeyCounts,
                List<String> unusedFallbackKeyTypes,
                long nextSequence) {
            this(toDeviceEvents, deviceListsChanged, deviceListsLeft, oneTimeKeyCounts, unusedFallbackKeyTypes, nextSequence, nextSequence);
        }

        public static MatrixSyncCrypto empty() {
            return new MatrixSyncCrypto(List.of(), List.of(), List.of(), Map.of(), List.of(), 0, 0);
        }
    }
}

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

    public Map<String, Object> whoami(String subject) {
        return project("whoami", Map.of("subject", subject == null ? "" : subject));
    }

    public Map<String, Object> sync(
            String subject,
            String cursor,
            String since,
            List<CanonicalConversation> conversations) {
        return project("sync", new CanonicalProjection(
                subject,
                cursor,
                since,
                conversations));
    }

    public void validateSyncToken(String since) {
        project("validate-sync-token", new CanonicalProjection("", "", since, List.of()));
    }

    public Map<String, Object> joinedRooms(List<CanonicalConversation> conversations) {
        return project("joined-rooms", new CanonicalProjection("", "", null, conversations));
    }

    public Map<String, Object> messages(
            String cursor,
            String from,
            CanonicalConversation conversation) {
        return project("messages", new CanonicalProjection("", cursor, from, List.of(conversation)));
    }

    public String parseSendBody(String requestJson) {
        Object body = projectRaw("parse-send", requestJson).get("body");
        if (!(body instanceof String value) || value.isBlank()) {
            throw new MatrixProtocolException("M_BAD_JSON", "Matrix message body must not be blank.");
        }
        return value;
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
            List<CanonicalConversation> conversations) {

        public CanonicalProjection {
            subject = subject == null ? "" : subject;
            cursor = cursor == null ? "" : cursor;
            conversations = conversations == null ? List.of() : List.copyOf(conversations);
        }
    }

    public record CanonicalConversation(
            String conversationId,
            String title,
            long updatedAtEpochMillis,
            long unreadCount,
            List<CanonicalMessage> messages) {

        public CanonicalConversation {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }
    }

    public record CanonicalMessage(
            String messageId,
            String senderRef,
            long sentAtEpochMillis,
            String body,
            String deliveryState,
            boolean encrypted) {
    }
}

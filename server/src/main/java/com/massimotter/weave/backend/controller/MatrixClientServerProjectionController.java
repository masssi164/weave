package com.massimotter.weave.backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.chat.ChatConversationResponse;
import com.massimotter.weave.backend.model.chat.ChatMessageResponse;
import com.massimotter.weave.backend.model.chat.ChatSendMessageRequest;
import com.massimotter.weave.backend.service.ChatFacadeService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

@RestController
@Hidden
public class MatrixClientServerProjectionController {

    private static final String MATRIX_ALLOW = "OPTIONS, GET, POST, PUT";
    private static final Pattern SEND_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/send/m\\.room\\.message/([^/]+)$");
    private static final Pattern ROOM_MESSAGES_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/messages$");

    private final ChatFacadeService chatFacadeService;
    private final ObjectMapper objectMapper;

    public MatrixClientServerProjectionController(ChatFacadeService chatFacadeService, ObjectMapper objectMapper) {
        this.chatFacadeService = chatFacadeService;
        this.objectMapper = objectMapper;
    }

    @RequestMapping(value = "/_matrix/client/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.ALLOW, MATRIX_ALLOW)
                .header("X-Weave-Projection", "matrix-client-server")
                .build();
    }

    @RequestMapping("/_matrix/client/**")
    public ResponseEntity<Map<String, Object>> handle(
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        if (!List.of("GET", "POST", "PUT").contains(method)) {
            return matrixError(
                    HttpStatus.NOT_IMPLEMENTED,
                    "M_WEAVE_MATRIX_METHOD_NOT_IMPLEMENTED",
                    "This Weave Matrix Client-Server projection supports OPTIONS, GET, POST, and PUT.");
        }
        try {
            String path = requestPath(request);
            if ("GET".equals(method) && isSync(path)) {
                return matrixOk(sync(jwt));
            }
            if ("GET".equals(method) && isJoinedRooms(path)) {
                return matrixOk(joinedRooms(jwt));
            }
            Matcher roomMessages = ROOM_MESSAGES_PATH.matcher(path);
            if ("GET".equals(method) && roomMessages.matches()) {
                return matrixOk(roomMessages(jwt, decodeRoomId(roomMessages.group(1))));
            }
            Matcher send = SEND_PATH.matcher(path);
            if (List.of("POST", "PUT").contains(method) && send.matches()) {
                return matrixOk(sendMessage(jwt, decodeRoomId(send.group(1)), requestBody(request)));
            }
            return matrixError(
                    HttpStatus.NOT_FOUND,
                    "M_NOT_FOUND",
                    "This Matrix Client-Server projection route is not implemented by the current Weave Chat facade slice.");
        } catch (ApiErrorException exception) {
            return matrixError(
                    exception.status(),
                    matrixErrcode(exception),
                    exception.getMessage());
        }
    }

    private Map<String, Object> sync(Jwt jwt) {
        Map<String, Object> joined = new LinkedHashMap<>();
        for (ChatConversationResponse conversation : chatFacadeService.conversations(jwt).conversations()) {
            var messages = chatFacadeService.messages(jwt, conversation.id()).messages();
            joined.put(matrixRoomId(conversation.id()), Map.of(
                    "summary", Map.of(
                            "m.joined_member_count", 1,
                            "m.invited_member_count", 0),
                    "state", Map.of("events", List.of(roomNameEvent(conversation))),
                    "timeline", Map.of(
                            "limited", false,
                            "prev_batch", "weave-start",
                            "events", messages.stream().map(this::matrixMessageEvent).toList()),
                    "unread_notifications", Map.of("notification_count", 0, "highlight_count", 0)));
        }
        return Map.of(
                "next_batch", "weave-" + Instant.now().toEpochMilli(),
                "rooms", Map.of("join", joined),
                "weaveBoundary", "northbound-matrix-client-server",
                "canonicalDomain", "chat",
                "providerDataPlaneExposed", false);
    }

    private Map<String, Object> joinedRooms(Jwt jwt) {
        return Map.of("joined_rooms", chatFacadeService.conversations(jwt).conversations().stream()
                .map(conversation -> matrixRoomId(conversation.id()))
                .toList());
    }

    private Map<String, Object> roomMessages(Jwt jwt, String conversationId) {
        return Map.of(
                "start", "weave-start",
                "end", "weave-end",
                "chunk", chatFacadeService.messages(jwt, conversationId).messages().stream()
                        .map(this::matrixMessageEvent)
                        .toList());
    }

    private Map<String, Object> sendMessage(Jwt jwt, String conversationId, String body) {
        Map<String, Object> payload = parseJson(body);
        Object content = payload.get("body");
        String text = content == null ? "" : content.toString();
        ChatMessageResponse message = chatFacadeService.sendMessage(
                jwt,
                conversationId,
                new ChatSendMessageRequest(text, List.of()));
        return Map.of("event_id", matrixEventId(message.id()));
    }

    private Map<String, Object> roomNameEvent(ChatConversationResponse conversation) {
        return Map.of(
                "type", "m.room.name",
                "state_key", "",
                "sender", "@weave:weave.local",
                "event_id", matrixEventId("state-" + conversation.id()),
                "origin_server_ts", conversation.lastMessageAt() == null
                        ? Instant.EPOCH.toEpochMilli()
                        : conversation.lastMessageAt().toEpochMilli(),
                "content", Map.of("name", conversation.title()));
    }

    private Map<String, Object> matrixMessageEvent(ChatMessageResponse message) {
        return Map.of(
                "type", "m.room.message",
                "sender", matrixSender(message.senderRef()),
                "event_id", matrixEventId(message.id()),
                "origin_server_ts", message.sentAt().toEpochMilli(),
                "content", Map.of(
                        "msgtype", "m.text",
                        "body", message.text() == null ? "" : message.text(),
                        "weaveMessageId", message.id(),
                        "weaveCanonicalDomain", "chat",
                        "providerDataPlaneExposed", false));
    }

    private boolean isSync(String path) {
        return path.startsWith("/_matrix/client/v3/sync") || path.startsWith("/_matrix/client/r0/sync");
    }

    private boolean isJoinedRooms(String path) {
        return path.equals("/_matrix/client/v3/joined_rooms") || path.equals("/_matrix/client/r0/joined_rooms");
    }

    private String matrixRoomId(String conversationId) {
        return "!" + conversationId.replaceAll("[^A-Za-z0-9._=-]", "_") + ":weave.local";
    }

    private String decodeRoomId(String matrixRoomId) {
        String decoded = UriUtils.decode(matrixRoomId, StandardCharsets.UTF_8);
        if (decoded != null && decoded.startsWith("!") && decoded.contains(":")) {
            return decoded.substring(1, decoded.indexOf(':'));
        }
        return decoded == null ? matrixRoomId : decoded;
    }

    private String matrixSender(String senderRef) {
        String safe = senderRef == null || senderRef.isBlank()
                ? "unknown"
                : senderRef.replaceFirst("^[^:]+:", "").replaceAll("[^A-Za-z0-9._=-]", "_");
        return "@" + safe + ":weave.local";
    }

    private String matrixEventId(String id) {
        return "$" + (id == null ? "weave-event" : id.replaceAll("[^A-Za-z0-9._=-]", "_")) + ":weave.local";
    }

    private String requestPath(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        return requestPath;
    }

    private String requestBody(HttpServletRequest request) {
        try {
            return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "matrix-request-unreadable",
                    "Matrix request body could not be read by the backend.",
                    Map.of("module", "chat", "operation", "matrix-client-server"));
        }
    }

    private Map<String, Object> parseJson(String body) {
        try {
            return objectMapper.readValue(body, new TypeReference<>() {});
        } catch (IOException exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "matrix-request-json-invalid",
                    "Matrix request body must be JSON.",
                    Map.of("module", "chat", "operation", "matrix-client-server"));
        }
    }

    private ResponseEntity<Map<String, Object>> matrixOk(Map<String, Object> body) {
        return ResponseEntity.ok()
                .header("X-Weave-Projection", "matrix-client-server")
                .body(body);
    }

    private String matrixErrcode(ApiErrorException exception) {
        return switch (exception.status()) {
            case UNAUTHORIZED -> "M_MISSING_TOKEN";
            case FORBIDDEN -> "M_FORBIDDEN";
            case NOT_FOUND -> "M_NOT_FOUND";
            case BAD_REQUEST -> "M_BAD_JSON";
            default -> "M_WEAVE_CHAT_FACADE_ERROR";
        };
    }

    private ResponseEntity<Map<String, Object>> matrixError(HttpStatus status, String errcode, String error) {
        return ResponseEntity.status(status)
                .header("X-Weave-Projection", "matrix-client-server")
                .body(Map.of(
                        "errcode", errcode,
                        "error", error,
                        "weaveBoundary", "northbound-matrix-client-server",
                        "canonicalDomain", "chat",
                        "supportSafe", true,
                        "providerDataPlaneExposed", false));
    }
}

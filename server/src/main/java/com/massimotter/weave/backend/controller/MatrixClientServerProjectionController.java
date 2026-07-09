package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.matrix.MatrixProtocolCoreService;
import com.massimotter.weave.backend.matrix.MatrixProtocolException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private final ChatDomainFacadeService chatDomainFacadeService;
    private final MatrixProtocolCoreService matrixProtocolCoreService;

    public MatrixClientServerProjectionController(
            ChatDomainFacadeService chatDomainFacadeService,
            MatrixProtocolCoreService matrixProtocolCoreService) {
        this.chatDomainFacadeService = chatDomainFacadeService;
        this.matrixProtocolCoreService = matrixProtocolCoreService;
    }

    @RequestMapping(value = "/_matrix/client/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.ALLOW, MATRIX_ALLOW)
                .header("X-Weave-Projection", "matrix-client-server")
                .header("X-Weave-Matrix-Core", "rust-ruma-jni")
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
            if ("GET".equals(method) && isVersions(path)) {
                return matrixOk(matrixProtocolCoreService.versions());
            }
            if ("GET".equals(method) && isWhoami(path)) {
                return matrixOk(matrixProtocolCoreService.whoami(jwt == null ? null : jwt.getSubject()));
            }
            if ("GET".equals(method) && isSync(path)) {
                return matrixOk(sync(jwt, request.getParameter("since")));
            }
            if ("GET".equals(method) && isJoinedRooms(path)) {
                return matrixOk(joinedRooms(jwt));
            }
            Matcher roomMessages = ROOM_MESSAGES_PATH.matcher(path);
            if ("GET".equals(method) && roomMessages.matches()) {
                return matrixOk(roomMessages(
                        jwt,
                        decodeRoomId(roomMessages.group(1)),
                        request.getParameter("from"),
                        boundedLimit(request.getParameter("limit"))));
            }
            Matcher send = SEND_PATH.matcher(path);
            if (List.of("POST", "PUT").contains(method) && send.matches()) {
                return matrixOk(sendMessage(
                        jwt,
                        decodeRoomId(send.group(1)),
                        UriUtils.decode(send.group(2), StandardCharsets.UTF_8),
                        requestBody(request)));
            }
            return matrixError(
                    HttpStatus.NOT_FOUND,
                    "M_NOT_FOUND",
                    "This Matrix Client-Server projection route is not implemented by the current Weave Chat facade profile.");
        } catch (MatrixProtocolException exception) {
            return matrixError(matrixStatus(exception.errcode()), exception.errcode(), exception.getMessage());
        } catch (ApiErrorException exception) {
            return matrixError(exception.status(), matrixErrcode(exception), exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return matrixError(HttpStatus.BAD_REQUEST, "M_INVALID_PARAM", "The Matrix request parameter is invalid.");
        } catch (IllegalStateException exception) {
            return matrixError(HttpStatus.SERVICE_UNAVAILABLE, "M_UNAVAILABLE", "Weave Chat is not available.");
        }
    }

    private Map<String, Object> sync(Jwt jwt, String since) {
        matrixProtocolCoreService.validateSyncToken(since);
        ChatConversations conversations = chatDomainFacadeService.conversations(jwt);
        List<MatrixProtocolCoreService.CanonicalConversation> projection = conversations.conversations().stream()
                .map(conversation -> projectConversation(
                        conversation,
                        chatDomainFacadeService.messages(conversation.conversationId(), jwt)))
                .toList();
        return matrixProtocolCoreService.sync(
                jwt == null ? "" : jwt.getSubject(),
                chatDomainFacadeService.syncCursor(jwt),
                since,
                projection);
    }

    private Map<String, Object> joinedRooms(Jwt jwt) {
        ChatConversations conversations = chatDomainFacadeService.conversations(jwt);
        List<MatrixProtocolCoreService.CanonicalConversation> projection = conversations.conversations().stream()
                .map(conversation -> projectConversation(conversation, null))
                .toList();
        return matrixProtocolCoreService.joinedRooms(projection);
    }

    private Map<String, Object> roomMessages(Jwt jwt, String conversationId, String from, int limit) {
        ChatMessages messages = chatDomainFacadeService.messages(conversationId, jwt);
        ChatConversation conversation = chatDomainFacadeService.conversations(jwt).conversations().stream()
                .filter(candidate -> candidate.conversationId().equals(conversationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("conversation not found"));
        MatrixProtocolCoreService.CanonicalConversation projection = projectConversation(
                conversation,
                new ChatMessages(messages.readiness(), messages.conversationId(), messages.messages().stream()
                        .skip(Math.max(0, messages.messages().size() - limit))
                        .toList()));
        return matrixProtocolCoreService.messages(
                chatDomainFacadeService.syncCursor(jwt),
                from,
                projection);
    }

    private Map<String, Object> sendMessage(
            Jwt jwt,
            String conversationId,
            String transactionId,
            String requestJson) {
        String body = matrixProtocolCoreService.parseSendBody(requestJson);
        ChatMessage message = chatDomainFacadeService.sendMessage(
                conversationId,
                transactionId,
                body,
                jwt);
        return matrixProtocolCoreService.sendResponse(message.messageId());
    }

    private MatrixProtocolCoreService.CanonicalConversation projectConversation(
            ChatConversation conversation,
            ChatMessages messages) {
        List<MatrixProtocolCoreService.CanonicalMessage> projectedMessages = messages == null
                ? List.of()
                : messages.messages().stream().map(this::projectMessage).toList();
        return new MatrixProtocolCoreService.CanonicalConversation(
                conversation.conversationId(),
                conversation.title(),
                conversation.updatedAt() == null ? 0 : conversation.updatedAt().toEpochMilli(),
                0,
                projectedMessages);
    }

    private MatrixProtocolCoreService.CanonicalMessage projectMessage(ChatMessage message) {
        return new MatrixProtocolCoreService.CanonicalMessage(
                message.messageId(),
                message.senderRef(),
                message.sentAt().toEpochMilli(),
                message.body(),
                message.deliveryState(),
                false);
    }

    private String decodeRoomId(String matrixRoomId) {
        return matrixProtocolCoreService.decodeRoomId(UriUtils.decode(matrixRoomId, StandardCharsets.UTF_8));
    }

    private int boundedLimit(String rawLimit) {
        if (rawLimit == null || rawLimit.isBlank()) {
            return 100;
        }
        return Math.max(1, Math.min(Integer.parseInt(rawLimit), 100));
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
            throw new MatrixProtocolException("M_BAD_JSON", "Matrix request body could not be read.");
        }
    }

    private boolean isSync(String path) {
        return path.equals("/_matrix/client/v3/sync") || path.equals("/_matrix/client/r0/sync");
    }

    private boolean isVersions(String path) {
        return path.equals("/_matrix/client/versions") || path.equals("/_matrix/client/v3/versions");
    }

    private boolean isJoinedRooms(String path) {
        return path.equals("/_matrix/client/v3/joined_rooms") || path.equals("/_matrix/client/r0/joined_rooms");
    }

    private boolean isWhoami(String path) {
        return path.equals("/_matrix/client/v3/account/whoami") || path.equals("/_matrix/client/r0/account/whoami");
    }

    private ResponseEntity<Map<String, Object>> matrixOk(Map<String, Object> body) {
        return ResponseEntity.ok()
                .header("X-Weave-Projection", "matrix-client-server")
                .header("X-Weave-Matrix-Core", "rust-ruma-jni")
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

    private HttpStatus matrixStatus(String errcode) {
        return switch (errcode) {
            case "M_MISSING_TOKEN", "M_UNKNOWN_TOKEN" -> HttpStatus.UNAUTHORIZED;
            case "M_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "M_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "M_BAD_JSON", "M_INVALID_PARAM", "M_UNSUPPORTED" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private ResponseEntity<Map<String, Object>> matrixError(HttpStatus status, String errcode, String error) {
        Map<String, Object> body;
        try {
            body = matrixProtocolCoreService.error(errcode, error);
        } catch (MatrixProtocolException exception) {
            body = Map.of(
                    "errcode", "M_WEAVE_MATRIX_CORE_ERROR",
                    "error", "The Rust/Ruma Matrix protocol core rejected the error projection.",
                    "supportSafe", true);
        }
        return ResponseEntity.status(status)
                .header("X-Weave-Projection", "matrix-client-server")
                .header("X-Weave-Matrix-Core", "rust-ruma-jni")
                .body(body);
    }
}

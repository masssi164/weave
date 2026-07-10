package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatEventKind;
import com.massimotter.weave.backend.chat.domain.ChatMembership;
import com.massimotter.weave.backend.chat.domain.ChatRelation;
import com.massimotter.weave.backend.chat.domain.ChatTimeline;
import com.massimotter.weave.backend.chat.domain.ChatTimelineEvent;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.matrix.MatrixFacadeClientStateService;
import com.massimotter.weave.backend.matrix.MatrixProtocolCoreService;
import com.massimotter.weave.backend.matrix.MatrixProtocolException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

@RestController
@Hidden
public class MatrixClientServerProjectionController {

    private static final String MATRIX_ALLOW = "OPTIONS, GET, POST, PUT";
    private static final Pattern SEND_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/send/([^/]+)/([^/]+)$");
    private static final Pattern REDACT_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/redact/([^/]+)/([^/]+)$");
    private static final Pattern ROOM_MESSAGES_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/messages$");
    private static final Pattern ROOM_LEAVE_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/leave$");
    private static final Pattern ROOM_STATE_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/state$");
    private static final Pattern ROOM_STATE_EVENT_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/state/([^/]+)(?:/(.*))?$");
    private static final Pattern ROOM_JOINED_MEMBERS_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/joined_members$");
    private static final Pattern ROOM_RECEIPT_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/receipt/m\\.read/([^/]+)$");
    private static final Pattern ROOM_TYPING_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/typing/([^/]+)$");
    private static final Pattern JOIN_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/join/([^/]+)$");
    private static final Pattern FILTER_COLLECTION_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/user/([^/]+)/filter$");
    private static final Pattern FILTER_ITEM_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/user/([^/]+)/filter/([^/]+)$");
    private static final Pattern ACCOUNT_DATA_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/user/([^/]+)/account_data/([^/]+)$");
    private static final Pattern PROFILE_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/profile/([^/]+)$");

    private final ChatDomainFacadeService chatDomainFacadeService;
    private final MatrixProtocolCoreService matrixProtocolCoreService;
    private final MatrixFacadeClientStateService matrixClientStateService;
    private final String facadeBaseUrl;

    public MatrixClientServerProjectionController(
            ChatDomainFacadeService chatDomainFacadeService,
            MatrixProtocolCoreService matrixProtocolCoreService,
            MatrixFacadeClientStateService matrixClientStateService,
            @Value("${weave.matrix.facade.base-url:https://api.weave.test}") String facadeBaseUrl) {
        this.chatDomainFacadeService = chatDomainFacadeService;
        this.matrixProtocolCoreService = matrixProtocolCoreService;
        this.matrixClientStateService = matrixClientStateService;
        this.facadeBaseUrl = facadeBaseUrl.replaceAll("/+$", "");
    }

    @GetMapping("/.well-known/matrix/client")
    public Map<String, Object> wellKnownClient() {
        return Map.of("m.homeserver", Map.of("base_url", facadeBaseUrl));
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
    public ResponseEntity<?> handle(
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
            if (matrixClientStateService.revoked(jwt) && !isLogout(path)) {
                throw new MatrixProtocolException("M_UNKNOWN_TOKEN", "The Matrix access token was revoked.");
            }
            MatrixFacadeClientStateService.MatrixIdentity identity = matrixClientStateService.register(jwt);
            if ("GET".equals(method) && isVersions(path)) {
                return matrixOk(matrixProtocolCoreService.versions());
            }
            if ("GET".equals(method) && isLogin(path)) {
                return matrixOk(loginFlows());
            }
            if ("POST".equals(method) && isLogout(path)) {
                matrixClientStateService.revoke(jwt);
                return matrixOk(Map.of());
            }
            if ("GET".equals(method) && isWhoami(path)) {
                return matrixOk(matrixProtocolCoreService.whoami(jwt.getSubject()));
            }
            if ("GET".equals(method) && isPushRules(path)) {
                return matrixOk(matrixClientStateService.pushRules());
            }

            Matcher filterCollection = FILTER_COLLECTION_PATH.matcher(path);
            if ("POST".equals(method) && filterCollection.matches()) {
                requireCurrentUser(identity, decode(filterCollection.group(1)));
                String filterId = matrixClientStateService.createFilter(
                        identity.userId(),
                        requestBodyMap(request));
                return matrixOk(Map.of("filter_id", filterId));
            }
            Matcher filterItem = FILTER_ITEM_PATH.matcher(path);
            if ("GET".equals(method) && filterItem.matches()) {
                requireCurrentUser(identity, decode(filterItem.group(1)));
                return matrixOk(matrixClientStateService.filter(
                        identity.userId(),
                        decode(filterItem.group(2))));
            }
            Matcher accountData = ACCOUNT_DATA_PATH.matcher(path);
            if ("PUT".equals(method) && accountData.matches()) {
                requireCurrentUser(identity, decode(accountData.group(1)));
                matrixClientStateService.putAccountData(
                        identity.userId(),
                        decode(accountData.group(2)),
                        requestBodyValue(request));
                return matrixOk(Map.of());
            }
            if ("GET".equals(method) && isSync(path)) {
                return matrixOk(sync(jwt, identity, request.getParameter("since")));
            }
            if ("GET".equals(method) && isJoinedRooms(path)) {
                return matrixOk(joinedRooms(jwt));
            }
            if ("POST".equals(method) && isCreateRoom(path)) {
                return matrixOk(createRoom(jwt, requestBodyMap(request)));
            }

            Matcher join = JOIN_PATH.matcher(path);
            if ("POST".equals(method) && join.matches()) {
                String matrixRoomId = decode(join.group(1));
                String conversationId = matrixProtocolCoreService.decodeRoomId(matrixRoomId);
                chatDomainFacadeService.joinConversation(conversationId, jwt);
                return matrixOk(Map.of("room_id", matrixRoomId));
            }
            Matcher leave = ROOM_LEAVE_PATH.matcher(path);
            if ("POST".equals(method) && leave.matches()) {
                chatDomainFacadeService.leaveConversation(decodeRoomId(leave.group(1)), jwt);
                return matrixOk(Map.of());
            }
            Matcher roomMessages = ROOM_MESSAGES_PATH.matcher(path);
            if ("GET".equals(method) && roomMessages.matches()) {
                return matrixOk(roomMessages(
                        jwt,
                        decodeRoomId(roomMessages.group(1)),
                        request.getParameter("from"),
                        boundedLimit(request.getParameter("limit"))));
            }
            Matcher roomState = ROOM_STATE_PATH.matcher(path);
            if ("GET".equals(method) && roomState.matches()) {
                return matrixOkList(roomState(jwt, decodeRoomId(roomState.group(1))));
            }
            Matcher roomStateEvent = ROOM_STATE_EVENT_PATH.matcher(path);
            if ("GET".equals(method) && roomStateEvent.matches()) {
                return matrixOk(roomStateEvent(
                        jwt,
                        decodeRoomId(roomStateEvent.group(1)),
                        decode(roomStateEvent.group(2)),
                        decode(roomStateEvent.group(3))));
            }
            Matcher joinedMembers = ROOM_JOINED_MEMBERS_PATH.matcher(path);
            if ("GET".equals(method) && joinedMembers.matches()) {
                return matrixOk(joinedMembers(jwt, decodeRoomId(joinedMembers.group(1))));
            }
            Matcher receipt = ROOM_RECEIPT_PATH.matcher(path);
            if ("POST".equals(method) && receipt.matches()) {
                chatDomainFacadeService.markRead(
                        decodeRoomId(receipt.group(1)),
                        matrixProtocolCoreService.decodeEventId(decode(receipt.group(2))),
                        jwt);
                return matrixOk(Map.of());
            }
            Matcher typing = ROOM_TYPING_PATH.matcher(path);
            if ("PUT".equals(method) && typing.matches()) {
                requireCurrentUser(identity, decode(typing.group(2)));
                Map<String, Object> body = requestBodyMap(request);
                Object rawTyping = body.get("typing");
                if (!(rawTyping instanceof Boolean typingValue)) {
                    throw new MatrixProtocolException("M_BAD_JSON", "Matrix typing state is invalid.");
                }
                int timeout = body.get("timeout") instanceof Number value ? value.intValue() : 30_000;
                chatDomainFacadeService.setTyping(
                        decodeRoomId(typing.group(1)),
                        typingValue,
                        timeout,
                        jwt);
                return matrixOk(Map.of());
            }
            Matcher profile = PROFILE_PATH.matcher(path);
            if ("GET".equals(method) && profile.matches()) {
                return matrixOk(profile(decode(profile.group(1))));
            }
            Matcher send = SEND_PATH.matcher(path);
            if (List.of("POST", "PUT").contains(method) && send.matches()) {
                return matrixOk(sendEvent(
                        jwt,
                        decodeRoomId(send.group(1)),
                        decode(send.group(2)),
                        decode(send.group(3)),
                        requestBody(request)));
            }
            Matcher redact = REDACT_PATH.matcher(path);
            if (List.of("POST", "PUT").contains(method) && redact.matches()) {
                return matrixOk(redactEvent(
                        jwt,
                        decodeRoomId(redact.group(1)),
                        matrixProtocolCoreService.decodeEventId(decode(redact.group(2))),
                        decode(redact.group(3))));
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

    private Map<String, Object> loginFlows() {
        return Map.of(
                "flows", List.of(Map.of("type", "org.matrix.login.jwt")),
                "weaveOidcGatekeeper", true,
                "passwordLoginSupported", false);
    }

    private Map<String, Object> sync(
            Jwt jwt,
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String since) {
        matrixProtocolCoreService.validateSyncToken(since);
        ChatConversations conversations = chatDomainFacadeService.conversations(jwt);
        List<MatrixProtocolCoreService.CanonicalConversation> projection = conversations.conversations().stream()
                .map(conversation -> projectConversation(
                        conversation,
                        chatDomainFacadeService.timeline(conversation.conversationId(), jwt, 100)))
                .toList();
        return matrixProtocolCoreService.sync(
                jwt.getSubject(),
                chatDomainFacadeService.syncCursor(jwt),
                since,
                projection,
                matrixClientStateService.accountData(identity.userId()));
    }

    private Map<String, Object> joinedRooms(Jwt jwt) {
        ChatConversations conversations = chatDomainFacadeService.conversations(jwt);
        List<MatrixProtocolCoreService.CanonicalConversation> projection = conversations.conversations().stream()
                .map(conversation -> projectConversation(conversation, null))
                .toList();
        return matrixProtocolCoreService.joinedRooms(projection);
    }

    private Map<String, Object> roomMessages(Jwt jwt, String conversationId, String from, int limit) {
        ChatConversation conversation = chatDomainFacadeService.conversation(conversationId, jwt);
        ChatTimeline timeline = chatDomainFacadeService.timeline(conversationId, jwt, limit);
        return matrixProtocolCoreService.messages(
                chatDomainFacadeService.syncCursor(jwt),
                from,
                projectConversation(conversation, timeline));
    }

    private Map<String, Object> sendEvent(
            Jwt jwt,
            String conversationId,
            String eventType,
            String transactionId,
            String requestJson) {
        MatrixProtocolCoreService.ParsedEventContent parsed =
                matrixProtocolCoreService.parseEvent(eventType, requestJson);
        ChatTimelineEvent event = chatDomainFacadeService.sendEvent(
                conversationId,
                transactionId,
                canonicalContent(parsed),
                jwt);
        return matrixProtocolCoreService.sendResponse(event.eventId());
    }

    private Map<String, Object> redactEvent(
            Jwt jwt,
            String conversationId,
            String eventId,
            String transactionId) {
        ChatTimelineEvent event = chatDomainFacadeService.redactEvent(
                conversationId,
                eventId,
                transactionId,
                jwt);
        return matrixProtocolCoreService.sendResponse(event.eventId());
    }

    private Map<String, Object> createRoom(Jwt jwt, Map<String, Object> body) {
        String title = firstText(body.get("name"), body.get("room_alias_name"), "Conversation");
        String kind = Boolean.TRUE.equals(body.get("is_direct")) ? "direct" : "channel";
        List<ChatActorRef> invitedActors = new ArrayList<>();
        Object rawInvite = body.get("invite");
        if (rawInvite instanceof List<?> invite) {
            for (Object value : invite) {
                if (!(value instanceof String matrixUserId)) {
                    throw new MatrixProtocolException("M_BAD_JSON", "Matrix invite identities are invalid.");
                }
                invitedActors.add(matrixClientStateService.actorForMatrixUserId(matrixUserId)
                        .orElseThrow(() -> new MatrixProtocolException(
                                "M_NOT_FOUND",
                                "The invited Matrix identity is not registered with Weave.")));
            }
        }
        String transactionId = "create-" + UUID.nameUUIDFromBytes(
                (jwt.getSubject() + body).getBytes(StandardCharsets.UTF_8));
        ChatConversation conversation = chatDomainFacadeService.createConversation(
                transactionId,
                title,
                kind,
                invitedActors,
                jwt);
        return Map.of("room_id", matrixProtocolCoreService.roomId(conversation.conversationId()));
    }

    private List<Map<String, Object>> roomState(Jwt jwt, String conversationId) {
        ChatConversation conversation = chatDomainFacadeService.conversation(conversationId, jwt);
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(Map.of(
                "type", "m.room.name",
                "state_key", "",
                "content", Map.of("name", conversation.title())));
        for (ChatMembership membership : conversation.memberships()) {
            events.add(Map.of(
                    "type", "m.room.member",
                    "state_key", matrixProtocolCoreService.userId(membership.memberRef()),
                    "content", Map.of("membership", matrixMembershipState(membership.state()))));
        }
        return List.copyOf(events);
    }

    private Map<String, Object> roomStateEvent(
            Jwt jwt,
            String conversationId,
            String eventType,
            String stateKey) {
        ChatConversation conversation = chatDomainFacadeService.conversation(conversationId, jwt);
        if ("m.room.name".equals(eventType) && (stateKey == null || stateKey.isBlank())) {
            return Map.of("name", conversation.title());
        }
        if ("m.room.member".equals(eventType)) {
            return conversation.memberships().stream()
                    .filter(membership -> matrixProtocolCoreService.userId(membership.memberRef()).equals(stateKey))
                    .findFirst()
                    .map(membership -> Map.<String, Object>of(
                            "membership", matrixMembershipState(membership.state())))
                    .orElseThrow(() -> new MatrixProtocolException("M_NOT_FOUND", "Matrix room state was not found."));
        }
        throw new MatrixProtocolException("M_NOT_FOUND", "Matrix room state was not found.");
    }

    private Map<String, Object> joinedMembers(Jwt jwt, String conversationId) {
        ChatConversation conversation = chatDomainFacadeService.conversation(conversationId, jwt);
        Map<String, Object> joined = new java.util.LinkedHashMap<>();
        for (ChatMembership membership : conversation.memberships()) {
            if ("join".equals(matrixMembershipState(membership.state()))) {
                String userId = matrixProtocolCoreService.userId(membership.memberRef());
                joined.put(userId, Map.of("display_name", matrixDisplayName(userId)));
            }
        }
        return Map.of("joined", Map.copyOf(joined));
    }

    private Map<String, Object> profile(String userId) {
        matrixClientStateService.actorForMatrixUserId(userId)
                .orElseThrow(() -> new MatrixProtocolException("M_NOT_FOUND", "Matrix profile was not found."));
        return Map.of("displayname", matrixDisplayName(userId));
    }

    private String matrixDisplayName(String userId) {
        int separator = userId.indexOf(':');
        int start = userId.startsWith("@") ? 1 : 0;
        return userId.substring(start, separator > start ? separator : userId.length());
    }

    private ChatEventContent canonicalContent(MatrixProtocolCoreService.ParsedEventContent parsed) {
        ChatEventKind kind = switch (parsed.kind()) {
            case "message" -> ChatEventKind.MESSAGE;
            case "reaction" -> ChatEventKind.REACTION;
            default -> throw new MatrixProtocolException("M_UNSUPPORTED", "Matrix event type is unsupported.");
        };
        ChatRelation relation = parsed.relationKind() == null
                ? null
                : new ChatRelation(
                        parsed.relationKind(),
                        parsed.relationTargetEventId(),
                        parsed.replyToEventId());
        return new ChatEventContent(
                kind,
                parsed.messageType(),
                parsed.body(),
                parsed.format(),
                parsed.formattedBody(),
                relation,
                parsed.reactionKey(),
                parsed.presentationExtensions());
    }

    private MatrixProtocolCoreService.CanonicalConversation projectConversation(
            ChatConversation conversation,
            ChatTimeline timeline) {
        List<MatrixProtocolCoreService.CanonicalMessage> projectedEvents = timeline == null
                ? List.of()
                : timeline.events().stream().map(this::projectEvent).toList();
        return new MatrixProtocolCoreService.CanonicalConversation(
                conversation.conversationId(),
                conversation.title(),
                conversation.updatedAt() == null ? 0 : conversation.updatedAt().toEpochMilli(),
                0,
                conversation.memberships().stream()
                        .map(membership -> new MatrixProtocolCoreService.CanonicalMembership(
                                membership.memberRef(), membership.state()))
                        .toList(),
                projectedEvents);
    }

    private String matrixMembershipState(String canonicalState) {
        return switch (canonicalState) {
            case "joined", "join" -> "join";
            case "invited", "invite" -> "invite";
            case "left", "leave" -> "leave";
            case "banned", "ban" -> "ban";
            default -> throw new MatrixProtocolException("M_BAD_JSON", "Canonical Chat membership state is invalid.");
        };
    }

    private MatrixProtocolCoreService.CanonicalMessage projectEvent(ChatTimelineEvent event) {
        ChatRelation relation = event.content().relation();
        return new MatrixProtocolCoreService.CanonicalMessage(
                event.eventId(),
                event.senderRef(),
                event.occurredAt().toEpochMilli(),
                event.content().kind().value(),
                event.content().messageType(),
                event.content().body(),
                event.content().format(),
                event.content().formattedBody(),
                relation == null ? null : relation.kind(),
                relation == null ? null : relation.targetEventId(),
                relation == null ? null : relation.replyToEventId(),
                event.content().reactionKey(),
                event.content().presentationExtensions(),
                event.deliveryState(),
                false,
                event.redacted());
    }

    private String decodeRoomId(String matrixRoomId) {
        return matrixProtocolCoreService.decodeRoomId(decode(matrixRoomId));
    }

    private String decode(String value) {
        return value == null ? null : UriUtils.decode(value, StandardCharsets.UTF_8);
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

    private Map<String, Object> requestBodyMap(HttpServletRequest request) {
        return matrixProtocolCoreService.parseObject(requestBody(request));
    }

    private Object requestBodyValue(HttpServletRequest request) {
        return matrixProtocolCoreService.parseObject(requestBody(request));
    }

    private void requireCurrentUser(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String requestedUserId) {
        if (!identity.userId().equals(requestedUserId)) {
            throw new MatrixProtocolException("M_FORBIDDEN", "Matrix user-scoped state belongs to another user.");
        }
    }

    private String firstText(Object first, Object second, String fallback) {
        for (Object value : List.of(first == null ? "" : first, second == null ? "" : second)) {
            if (value instanceof String text && !text.isBlank()) {
                return text.trim();
            }
        }
        return fallback;
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

    private boolean isPushRules(String path) {
        return path.matches("^/_matrix/client/(?:v3|r0)/pushrules/?$");
    }

    private boolean isCreateRoom(String path) {
        return path.matches("^/_matrix/client/(?:v3|r0)/createRoom$");
    }

    private boolean isLogin(String path) {
        return path.matches("^/_matrix/client/(?:v3|r0)/login$");
    }

    private boolean isLogout(String path) {
        return path.matches("^/_matrix/client/(?:v3|r0)/logout$");
    }

    private ResponseEntity<Map<String, Object>> matrixOk(Map<String, Object> body) {
        return ResponseEntity.ok()
                .header("X-Weave-Projection", "matrix-client-server")
                .header("X-Weave-Matrix-Core", "rust-ruma-jni")
                .body(body);
    }

    private ResponseEntity<List<Map<String, Object>>> matrixOkList(List<Map<String, Object>> body) {
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
            case "M_LIMIT_EXCEEDED" -> HttpStatus.TOO_MANY_REQUESTS;
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

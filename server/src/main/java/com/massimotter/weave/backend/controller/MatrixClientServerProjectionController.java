package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatAccessDeniedException;
import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatEncryptedEnvelope;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatEventKind;
import com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity;
import com.massimotter.weave.backend.chat.domain.ChatMembership;
import com.massimotter.weave.backend.chat.domain.ChatProviderUnavailableException;
import com.massimotter.weave.backend.chat.domain.ChatRedactionReceipt;
import com.massimotter.weave.backend.chat.domain.ChatRelation;
import com.massimotter.weave.backend.chat.domain.ChatTimeline;
import com.massimotter.weave.backend.chat.domain.ChatTimelineEvent;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.matrix.MatrixFacadeClientStateService;
import com.massimotter.weave.backend.matrix.MatrixE2eeStateService;
import com.massimotter.weave.backend.matrix.MatrixProtocolCoreService;
import com.massimotter.weave.backend.matrix.MatrixProtocolException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

    private static final String MATRIX_ALLOW = "OPTIONS, GET, POST, PUT, DELETE";
    private static final Pattern SEND_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/send/([^/]+)/([^/]+)$");
    private static final Pattern REDACT_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/redact/([^/]+)/([^/]+)$");
    private static final Pattern ROOM_MESSAGES_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/messages$");
    private static final Pattern ROOM_MEMBERS_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/members$");
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
    private static final Pattern SEND_TO_DEVICE_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/sendToDevice/([^/]+)/([^/]+)$");
    private static final Pattern DEVICE_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/devices/([^/]+)$");
    private static final Pattern ROOM_KEYS_VERSION_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/room_keys/version(?:/([^/]+))?$");
    private static final Pattern ROOM_KEYS_KEYS_PATH = Pattern.compile(
            "^/_matrix/client/(?:v3|r0)/room_keys/keys(?:/([^/]+)(?:/([^/]+))?)?$");

    private final ChatDomainFacadeService chatDomainFacadeService;
    private final MatrixProtocolCoreService matrixProtocolCoreService;
    private final MatrixFacadeClientStateService matrixClientStateService;
    private final MatrixE2eeStateService matrixE2eeStateService;
    private final String facadeBaseUrl;

    public MatrixClientServerProjectionController(
            ChatDomainFacadeService chatDomainFacadeService,
            MatrixProtocolCoreService matrixProtocolCoreService,
            MatrixFacadeClientStateService matrixClientStateService,
            MatrixE2eeStateService matrixE2eeStateService,
            @Value("${weave.matrix.facade.base-url:https://api.weave.test}") String facadeBaseUrl) {
        this.chatDomainFacadeService = chatDomainFacadeService;
        this.matrixProtocolCoreService = matrixProtocolCoreService;
        this.matrixClientStateService = matrixClientStateService;
        this.matrixE2eeStateService = matrixE2eeStateService;
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
        if (!List.of("GET", "POST", "PUT", "DELETE").contains(method)) {
            return matrixError(
                    HttpStatus.NOT_IMPLEMENTED,
                    "M_WEAVE_MATRIX_METHOD_NOT_IMPLEMENTED",
                    "This Weave Matrix Client-Server projection supports OPTIONS, GET, POST, PUT, and DELETE.");
        }
        try {
            String path = requestPath(request);
            if (matrixClientStateService.revoked(jwt) && !isLogout(path)) {
                throw new MatrixProtocolException("M_UNKNOWN_TOKEN", "The Matrix access token was revoked.");
            }
            MatrixFacadeClientStateService.MatrixIdentity identity = matrixClientStateService.register(
                    jwt,
                    request.getHeader(MatrixFacadeClientStateService.DEVICE_ID_HEADER));
            matrixE2eeStateService.requireActive(identity);
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
                return matrixOk(matrixProtocolCoreService.whoami(jwt.getSubject(), identity.deviceId()));
            }
            if ("GET".equals(method) && isPushRules(path)) {
                return matrixOk(matrixClientStateService.pushRules());
            }
            if ("POST".equals(method) && isKeysUpload(path)) {
                return matrixOk(matrixE2eeStateService.uploadKeys(identity, requestBodyMap(request)));
            }
            if ("POST".equals(method) && isKeysQuery(path)) {
                return matrixOk(matrixE2eeStateService.queryKeys(identity, requestBodyMap(request)));
            }
            if ("POST".equals(method) && isKeysClaim(path)) {
                return matrixOk(matrixE2eeStateService.claimKeys(identity, requestBodyMap(request)));
            }
            if ("POST".equals(method) && isDeviceSigningUpload(path)) {
                matrixE2eeStateService.uploadCrossSigning(identity, requestBodyMap(request));
                return matrixOk(Map.of());
            }
            if ("POST".equals(method) && isSignaturesUpload(path)) {
                return matrixOk(matrixE2eeStateService.uploadSignatures(identity, requestBodyMap(request)));
            }
            if ("GET".equals(method) && isKeysChanges(path)) {
                String decodedFrom = matrixProtocolCoreService.decodeSyncCursor(request.getParameter("from"));
                return matrixOk(matrixE2eeStateService.keyChanges(
                        identity,
                        matrixE2eeStateService.cryptoSequence(decodedFrom)));
            }
            Matcher sendToDevice = SEND_TO_DEVICE_PATH.matcher(path);
            if ("PUT".equals(method) && sendToDevice.matches()) {
                matrixE2eeStateService.sendToDevice(
                        identity,
                        decode(sendToDevice.group(1)),
                        decode(sendToDevice.group(2)),
                        requestBodyMap(request));
                return matrixOk(Map.of());
            }
            Matcher device = DEVICE_PATH.matcher(path);
            if ("DELETE".equals(method) && device.matches()) {
                matrixE2eeStateService.revokeDevice(identity, decode(device.group(1)));
                return matrixOk(Map.of());
            }
            Matcher backupVersion = ROOM_KEYS_VERSION_PATH.matcher(path);
            if (backupVersion.matches()) {
                String version = decode(backupVersion.group(1));
                if ("POST".equals(method) && version == null) {
                    return matrixOk(matrixE2eeStateService.createBackupVersion(identity, requestBodyMap(request)));
                }
                if ("GET".equals(method)) {
                    return matrixOk(matrixE2eeStateService.backupVersion(identity, version));
                }
                if ("PUT".equals(method) && version != null) {
                    matrixE2eeStateService.updateBackupVersion(identity, version, requestBodyMap(request));
                    return matrixOk(Map.of());
                }
                if ("DELETE".equals(method) && version != null) {
                    matrixE2eeStateService.deleteBackupVersion(identity, version);
                    return matrixOk(Map.of());
                }
            }
            Matcher backupKeys = ROOM_KEYS_KEYS_PATH.matcher(path);
            if (backupKeys.matches()) {
                String version = request.getParameter("version");
                if (version == null || version.isBlank()) {
                    throw new MatrixProtocolException("M_INVALID_PARAM", "The Matrix room-key backup version is required.");
                }
                String roomId = decode(backupKeys.group(1));
                String sessionId = decode(backupKeys.group(2));
                if ("PUT".equals(method)) {
                    return matrixOk(matrixE2eeStateService.putBackupKeys(
                            identity,
                            version,
                            roomId,
                            sessionId,
                            requestBodyMap(request)));
                }
                if ("GET".equals(method)) {
                    return matrixOk(matrixE2eeStateService.backupKeys(identity, version, roomId, sessionId));
                }
                if ("DELETE".equals(method)) {
                    matrixE2eeStateService.deleteBackupKeys(identity, version, roomId, sessionId);
                    return matrixOk(Map.of());
                }
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
            if (accountData.matches()) {
                requireCurrentUser(identity, decode(accountData.group(1)));
                String eventType = decode(accountData.group(2));
                if ("PUT".equals(method)) {
                    matrixE2eeStateService.putAccountData(
                            identity,
                            eventType,
                            requestBodyMap(request));
                    return matrixOk(Map.of());
                }
                if ("GET".equals(method)) {
                    return matrixOk(matrixE2eeStateService.accountData(identity, eventType));
                }
            }
            if ("GET".equals(method) && isSync(path)) {
                return matrixOk(sync(jwt, identity, request.getParameter("since")));
            }
            if ("GET".equals(method) && isJoinedRooms(path)) {
                return matrixOk(joinedRooms(jwt));
            }
            if ("POST".equals(method) && isCreateRoom(path)) {
                return matrixOk(createRoom(
                        jwt,
                        identity,
                        requestBodyMap(request),
                        request.getHeader("Idempotency-Key")));
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
            Matcher roomMembers = ROOM_MEMBERS_PATH.matcher(path);
            if ("GET".equals(method) && roomMembers.matches()) {
                ChatConversation conversation = chatDomainFacadeService.conversation(
                        decodeRoomId(roomMembers.group(1)),
                        jwt);
                return matrixOk(matrixProtocolCoreService.members(projectConversation(conversation, null)));
            }
            Matcher roomState = ROOM_STATE_PATH.matcher(path);
            if ("GET".equals(method) && roomState.matches()) {
                return matrixOkList(roomState(jwt, decodeRoomId(roomState.group(1))));
            }
            Matcher roomStateEvent = ROOM_STATE_EVENT_PATH.matcher(path);
            if ("PUT".equals(method) && roomStateEvent.matches()) {
                String conversationId = decodeRoomId(roomStateEvent.group(1));
                String eventType = decode(roomStateEvent.group(2));
                String stateKey = decode(roomStateEvent.group(3));
                if (!"m.room.encryption".equals(eventType) || (stateKey != null && !stateKey.isBlank())) {
                    throw new MatrixProtocolException("M_UNSUPPORTED", "This Matrix room state write is unsupported.");
                }
                Map<String, Object> content = requestBodyMap(request);
                Object rawAlgorithm = content.get("algorithm");
                if (!(rawAlgorithm instanceof String algorithm)) {
                    throw new MatrixProtocolException("M_BAD_JSON", "Matrix room encryption algorithm is required.");
                }
                chatDomainFacadeService.enableEncryption(conversationId, algorithm, jwt);
                return matrixOk(matrixProtocolCoreService.sendResponse("state-encryption-" + conversationId));
            }
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
                return matrixOk(profile(identity, decode(profile.group(1))));
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
        } catch (ChatAccessDeniedException exception) {
            return matrixError(HttpStatus.FORBIDDEN, "M_FORBIDDEN", exception.getMessage());
        } catch (ChatProviderUnavailableException exception) {
            if (exception.throttled()) {
                return matrixThrottled(exception.retryAfterMilliseconds(Instant.now()));
            }
            return matrixError(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    supportSafeProviderErrcode(exception),
                    "Weave Chat is temporarily unavailable.");
        } catch (IllegalArgumentException exception) {
            return matrixError(HttpStatus.BAD_REQUEST, "M_INVALID_PARAM", "The Matrix request parameter is invalid.");
        } catch (IllegalStateException exception) {
            return matrixError(HttpStatus.SERVICE_UNAVAILABLE, "M_UNAVAILABLE", "Weave Chat is not available.");
        }
    }

    private String supportSafeProviderErrcode(ChatProviderUnavailableException exception) {
        String prefix = "chat-conversation-mapping-degraded-";
        String code = exception.supportSafeCode();
        if (!code.startsWith(prefix)) {
            return "M_UNAVAILABLE";
        }
        String reason = code.substring(prefix.length());
        if (!reason.matches("[a-z0-9-]{2,55}")) {
            return "M_WEAVE_CHAT_DEGRADED_UNKNOWN";
        }
        return "M_WEAVE_CHAT_DEGRADED_" + reason.toUpperCase(java.util.Locale.ROOT).replace('-', '_');
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
        String decodedCursor = matrixProtocolCoreService.decodeSyncCursor(since);
        long cryptoSequence = matrixE2eeStateService.cryptoSequence(decodedCursor);
        ChatConversations conversations = chatDomainFacadeService.conversations(jwt);
        List<MatrixProtocolCoreService.CanonicalConversation> projection = conversations.conversations().stream()
                .map(conversation -> projectConversation(
                        conversation,
                        chatDomainFacadeService.timeline(conversation.conversationId(), jwt, 100)))
                .toList();
        MatrixProtocolCoreService.MatrixSyncCrypto crypto = matrixE2eeStateService.sync(
                identity,
                cryptoSequence,
                sharedEncryptedRoomUsers(identity, projection));
        return matrixProtocolCoreService.sync(
                jwt.getSubject(),
                matrixE2eeStateService.combinedCursor(
                        chatDomainFacadeService.syncCursor(jwt),
                        crypto.nextSequence()),
                since,
                projection,
                matrixE2eeStateService.accountData(identity),
                crypto);
    }

    private Set<String> sharedEncryptedRoomUsers(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            List<MatrixProtocolCoreService.CanonicalConversation> conversations) {
        return conversations.stream()
                .filter(conversation -> conversation.encryptionAlgorithm() != null)
                .flatMap(conversation -> conversation.memberships().stream())
                .filter(membership -> "join".equals(matrixMembershipState(membership.state())))
                .map(membership -> matrixProtocolCoreService.userId(membership.memberRef()))
                .filter(userId -> !userId.equals(identity.userId()))
                .collect(Collectors.toUnmodifiableSet());
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
        ChatRedactionReceipt receipt = chatDomainFacadeService.redactEvent(
                conversationId,
                eventId,
                transactionId,
                jwt);
        return matrixProtocolCoreService.sendResponse(receipt.redactionEventId());
    }

    private Map<String, Object> createRoom(
            Jwt jwt,
            MatrixFacadeClientStateService.MatrixIdentity identity,
            Map<String, Object> body,
            String idempotencyKey) {
        String title = firstText(body.get("name"), body.get("room_alias_name"), "Conversation");
        String kind = Boolean.TRUE.equals(body.get("is_direct")) ? "direct" : "channel";
        List<ChatResolvedIdentity> invitedIdentities = new ArrayList<>();
        Object rawInvite = body.get("invite");
        if (rawInvite instanceof List<?> invite) {
            for (Object value : invite) {
                if (!(value instanceof String matrixUserId)) {
                    throw new MatrixProtocolException("M_BAD_JSON", "Matrix invite identities are invalid.");
                }
                invitedIdentities.add(matrixClientStateService.identityForMatrixUserId(
                                matrixUserId,
                                identity.tenantId(),
                                identity.identityIssuer())
                        .orElseThrow(() -> new MatrixProtocolException(
                                "M_NOT_FOUND",
                                "The invited Matrix identity is not registered with Weave.")));
            }
        }
        String transactionId = createRoomTransactionId(identity, idempotencyKey, contextId(jwt));
        ChatEncryptionState initialEncryption = initialEncryption(body);
        ChatConversation conversation = chatDomainFacadeService.createConversation(
                transactionId,
                title,
                kind,
                invitedIdentities,
                initialEncryption,
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
        if (conversation.encryptionState().encrypted()) {
            events.add(Map.of(
                    "type", "m.room.encryption",
                    "state_key", "",
                    "content", Map.of("algorithm", conversation.encryptionState().mode())));
        }
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
        if ("m.room.encryption".equals(eventType)
                && (stateKey == null || stateKey.isBlank())
                && conversation.encryptionState().encrypted()) {
            return Map.of("algorithm", conversation.encryptionState().mode());
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

    private Map<String, Object> profile(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String userId) {
        matrixClientStateService.identityForMatrixUserId(
                        userId,
                        identity.tenantId(),
                        identity.identityIssuer())
                .orElseThrow(() -> new MatrixProtocolException("M_NOT_FOUND", "Matrix profile was not found."));
        return Map.of("displayname", matrixDisplayName(userId));
    }

    private String createRoomTransactionId(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String idempotencyKey,
            String contextId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return "create-" + java.util.UUID.randomUUID();
        }
        String normalized = idempotencyKey.trim();
        if (!normalized.matches("[A-Za-z0-9._:=/-]{8,160}")) {
            throw new MatrixProtocolException("M_INVALID_PARAM", "The Idempotency-Key is invalid.");
        }
        String scope = identity.tenantId()
                + "\n" + identity.identityIssuer()
                + "\n" + contextId
                + "\n" + identity.actorRef().value()
                + "\n" + normalized;
        return "create-" + sha256(scope);
    }

    private String contextId(Jwt jwt) {
        String value = jwt.getClaimAsString("weave_context_id");
        if (value == null || value.isBlank()) {
            value = jwt.getClaimAsString("context_id");
        }
        return value == null || value.isBlank() ? "workspace-default" : value.trim();
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
            case "encrypted" -> ChatEventKind.ENCRYPTED;
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
                parsed.presentationExtensions(),
                parsed.encryptedContent() == null ? null : new ChatEncryptedEnvelope(parsed.encryptedContent()));
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
                conversation.encryptionState().encrypted()
                        ? conversation.encryptionState().mode()
                        : null,
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
                event.content().encryptedEnvelope() == null
                        ? null
                        : event.content().encryptedEnvelope().content(),
                event.redacted());
    }

    private ChatEncryptionState initialEncryption(Map<String, Object> body) {
        Object rawInitialState = body.get("initial_state");
        if (rawInitialState == null) {
            return ChatEncryptionState.unencrypted();
        }
        if (!(rawInitialState instanceof List<?> initialState)) {
            throw new MatrixProtocolException("M_BAD_JSON", "Matrix initial room state is invalid.");
        }
        int encryptionEvents = 0;
        for (Object rawEvent : initialState) {
            if (!(rawEvent instanceof Map<?, ?> event)) {
                throw new MatrixProtocolException("M_BAD_JSON", "Matrix initial room state is invalid.");
            }
            if (!"m.room.encryption".equals(event.get("type"))) {
                continue;
            }
            encryptionEvents++;
            if (encryptionEvents > 1) {
                throw new MatrixProtocolException("M_BAD_JSON", "Matrix room encryption state is duplicated.");
            }
            if (!(event.get("state_key") instanceof String stateKey) || !stateKey.isEmpty()) {
                throw new MatrixProtocolException("M_BAD_JSON", "Matrix room encryption state key is invalid.");
            }
            if (!(event.get("content") instanceof Map<?, ?> content)
                    || !(content.get("algorithm") instanceof String algorithm)) {
                throw new MatrixProtocolException("M_BAD_JSON", "Matrix room encryption algorithm is required.");
            }
            if (!ChatEncryptedEnvelope.MEGOLM_V1.equals(algorithm)) {
                throw new MatrixProtocolException("M_UNSUPPORTED", "Matrix room encryption algorithm is unsupported.");
            }
        }
        return encryptionEvents == 1
                ? ChatEncryptionState.matrixMegolm()
                : ChatEncryptionState.unencrypted();
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

    private boolean isKeysUpload(String path) {
        return path.matches("^/_matrix/client/(?:v3|r0)/keys/upload$");
    }

    private boolean isKeysQuery(String path) {
        return path.matches("^/_matrix/client/(?:v3|r0)/keys/query$");
    }

    private boolean isKeysClaim(String path) {
        return path.matches("^/_matrix/client/(?:v3|r0)/keys/claim$");
    }

    private boolean isKeysChanges(String path) {
        return path.matches("^/_matrix/client/(?:v3|r0)/keys/changes$");
    }

    private boolean isDeviceSigningUpload(String path) {
        return path.matches("^/_matrix/client/(?:v3|r0)/keys/device_signing/upload$");
    }

    private boolean isSignaturesUpload(String path) {
        return path.matches("^/_matrix/client/(?:v3|r0)/keys/signatures/upload$");
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

    private ResponseEntity<Map<String, Object>> matrixThrottled(long retryAfterMilliseconds) {
        long bounded = Math.max(1_000, Math.min(retryAfterMilliseconds, 3_600_000));
        Map<String, Object> body = new java.util.LinkedHashMap<>(
                matrixProtocolCoreService.error("M_LIMIT_EXCEEDED", "Weave Chat is temporarily throttled."));
        body.put("retry_after_ms", bounded);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(Math.max(1, (bounded + 999) / 1000)))
                .header("X-Weave-Projection", "matrix-client-server")
                .header("X-Weave-Matrix-Core", "rust-ruma-jni")
                .body(Map.copyOf(body));
    }
}

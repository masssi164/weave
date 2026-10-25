package com.massimotter.weave.backend.chat.api.matrix;

import com.massimotter.weave.backend.chat.port.MatrixFacadeClientStateStore;

import com.massimotter.weave.backend.chat.application.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatAccessDeniedException;
import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatCursor;
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
import com.massimotter.weave.backend.chat.domain.ChatTimelinePage;
import com.massimotter.weave.backend.chat.domain.ChatTimelineEvent;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.chat.application.matrix.MatrixFacadeClientStateService;
import com.massimotter.weave.backend.chat.application.matrix.MatrixE2eeStateService;
import com.massimotter.weave.backend.chat.application.matrix.MatrixDeviceProofService;
import com.massimotter.weave.backend.chat.port.MatrixProtocolCodec;
import com.massimotter.weave.backend.chat.domain.matrix.MatrixProtocolException;
import com.massimotter.weave.backend.identity.application.MemberDisplayNameQuery;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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

    public static final String SPACE_REF_HEADER = "X-Weave-Space-Ref";
    private static final String MATRIX_ALLOW = "OPTIONS, GET, POST, PUT, DELETE";
    private static final Pattern SEND_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/send/([^/]+)/([^/]+)$");
    private static final Pattern REDACT_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/redact/([^/]+)/([^/]+)$");
    private static final Pattern ROOM_MESSAGES_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/messages$");
    private static final Pattern ROOM_MEMBERS_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/members$");
    private static final Pattern ROOM_LEAVE_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/leave$");
    private static final Pattern ROOM_STATE_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/state$");
    private static final Pattern ROOM_STATE_EVENT_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/state/([^/]+)(?:/(.*))?$");
    private static final Pattern ROOM_JOINED_MEMBERS_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/joined_members$");
    private static final Pattern ROOM_RECEIPT_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/receipt/m\\.read/([^/]+)$");
    private static final Pattern ROOM_TYPING_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/rooms/([^/]+)/typing/([^/]+)$");
    private static final Pattern JOIN_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/join/([^/]+)$");
    private static final Pattern FILTER_COLLECTION_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/user/([^/]+)/filter$");
    private static final Pattern FILTER_ITEM_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/user/([^/]+)/filter/([^/]+)$");
    private static final Pattern ACCOUNT_DATA_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/user/([^/]+)/account_data/([^/]+)$");
    private static final Pattern PROFILE_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/profile/([^/]+)$");
    private static final Pattern SEND_TO_DEVICE_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/sendToDevice/([^/]+)/([^/]+)$");
    private static final Pattern DEVICE_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/devices/([^/]+)$");
    private static final Pattern ROOM_KEYS_VERSION_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/room_keys/version(?:/([^/]+))?$");
    private static final Pattern ROOM_KEYS_KEYS_PATH = Pattern.compile("^/_matrix/client/(?:v3|r0)/room_keys/keys(?:/([^/]+)(?:/([^/]+))?)?$");

    private final ChatDomainFacadeService chatDomainFacadeService;
    private final MatrixProtocolCodec matrixProtocolCoreService;
    private final MatrixFacadeClientStateService matrixClientStateService;
    private final MatrixE2eeStateService matrixE2eeStateService;
    private final MatrixDeviceProofService matrixDeviceProofService;
    private final String facadeBaseUrl;
    private final MemberDisplayNameQuery memberDisplayNames;

    public MatrixClientServerProjectionController(
            ChatDomainFacadeService chatDomainFacadeService,
            MatrixProtocolCodec matrixProtocolCoreService,
            MatrixFacadeClientStateService matrixClientStateService,
            MatrixE2eeStateService matrixE2eeStateService,
            MatrixDeviceProofService matrixDeviceProofService,
            MemberDisplayNameQuery memberDisplayNames,
            @Value("${weave.matrix.facade.base-url:https://api.weave.test}") String facadeBaseUrl) {
        this.chatDomainFacadeService = chatDomainFacadeService;
        this.matrixProtocolCoreService = matrixProtocolCoreService;
        this.matrixClientStateService = matrixClientStateService;
        this.matrixE2eeStateService = matrixE2eeStateService;
        this.matrixDeviceProofService = matrixDeviceProofService;
        this.memberDisplayNames = memberDisplayNames;
        this.facadeBaseUrl = facadeBaseUrl.replaceAll("/+$", "");
    }

    @GetMapping("/.well-known/matrix/client")
    public Map<String, Object> wellKnownClient() { return Map.of("m.homeserver", Map.of("base_url", facadeBaseUrl)); }

    @RequestMapping(value = "/_matrix/client/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.noContent().header(HttpHeaders.ALLOW, MATRIX_ALLOW).header("X-Weave-Projection", "matrix-client-server").header("X-Weave-Matrix-Core", "rust-ruma-jni").build();
    }

    @RequestMapping("/_matrix/client/**")
    public ResponseEntity<?> handle(HttpServletRequest request, @AuthenticationPrincipal Jwt jwt) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        if (!List.of("GET", "POST", "PUT", "DELETE").contains(method)) {
            return matrixError(HttpStatus.NOT_IMPLEMENTED, "M_WEAVE_MATRIX_METHOD_NOT_IMPLEMENTED", "This Weave Matrix Client-Server projection supports OPTIONS, GET, POST, PUT, and DELETE.");
        }
        try {
            String path = requestPath(request);
            if ("GET".equals(method) && isVersions(path)) return matrixOk(matrixProtocolCoreService.versions());
            if ("GET".equals(method) && isLogin(path)) return matrixOk(loginFlows());
            if (matrixClientStateService.revoked(jwt) && !isLogout(path)) throw new MatrixProtocolException("M_UNKNOWN_TOKEN", "The Matrix access token was revoked.");
            MatrixFacadeClientStateService.MatrixIdentity identity = matrixClientStateService.register(jwt, request.getHeader(MatrixFacadeClientStateService.DEVICE_ID_HEADER));
            if ("POST".equals(method) && path.equals("/_matrix/client/unstable/org.weave.device_continuity/challenge")) {
                matrixE2eeStateService.requireActive(identity);
                return matrixOk(matrixDeviceProofService.issueRecovery(identity,
                        request.getHeader(MatrixFacadeClientStateService.DEVICE_ID_HEADER),
                        request.getHeader(MatrixDeviceProofService.DEVICE_PROOF_HEADER)));
            }
            if ("POST".equals(method) && path.equals("/_matrix/client/unstable/org.weave.device_continuity/complete")) {
                matrixE2eeStateService.requireActive(identity);
                return matrixOk(matrixDeviceProofService.completeRecovery(identity,
                        request.getHeader(MatrixFacadeClientStateService.DEVICE_ID_HEADER),
                        request.getHeader(MatrixDeviceProofService.DEVICE_PROOF_HEADER), requestBodyMap(request)));
            }
            matrixDeviceProofService.require(identity,
                    request.getHeader(MatrixFacadeClientStateService.DEVICE_ID_HEADER),
                    request.getHeader(MatrixDeviceProofService.DEVICE_PROOF_HEADER));
            matrixE2eeStateService.requireActive(identity);
            if ("POST".equals(method) && isLogout(path)) { matrixClientStateService.revoke(jwt); return matrixOk(Map.of()); }
            if ("POST".equals(method) && (path.equals("/_matrix/client/v3/user_directory/search")
                    || path.equals("/_matrix/client/r0/user_directory/search"))) {
                return matrixOk(searchParticipants(jwt, identity, requestBodyMap(request), selectedSpaceRef(request)));
            }
            if ("GET".equals(method) && isWhoami(path)) return matrixOk(matrixProtocolCoreService.whoami(jwt.getSubject(), identity.deviceId()));
            if ("GET".equals(method) && isPushRules(path)) return matrixOk(matrixClientStateService.pushRules());
            if ("POST".equals(method) && isKeysUpload(path)) return matrixOk(matrixE2eeStateService.uploadKeys(identity, requestBodyMap(request)));
            if ("POST".equals(method) && isKeysQuery(path)) return matrixOk(matrixE2eeStateService.queryKeys(identity, requestBodyMap(request)));
            if ("POST".equals(method) && isKeysClaim(path)) return matrixOk(matrixE2eeStateService.claimKeys(identity, requestBodyMap(request)));
            if ("POST".equals(method) && isDeviceSigningUpload(path)) { matrixE2eeStateService.uploadCrossSigning(identity, requestBodyMap(request)); return matrixOk(Map.of()); }
            if ("POST".equals(method) && isSignaturesUpload(path)) return matrixOk(matrixE2eeStateService.uploadSignatures(identity, requestBodyMap(request)));
            if ("GET".equals(method) && isKeysChanges(path)) {
                String decodedFrom = matrixProtocolCoreService.decodeSyncCursor(request.getParameter("from"));
                return matrixOk(matrixE2eeStateService.keyChanges(identity, matrixE2eeStateService.cryptoDeviceListSequence(decodedFrom)));
            }
            Matcher sendToDevice = SEND_TO_DEVICE_PATH.matcher(path);
            if ("PUT".equals(method) && sendToDevice.matches()) { matrixE2eeStateService.sendToDevice(identity, decode(sendToDevice.group(1)), decode(sendToDevice.group(2)), requestBodyMap(request)); return matrixOk(Map.of()); }
            Matcher device = DEVICE_PATH.matcher(path);
            if ("DELETE".equals(method) && device.matches()) { matrixE2eeStateService.revokeDevice(identity, decode(device.group(1))); return matrixOk(Map.of()); }
            Matcher backupVersion = ROOM_KEYS_VERSION_PATH.matcher(path);
            if (backupVersion.matches()) {
                String version = decode(backupVersion.group(1));
                if ("POST".equals(method) && version == null) return matrixOk(matrixE2eeStateService.createBackupVersion(identity, requestBodyMap(request)));
                if ("GET".equals(method)) return matrixOk(matrixE2eeStateService.backupVersion(identity, version));
                if ("PUT".equals(method) && version != null) { matrixE2eeStateService.updateBackupVersion(identity, version, requestBodyMap(request)); return matrixOk(Map.of()); }
                if ("DELETE".equals(method) && version != null) { matrixE2eeStateService.deleteBackupVersion(identity, version); return matrixOk(Map.of()); }
            }
            Matcher backupKeys = ROOM_KEYS_KEYS_PATH.matcher(path);
            if (backupKeys.matches()) {
                String version = request.getParameter("version");
                if (version == null || version.isBlank()) throw new MatrixProtocolException("M_INVALID_PARAM", "The Matrix room-key backup version is required.");
                String roomId = decode(backupKeys.group(1));
                String sessionId = decode(backupKeys.group(2));
                if ("PUT".equals(method)) return matrixOk(matrixE2eeStateService.putBackupKeys(identity, version, roomId, sessionId, requestBodyMap(request)));
                if ("GET".equals(method)) return matrixOk(matrixE2eeStateService.backupKeys(identity, version, roomId, sessionId));
                if ("DELETE".equals(method)) { matrixE2eeStateService.deleteBackupKeys(identity, version, roomId, sessionId); return matrixOk(Map.of()); }
            }
            Matcher filterCollection = FILTER_COLLECTION_PATH.matcher(path);
            if ("POST".equals(method) && filterCollection.matches()) { requireCurrentUser(identity, decode(filterCollection.group(1))); String filterId = matrixClientStateService.createFilter(identity.userId(), requestBodyMap(request)); return matrixOk(Map.of("filter_id", filterId)); }
            Matcher filterItem = FILTER_ITEM_PATH.matcher(path);
            if ("GET".equals(method) && filterItem.matches()) { requireCurrentUser(identity, decode(filterItem.group(1))); return matrixOk(matrixClientStateService.filter(identity.userId(), decode(filterItem.group(2)))); }
            Matcher accountData = ACCOUNT_DATA_PATH.matcher(path);
            if (accountData.matches()) {
                requireCurrentUser(identity, decode(accountData.group(1)));
                String eventType = decode(accountData.group(2));
                if ("PUT".equals(method)) { matrixE2eeStateService.putAccountData(identity, eventType, requestBodyMap(request)); return matrixOk(Map.of()); }
                if ("GET".equals(method)) return matrixOk(matrixE2eeStateService.accountData(identity, eventType));
            }
            if ("GET".equals(method) && isSync(path)) return matrixOk(sync(jwt, identity, request.getParameter("since")));
            if ("GET".equals(method) && isJoinedRooms(path)) return matrixOk(joinedRooms(jwt));
            if ("POST".equals(method) && isCreateRoom(path)) return matrixOk(createRoom(jwt, identity, requestBodyMap(request), request.getHeader("Idempotency-Key"), selectedSpaceRef(request)));
            Matcher join = JOIN_PATH.matcher(path);
            if ("POST".equals(method) && join.matches()) { String matrixRoomId = decode(join.group(1)); String conversationId = matrixProtocolCoreService.decodeRoomId(matrixRoomId); chatDomainFacadeService.joinConversation(conversationId, jwt); return matrixOk(Map.of("room_id", matrixRoomId)); }
            Matcher leave = ROOM_LEAVE_PATH.matcher(path);
            if ("POST".equals(method) && leave.matches()) { chatDomainFacadeService.leaveConversation(decodeRoomId(leave.group(1)), jwt); return matrixOk(Map.of()); }
            Matcher roomMessages = ROOM_MESSAGES_PATH.matcher(path);
            if ("GET".equals(method) && roomMessages.matches()) return matrixOk(roomMessages(jwt, decodeRoomId(roomMessages.group(1)), request.getParameter("from"), request.getParameter("dir"), boundedLimit(request.getParameter("limit"))));
            Matcher roomMembers = ROOM_MEMBERS_PATH.matcher(path);
            if ("GET".equals(method) && roomMembers.matches()) { ChatConversation conversation = chatDomainFacadeService.conversation(decodeRoomId(roomMembers.group(1)), jwt); return matrixOk(matrixProtocolCoreService.members(withMemberDisplayNames(jwt, List.of(projectConversation(conversation, null))).getFirst())); }
            Matcher roomState = ROOM_STATE_PATH.matcher(path);
            if ("GET".equals(method) && roomState.matches()) return matrixOkList(roomState(jwt, decodeRoomId(roomState.group(1))));
            Matcher roomStateEvent = ROOM_STATE_EVENT_PATH.matcher(path);
            if ("PUT".equals(method) && roomStateEvent.matches()) {
                String conversationId = decodeRoomId(roomStateEvent.group(1));
                String eventType = decode(roomStateEvent.group(2));
                String stateKey = decode(roomStateEvent.group(3));
                if (!"m.room.encryption".equals(eventType) || (stateKey != null && !stateKey.isBlank())) throw new MatrixProtocolException("M_UNSUPPORTED", "This Matrix room state write is unsupported.");
                Map<String, Object> content = requestBodyMap(request);
                Object rawAlgorithm = content.get("algorithm");
                if (!(rawAlgorithm instanceof String algorithm)) throw new MatrixProtocolException("M_BAD_JSON", "Matrix room encryption algorithm is required.");
                chatDomainFacadeService.enableEncryption(conversationId, algorithm, jwt);
                return matrixOk(matrixProtocolCoreService.sendResponse("state-encryption-" + conversationId));
            }
            if ("GET".equals(method) && roomStateEvent.matches()) return matrixOk(roomStateEvent(jwt, decodeRoomId(roomStateEvent.group(1)), decode(roomStateEvent.group(2)), decode(roomStateEvent.group(3))));
            Matcher joinedMembers = ROOM_JOINED_MEMBERS_PATH.matcher(path);
            if ("GET".equals(method) && joinedMembers.matches()) return matrixOk(joinedMembers(jwt, decodeRoomId(joinedMembers.group(1))));
            Matcher receipt = ROOM_RECEIPT_PATH.matcher(path);
            if ("POST".equals(method) && receipt.matches()) { chatDomainFacadeService.markRead(decodeRoomId(receipt.group(1)), matrixProtocolCoreService.decodeEventId(decode(receipt.group(2))), jwt); return matrixOk(Map.of()); }
            Matcher typing = ROOM_TYPING_PATH.matcher(path);
            if ("PUT".equals(method) && typing.matches()) { requireCurrentUser(identity, decode(typing.group(2))); Map<String, Object> body = requestBodyMap(request); Object rawTyping = body.get("typing"); if (!(rawTyping instanceof Boolean typingValue)) throw new MatrixProtocolException("M_BAD_JSON", "Matrix typing state is invalid."); int timeout = body.get("timeout") instanceof Number value ? value.intValue() : 30_000; chatDomainFacadeService.setTyping(decodeRoomId(typing.group(1)), typingValue, timeout, jwt); return matrixOk(Map.of()); }
            Matcher profile = PROFILE_PATH.matcher(path);
            if ("GET".equals(method) && profile.matches()) return matrixOk(profile(jwt, identity, decode(profile.group(1))));
            Matcher send = SEND_PATH.matcher(path);
            if (List.of("POST", "PUT").contains(method) && send.matches()) return matrixOk(sendEvent(jwt, identity, method, decodeRoomId(send.group(1)), decode(send.group(2)), decode(send.group(3)), requestBody(request)));
            Matcher redact = REDACT_PATH.matcher(path);
            if (List.of("POST", "PUT").contains(method) && redact.matches()) return matrixOk(redactEvent(jwt, decodeRoomId(redact.group(1)), matrixProtocolCoreService.decodeEventId(decode(redact.group(2))), decode(redact.group(3))));
            return matrixError(HttpStatus.NOT_FOUND, "M_NOT_FOUND", "This Matrix Client-Server projection route is not implemented by the current Weave Chat facade profile.");
        } catch (MatrixProtocolException exception) { return matrixError(matrixStatus(exception.errcode()), exception.errcode(), exception.getMessage()); }
        catch (ApiErrorException exception) { return matrixError(exception.status(), matrixErrcode(exception), exception.getMessage()); }
        catch (ChatAccessDeniedException exception) { return matrixError(HttpStatus.FORBIDDEN, "M_FORBIDDEN", exception.getMessage()); }
        catch (ChatProviderUnavailableException exception) { if (exception.throttled()) return matrixThrottled(exception.retryAfterMilliseconds(Instant.now())); return matrixError(HttpStatus.SERVICE_UNAVAILABLE, supportSafeProviderErrcode(exception), "Weave Chat is temporarily unavailable."); }
        catch (IllegalArgumentException exception) { return matrixError(HttpStatus.BAD_REQUEST, "M_INVALID_PARAM", "The Matrix request parameter is invalid."); }
        catch (IllegalStateException exception) { return matrixError(HttpStatus.SERVICE_UNAVAILABLE, "M_UNAVAILABLE", "Weave Chat is not available."); }
    }

    private String supportSafeProviderErrcode(ChatProviderUnavailableException exception) { String prefix = "chat-conversation-mapping-degraded-"; String code = exception.supportSafeCode(); if (!code.startsWith(prefix)) return "M_UNAVAILABLE"; String reason = code.substring(prefix.length()); if (!reason.matches("[a-z0-9-]{2,55}")) return "M_WEAVE_CHAT_DEGRADED_UNKNOWN"; return "M_WEAVE_CHAT_DEGRADED_" + reason.toUpperCase(Locale.ROOT).replace('-', '_'); }
    private Map<String, Object> loginFlows() { return Map.of("flows", List.of(), "weaveOidcGatekeeper", true, "passwordLoginSupported", false); }

    private String selectedSpaceRef(HttpServletRequest request) {
        List<String> values = Collections.list(request.getHeaders(SPACE_REF_HEADER));
        if (values.isEmpty()) return null;
        if (values.size() != 1 || !values.getFirst().matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,254}")) {
            throw new MatrixProtocolException("M_INVALID_PARAM", "The selected Space reference is invalid.");
        }
        return values.getFirst();
    }

    private Map<String, Object> sync(Jwt jwt, MatrixFacadeClientStateService.MatrixIdentity identity, String since) {
        matrixProtocolCoreService.validateSyncToken(since);
        String decodedCursor = matrixProtocolCoreService.decodeSyncCursor(since);
        MatrixE2eeStateService.E2eeSyncCursor cryptoCursor = matrixE2eeStateService.cryptoCursor(decodedCursor);
        String previousChatCursor = matrixE2eeStateService.chatCursor(decodedCursor);
        // Capture the high water before reading any timeline. A concurrent commit after
        // this point remains reachable from the returned token on the next poll.
        ChatDomainFacadeService.ChatSyncDelta delta = since == null ? null
                : chatDomainFacadeService.syncDelta(jwt, previousChatCursor, 100);
        String nextChatCursor = delta == null ? chatDomainFacadeService.syncCursor(jwt) : delta.cursor();
        ChatConversations conversations = chatDomainFacadeService.conversations(jwt);
        List<MatrixProtocolCodec.CanonicalConversation> projection = conversations.conversations().stream()
                .map(conversation -> {
                    if (delta == null) {
                        ChatDomainFacadeService.ChatTimelineWindow window = chatDomainFacadeService
                                .timelineWindow(conversation.conversationId(), jwt, 100);
                        return projectConversation(conversation, window.timeline(), window.limited(), window.beforeCursor());
                    }
                    return projectConversation(conversation, new ChatTimeline(conversation.conversationId(),
                            delta.events().getOrDefault(conversation.conversationId(), List.of())));
                })
                .toList();
        MatrixProtocolCodec.MatrixSyncCrypto crypto = matrixE2eeStateService.sync(identity, cryptoCursor.toDeviceSequence(), cryptoCursor.deviceListSequence(), sharedEncryptedRoomUsers(identity, projection));
        List<String> joinedIds = projection.stream().map(MatrixProtocolCodec.CanonicalConversation::conversationId).toList();
        List<String> leftRooms = delta == null ? List.of() : delta.leftRooms().stream()
                .filter(room -> !joinedIds.contains(room)).toList();
        List<MatrixProtocolCodec.CanonicalConversation> withInvitations = new ArrayList<>(projection);
        chatDomainFacadeService.invitations(jwt).stream()
                .map(invitation -> new MatrixProtocolCodec.CanonicalConversation(
                        invitation.conversationId(), invitation.title(),
                        invitation.updatedAt() == null ? 0 : invitation.updatedAt().toEpochMilli(), 0,
                        invitation.encryptionState().encrypted() ? invitation.encryptionState().mode() : null,
                        List.of(new MatrixProtocolCodec.CanonicalMembership(invitation.membership().memberRef(), "invited", null)),
                        List.of(), false, null, invitation.inviterRef().value())).forEach(withInvitations::add);
        return matrixProtocolCoreService.sync(identity.actorRef().value(), matrixE2eeStateService.combinedCursor(nextChatCursor, crypto), since, withMemberDisplayNames(jwt, withInvitations), directAccountData(identity, conversations), crypto, leftRooms);
    }

    private Map<String, Map<String, Object>> directAccountData(
            MatrixFacadeClientStateService.MatrixIdentity identity, ChatConversations conversations) {
        Map<String, Map<String, Object>> accountData = new java.util.LinkedHashMap<>(
                matrixE2eeStateService.accountData(identity));
        Map<String, Object> direct = new java.util.LinkedHashMap<>(accountData.getOrDefault("m.direct", Map.of()));
        for (ChatConversation conversation : conversations.conversations()) {
            if (!"direct".equals(conversation.kind())) continue;
            var members = conversation.memberships();
            if (members.size() != 2 || members.stream().filter(member ->
                    identity.actorRef().value().equals(member.memberRef()) && "joined".equals(member.state())).count() != 1
                    || members.stream().map(member -> member.memberRef()).distinct().count() != 2) {
                continue;
            }
            String peer = members.stream().map(member -> member.memberRef())
                    .filter(member -> !identity.actorRef().value().equals(member)).findFirst().orElseThrow();
            String peerId = matrixProtocolCoreService.userId(peer);
            var rooms = new java.util.LinkedHashSet<String>();
            if (direct.get(peerId) instanceof List<?> storedRooms) {
                storedRooms.stream().filter(String.class::isInstance).map(String.class::cast).forEach(rooms::add);
            }
            rooms.add(matrixProtocolCoreService.roomId(conversation.conversationId()));
            direct.put(peerId, List.copyOf(rooms));
        }
        // Standard Matrix account data retains personal hints and adds admitted product DMs.
        // Neither personal nor derived classification grants membership.
        accountData.put("m.direct", Map.copyOf(direct));
        return Map.copyOf(accountData);
    }

    private Set<String> sharedEncryptedRoomUsers(MatrixFacadeClientStateService.MatrixIdentity identity, List<MatrixProtocolCodec.CanonicalConversation> conversations) {
        return conversations.stream().filter(conversation -> conversation.encryptionAlgorithm() != null).flatMap(conversation -> conversation.memberships().stream()).filter(membership -> "join".equals(matrixMembershipState(membership.state()))).map(membership -> matrixProtocolCoreService.userId(membership.memberRef())).filter(userId -> !userId.equals(identity.userId())).collect(Collectors.toUnmodifiableSet());
    }

    private Map<String, Object> joinedRooms(Jwt jwt) { ChatConversations conversations = chatDomainFacadeService.conversations(jwt); List<MatrixProtocolCodec.CanonicalConversation> projection = conversations.conversations().stream().map(conversation -> projectConversation(conversation, null)).toList(); return matrixProtocolCoreService.joinedRooms(projection); }
    private Map<String, Object> roomMessages(Jwt jwt, String conversationId, String from, String direction, int limit) {
        if (direction != null && !"b".equals(direction)) {
            throw new MatrixProtocolException("M_UNSUPPORTED", "Forward Matrix history pagination is outside the current profile.");
        }
        String decoded = from == null ? null : matrixProtocolCoreService.decodeSyncCursor(from);
        if (decoded != null && !decoded.matches("timeline-revision-[0-9]+")) {
            throw new MatrixProtocolException("M_INVALID_PARAM", "Matrix history cursor is invalid.");
        }
        ChatConversation conversation = chatDomainFacadeService.conversation(conversationId, jwt);
        ChatTimelinePage page = chatDomainFacadeService.timelinePage(
                conversationId, jwt, decoded == null ? null : new ChatCursor(decoded), limit);
        return matrixProtocolCoreService.messages(page.nextBackwardCursor(), from,
                projectConversation(conversation, page.timeline(), page.hasEarlier(), page.nextBackwardCursor()));
    }

    private Map<String, Object> sendEvent(Jwt jwt, MatrixFacadeClientStateService.MatrixIdentity identity, String method, String conversationId, String eventType, String transactionId, String requestJson) {
        try { new ChatTransactionId(transactionId); }
        catch (IllegalArgumentException invalid) { throw new MatrixProtocolException("M_INVALID_PARAM", "The Matrix transaction ID is invalid."); }
        MatrixProtocolCodec.ParsedEventContent parsed = matrixProtocolCoreService.parseEvent(eventType, requestJson);
        String scope = String.join("\u0000", identity.tenantId(), identity.identityIssuer(),
                identity.actorRef().value(), identity.deviceId(), method, conversationId,
                eventType, transactionId);
        ChatTimelineEvent event = chatDomainFacadeService.sendEvent(conversationId,
                "matrix-send-" + sha256(scope), canonicalContent(parsed), jwt);
        return matrixProtocolCoreService.sendResponse(event.eventId());
    }

    private Map<String, Object> redactEvent(Jwt jwt, String conversationId, String eventId, String transactionId) { ChatRedactionReceipt receipt = chatDomainFacadeService.redactEvent(conversationId, eventId, transactionId, jwt); return matrixProtocolCoreService.sendResponse(receipt.redactionEventId()); }
    private Map<String, Object> searchParticipants(Jwt jwt,
            MatrixFacadeClientStateService.MatrixIdentity identity, Map<String, Object> body,
            String selectedSpaceRef) {
        if (selectedSpaceRef == null) chatDomainFacadeService.requireParticipantDiscovery(jwt);
        else chatDomainFacadeService.requireParticipantDiscovery(jwt, selectedSpaceRef);
        Object rawTerm = body.get("search_term");
        Object rawLimit = body.getOrDefault("limit", 10);
        if (!(rawTerm instanceof String term) || term.isBlank() || term.length() > 200
                || !(rawLimit instanceof Number number) || number.intValue() < 1 || number.intValue() > 100
                || number.doubleValue() != number.intValue()) {
            throw new MatrixProtocolException("M_BAD_JSON", "A bounded search term and limit are required.");
        }
        int limit = number.intValue();
        String query = term.trim().toLowerCase(Locale.ROOT);
        List<com.massimotter.weave.backend.chat.port.MatrixFacadeClientStateStore.IdentityProjection> admitted = new ArrayList<>();
        boolean scanLimited = true;
        String after = "";
        for (int pageNumber = 0; pageNumber < 10; pageNumber++) {
            var page = matrixClientStateService.registeredIdentities(identity, after, 100);
            if (page == null || page.size() > 100) throw new MatrixProtocolException("M_UNKNOWN", "Directory unavailable.");
            for (var candidate : page) {
                if (!identity.tenantId().equals(candidate.tenantId())
                        || !identity.identityIssuer().equals(candidate.identityIssuer())
                        || candidate.matrixUserId().compareTo(after) <= 0) {
                    throw new MatrixProtocolException("M_UNKNOWN", "Directory unavailable.");
                }
                after = candidate.matrixUserId();
                var resolved = new ChatResolvedIdentity(new com.massimotter.weave.backend.chat.domain.ChatIdentityRef(
                        candidate.tenantId(), candidate.identityIssuer(),
                        new com.massimotter.weave.backend.chat.domain.ChatActorRef(candidate.actorRef())),
                        candidate.authorizationPrincipalRef());
                if (!identity.userId().equals(candidate.matrixUserId())
                        && candidate.actorRef().startsWith("user:")
                        && (selectedSpaceRef == null
                            ? chatDomainFacadeService.canDiscoverParticipant(jwt, resolved)
                            : chatDomainFacadeService.canDiscoverParticipant(jwt, resolved, selectedSpaceRef))) {
                    admitted.add(candidate);
                }
            }
            if (page.size() < 100) { scanLimited = false; break; }
        }
        if (admitted.isEmpty()) return Map.of("results", List.of(), "limited", scanLimited);
        // Hydrate only admitted members, using the existing canonical profile/identity query.
        // No email, provider endpoint, role or raw identity is projected to Matrix clients.
        var names = memberDisplayNames.displayNames(jwt, admitted.stream()
                .map(candidate -> candidate.actorRef().substring("user:".length())).distinct().toList());
        List<Map<String, Object>> results = new ArrayList<>();
        for (var candidate : admitted) {
            String name = names.get(candidate.actorRef().substring("user:".length()));
            if (name != null && (name.toLowerCase(Locale.ROOT).contains(query)
                    || candidate.matrixUserId().toLowerCase(Locale.ROOT).contains(query))) {
                if (results.size() == limit) return Map.of("results", results, "limited", true);
                results.add(Map.of("user_id", candidate.matrixUserId(), "display_name", name));
            }
        }
        return Map.of("results", results, "limited", scanLimited);
    }

    private Map<String, Object> createRoom(Jwt jwt, MatrixFacadeClientStateService.MatrixIdentity identity,
            Map<String, Object> body, String idempotencyKey, String selectedSpaceRef) {
        if (body.containsKey("is_direct") && !(body.get("is_direct") instanceof Boolean)) {
            throw new MatrixProtocolException("M_BAD_JSON", "Direct conversation intent is invalid.");
        }
        String title = firstText(body.get("name"), body.get("room_alias_name"), "Conversation");
        String kind = Boolean.TRUE.equals(body.get("is_direct")) ? "direct" : "channel";
        List<ChatResolvedIdentity> invitedIdentities = new ArrayList<>();
        Object rawInvite = body.get("invite");
        if (rawInvite instanceof List<?> invite) for (Object value : invite) {
            if (!(value instanceof String matrixUserId)) {
                throw new MatrixProtocolException("M_BAD_JSON", "Matrix invite identities are invalid.");
            }
            invitedIdentities.add(matrixClientStateService.identityForMatrixUserId(
                    matrixUserId, identity.tenantId(), identity.identityIssuer()).orElseThrow(() ->
                    new MatrixProtocolException("M_NOT_FOUND", "The invited Matrix identity is not registered with Weave.")));
        }
        String transactionId = createRoomTransactionId(identity, idempotencyKey,
                selectedSpaceRef == null ? contextId(jwt) : selectedSpaceRef);
        ChatEncryptionState initialEncryption = initialEncryption(body);
        ChatConversation conversation = selectedSpaceRef == null
                ? chatDomainFacadeService.createConversation(
                        transactionId, title, kind, invitedIdentities, initialEncryption, jwt)
                : chatDomainFacadeService.createConversation(
                        transactionId, title, kind, invitedIdentities, initialEncryption, jwt, selectedSpaceRef);
        return Map.of("room_id", matrixProtocolCoreService.roomId(conversation.conversationId()));
    }

    private List<Map<String, Object>> roomState(Jwt jwt, String conversationId) {
        ChatConversation conversation = chatDomainFacadeService.conversation(conversationId, jwt);
        var names = admittedDisplayNames(jwt, conversation.memberships().stream().map(ChatMembership::memberRef).toList());
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(Map.of("type", "m.room.name", "state_key", "", "content", Map.of("name", conversation.title())));
        if (conversation.encryptionState().encrypted()) events.add(Map.of("type", "m.room.encryption", "state_key", "", "content", Map.of("algorithm", conversation.encryptionState().mode())));
        for (ChatMembership membership : conversation.memberships()) events.add(Map.of("type", "m.room.member",
                "state_key", matrixProtocolCoreService.userId(membership.memberRef()), "content", membershipContent(membership, names)));
        return List.copyOf(events);
    }

    private Map<String, Object> roomStateEvent(Jwt jwt, String conversationId, String eventType, String stateKey) {
        ChatConversation conversation = chatDomainFacadeService.conversation(conversationId, jwt);
        if ("m.room.name".equals(eventType) && (stateKey == null || stateKey.isBlank())) return Map.of("name", conversation.title());
        if ("m.room.encryption".equals(eventType) && (stateKey == null || stateKey.isBlank()) && conversation.encryptionState().encrypted()) return Map.of("algorithm", conversation.encryptionState().mode());
        if ("m.room.member".equals(eventType)) {
            var membership = conversation.memberships().stream()
                    .filter(member -> matrixProtocolCoreService.userId(member.memberRef()).equals(stateKey)).findFirst()
                    .orElseThrow(() -> new MatrixProtocolException("M_NOT_FOUND", "Matrix room state was not found."));
            return membershipContent(membership, admittedDisplayNames(jwt, List.of(membership.memberRef())));
        }
        throw new MatrixProtocolException("M_NOT_FOUND", "Matrix room state was not found.");
    }

    private Map<String, Object> membershipContent(ChatMembership membership, Map<String, String> names) {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("membership", matrixMembershipState(membership.state()));
        String name = names.get(membership.memberRef());
        if (name != null) content.put("displayname", name);
        return Map.copyOf(content);
    }

    private Map<String, Object> joinedMembers(Jwt jwt, String conversationId) {
        ChatConversation conversation = chatDomainFacadeService.conversation(conversationId, jwt);
        var members = conversation.memberships().stream()
                .filter(member -> "join".equals(matrixMembershipState(member.state()))).toList();
        var names = admittedDisplayNames(jwt, members.stream().map(ChatMembership::memberRef).toList());
        Map<String, Object> joined = new java.util.LinkedHashMap<>();
        for (ChatMembership membership : members) {
            String name = names.get(membership.memberRef());
            joined.put(matrixProtocolCoreService.userId(membership.memberRef()),
                    name == null ? Map.of() : Map.of("display_name", name));
        }
        return Map.of("joined", Map.copyOf(joined));
    }

    private Map<String, Object> profile(Jwt jwt, MatrixFacadeClientStateService.MatrixIdentity identity, String userId) {
        chatDomainFacadeService.requireParticipantDiscovery(jwt);
        ChatResolvedIdentity resolved = matrixClientStateService.identityForMatrixUserId(userId, identity.tenantId(), identity.identityIssuer())
                .orElseThrow(() -> new MatrixProtocolException("M_NOT_FOUND", "Matrix profile was not found."));
        if (!identity.userId().equals(userId) && !chatDomainFacadeService.canDiscoverParticipant(jwt, resolved)) {
            throw new MatrixProtocolException("M_NOT_FOUND", "Matrix profile was not found.");
        }
        String name = admittedDisplayNames(jwt, List.of(resolved.actorRef().value())).get(resolved.actorRef().value());
        if (name == null) throw new MatrixProtocolException("M_NOT_FOUND", "Matrix profile was not found.");
        return Map.of("displayname", name);
    }

    /** Names decorate admitted identities; they never grant discovery, membership or access. */
    private Map<String, String> admittedDisplayNames(Jwt jwt, List<String> actorRefs) {
        var subjects = actorRefs.stream().filter(actor -> actor.startsWith("user:") && actor.length() > 5)
                .map(actor -> actor.substring(5)).distinct().toList();
        if (subjects.isEmpty()) return Map.of();
        var names = memberDisplayNames.displayNames(jwt, subjects);
        Map<String, String> byActor = new java.util.LinkedHashMap<>();
        for (String subject : subjects) {
            String name = names.get(subject);
            if (name != null && !name.isBlank()) byActor.put("user:" + subject, name);
        }
        return Map.copyOf(byActor);
    }

    private List<MatrixProtocolCodec.CanonicalConversation> withMemberDisplayNames(
            Jwt jwt, List<MatrixProtocolCodec.CanonicalConversation> conversations) {
        var names = admittedDisplayNames(jwt, conversations.stream().flatMap(room -> room.memberships().stream())
                .map(MatrixProtocolCodec.CanonicalMembership::memberRef).toList());
        return conversations.stream().map(room -> new MatrixProtocolCodec.CanonicalConversation(
                room.conversationId(), room.title(), room.updatedAtEpochMillis(), room.unreadCount(), room.encryptionAlgorithm(),
                room.memberships().stream().map(member -> new MatrixProtocolCodec.CanonicalMembership(
                        member.memberRef(), member.state(), names.get(member.memberRef()))).toList(),
                room.messages(), room.timelineLimited(), room.timelineBeforeCursor(), room.inviterRef())).toList();
    }

    private String createRoomTransactionId(MatrixFacadeClientStateService.MatrixIdentity identity, String idempotencyKey, String contextId) { if (idempotencyKey == null || idempotencyKey.isBlank()) return "create-" + java.util.UUID.randomUUID(); String normalized = idempotencyKey.trim(); if (!normalized.matches("[A-Za-z0-9._:=/-]{8,160}")) throw new MatrixProtocolException("M_INVALID_PARAM", "The Idempotency-Key is invalid."); String scope = identity.tenantId() + "\n" + identity.identityIssuer() + "\n" + contextId + "\n" + identity.actorRef().value() + "\n" + normalized; return "create-" + sha256(scope); }
    private String contextId(Jwt jwt) { String value = jwt.getClaimAsString("weave_context_id"); if (value == null || value.isBlank()) value = jwt.getClaimAsString("context_id"); return value == null || value.isBlank() ? "workspace-default" : value.trim(); }
    private String sha256(String value) { try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); } }
    private ChatEventContent canonicalContent(MatrixProtocolCodec.ParsedEventContent parsed) { ChatEventKind kind = switch (parsed.kind()) { case "message" -> ChatEventKind.MESSAGE; case "reaction" -> ChatEventKind.REACTION; case "encrypted" -> ChatEventKind.ENCRYPTED; default -> throw new MatrixProtocolException("M_UNSUPPORTED", "Matrix event type is unsupported."); }; ChatRelation relation = parsed.relationKind() == null ? null : new ChatRelation(parsed.relationKind(), parsed.relationTargetEventId(), parsed.replyToEventId()); return new ChatEventContent(kind, parsed.messageType(), parsed.body(), parsed.format(), parsed.formattedBody(), relation, parsed.reactionKey(), parsed.presentationExtensions(), parsed.encryptedContent() == null ? null : new ChatEncryptedEnvelope(parsed.encryptedContent())); }
    private MatrixProtocolCodec.CanonicalConversation projectConversation(ChatConversation conversation, ChatTimeline timeline) { return projectConversation(conversation, timeline, false, null); }
    private MatrixProtocolCodec.CanonicalConversation projectConversation(ChatConversation conversation, ChatTimeline timeline, boolean limited, String beforeCursor) { List<MatrixProtocolCodec.CanonicalMessage> projectedEvents = timeline == null ? List.of() : timeline.events().stream().map(this::projectEvent).toList(); return new MatrixProtocolCodec.CanonicalConversation(conversation.conversationId(), conversation.title(), conversation.updatedAt() == null ? 0 : conversation.updatedAt().toEpochMilli(), 0, conversation.encryptionState().encrypted() ? conversation.encryptionState().mode() : null, conversation.memberships().stream().map(membership -> new MatrixProtocolCodec.CanonicalMembership(membership.memberRef(), membership.state(), null)).toList(), projectedEvents, limited, beforeCursor); }
    private String matrixMembershipState(String canonicalState) { return switch (canonicalState) { case "joined", "join" -> "join"; case "invited", "invite" -> "invite"; case "left", "leave" -> "leave"; case "banned", "ban" -> "ban"; default -> throw new MatrixProtocolException("M_BAD_JSON", "Canonical Chat membership state is invalid."); }; }
    private MatrixProtocolCodec.CanonicalMessage projectEvent(ChatTimelineEvent event) { ChatRelation relation = event.content().relation(); return new MatrixProtocolCodec.CanonicalMessage(event.eventId(), event.senderRef(), event.occurredAt().toEpochMilli(), event.content().kind().value(), event.content().messageType(), event.content().body(), event.content().format(), event.content().formattedBody(), relation == null ? null : relation.kind(), relation == null ? null : relation.targetEventId(), relation == null ? null : relation.replyToEventId(), event.content().reactionKey(), event.content().presentationExtensions(), event.deliveryState(), event.content().encryptedEnvelope() == null ? null : event.content().encryptedEnvelope().content(), event.redacted()); }
    private ChatEncryptionState initialEncryption(Map<String, Object> body) { Object rawInitialState = body.get("initial_state"); if (rawInitialState == null) return ChatEncryptionState.unencrypted(); if (!(rawInitialState instanceof List<?> initialState)) throw new MatrixProtocolException("M_BAD_JSON", "Matrix initial room state is invalid."); int encryptionEvents = 0; for (Object rawEvent : initialState) { if (!(rawEvent instanceof Map<?, ?> event)) throw new MatrixProtocolException("M_BAD_JSON", "Matrix initial room state is invalid."); if (!"m.room.encryption".equals(event.get("type"))) continue; encryptionEvents++; if (encryptionEvents > 1) throw new MatrixProtocolException("M_BAD_JSON", "Matrix room encryption state is duplicated."); if (!(event.get("state_key") instanceof String stateKey) || !stateKey.isEmpty()) throw new MatrixProtocolException("M_BAD_JSON", "Matrix room encryption state key is invalid."); if (!(event.get("content") instanceof Map<?, ?> content) || !(content.get("algorithm") instanceof String algorithm)) throw new MatrixProtocolException("M_BAD_JSON", "Matrix room encryption algorithm is required."); if (!ChatEncryptedEnvelope.MEGOLM_V1.equals(algorithm)) throw new MatrixProtocolException("M_UNSUPPORTED", "Matrix room encryption algorithm is unsupported."); } return encryptionEvents == 1 ? ChatEncryptionState.matrixMegolm() : ChatEncryptionState.unencrypted(); }
    private String decodeRoomId(String matrixRoomId) { return matrixProtocolCoreService.decodeRoomId(decode(matrixRoomId)); }
    private String decode(String value) { return value == null ? null : UriUtils.decode(value, StandardCharsets.UTF_8); }
    private int boundedLimit(String rawLimit) { if (rawLimit == null || rawLimit.isBlank()) return 100; return Math.max(1, Math.min(Integer.parseInt(rawLimit), 100)); }
    private String requestPath(HttpServletRequest request) { String requestPath = request.getRequestURI(); String contextPath = request.getContextPath(); if (contextPath != null && !contextPath.isBlank() && requestPath.startsWith(contextPath)) requestPath = requestPath.substring(contextPath.length()); return requestPath; }
    private String requestBody(HttpServletRequest request) { try { return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8); } catch (IOException exception) { throw new MatrixProtocolException("M_BAD_JSON", "Matrix request body could not be read."); } }
    private Map<String, Object> requestBodyMap(HttpServletRequest request) { return matrixProtocolCoreService.parseObject(requestBody(request)); }
    private Object requestBodyValue(HttpServletRequest request) { return matrixProtocolCoreService.parseObject(requestBody(request)); }
    private void requireCurrentUser(MatrixFacadeClientStateService.MatrixIdentity identity, String requestedUserId) { if (!identity.userId().equals(requestedUserId)) throw new MatrixProtocolException("M_FORBIDDEN", "Matrix user-scoped state belongs to another user."); }
    private String firstText(Object first, Object second, String fallback) { for (Object value : List.of(first == null ? "" : first, second == null ? "" : second)) if (value instanceof String text && !text.isBlank()) return text.trim(); return fallback; }
    private boolean isSync(String path) { return path.equals("/_matrix/client/v3/sync") || path.equals("/_matrix/client/r0/sync"); }
    private boolean isVersions(String path) { return path.equals("/_matrix/client/versions") || path.equals("/_matrix/client/v3/versions"); }
    private boolean isJoinedRooms(String path) { return path.equals("/_matrix/client/v3/joined_rooms") || path.equals("/_matrix/client/r0/joined_rooms"); }
    private boolean isWhoami(String path) { return path.equals("/_matrix/client/v3/account/whoami") || path.equals("/_matrix/client/r0/account/whoami"); }
    private boolean isPushRules(String path) { return path.matches("^/_matrix/client/(?:v3|r0)/pushrules/?$"); }
    private boolean isCreateRoom(String path) { return path.matches("^/_matrix/client/(?:v3|r0)/createRoom$"); }
    private boolean isKeysUpload(String path) { return path.matches("^/_matrix/client/(?:v3|r0)/keys/upload$"); }
    private boolean isKeysQuery(String path) { return path.matches("^/_matrix/client/(?:v3|r0)/keys/query$"); }
    private boolean isKeysClaim(String path) { return path.matches("^/_matrix/client/(?:v3|r0)/keys/claim$"); }
    private boolean isKeysChanges(String path) { return path.matches("^/_matrix/client/(?:v3|r0)/keys/changes$"); }
    private boolean isDeviceSigningUpload(String path) { return path.matches("^/_matrix/client/(?:v3|r0)/keys/device_signing/upload$"); }
    private boolean isSignaturesUpload(String path) { return path.matches("^/_matrix/client/(?:v3|r0)/keys/signatures/upload$"); }
    private boolean isLogin(String path) { return path.matches("^/_matrix/client/(?:v3|r0)/login$"); }
    private boolean isLogout(String path) { return path.matches("^/_matrix/client/(?:v3|r0)/logout$"); }
    private ResponseEntity<Map<String, Object>> matrixOk(Map<String, Object> body) { return ResponseEntity.ok().header("X-Weave-Projection", "matrix-client-server").header("X-Weave-Matrix-Core", "rust-ruma-jni").body(body); }
    private ResponseEntity<List<Map<String, Object>>> matrixOkList(List<Map<String, Object>> body) { return ResponseEntity.ok().header("X-Weave-Projection", "matrix-client-server").header("X-Weave-Matrix-Core", "rust-ruma-jni").body(body); }
    private String matrixErrcode(ApiErrorException exception) { return switch (exception.status()) { case UNAUTHORIZED -> "M_MISSING_TOKEN"; case FORBIDDEN -> "M_FORBIDDEN"; case NOT_FOUND -> "M_NOT_FOUND"; case BAD_REQUEST -> "M_BAD_JSON"; default -> "M_WEAVE_CHAT_FACADE_ERROR"; }; }
    private HttpStatus matrixStatus(String errcode) { return switch (errcode) { case "M_MISSING_TOKEN", "M_UNKNOWN_TOKEN", "M_WEAVE_DEVICE_RECOVERY_REQUIRED" -> HttpStatus.UNAUTHORIZED; case "M_FORBIDDEN" -> HttpStatus.FORBIDDEN; case "M_NOT_FOUND" -> HttpStatus.NOT_FOUND; case "M_LIMIT_EXCEEDED" -> HttpStatus.TOO_MANY_REQUESTS; case "M_BAD_JSON", "M_INVALID_PARAM", "M_UNSUPPORTED" -> HttpStatus.BAD_REQUEST; default -> HttpStatus.SERVICE_UNAVAILABLE; }; }
    private ResponseEntity<Map<String, Object>> matrixError(HttpStatus status, String errcode, String error) { Map<String, Object> body; try { body = matrixProtocolCoreService.error(errcode, error); } catch (MatrixProtocolException exception) { body = Map.of("errcode", "M_WEAVE_MATRIX_CORE_ERROR", "error", "The Rust/Ruma Matrix protocol core rejected the error projection.", "supportSafe", true); } return ResponseEntity.status(status).header("X-Weave-Projection", "matrix-client-server").header("X-Weave-Matrix-Core", "rust-ruma-jni").body(body); }
    private ResponseEntity<Map<String, Object>> matrixThrottled(long retryAfterMilliseconds) { long bounded = Math.max(1_000, Math.min(retryAfterMilliseconds, 3_600_000)); Map<String, Object> body = new java.util.LinkedHashMap<>(matrixProtocolCoreService.error("M_LIMIT_EXCEEDED", "Weave Chat is temporarily throttled.")); body.put("retry_after_ms", bounded); return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", Long.toString(Math.max(1, (bounded + 999) / 1000))).header("X-Weave-Projection", "matrix-client-server").header("X-Weave-Matrix-Core", "rust-ruma-jni").body(Map.copyOf(body)); }
}

package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.calls.domain.CallRole;
import com.massimotter.weave.backend.calls.domain.JoinGrant;
import com.massimotter.weave.backend.calls.livekit.LiveKitJoinGrantService;
import com.massimotter.weave.backend.config.LiveKitMeetingsProviderProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.calls.CallCreateRequest;
import com.massimotter.weave.backend.model.calls.CallJoinRequest;
import com.massimotter.weave.backend.model.calls.CallJoinResponse;
import com.massimotter.weave.backend.model.calls.CallLeaveResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.model.calls.CallNativeBoundaryOptionResponse;
import com.massimotter.weave.backend.model.calls.CallNativeBoundarySetupResponse;
import com.massimotter.weave.backend.model.calls.CallResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class CallsFacadeService {

    private final LiveKitMeetingsProviderProperties liveKitProperties;
    private final Clock clock;
    private final Map<String, CallState> calls = new ConcurrentHashMap<>();

    @Autowired
    public CallsFacadeService(ObjectProvider<LiveKitMeetingsProviderProperties> liveKitPropertiesProvider) {
        this(liveKitPropertiesProvider.getIfAvailable(), Clock.systemUTC());
    }

    CallsFacadeService(LiveKitMeetingsProviderProperties liveKitProperties, Clock clock) {
        this.liveKitProperties = liveKitProperties == null
                ? new LiveKitMeetingsProviderProperties(false, "", "", "", "")
                : liveKitProperties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public CallResponse createCall(CallCreateRequest request) {
        Instant now = clock.instant();
        String id = "call_" + UUID.randomUUID();
        CallState state = new CallState(
                id,
                "weave-call-" + id,
                clean(request == null ? null : request.spaceId(), "workspace-default"),
                clean(request == null ? null : request.title(), "Weave call"),
                safeList(request == null ? null : request.linkedCalendarRefs()),
                safeList(request == null ? null : request.linkedChatRefs()),
                safeList(request == null ? null : request.linkedFileRefs()),
                safeList(request == null ? null : request.linkedDecisionRefs()),
                false,
                now);
        calls.put(id, state);
        return response(state);
    }

    public CallResponse getCall(String callId) {
        return response(existingCall(callId));
    }

    public CallJoinResponse joinCall(String callId, CallJoinRequest request, Jwt jwt) {
        CallState state = existingCall(callId);
        if (state.ended()) {
            throw new ApiErrorException(
                    HttpStatus.GONE,
                    "call-ended",
                    "This Weave call has ended.",
                    Map.of("module", "calls", "operation", "join-call"));
        }
        if (!liveKitProperties.configured() || !liveKitProperties.enabled()) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "calls-media-provider-not-configured",
                    "Calls join grants are blocked until the LiveKit media provider is configured.",
                    Map.of(
                            "module", "calls",
                            "operation", "join-call",
                            "mediaProvider", "livekit",
                            "tokenReturned", false,
                            "providerSecretReturned", false));
        }
        CallRole role = role(request == null ? null : request.role());
        JoinGrant grant = liveKitGrantService().issueGrant(state.id(), state.roomRef(), personRef(jwt), role);
        return new CallJoinResponse(
                state.id(),
                state.roomRef(),
                "livekit",
                grant.mediaUrl(),
                grant.token(),
                grant.expiresAt());
    }

    public CallLeaveResponse leaveCall(String callId, Jwt jwt) {
        CallState state = existingCall(callId);
        return new CallLeaveResponse(
                state.id(),
                true,
                "calls-leave:" + state.id() + ":" + Math.abs(personRef(jwt).hashCode()),
                clock.instant());
    }

    public CallResponse endCall(String callId) {
        CallState state = existingCall(callId);
        CallState ended = new CallState(
                state.id(),
                state.roomRef(),
                state.spaceId(),
                state.title(),
                state.linkedCalendarRefs(),
                state.linkedChatRefs(),
                state.linkedFileRefs(),
                state.linkedDecisionRefs(),
                true,
                clock.instant());
        calls.put(callId, ended);
        return response(ended);
    }

    public CallNativeBoundarySetupResponse nativeBoundarySetup(WorkspaceCapabilityStatusResponse readiness) {
        return new CallNativeBoundarySetupResponse(
                readiness,
                true,
                false,
                false,
                "/api/calls",
                "/api/calls/meetings/{meetingId}/join-grants",
                "Weave meeting invitations and short-lived join grants drive native incoming-call state.",
                "Weave owns readiness, policy, consent, and grants; the media transport receives only a scoped join grant.",
                List.of(
                        new CallNativeBoundaryOptionResponse(
                                "ios",
                                "CallKitPushKit",
                                "pigeon-or-platform-channel",
                                false,
                                "boundary_contract_ready_entitlement_and_push_flow_blocked",
                                "open-weave-calls-native-setup",
                                List.of(
                                        "ios-callkit-reporting",
                                        "ios-voip-push-routing",
                                        "webrtc-audio-session-activation",
                                        "server-issued-meeting-invitation-grant",
                                        "physical-ios-call-evidence"),
                                List.of(
                                        "Native call UI is driven by Weave meeting invitations, not media-provider pushes.",
                                        "CallKit reports calls; media and signaling stay behind Weave grants.",
                                        "Push entitlement, audio-session behavior, and device evidence remain gated.")),
                        new CallNativeBoundaryOptionResponse(
                                "android",
                                "TelecomConnectionService",
                                "pigeon-or-platform-channel",
                                false,
                                "boundary_contract_ready_connection_service_blocked",
                                "open-weave-calls-native-setup",
                                List.of(
                                        "android-telecom-connection-service",
                                        "android-audio-route-policy",
                                        "server-issued-meeting-invitation-grant",
                                        "android-instrumentation-call-evidence"),
                                List.of(
                                        "Android native call UI uses Telecom/ConnectionService concepts where supported.",
                                        "Flutter may hand off setup/status and active call state only.",
                                        "Provider-switch readiness remains admin/support-only and does not change member call copy."))),
                List.of(
                        "GET /api/calls/native-boundary-setup",
                        "GET /api/workspace/capabilities",
                        "server calls grant TTL/revocation tests",
                        "native bridge contract tests"),
                List.of(
                        "Provider-neutral meetings facade endpoints are not fully exposed yet.",
                        "Native CallKit/PushKit and Telecom/ConnectionService implementations are not wired yet.",
                        "Physical-device camera, microphone, audio-route, incoming-call, and revoke evidence is still required.",
                        "Recording, captions, transcription, and retention remain separately gated by consent/audit evidence."));
    }

    private CallState existingCall(String callId) {
        String id = clean(callId, "");
        CallState state = calls.get(id);
        if (state == null) {
            throw new ApiErrorException(
                    HttpStatus.NOT_FOUND,
                    "call-not-found",
                    "The requested Weave call was not found.",
                    Map.of("module", "calls", "operation", "read-call"));
        }
        return state;
    }

    private CallResponse response(CallState state) {
        return new CallResponse(
                state.id(),
                state.roomRef(),
                "livekit",
                liveKitProperties.enabled() && liveKitProperties.configured() && !state.ended(),
                state.ended(),
                state.title(),
                state.spaceId(),
                state.linkedCalendarRefs(),
                state.linkedChatRefs(),
                state.linkedFileRefs(),
                state.linkedDecisionRefs(),
                state.updatedAt());
    }

    private LiveKitJoinGrantService liveKitGrantService() {
        return new LiveKitJoinGrantService(
                liveKitProperties.apiKey(),
                liveKitProperties.apiSecret(),
                liveKitProperties.url(),
                Duration.ofMinutes(10),
                clock);
    }

    private String personRef(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return "user:unknown";
        }
        return "user:" + jwt.getSubject();
    }

    private CallRole role(String requestedRole) {
        String normalized = clean(requestedRole, "participant").toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return CallRole.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return CallRole.PARTICIPANT;
        }
    }

    private String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private record CallState(
            String id,
            String roomRef,
            String spaceId,
            String title,
            List<String> linkedCalendarRefs,
            List<String> linkedChatRefs,
            List<String> linkedFileRefs,
            List<String> linkedDecisionRefs,
            boolean ended,
            Instant updatedAt) {
    }
}

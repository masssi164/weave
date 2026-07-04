package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.model.calls.CallNativeBoundaryOptionResponse;
import com.massimotter.weave.backend.model.calls.CallNativeBoundarySetupResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallsFacadeService {

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
}

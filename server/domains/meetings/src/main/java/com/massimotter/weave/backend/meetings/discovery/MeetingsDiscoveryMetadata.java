package com.massimotter.weave.backend.meetings.discovery;

import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainRegistry;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryContract;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Concrete discovery declarations owned by meetings; shared policy contains no feature catalog. */
public final class MeetingsDiscoveryMetadata {
    private MeetingsDiscoveryMetadata() {}

    public static ProviderCategoryFragment meetingsCallsCategory() {
        return new ProviderCategoryFragment("meetings", 4, "meetings-calls", "meetings/calls",
                null, true, "Meetings and calls are available only when the configured media provider is ready behind the backend token facade.",
                ProviderRealityLevel.CONFIGURED, Set.of(ProviderModule.MEETINGS),
                new ProviderCategoryContract(
                    List.of("meetings.join", "meetings.host", "meetings.recording_policy"),
                    List.of("livekit"),
                    List.of("generic-webrtc-sfu", "microsoft-teams-meeting-link", "google-meet-link"),
                    List.of("Meeting", "MatrixRtcSlot", "MatrixRtcMember", "DeviceBinding", "MediaSession", "RtcAuthorization", "Recording", "Caption", "ConsentRecord"),
                    "MatrixRTC owns member signaling; Weave owns RTC authorization, consent, and artifacts; the selected SFU carries media only",
                    List.of("media E2EE", "TURN/reconnect", "recording consent and retention", "captions", "external meeting links", "device revocation"),
                    "export and delete governed artifact metadata under retention policy; active media sessions are never live-migrated",
                    "SFU replacement requires a dry-run covering Profile 0 interoperability, RTC Authorizer, media-E2EE, revocation, TURN, consent/artifact, accessibility, and rollback evidence"));
    }

    public static CanonicalDomainContribution callsDomain() {
        return new CanonicalDomainContribution("meetings", 7, CanonicalDomainRegistry.entry(
                "calls",
                "Calls & Meetings",
                "Meeting identity, MatrixRTC slot/member state, independently authorized media sessions, participants, recordings, transcripts, captions, consent, retention, and audit.",
                List.of("Meeting", "MatrixRtcSlot", "MatrixRtcMember", "DeviceBinding", "MediaSession", "RtcAuthorization", "Recording", "Caption", "ConsentRecord"),
                List.of("meetings-calls"),
                CanonicalDomainRegistry.standardCapabilities("calls"),
                Map.of(
                    "livekit", "configured",
                    "generic-webrtc-sfu", "contract_only",
                    "microsoft-teams-meeting-link", "contract_only",
                    "google-meet-link", "contract_only")));
    }
}

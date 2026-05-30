package com.massimotter.weave.backend.calls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.calls.domain.CallRole;
import com.massimotter.weave.backend.calls.domain.JoinGrant;
import com.massimotter.weave.backend.calls.domain.Meeting;
import com.massimotter.weave.backend.calls.domain.MeetingArtifacts;
import com.massimotter.weave.backend.calls.livekit.LiveKitJoinGrantService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveKitJoinGrantServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-05-30T20:00:00Z"), ZoneOffset.UTC);

    @Test
    void hostParticipantViewerAndRecorderGrantsCarryRoleBoundPermissionsWithoutApiSecretLeakage() throws Exception {
        LiveKitJoinGrantService service = new LiveKitJoinGrantService(
                "livekit-api-key",
                "livekit-api-secret",
                "wss://media.example.test",
                Duration.ofMinutes(10),
                FIXED);

        JoinGrant host = service.issueGrant("meeting:launch", "space-launch", "person:ada", CallRole.HOST);
        JoinGrant participant = service.issueGrant("meeting:launch", "space-launch", "person:grace", CallRole.PARTICIPANT);
        JoinGrant viewer = service.issueGrant("meeting:launch", "space-launch", "person:linus", CallRole.VIEWER);
        JoinGrant recorder = service.issueGrant("meeting:launch", "space-launch", "service:recorder", CallRole.RECORDER_SERVICE);

        assertThat(host.permissions()).contains("roomAdmin", "canPublish", "canSubscribe");
        assertThat(participant.permissions()).contains("canPublish", "canSubscribe").doesNotContain("roomAdmin");
        assertThat(viewer.permissions()).containsExactly("roomJoin", "canSubscribe");
        assertThat(recorder.permissions()).contains("hidden", "recorder").doesNotContain("canPublish");
        assertThat(host.expiresAt()).isEqualTo(Instant.parse("2026-05-30T20:10:00Z"));
        assertThat(host.supportSafeDiagnostics())
                .containsEntry("provider", "livekit")
                .containsEntry("apiSecretReturned", false)
                .containsEntry("supportSafe", true);
        assertThat(host.toString()).doesNotContain("livekit-api-secret");

        var payload = OBJECT_MAPPER.readTree(new String(Base64.getUrlDecoder().decode(host.token().split("\\.")[1]), StandardCharsets.UTF_8));
        assertThat(payload.path("iss").asText()).isEqualTo("livekit-api-key");
        assertThat(payload.path("sub").asText()).isEqualTo("person:ada");
        assertThat(payload.path("video").path("room").asText()).isEqualTo("space-launch");
        assertThat(payload.path("video").path("roomAdmin").asBoolean()).isTrue();
    }

    @Test
    void tokenTtlIsBackendControlledAndBounded() {
        assertThatThrownBy(() -> new LiveKitJoinGrantService(
                "key",
                "secret",
                "wss://media.example.test",
                Duration.ofMinutes(31),
                FIXED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL");
    }

    @Test
    void meetingArtifactsRepresentGuardedRecordingTranscriptCaptionConsentAndRetention() {
        MeetingArtifacts artifacts = new MeetingArtifacts(
                List.of(new MeetingArtifacts.RecordingRef("recording:launch", MeetingArtifacts.ArtifactAvailability.GUARDED_UNAVAILABLE, null)),
                List.of(new MeetingArtifacts.TranscriptRef("transcript:launch", MeetingArtifacts.ArtifactAvailability.GUARDED_UNAVAILABLE, null)),
                List.of(new MeetingArtifacts.CaptionRef("caption:launch", MeetingArtifacts.ArtifactAvailability.DISABLED_BY_POLICY, null)),
                List.of(new MeetingArtifacts.ConsentRecord("person:ada", "recording", false, "audit:consent:1")),
                new MeetingArtifacts.RetentionPolicy("meeting-default", 30, true),
                "chat:conversation:launch");
        Meeting meeting = new Meeting(
                "meeting:launch",
                "space:launch",
                "Launch review",
                "room:launch",
                List.of("calendar:event:launch"),
                List.of("chat:conversation:launch"),
                List.of("files:folder:launch"),
                List.of("decision:go-no-go"),
                artifacts,
                FIXED.instant());

        assertThat(meeting.spaceId()).isEqualTo("space:launch");
        assertThat(meeting.linkedCalendarRefs()).containsExactly("calendar:event:launch");
        assertThat(meeting.artifacts().recordings()).singleElement().satisfies(recording ->
                assertThat(recording.availability()).isEqualTo(MeetingArtifacts.ArtifactAvailability.GUARDED_UNAVAILABLE));
        assertThat(meeting.artifacts().consentRecords()).singleElement().satisfies(consent ->
                assertThat(consent.auditRef()).isEqualTo("audit:consent:1"));
    }
}

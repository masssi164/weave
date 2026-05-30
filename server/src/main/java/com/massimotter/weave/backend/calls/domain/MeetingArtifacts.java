package com.massimotter.weave.backend.calls.domain;

import java.util.List;

public record MeetingArtifacts(
        List<RecordingRef> recordings,
        List<TranscriptRef> transcripts,
        List<CaptionRef> captions,
        List<ConsentRecord> consentRecords,
        RetentionPolicy retentionPolicy,
        String meetingChatRef) {

    public MeetingArtifacts {
        recordings = recordings == null ? List.of() : List.copyOf(recordings);
        transcripts = transcripts == null ? List.of() : List.copyOf(transcripts);
        captions = captions == null ? List.of() : List.copyOf(captions);
        consentRecords = consentRecords == null ? List.of() : List.copyOf(consentRecords);
        retentionPolicy = java.util.Objects.requireNonNull(retentionPolicy, "retentionPolicy must not be null");
    }

    public record RecordingRef(String id, ArtifactAvailability availability, String storageRef) {}
    public record TranscriptRef(String id, ArtifactAvailability availability, String evidenceRef) {}
    public record CaptionRef(String id, ArtifactAvailability availability, String evidenceRef) {}
    public record ConsentRecord(String personRef, String scope, boolean granted, String auditRef) {}
    public record RetentionPolicy(String policyKey, int retentionDays, boolean legalHoldSupported) {}

    public enum ArtifactAvailability {
        AVAILABLE,
        GUARDED_UNAVAILABLE,
        DISABLED_BY_POLICY
    }
}

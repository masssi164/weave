package com.massimotter.weave.backend.meetings.discovery;

import com.massimotter.weave.backend.admin.domain.SuiteDomainDeclaration;
import com.massimotter.weave.backend.admin.domain.SuiteDomainDefinition;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration.ProfileGrant;
import java.util.List;

/** Existing meetings capability vocabulary for shared Admin simulation and profile presentation. */
public final class MeetingsAdminMetadata {
    private MeetingsAdminMetadata() {}

    public static AdminCapabilityDeclaration capabilities() {
        return new AdminCapabilityDeclaration("meetings", 50,
                List.of("meetings.join", "meetings.host"), List.of("meetings.join", "meetings.host"), List.of(), List.of("meetings.join"),
                List.of());
    }
    public static SuiteDomainDeclaration suite() {
        return new SuiteDomainDeclaration("meetings", 2, 1,
                new SuiteDomainDefinition(
                        "calendar-meetings", "Calendar and meetings", List.of("meetings-calls"), List.of("meetings.join"), List.of("meetings.host", "meetings.recording_policy"), List.of(
                        "meeting",
                        "meeting_room",
                        "participant",
                        "join_grant",
                        "media_session",
                        "recording",
                        "transcript",
                        "caption",
                        "meeting_chat_ref",
                        "consent_record",
                        "retention_policy",
                        "space_ref",
                        "agenda_ref")),
                "workspace/team/channel calendar facade; private personal calendars blocked", List.of(
                        "Secret-free setup metadata only",
                        "Private calendar ingestion and credential profile downloads are out of scope"), "Confirm workspace/team/channel event readiness and keep private-calendar setup blocked.");
    }
}

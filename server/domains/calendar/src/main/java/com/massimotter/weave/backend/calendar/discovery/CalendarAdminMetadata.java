package com.massimotter.weave.backend.calendar.discovery;

import com.massimotter.weave.backend.admin.domain.SuiteDomainDeclaration;
import com.massimotter.weave.backend.admin.domain.SuiteDomainDefinition;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration.ProfileGrant;
import java.util.List;

/** Existing calendar capability vocabulary for shared Admin simulation and profile presentation. */
public final class CalendarAdminMetadata {
    private CalendarAdminMetadata() {}

    public static AdminCapabilityDeclaration capabilities() {
        return new AdminCapabilityDeclaration("calendar", 30,
                List.of("calendar.read", "calendar.manage_events"), List.of("calendar.read", "calendar.manage_events"), List.of(), List.of("calendar.read"),
                List.of(new ProfileGrant("workspace-admin", 30, List.of("calendar.read", "calendar.manage_events")), new ProfileGrant("member-default", 30, List.of("calendar.read"))));
    }
    public static SuiteDomainDeclaration suite() {
        return new SuiteDomainDeclaration("calendar", 2, 0,
                new SuiteDomainDefinition(
                        "calendar-meetings", "Calendar and meetings", List.of("calendar"), List.of("calendar.read"), List.of("calendar.manage_events"), List.of(
                        "calendar",
                        "event",
                        "occurrence",
                        "recurrence_rule",
                        "recurrence_exception",
                        "attendee",
                        "resource",
                        "reminder",
                        "time_zone",
                        "conference_link")),
                "workspace/team/channel calendar facade; private personal calendars blocked", List.of(
                        "Secret-free setup metadata only",
                        "Private calendar ingestion and credential profile downloads are out of scope"), "Confirm workspace/team/channel event readiness and keep private-calendar setup blocked.");
    }
}

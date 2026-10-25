package com.massimotter.weave.backend.discovery.admin;

import java.util.List;

/** Existing Admin readiness metadata; actual product behavior belongs to domain use cases. */
public record SuiteDomainDefinition(
        String domain, String label, List<String> providerCategoryKeys,
        List<String> readCapabilities, List<String> writeCapabilities, List<String> canonicalObjectKinds) {
    public SuiteDomainDefinition {
        providerCategoryKeys = List.copyOf(providerCategoryKeys);
        readCapabilities = List.copyOf(readCapabilities);
        writeCapabilities = List.copyOf(writeCapabilities);
        canonicalObjectKinds = List.copyOf(canonicalObjectKinds);
    }

    public static final SuiteDomainDefinition FILES_DOCS = new SuiteDomainDefinition(
            "files-docs",
            "Files and documents",
            List.of("files", "documents-collaboration"),
            List.of("files.read", "documents.view"),
            List.of("files.upload", "files.delete", "documents.edit", "documents.comment", "documents.collaborate"),
            List.of("drive", "folder", "file", "file_version", "blob_ref", "permission", "share_link", "lock", "trash_entry", "checksum", "document", "editor_session", "editor_provider", "coauthoring_state", "version_ref", "space_ref", "chat_attachment_ref", "task_attachment_ref"));

    public static final SuiteDomainDefinition CALENDAR_MEETINGS = new SuiteDomainDefinition(
            "calendar-meetings",
            "Calendar and meetings",
            List.of("calendar", "meetings-calls"),
            List.of("calendar.read", "meetings.join"),
            List.of("calendar.manage_events", "meetings.host", "meetings.recording_policy"),
            List.of("calendar", "event", "occurrence", "recurrence_rule", "recurrence_exception", "attendee", "resource", "reminder", "time_zone", "conference_link", "meeting", "meeting_room", "participant", "join_grant", "media_session", "recording", "transcript", "caption", "meeting_chat_ref", "consent_record", "retention_policy", "space_ref", "agenda_ref"));

    public static final SuiteDomainDefinition BOARDS_TASKS = new SuiteDomainDefinition(
            "boards-tasks",
            "Boards and tasks",
            List.of("boards-tasks"),
            List.of("boards.read"),
            List.of("boards.update_task", "boards.sync_workspace"),
            List.of("board", "list", "task", "status", "assignee", "watcher", "comment", "attachment_ref", "dependency", "label", "custom_field", "estimate", "priority", "milestone", "sprint", "workflow_rule", "decision_link", "space_ref", "chat_ref", "file_ref"));
}

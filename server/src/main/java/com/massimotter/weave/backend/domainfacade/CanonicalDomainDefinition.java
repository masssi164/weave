package com.massimotter.weave.backend.domainfacade;

import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import java.util.List;
import java.util.Set;

public enum CanonicalDomainDefinition {
    FILES_DOCS(
            "files-docs",
            "Files and documents",
            List.of("files", "documents-collaboration"),
            List.of("files.read", "documents.view"),
            List.of("files.upload", "files.delete", "documents.edit", "documents.comment", "documents.collaborate"),
            List.of("list_drives", "list_folders", "read_file_metadata", "verify_file_checksum", "upload_binary", "delete_item", "request_document_session", "request_guarded_wopi_launch", "read_space_scoped_file_refs", "link_document_to_space_context", "attach_file_ref_to_chat_or_task"),
            List.of("provider_native_share_links", "raw_storage_credentials", "editor_launch_without_backend_session"),
            List.of("drive", "folder", "file", "file_version", "blob_ref", "permission", "share_link", "lock", "trash_entry", "checksum", "document", "editor_session", "editor_provider", "coauthoring_state", "version_ref", "space_ref", "chat_attachment_ref", "task_attachment_ref")),
    CALENDAR_MEETINGS(
            "calendar-meetings",
            "Calendar and meetings",
            List.of("calendar", "meetings-calls"),
            List.of("calendar.read", "meetings.join"),
            List.of("calendar.manage_events", "meetings.host", "meetings.recording_policy"),
            List.of("list_calendars", "list_events", "read_event", "create_event", "update_event", "delete_event", "resolve_series_exception", "request_meeting_join", "read_meeting_artifacts", "bind_event_to_space_context", "link_meeting_capsule_to_chat_thread", "read_space_scoped_agenda_refs"),
            List.of("meeting_recording_control", "provider_native_recurrence_extensions", "external_meeting_admin", "client_side_media_secret"),
            List.of("calendar", "event", "occurrence", "recurrence_rule", "recurrence_exception", "attendee", "resource", "reminder", "time_zone", "conference_link", "meeting", "meeting_room", "participant", "join_grant", "media_session", "recording", "transcript", "caption", "meeting_chat_ref", "consent_record", "retention_policy", "space_ref", "agenda_ref")),
    BOARDS_TASKS(
            "boards-tasks",
            "Boards and tasks",
            List.of("boards-tasks"),
            List.of("boards.read"),
            List.of("boards.update_task", "boards.sync_workspace"),
            List.of("list_boards", "list_lists", "list_tasks", "read_status", "create_task", "move_task", "update_task_status", "link_decision", "preview_write", "report_lossy_mapping", "report_conflicts", "read_space_scoped_tasks", "link_task_to_chat_decision_or_file", "preview_space_task_write"),
            List.of("raw_project_admin", "provider_native_workflow_mutation", "cross_provider_bulk_migration_apply", "write_without_rbac_audit_dry_run"),
            List.of("board", "list", "task", "status", "assignee", "watcher", "comment", "attachment_ref", "dependency", "label", "custom_field", "estimate", "priority", "milestone", "sprint", "workflow_rule", "decision_link", "space_ref", "chat_ref", "file_ref")),
    IDENTITY_ADMIN_POLICY(
            "identity-admin-policy",
            "Identity, admin, and policy",
            List.of("identity-idm"),
            List.of("identity.sign_in", "identity.groups", "identity.roles", "policy.read"),
            List.of("policy.manage", "admin.provider_mapping.write", "admin.user_lifecycle.write"),
            List.of("resolve_subject", "read_effective_policy", "list_support_safe_admin_policy", "dry_run_policy_change"),
            List.of("direct_idm_mutation", "credential_export", "raw_claims_dump", "policy_apply_without_audit"),
            List.of("subject", "group", "role", "capability_profile", "policy_decision", "audit_ref"));

    private static final String CONTRACT_VERSION = "canonical-domain-facade-v1";

    private final String domain;
    private final String label;
    private final List<String> providerCategoryKeys;
    private final List<String> readCapabilities;
    private final List<String> writeCapabilities;
    private final List<String> adapterBoundaryOperations;
    private final List<String> unsupportedUntilAdapterMapped;
    private final List<String> canonicalObjectKinds;

    CanonicalDomainDefinition(
            String domain,
            String label,
            List<String> providerCategoryKeys,
            List<String> readCapabilities,
            List<String> writeCapabilities,
            List<String> adapterBoundaryOperations,
            List<String> unsupportedUntilAdapterMapped,
            List<String> canonicalObjectKinds) {
        this.domain = domain;
        this.label = label;
        this.providerCategoryKeys = List.copyOf(providerCategoryKeys);
        this.readCapabilities = List.copyOf(readCapabilities);
        this.writeCapabilities = List.copyOf(writeCapabilities);
        this.adapterBoundaryOperations = List.copyOf(adapterBoundaryOperations);
        this.unsupportedUntilAdapterMapped = List.copyOf(unsupportedUntilAdapterMapped);
        this.canonicalObjectKinds = List.copyOf(canonicalObjectKinds);
    }

    public CanonicalDomainContract contract() {
        return new CanonicalDomainContract(
                CONTRACT_VERSION,
                domain,
                label,
                providerCategoryKeys,
                readCapabilities,
                writeCapabilities,
                adapterBoundaryOperations,
                unsupportedUntilAdapterMapped,
                canonicalObjectKinds,
                true,
                true,
                false);
    }

    public WorkspaceCapabilityStatusResponse primaryCapability(WorkspaceCapabilitiesResponse snapshot) {
        return switch (this) {
            case FILES_DOCS -> snapshot.files();
            case CALENDAR_MEETINGS -> snapshot.calendar();
            case BOARDS_TASKS -> snapshot.boards();
            case IDENTITY_ADMIN_POLICY -> snapshot.shellAccess();
        };
    }

    public boolean knownCapability(String capability) {
        return allCapabilities().contains(capability);
    }

    public boolean knownAdapterBoundaryOperation(String operation) {
        return adapterBoundaryOperations.contains(operation);
    }

    public boolean knownObjectKind(String objectKind) {
        return canonicalObjectKinds.contains(objectKind);
    }

    public Set<String> allCapabilities() {
        return java.util.stream.Stream.concat(readCapabilities.stream(), writeCapabilities.stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public String domain() {
        return domain;
    }

    public String label() {
        return label;
    }

    public List<String> providerCategoryKeys() {
        return providerCategoryKeys;
    }

    public List<String> readCapabilities() {
        return readCapabilities;
    }

    public List<String> writeCapabilities() {
        return writeCapabilities;
    }

    public List<String> canonicalObjectKinds() {
        return canonicalObjectKinds;
    }

    public List<String> adapterBoundaryOperations() {
        return adapterBoundaryOperations;
    }

    public List<String> unsupportedUntilAdapterMapped() {
        return unsupportedUntilAdapterMapped;
    }
}

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
            List.of("list_items", "read_metadata", "upload_binary", "delete_item", "request_document_session"),
            List.of("document_edit_session", "coauthoring_presence", "provider_native_share_links"),
            List.of("drive", "folder", "file", "document", "comment", "version")),
    CALENDAR_MEETINGS(
            "calendar-meetings",
            "Calendar and meetings",
            List.of("calendar", "meetings-calls"),
            List.of("calendar.read", "meetings.join"),
            List.of("calendar.manage_events", "meetings.host", "meetings.recording_policy"),
            List.of("list_events", "read_event", "create_event", "update_event", "delete_event", "request_meeting_join"),
            List.of("meeting_recording_control", "provider_native_recurrence_extensions", "external_meeting_admin"),
            List.of("calendar", "event", "availability", "meeting", "join_capability")),
    BOARDS_TASKS(
            "boards-tasks",
            "Boards and tasks",
            List.of("boards-tasks"),
            List.of("boards.read"),
            List.of("boards.update_task", "boards.sync_workspace"),
            List.of("list_boards", "list_tasks", "create_task", "move_task", "update_task_status", "link_decision"),
            List.of("raw_project_admin", "provider_native_workflow_mutation", "cross_provider_bulk_migration_apply"),
            List.of("board", "column", "task", "task_status", "decision_link")),
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
}

package com.massimotter.weave.contract.domain;

import java.util.List;

public enum ProductDomainDefinition {
    IDENTITY("identity", "Identity", List.of("organization", "user", "group", "role", "session", "credential_ref"), List.of("identity.read"), List.of("identity.manage_members", "identity.manage_roles"), List.of("identity-facade", "idm-adapter"), List.of("identity-idm", "sso", "directory"), List.of("stable_subject_refs", "external_id_redaction", "exportable_membership_graph")),
    PEOPLE("people", "People", List.of("person", "profile", "contact", "team", "presence"), List.of("people.read"), List.of("people.update_profile", "people.manage_contacts"), List.of("people-facade", "contacts-adapter"), List.of("contacts", "directory"), List.of("portable_contact_cards", "provider_neutral_profile_refs")),
    SPACES("spaces", "Spaces", List.of("space", "membership", "channel_ref", "permission", "announcement"), List.of("spaces.read"), List.of("spaces.manage"), List.of("spaces-facade", "workspace-adapter"), List.of("workspace", "collaboration"), List.of("portable_space_refs", "membership_policy_export")),
    CHAT("chat", "Chat", List.of("conversation", "channel", "thread", "message", "reaction", "attachment_ref"), List.of("chat.read"), List.of("chat.send", "chat.moderate"), List.of("chat-facade", "message-adapter"), List.of("messaging", "matrix"), List.of("message_export", "thread_identity_stability", "attachment_ref_portability")),
    FILES("files", "Files", List.of("drive", "folder", "file", "file_version", "blob_ref", "permission", "share_link", "lock", "trash_entry", "checksum"), List.of("files.read"), List.of("files.upload", "files.delete", "files.share"), List.of("files-facade", "storage-adapter"), List.of("files", "object-storage", "nextcloud"), List.of("content_export", "metadata_export", "permission_mapping")),
    DOCUMENTS("documents", "Documents", List.of("document", "document_version", "editor_session", "comment", "template"), List.of("documents.read"), List.of("documents.edit", "documents.comment"), List.of("documents-facade", "collaboration-adapter"), List.of("documents-collaboration", "office"), List.of("document_format_export", "version_history_export", "comment_export")),
    CALENDAR("calendar", "Calendar", List.of("calendar", "event", "occurrence", "attendee", "availability", "agenda_ref"), List.of("calendar.read"), List.of("calendar.manage_events"), List.of("calendar-facade", "calendar-adapter"), List.of("calendar", "scheduling"), List.of("ics_export", "attendee_ref_mapping", "recurrence_portability")),
    CALLS("calls", "Calls", List.of("call", "meeting", "conference_link", "participant", "recording_ref", "transcript_ref"), List.of("calls.read"), List.of("calls.schedule", "calls.manage"), List.of("calls-facade", "media-adapter"), List.of("meetings-calls", "video", "livekit"), List.of("meeting_link_abstraction", "recording_consent_metadata", "transcript_export")),
    BOARDS("boards", "Boards", List.of("board", "list", "task", "status", "assignee", "comment", "attachment_ref", "decision_link"), List.of("boards.read"), List.of("boards.update_task", "boards.sync_workspace"), List.of("boards-facade", "work-tracking-adapter"), List.of("boards-tasks", "project-management", "openproject"), List.of("task_export", "status_mapping", "assignee_ref_mapping")),
    DECISIONS("decisions", "Decisions", List.of("decision", "proposal", "vote", "approval", "evidence_ref", "audit_ref"), List.of("decisions.read"), List.of("decisions.record", "decisions.approve"), List.of("decisions-facade", "evidence-adapter"), List.of("decisions-evidence", "audit"), List.of("decision_log_export", "evidence_ref_portability", "audit_chain_preservation")),
    NOTIFICATIONS("notifications", "Notifications", List.of("notification", "subscription", "preference", "delivery", "action_request"), List.of("notifications.read"), List.of("notifications.send", "notifications.manage_preferences"), List.of("notifications-facade", "delivery-adapter"), List.of("notifications", "email", "push"), List.of("preference_export", "delivery_audit_redaction", "unsubscribe_portability"));

    public static final String CONTRACT_VERSION = "product-domain-contract-v1";

    private final ProductDomainContract contract;

    ProductDomainDefinition(String key, String label, List<String> canonicalObjectKinds, List<String> readCapabilities,
            List<String> writeCapabilities, List<String> providerAdapterSlots, List<String> providerCategoryHints,
            List<String> portabilityRequirements) {
        this.contract = new ProductDomainContract(key, label, canonicalObjectKinds, readCapabilities, writeCapabilities,
                providerAdapterSlots, providerCategoryHints, portabilityRequirements);
    }

    public ProductDomainContract contract() { return contract; }
    public String key() { return contract.key(); }
}

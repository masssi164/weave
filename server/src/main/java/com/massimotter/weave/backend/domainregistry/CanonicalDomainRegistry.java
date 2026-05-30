package com.massimotter.weave.backend.domainregistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CanonicalDomainRegistry {

    public static final String REGISTRY_VERSION = "canonical-domain-registry-v1";

    public static final List<String> MEMBER_STATES = List.of(
            "available",
            "disabled_by_policy",
            "not_configured",
            "degraded",
            "unavailable",
            "coming_later");

    public static final List<String> ADMIN_STATES = List.of(
            "provider_not_configured",
            "secret_missing",
            "ready",
            "degraded",
            "dry_run_required",
            "lossy_mapping_pending",
            "apply_blocked",
            "migration_ready");

    public static final List<String> LOSS_CLASSES = List.of(
            "lossless_canonical",
            "lossless_extension",
            "archive_only",
            "lossy_with_report",
            "blocked_nonportable",
            "provider_unexportable");

    private static final List<String> STANDARD_CAPABILITIES = List.of(
            "read", "search", "export", "import", "dryRun", "apply", "write", "delete", "adminConfigure");

    private static final List<String> SOURCE_OF_TRUTH_MODES = List.of(
            "weave_owned",
            "selected_provider_owned",
            "hybrid_composite",
            "external_existing_provider",
            "archive_only");

    private static final List<String> PORTABILITY_REQUIREMENTS = List.of(
            "source_of_truth_declared",
            "export_delete_boundary_declared",
            "provider_mapping_refs_required",
            "loss_classification_required",
            "dry_run_required_before_apply",
            "support_safe_migration_evidence_required",
            "audit_ref_required");

    private static final List<String> ADAPTER_MANIFEST_REQUIREMENTS = List.of(
            "supported_domain_key",
            "supported_capabilities",
            "canonical_object_coverage",
            "source_of_truth_mode",
            "export_manifest_support",
            "import_manifest_support",
            "lossy_mapping_report_support",
            "conflict_report_support",
            "secret_ref_only",
            "support_safe_diagnostics");

    private CanonicalDomainRegistry() {
    }

    public static CanonicalDomainRegistryResponse snapshot() {
        List<CanonicalDomainRegistryEntryResponse> domains = domains();
        return new CanonicalDomainRegistryResponse(
                REGISTRY_VERSION,
                MEMBER_STATES,
                ADMIN_STATES,
                LOSS_CLASSES,
                domains,
                aliases(domains),
                true,
                false);
    }

    public static List<CanonicalDomainRegistryEntryResponse> domains() {
        return List.of(
                domain("identity", "Identity", "Authentication, identity sources, accounts, groups, roles, service principals, and deprovisioning boundaries.",
                        List.of("IdentitySource", "IdentityAccount", "Group", "Role", "ServicePrincipal", "GuestIdentity", "DeprovisioningEvent"),
                        List.of("identity-idm")),
                domain("people", "People", "Provider-neutral people/profile directory separate from authentication credentials.",
                        List.of("Person", "Profile", "ContactMethod", "AvatarRef", "OrganizationUnit", "ExternalContact"),
                        List.of()),
                domain("spaces", "Spaces", "Cross-domain work context anchor binding chat, files, calendar, boards, calls, decisions, and Weaver context.",
                        List.of("Space", "SpaceType", "SpaceMembership", "SpaceRole", "DomainBinding", "ContextPolicy", "DefaultSurface", "ContextArchive"),
                        List.of()),
                domain("chat", "Chat", "Conversation, message, membership, history, attachment, and presence facade independent from the selected chat adapter.",
                        List.of("Conversation", "Message", "Thread", "Reaction", "AttachmentRef", "Membership", "Presence", "HistoryPolicy"),
                        List.of()),
                domain("files", "Files", "Storage, file tree, versions, shares, permissions, locks, and binary portability facade.",
                        List.of("Drive", "Node", "Folder", "File", "Version", "Share", "Permission", "Lock"),
                        List.of()),
                domain("documents", "Documents", "Document collaboration sessions, comments, format fidelity, locks, and storage/editor boundaries.",
                        List.of("Document", "EditSession", "Comment", "Suggestion", "Version", "Permission", "Lock"),
                        List.of("documents-collaboration")),
                domain("calendar", "Calendar", "Workspace and shared calendar facade for events, recurrence, attendees, availability, and resources.",
                        List.of("Calendar", "Event", "Attendee", "Recurrence", "Availability", "Resource"),
                        List.of()),
                domain("boards", "Boards & Tasks", "Provider-neutral boards/tasks workflow contract for boards, lists, tasks, statuses, assignments, and workflow rules.",
                        List.of("Board", "List", "Task", "Status", "Assignee", "Watcher", "Comment", "AttachmentRef", "Dependency", "Label", "CustomField", "Estimate", "Priority", "Milestone", "Sprint", "WorkflowRule"),
                        List.of("boards-tasks")),
                domain("calls", "Calls & Meetings", "Meeting/call media-session, participant, recording, captions, consent, and join-token facade.",
                        List.of("Call", "Meeting", "Participant", "Recording", "Captions", "MediaSession", "ConsentState"),
                        List.of("meetings-calls")),
                domain("decisions", "Decisions", "Weave-owned decisions and evidence references across chat, files, boards, calls, and calendar.",
                        List.of("Decision", "SourceRef", "EvidenceRef", "Status", "AuditRef"),
                        List.of("decisions-evidence")),
                domain("notifications", "Notifications", "Provider-switch-safe notifications, action requests, approval requests, digests, and read state.",
                        List.of("Notification", "ActionRequest", "ApprovalRequest", "Digest", "ReadState"),
                        List.of()),
                domain("health", "Health", "Support-safe readiness, risk notes, support bundle references, migration runs, and secret reference state.",
                        List.of("Readiness", "RiskNote", "SupportBundleRef", "MigrationRunRef", "SecretRef", "PolicyImpact"),
                        List.of("admin-control-plane", "release-evidence", "manuals-help")),
                domain("weaver", "Weaver", "Optional governed per-user PA runtime domain, disabled by default and controlled by organization policy.",
                        List.of("RuntimeProfile", "ToolCapability", "ApprovalReceipt", "AuditEvent", "SandboxProfile"),
                        List.of()));
    }

    private static CanonicalDomainRegistryEntryResponse domain(
            String key,
            String displayName,
            String purpose,
            List<String> canonicalObjects,
            List<String> aliases) {
        return new CanonicalDomainRegistryEntryResponse(
                key,
                1,
                displayName,
                purpose,
                canonicalObjects,
                STANDARD_CAPABILITIES.stream().map(capability -> key + "." + capability).toList(),
                MEMBER_STATES,
                ADMIN_STATES,
                SOURCE_OF_TRUTH_MODES,
                PORTABILITY_REQUIREMENTS,
                ADAPTER_MANIFEST_REQUIREMENTS,
                aliases);
    }

    private static Map<String, String> aliases(List<CanonicalDomainRegistryEntryResponse> domains) {
        Map<String, String> aliases = new LinkedHashMap<>();
        for (CanonicalDomainRegistryEntryResponse domain : domains) {
            for (String alias : domain.compatibilityAliases()) {
                aliases.put(alias, domain.key());
            }
        }
        return aliases;
    }
}

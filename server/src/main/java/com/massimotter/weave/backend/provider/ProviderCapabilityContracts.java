package com.massimotter.weave.backend.provider;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProviderCapabilityContracts {

    private static final List<String> STABLE_MEMBER_IMPACT_STATES = List.of(
            "usable",
            "disabled",
            "degraded",
            "policy-blocked");

    private static final Map<String, Definition> DEFINITIONS = Map.ofEntries(
            Map.entry("identity-idm", new Definition(
                    List.of("identity.sign_in", "identity.groups", "identity.roles"),
                    List.of("keycloak-realm", "matrix-authentication-service"),
                    List.of("entra-id", "authentik", "auth0", "generic-oidc", "generic-saml", "scim-ldap"),
                    List.of("Organization", "UserAccount", "Person", "Group", "Role", "IdentitySource", "CapabilityPolicy"),
                    "authoritative IdP/provisioning source owns lifecycle and groups; Weave owns capability policy mappings",
                    List.of("email rename", "nested groups", "guest identity", "deleted/recreated account", "service principal"),
                    "SCIM or provider API deactivation first; content retention/reassignment follows org policy",
                    "identity replacement requires immutable-ID mapping, conflict quarantine, last-admin guard, and dry-run")),
            Map.entry("chat", new Definition(
                    List.of("chat.read", "chat.send", "chat.channels"),
                    List.of("synapse-homeserver"),
                    List.of("microsoft-teams", "slack", "nextcloud-talk"),
                    List.of("Space", "Conversation", "Message", "Thread", "Reaction", "Attachment", "Membership", "Presence"),
                    "selected chat provider owns message history unless an admin migrates or declares Weave-owned retention",
                    List.of("Slack broadcast/thread semantics", "Teams channel permissions", "Matrix E2EE recovery", "rich cards/adaptive blocks", "attachment retention"),
                    "export conversation/message/attachment provenance or document provider export boundary; delete/deprovision follows provider and retention policy",
                    "chat replacement requires preflight, dry-run, membership/history/attachment loss report, and rollback/retention note")),
            Map.entry("files", new Definition(
                    List.of("files.read", "files.upload", "files.download", "files.delete"),
                    List.of("nextcloud-files"),
                    List.of("sharepoint", "onedrive", "s3-compatible", "smb"),
                    List.of("Drive", "Node", "Folder", "File", "Version", "Share", "Permission", "Lock", "EditSession"),
                    "selected storage provider owns file bytes and native permissions; Weave owns canonical references and member impact states",
                    List.of("public links", "provider-specific shares", "version history", "locks", "external users", "storage quotas"),
                    "export file tree, permissions, versions where available; delete follows provider and retention policy",
                    "files replacement requires dry-run for permissions, versions, links, storage quota, and binary transfer feasibility")),
            Map.entry("calendar", new Definition(
                    List.of("calendar.read", "calendar.manage_events", "calendar.thread_refs"),
                    List.of("nextcloud-caldav"),
                    List.of("microsoft-graph-calendar", "google-workspace-calendar", "generic-caldav", "workspace-calendar", "team-channel-calendar"),
                    List.of("Calendar", "Event", "Attendee", "Recurrence", "Availability", "Resource"),
                    "workspace/team/channel calendar source is selected by admin; private personal calendars are not the default product source",
                    List.of("RRULE fidelity", "time zones", "resource booking", "attendee response semantics", "online meeting links"),
                    "export iCalendar-compatible events where possible; deletion follows calendar retention and legal hold policy",
                    "calendar replacement requires recurrence/time-zone/resource dry-run and attendee impact report")),
            Map.entry("boards-tasks", new Definition(
                    List.of("boards.read", "boards.update_task", "boards.sync_workspace"),
                    List.of("openproject-primary"),
                    List.of("microsoft-planner", "jira", "nextcloud-deck", "vikunja"),
                    List.of("Board", "List", "Task", "Status", "Assignee", "Comment", "Attachment", "Dependency", "CustomField"),
                    "selected work-management provider owns workflow/status semantics unless Weave-owned task service is explicitly selected",
                    List.of("custom fields", "multi-assignee", "workflow transitions", "dependencies", "comments", "attachments", "optimistic locking"),
                    "export tasks/comments/attachments/dependencies where provider allows; archive/delete follows project retention",
                    "boards replacement requires dry-run loss report for workflow, custom fields, assignees, comments, attachments, and dependencies")),
            Map.entry("meetings-calls", new Definition(
                    List.of("meetings.join", "meetings.host", "meetings.recording_policy"),
                    List.of("livekit"),
                    List.of("microsoft-teams-meetings", "zoom", "google-meet", "jitsi", "managed-meetings-provider", "external-meeting-link"),
                    List.of("Meeting", "Participant", "Recording", "Captions", "MediaSession"),
                    "meeting provider owns media session; Weave owns token facade, calendar/context binding, and consent/readiness state",
                    List.of("recording retention", "captions", "external join links", "lobby/guest semantics", "provider consent"),
                    "export recording/caption metadata where available; media deletion follows provider retention policy",
                    "meeting replacement requires token/recording/consent dry-run and clear E2EE/media-boundary note")),
            Map.entry("documents-collaboration", new Definition(
                    List.of("documents.view", "documents.edit", "documents.comment", "documents.collaborate"),
                    List.of("onlyoffice-community"),
                    List.of("microsoft-365-office-graph", "collabora-code", "google-workspace-docs", "wopi-host"),
                    List.of("EditSession", "File", "Version", "Permission", "Lock", "Comment"),
                    "document editor owns edit session; storage provider owns file bytes and versions",
                    List.of("co-edit locks", "comments", "track changes", "format fidelity", "license/edition constraints"),
                    "export document file and versions through storage provider; editor session data export depends on adapter",
                    "docs replacement requires WOPI/session/format fidelity dry-run and license/commercial-use risk note")),
            Map.entry("decisions-evidence", new Definition(
                    List.of("decisions.read", "decisions.record", "evidence.attach", "evidence.audit_read"),
                    List.of("weave-decisions-evidence"),
                    List.of("confluence-decisions", "notion-databases", "sharepoint-lists"),
                    List.of("Decision", "SourceRef", "Status", "AuditRef"),
                    "Weave owns canonical decision records unless imported source is explicitly declared authoritative",
                    List.of("external page permissions", "source citation drift", "status mapping"),
                    "export decision records, source refs, and audit refs",
                    "decision import/replacement requires citation and permission dry-run")),
            Map.entry("manuals-help", new Definition(
                    List.of("manuals.read", "manuals.admin", "help.search", "help.embed"),
                    List.of("mkdocs-material-embedded"),
                    List.of("confluence-space", "gitbook", "notion-wiki"),
                    List.of("Manual", "Page", "SearchIndex", "HelpContext"),
                    "selected docs/help source owns content; Weave owns embedded accessible presentation and context linking",
                    List.of("search relevance", "permissions", "embedded accessibility", "stale content"),
                    "export pages/search index where supported; delete follows source repository policy",
                    "manuals replacement requires accessibility/search/permission dry-run")),
            Map.entry("release-evidence", new Definition(
                    List.of("release_evidence.read", "release_evidence.manage", "release_notes.draft"),
                    List.of("weave-release-notes"),
                    List.of("github-releases", "gitlab-releases", "jira-releases"),
                    List.of("Release", "Evidence", "CheckRun", "ReleaseNote", "SupportBundleRef"),
                    "Weave release evidence owns canonical release posture; external systems provide source evidence",
                    List.of("CI provider retention", "artifact redaction", "release-note category mapping"),
                    "export release notes and evidence refs; artifact deletion follows provider retention",
                    "release evidence replacement requires artifact/redaction/category dry-run")),
            Map.entry("admin-control-plane", new Definition(
                    List.of("admin_control_plane.readiness_read", "admin_control_plane.adapter_select", "admin_control_plane.support_bundle"),
                    List.of("weave-admin-console"),
                    List.of("service-now", "grafana", "backstage"),
                    List.of("ProviderConfig", "Readiness", "RiskNote", "SupportBundle", "AuditEvent"),
                    "Weave admin control plane owns readiness and policy explanations; external tools may mirror or ticket events",
                    List.of("redaction vs usefulness", "ticket synchronization", "delegated operator scope"),
                    "export support-safe readiness/audit records; delete follows audit retention policy",
                    "admin tooling replacement requires policy/readiness/audit dry-run and delegated-scope review")),
            Map.entry("weaver", new Definition(
                    List.of("weaver.enabled", "weaver.files_read", "weaver.exec_disabled"),
                    List.of("weaver-runtime-disabled"),
                    List.of("openclaw-governed-runtime"),
                    List.of("RuntimeProfile", "ToolCapability", "ApprovalReceipt", "AuditEvent"),
                    "organization policy owns runtime/tool allowlist; user rights constrain every Weaver action",
                    List.of("tool scope", "secret handling", "sandboxing", "group-chat consent", "step-up approvals"),
                    "export runtime profile and audit receipts; delete runtime workspace per retention policy",
                    "Weaver enablement requires policy dry-run, sandbox profile, and audit/receipt proof")));

    private ProviderCapabilityContracts() {
    }

    static ProviderCategoryContractResponse contract(String category, Set<ProviderModule> modules) {
        Definition definition = definition(category);
        return new ProviderCategoryContractResponse(
                category,
                definition.featureCapabilities(),
                definition.defaultAdapters(),
                definition.externalAdapters(),
                definition.canonicalObjects(),
                definition.sourceOfTruth(),
                definition.lossyMappingRisks(),
                definition.exportDeleteExpectation(),
                definition.replacementRequirement(),
                choiceModels(definition.defaultAdapters(), definition.externalAdapters()),
                moduleNames(modules),
                STABLE_MEMBER_IMPACT_STATES,
                true,
                false);
    }


    public static ProviderRealityLevel defaultRealityLevel(String category) {
        return switch (category) {
            case "identity-idm", "chat", "files", "calendar", "boards-tasks", "admin-control-plane", "release-evidence", "manuals-help", "decisions-evidence" -> ProviderRealityLevel.RELEASE_READY;
            case "meetings-calls" -> ProviderRealityLevel.CONFIGURED_READINESS;
            case "documents-collaboration", "weaver" -> ProviderRealityLevel.CONTRACT_ONLY;
            default -> ProviderRealityLevel.CONTRACT_ONLY;
        };
    }

    public static List<String> providerCandidates(String category) {
        Definition definition = definition(category);
        return java.util.stream.Stream.concat(definition.defaultAdapters().stream(), definition.externalAdapters().stream())
                .distinct()
                .sorted()
                .toList();
    }

    public static List<String> canonicalObjects(String category) {
        return List.copyOf(definition(category).canonicalObjects());
    }

    public static String sourceOfTruth(String category) {
        return definition(category).sourceOfTruth();
    }

    public static List<String> lossyMappingRisks(String category) {
        return List.copyOf(definition(category).lossyMappingRisks());
    }

    public static String exportDeleteExpectation(String category) {
        return definition(category).exportDeleteExpectation();
    }

    public static String replacementRequirement(String category) {
        return definition(category).replacementRequirement();
    }

    private static Definition definition(String category) {
        Definition definition = DEFINITIONS.get(category);
        if (definition == null) {
            return new Definition(List.of(), List.of(), List.of(), List.of(), "admin-declared per organization", List.of(),
                    "export/delete behavior must be declared before provider activation",
                    "preflight and dry-run required before adapter replacement");
        }
        return definition;
    }

    private static List<ProviderChoiceModelResponse> choiceModels(
            List<String> defaultAdapters,
            List<String> externalAdapters) {
        return List.of(
                new ProviderChoiceModelResponse(
                        "recommended_self_hosted_default",
                        defaultAdapters,
                        List.of(
                                "recommended sovereign/default posture",
                                "admin still verifies backup, jurisdiction, lifecycle, and operator evidence"),
                        true),
                new ProviderChoiceModelResponse(
                        "external_existing_provider",
                        externalAdapters,
                        List.of(
                                "allowed when the organization already operates this provider category elsewhere",
                                "admin records tenant, data residency, retention, audit, and support boundary risk outside member UX"),
                        false),
                new ProviderChoiceModelResponse(
                        "managed_cloud_provider",
                        externalAdapters,
                        List.of(
                                "allowed as an interchangeable adapter posture, not a product boundary",
                                "admin must assess privacy, compliance, export, availability, and vendor lock-in risks"),
                        false),
                new ProviderChoiceModelResponse(
                        "hybrid_composite",
                        java.util.stream.Stream.concat(defaultAdapters.stream(), externalAdapters.stream()).distinct().sorted().toList(),
                        List.of(
                                "allowed when an organization mixes self-hosted, managed-cloud, and external providers across one capability boundary",
                                "admin must record source-of-truth, lossy mapping, support boundary, and replacement risk before member go-live"),
                        false));
    }

    private static List<String> moduleNames(Set<ProviderModule> modules) {
        return modules.stream()
                .map(ProviderModule::contractName)
                .sorted()
                .toList();
    }

    private record Definition(
            List<String> featureCapabilities,
            List<String> defaultAdapters,
            List<String> externalAdapters,
            List<String> canonicalObjects,
            String sourceOfTruth,
            List<String> lossyMappingRisks,
            String exportDeleteExpectation,
            String replacementRequirement) {
    }
}

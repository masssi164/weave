package com.massimotter.weave.backend.provider;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProviderCapabilityContracts {

    private static final List<String> STABLE_MEMBER_IMPACT_STATES = List.of(
            "available",
            "disabled_by_policy",
            "not_configured",
            "degraded",
            "unavailable",
            "coming_later");

    private static final Map<String, Definition> DEFINITIONS = Map.ofEntries(
            Map.entry("identity-idm", new Definition(
                    List.of("identity.sign_in", "identity.groups", "identity.roles"),
                    List.of("keycloak-realm", "matrix-authentication-service"),
                    List.of("entra-id", "authentik", "auth0", "generic-oidc", "generic-saml", "scim-ldap"),
                    List.of("Subject", "IdentitySource", "Group", "Role", "CapabilityProfile", "LoginSession"),
                    "authoritative IdP/provisioning source owns lifecycle and groups; Weave owns capability policy mappings",
                    List.of("email rename", "nested groups", "guest identity", "deleted/recreated account", "service principal"),
                    "SCIM or provider API deactivation first; content retention/reassignment follows org policy",
                    "identity replacement requires immutable-ID mapping, conflict quarantine, last-admin guard, and dry-run")),
            Map.entry("chat", new Definition(
                    List.of("chat.read", "chat.send", "chat.channels"),
                    List.of("matrix-chat", "synapse-homeserver"),
                    List.of("microsoft-teams", "slack", "nextcloud-talk"),
                    List.of("WeaveSpace", "WeaveConversation", "WeaveMessage", "WeaveThread", "WeaveReaction", "WeaveAttachment", "WeaveMembership", "WeaveHistoryPolicy", "ProviderRef", "MigrationReceipt", "RollbackReceipt", "LossyFieldReport"),
                    "selected chat provider owns message history; Matrix Chat is the current real release provider path and non-Matrix chat providers remain contract-only until promoted by adapter evidence",
                    List.of("Slack broadcast/thread semantics", "Teams channel permissions", "Matrix E2EE recovery", "rich cards/adaptive blocks", "attachment retention"),
                    "export conversation/message/attachment provenance or document provider export boundary; delete/deprovision follows provider and retention policy",
                    "chat replacement requires preflight, dry-run, membership/history/attachment loss report, and rollback/retention note")),
            Map.entry("files", new Definition(
                    List.of("files.read", "files.upload", "files.download", "files.delete"),
                    List.of("nextcloud-files"),
                    List.of("sharepoint", "onedrive", "s3-compatible", "smb"),
                    List.of("WeaveDrive", "WeaveFolder", "WeaveFile", "WeaveVersion", "WeaveShare", "WeavePermission", "WeaveLock", "WeaveQuota", "ProviderRef"),
                    "selected storage provider owns file bytes and native permissions; Weave owns canonical references and member impact states",
                    List.of("public links", "provider-specific shares", "version history", "locks", "external users", "storage quotas"),
                    "export file tree, permissions, versions where available; delete follows provider and retention policy",
                    "files replacement requires dry-run for permissions, versions, links, storage quota, and binary transfer feasibility")),
            Map.entry("calendar", new Definition(
                    List.of("calendar.read", "calendar.manage_events", "calendar.thread_refs"),
                    List.of("nextcloud-caldav"),
                    List.of("microsoft-graph-calendar", "google-workspace-calendar", "generic-caldav", "weave-calendar"),
                    List.of("WeaveCalendar", "WeaveEvent", "WeaveRecurrence", "WeaveAttendee", "WeaveResource", "WeaveAvailability", "ProviderRef"),
                    "workspace/team/channel calendar source is selected by admin; private personal calendars are not the default product source",
                    List.of("RRULE fidelity", "time zones", "resource booking", "attendee response semantics", "online meeting links"),
                    "export iCalendar-compatible events where possible; deletion follows calendar retention and legal hold policy",
                    "calendar replacement requires recurrence/time-zone/resource dry-run and attendee impact report")),
            Map.entry("boards-tasks", new Definition(
                    List.of("boards.read", "boards.update_task", "boards.sync_workspace"),
                    List.of("openproject-primary"),
                    List.of("placeholder-boards", "jira", "microsoft-planner", "nextcloud-deck", "vikunja"),
                    List.of("Board", "List", "Task", "Status", "Assignee", "Comment", "AttachmentRef", "Dependency", "CustomField"),
                    "selected work-management provider owns workflow/status semantics unless Weave-owned task service is explicitly selected",
                    List.of("custom fields", "multi-assignee", "workflow transitions", "dependencies", "comments", "attachments", "optimistic locking"),
                    "export tasks/comments/attachments/dependencies where provider allows; archive/delete follows project retention",
                    "boards replacement requires dry-run loss report for workflow, custom fields, assignees, comments, attachments, and dependencies")),
            Map.entry("meetings-calls", new Definition(
                    List.of("meetings.join", "meetings.host", "meetings.recording_policy"),
                    List.of("livekit"),
                    List.of("generic-webrtc-sfu", "microsoft-teams-meeting-link", "google-meet-link"),
                    List.of("Meeting", "MatrixRtcSlot", "MatrixRtcMember", "DeviceBinding", "MediaSession", "RtcAuthorization", "Recording", "Caption", "ConsentRecord"),
                    "MatrixRTC owns member signaling; Weave owns RTC authorization, consent, and artifacts; the selected SFU carries media only",
                    List.of("media E2EE", "TURN/reconnect", "recording consent and retention", "captions", "external meeting links", "device revocation"),
                    "export and delete governed artifact metadata under retention policy; active media sessions are never live-migrated",
                    "SFU replacement requires a dry-run covering Profile 0 interoperability, RTC Authorizer, media-E2EE, revocation, TURN, consent/artifact, accessibility, and rollback evidence")),
            Map.entry("documents-collaboration", new Definition(
                    List.of("documents.view", "documents.edit", "documents.comment", "documents.collaborate"),
                    List.of("onlyoffice"),
                    List.of("collabora", "microsoft-365-office", "google-workspace-docs"),
                    List.of("Document", "EditSession", "Comment", "Suggestion", "CoauthorPresence", "Version", "Export"),
                    "document editor owns edit session; storage provider owns file bytes and versions",
                    List.of("co-edit locks", "comments", "track changes", "format fidelity", "license/edition constraints"),
                    "export document file and versions through storage provider; editor session data export depends on adapter",
                    "docs replacement requires WOPI/session/format fidelity dry-run and license/commercial-use risk note")),
            Map.entry("decisions-evidence", new Definition(
                    List.of("decisions.read", "decisions.record", "evidence.attach", "evidence.audit_read"),
                    List.of("weave-decision-ledger"),
                    List.of("openproject-wiki", "nextcloud-docs", "github-issues"),
                    List.of("Decision", "Proposal", "Approval", "Rationale", "DecisionLink", "EvidenceRef", "Supersession"),
                    "Weave owns canonical decision records unless imported source is explicitly declared authoritative",
                    List.of("external page permissions", "source citation drift", "status mapping"),
                    "export decision records, source refs, and audit refs",
                    "decision import/replacement requires citation and permission dry-run")),
            Map.entry("manuals-help", new Definition(
                    List.of("manuals.read", "manuals.admin", "help.search", "help.embed"),
                    List.of("mkdocs-material-embedded"),
                    List.of("confluence-space", "gitbook", "notion-wiki"),
                    List.of("ReadinessCard", "Diagnostic", "SupportBundle", "BackupJob", "RestoreDrill", "EvidenceItem", "AuditRef"),
                    "selected docs/help source owns content; Weave owns embedded accessible presentation and context linking",
                    List.of("search relevance", "permissions", "embedded accessibility", "stale content"),
                    "export pages/search index where supported; delete follows source repository policy",
                    "manuals replacement requires accessibility/search/permission dry-run")),
            Map.entry("release-evidence", new Definition(
                    List.of("release_evidence.read", "release_evidence.manage", "release_notes.draft"),
                    List.of("release-evidence"),
                    List.of("weave-health-facade", "provider-stack-health"),
                    List.of("ReadinessCard", "Diagnostic", "SupportBundle", "BackupJob", "RestoreDrill", "EvidenceItem", "AuditRef"),
                    "Weave release evidence owns canonical release posture; external systems provide source evidence",
                    List.of("CI provider retention", "artifact redaction", "release-note category mapping"),
                    "export release notes and evidence refs; artifact deletion follows provider retention",
                    "release evidence replacement requires artifact/redaction/category dry-run")),
            Map.entry("admin-control-plane", new Definition(
                    List.of("admin_control_plane.readiness_read", "admin_control_plane.adapter_select", "admin_control_plane.support_bundle"),
                    List.of("weave-health-facade"),
                    List.of("provider-stack-health", "release-evidence"),
                    List.of("ReadinessCard", "Diagnostic", "SupportBundle", "BackupJob", "RestoreDrill", "EvidenceItem", "AuditRef"),
                    "Weave admin control plane owns readiness and policy explanations; external tools may mirror or ticket events",
                    List.of("redaction vs usefulness", "ticket synchronization", "delegated operator scope"),
                    "export support-safe readiness/audit records; delete follows audit retention policy",
                    "admin tooling replacement requires policy/readiness/audit dry-run and delegated-scope review")),
            Map.entry("agent-runtime-control", new Definition(
                    List.of("agent-runtime.entitled", "agent-runtime.profile.read", "agent-runtime.lifecycle.write", "agent-runtime.wake", "agent-runtime.approval.attest"),
                    List.of("weaver-openclaw"),
                    List.of(),
                    List.of("RuntimeEntitlementRef", "RuntimeProfile", "ApprovalChallenge", "RuntimeCell", "WorkspaceRevision", "RuntimeRevocation", "RuntimeAuditCorrelation"),
                    "Keycloak owns entitlement; ARC owns profile/cell bindings and external-state lifecycle; collaboration domains retain content and authorization",
                    List.of("cross-cell state", "stale entitlement/profile", "workload credential exposure", "durable cell residue", "unreconciled restore"),
                    "export support-safe ARC bindings/evidence; delete only through explicit retention-scoped operations",
                    "runtime-provider replacement requires current entitlement, signed profile trust, external-state portability, kill/recreate, and cross-cell denial proof")),
            Map.entry("model", new Definition(
                    List.of("model.chat_completion", "model.embedding", "model.admin_select"),
                    List.of("lmstudio", "lmstudio-openai-compatible"),
                    List.of("ollama-openai-compatible", "generic-openai-compatible", "openai", "anthropic"),
                    List.of("ModelProvider", "ModelAlias", "CompletionRequest", "CompletionResponse", "ProviderCredentialRef"),
                    "Admin-selected model provider owns inference; Weave owns support-safe aliases, routing, credentials by reference, and member impact states",
                    List.of("model behavior drift", "context-window differences", "tool-call support", "data residency", "rate limits"),
                    "export model alias policy and audit receipts; revoke credential refs through Credential Broker",
                    "model provider replacement requires readiness proof, credential-ref validation, and support-safe live completion evidence")));

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
            case "meetings-calls", "model" -> ProviderRealityLevel.CONFIGURED;
            case "documents-collaboration", "agent-runtime-control" -> ProviderRealityLevel.CONTRACT_ONLY;
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

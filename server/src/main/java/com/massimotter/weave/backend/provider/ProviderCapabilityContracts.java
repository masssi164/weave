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

    private static final Map<String, Definition> DEFINITIONS = Map.of(
            "identity-idm", new Definition(
                    List.of("identity.sign_in", "identity.groups", "identity.roles"),
                    List.of("keycloak-realm", "matrix-authentication-service"),
                    List.of("entra-id", "authentik", "auth0", "generic-oidc", "generic-saml", "scim-ldap")),
            "chat", new Definition(
                    List.of("chat.read", "chat.send", "chat.channels"),
                    List.of("synapse-homeserver"),
                    List.of("microsoft-teams", "slack", "nextcloud-talk")),
            "files", new Definition(
                    List.of("files.read", "files.upload", "files.download", "files.delete"),
                    List.of("nextcloud-files"),
                    List.of("sharepoint", "onedrive", "s3-compatible", "smb")),
            "calendar", new Definition(
                    List.of("calendar.read", "calendar.manage_events", "calendar.thread_refs"),
                    List.of("nextcloud-caldav"),
                    List.of("microsoft-graph-calendar", "generic-caldav", "workspace-calendar", "team-channel-calendar")),
            "boards-tasks", new Definition(
                    List.of("boards.read", "boards.update_task", "boards.sync_workspace"),
                    List.of("openproject-primary"),
                    List.of("microsoft-planner", "jira", "nextcloud-deck", "vikunja")),
            "meetings-calls", new Definition(
                    List.of("meetings.join", "meetings.host", "meetings.recording_policy"),
                    List.of("livekit"),
                    List.of("microsoft-teams-meetings", "managed-meetings-provider", "external-meeting-link")),
            "documents-collaboration", new Definition(
                    List.of("documents.view", "documents.edit", "documents.comment", "documents.collaborate"),
                    List.of("onlyoffice-community"),
                    List.of("microsoft-365-office-graph", "collabora-code", "wopi-host")),
            "weaver", new Definition(
                    List.of("weaver.enabled", "weaver.files_read", "weaver.exec_disabled"),
                    List.of("weaver-runtime-disabled"),
                    List.of("openclaw-governed-runtime")));

    private ProviderCapabilityContracts() {
    }

    static ProviderCategoryContractResponse contract(String category, Set<ProviderModule> modules) {
        Definition definition = definition(category);
        return new ProviderCategoryContractResponse(
                category,
                definition.featureCapabilities(),
                definition.defaultAdapters(),
                definition.externalAdapters(),
                choiceModels(definition.defaultAdapters(), definition.externalAdapters()),
                moduleNames(modules),
                STABLE_MEMBER_IMPACT_STATES,
                true,
                false);
    }

    public static List<String> providerCandidates(String category) {
        Definition definition = definition(category);
        return java.util.stream.Stream.concat(definition.defaultAdapters().stream(), definition.externalAdapters().stream())
                .distinct()
                .sorted()
                .toList();
    }

    private static Definition definition(String category) {
        Definition definition = DEFINITIONS.get(category);
        if (definition == null) {
            return new Definition(List.of(), List.of(), List.of());
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
            List<String> externalAdapters) {
    }
}

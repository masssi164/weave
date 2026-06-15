package com.massimotter.weave.backend.provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ProviderCategoryCatalog {

    private static final Map<String, ProviderCategoryDefinition> CATEGORIES = Map.of(
            "identity-idm", new ProviderCategoryDefinition("identity-idm", "identity/IDM", Set.of(ProviderModule.IDENTITY_REALM, ProviderModule.MATRIX_AUTH), true,
                    List.of("keycloak-realm", "matrix-authentication-service"),
                    List.of("entra-id", "authentik", "auth0", "generic-oidc", "generic-saml", "scim-ldap")),
            "chat", new ProviderCategoryDefinition("chat", "chat", Set.of(ProviderModule.MATRIX), true,
                    List.of("matrix-chat", "synapse-homeserver"),
                    List.of("nextcloud-talk")),
            "files", new ProviderCategoryDefinition("files", "files", Set.of(ProviderModule.FILES), true,
                    List.of("nextcloud-files"),
                    List.of("sharepoint", "onedrive", "s3-compatible", "smb")),
            "calendar", new ProviderCategoryDefinition("calendar", "calendar", Set.of(ProviderModule.CALENDAR), true,
                    List.of("nextcloud-caldav"),
                    List.of("google-workspace-calendar", "generic-caldav", "weave-calendar")),
            "boards-tasks", new ProviderCategoryDefinition("boards-tasks", "boards/tasks", Set.of(ProviderModule.BOARDS), true,
                    List.of("openproject-primary"),
                    List.of("placeholder-boards", "jira", "microsoft-planner", "nextcloud-deck", "vikunja")),
            "meetings-calls", new ProviderCategoryDefinition("meetings-calls", "meetings/calls", Set.of(ProviderModule.MEETINGS), false,
                    List.of("livekit"),
                    List.of("jitsi", "zoom", "google-meet", "external-meeting-link")),
            "documents-collaboration", new ProviderCategoryDefinition("documents-collaboration", "documents/collaboration", Set.of(ProviderModule.OFFICE, ProviderModule.FORMS, ProviderModule.CONTACTS), false,
                    List.of("onlyoffice"),
                    List.of("collabora", "microsoft-365-office", "google-workspace-docs")),
            "weaver", new ProviderCategoryDefinition("weaver", "Weaver", Set.of(), true,
                    List.of("openclaw-derived-profile"),
                    List.of()),
            "model", new ProviderCategoryDefinition("model", "model provider", Set.of(), true,
                    List.of("lmstudio", "lmstudio-openai-compatible"),
                    List.of("ollama-openai-compatible", "generic-openai-compatible", "openai", "anthropic")));

    private ProviderCategoryCatalog() {
    }

    public static List<String> categoryKeys() {
        return List.of("identity-idm", "chat", "files", "calendar", "boards-tasks", "meetings-calls", "documents-collaboration", "model", "weaver");
    }

    public static Optional<ProviderCategoryDefinition> category(String key) {
        return Optional.ofNullable(CATEGORIES.get(key));
    }

    public static Optional<String> categoryForModule(ProviderModule module) {
        return CATEGORIES.values().stream()
                .filter(category -> category.modules().contains(module))
                .map(ProviderCategoryDefinition::key)
                .findFirst();
    }

    public static boolean providerMatchesCategory(ProviderStatusResponse provider, String categoryKey) {
        return category(categoryKey)
                .map(category -> category.modules().contains(provider.module()))
                .orElse(false);
    }
}

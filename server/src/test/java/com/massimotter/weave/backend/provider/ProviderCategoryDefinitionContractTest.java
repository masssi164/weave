package com.massimotter.weave.backend.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderCategoryDefinitionContractTest {

    @Test
    void catalogDefinitionsAreTheSourceForDefaultAdaptersAndCategoryModules() {
        ProviderCategoryCatalog.categoryKeys().forEach(category -> {
            ProviderCategoryDefinition definition = ProviderCategoryCatalog.category(category).orElseThrow();
            ProviderCategoryContractResponse contract = ProviderCapabilityContracts.contract(category, definition.modules());

            assertThat(contract.defaultAdapters()).containsExactlyElementsOf(definition.defaultAdapters());
            assertThat(ProviderCapabilityContracts.providerCandidates(category))
                    .containsAll(definition.providerCandidates());
            assertThat(contract.adapterModules())
                    .containsExactlyElementsOf(definition.modules().stream().map(ProviderModule::contractName).sorted().toList());
        });
    }

    @Test
    void knownCategoryKeysModulesAndCatalogCandidatesStayStable() {
        assertThat(ProviderCategoryCatalog.categoryKeys()).containsExactly(
                "identity-idm",
                "chat",
                "files",
                "calendar",
                "boards-tasks",
                "meetings-calls",
                "documents-collaboration",
                "model",
                "weaver");

        assertCategory("identity-idm", "identity/IDM", Set.of(ProviderModule.IDENTITY_REALM, ProviderModule.MATRIX_AUTH),
                List.of("auth0", "authentik", "entra-id", "generic-oidc", "generic-saml", "keycloak-realm", "matrix-authentication-service", "scim-ldap"));
        assertCategory("chat", "chat", Set.of(ProviderModule.MATRIX),
                List.of("matrix-chat", "nextcloud-talk", "synapse-homeserver"));
        assertCategory("files", "files", Set.of(ProviderModule.FILES),
                List.of("nextcloud-files", "onedrive", "s3-compatible", "sharepoint", "smb"));
        assertCategory("calendar", "calendar", Set.of(ProviderModule.CALENDAR),
                List.of("generic-caldav", "google-workspace-calendar", "nextcloud-caldav", "weave-calendar"));
        assertCategory("boards-tasks", "boards/tasks", Set.of(ProviderModule.BOARDS),
                List.of("jira", "microsoft-planner", "nextcloud-deck", "openproject-primary", "placeholder-boards", "vikunja"));
        assertCategory("meetings-calls", "meetings/calls", Set.of(ProviderModule.MEETINGS),
                List.of("external-meeting-link", "google-meet", "jitsi", "livekit", "zoom"));
        assertCategory("documents-collaboration", "documents/collaboration", Set.of(ProviderModule.OFFICE, ProviderModule.FORMS, ProviderModule.CONTACTS),
                List.of("collabora", "google-workspace-docs", "microsoft-365-office", "onlyoffice"));
        assertCategory("model", "model provider", Set.of(),
                List.of("anthropic", "generic-openai-compatible", "lmstudio", "lmstudio-openai-compatible", "ollama-openai-compatible", "openai"));
        assertCategory("weaver", "Weaver", Set.of(),
                List.of("openclaw-derived-profile"));
    }

    @Test
    void guardedAdapterReferencesRemainBehindCapabilityContractsOnly() {
        ProviderCategoryCatalog.categoryKeys().forEach(category -> {
            ProviderCategoryDefinition definition = ProviderCategoryCatalog.category(category).orElseThrow();
            ProviderCategoryContractResponse contract = ProviderCapabilityContracts.contract(category, definition.modules());

            assertThat(contract.choiceModels())
                    .filteredOn(choice -> choice.choiceModel().equals(ProviderChoiceModel.MANAGED_CLOUD_PROVIDER))
                    .singleElement()
                    .satisfies(choice -> assertThat(choice.adminRiskNotes()).isNotEmpty());
            assertThat(contract.normalMembersConfigureProviders()).isFalse();
            assertThat(contract.adminSelectable()).isTrue();
        });
    }

    @Test
    void touchedCategoryResponsePathsDoNotLeakRawSecretRefs() {
        ProviderCategoryDefinition definition = ProviderCategoryCatalog.category("files").orElseThrow();
        ProviderCategoryStatusResponse response = new ProviderCategoryStatusResponse(
                definition.key(),
                definition.label(),
                ProviderCapabilityContracts.contract(definition.key(), definition.modules()),
                ProviderCategoryReadiness.READY,
                ProviderRealityLevel.RELEASE_READY,
                "usable",
                "Release-ready provider: keep policy, readiness, support-safe diagnostics, and release evidence current.",
                com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState.ALLOWED,
                "Files are available through Weave.",
                List.of("files"),
                definition.providerCandidates(),
                "nextcloud-files",
                "recommended_self_hosted_default",
                true,
                false,
                List.of(),
                List.of(),
                java.util.Map.of("secretsReturned", false, "rawProviderErrorsReturned", false));

        assertThat(response.toString()).doesNotContain("secretref://", "SecretRef(", "Bearer ", "access_token");
        assertThat(response.diagnostics()).containsEntry("secretsReturned", false);
    }

    private void assertCategory(String key, String label, Set<ProviderModule> modules, List<String> candidates) {
        ProviderCategoryDefinition definition = ProviderCategoryCatalog.category(key).orElseThrow();

        assertThat(definition.label()).isEqualTo(label);
        assertThat(definition.modules()).containsExactlyInAnyOrderElementsOf(modules);
        assertThat(definition.providerCandidates()).containsExactlyElementsOf(candidates);
    }
}

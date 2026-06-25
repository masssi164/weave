package com.massimotter.weave.backend.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProviderRegistryTest {

    @Test
    void bootstrapDefaultsRemainSuggestionsUntilAdminSelection() {
        ProviderRegistry registry = new ProviderRegistry(
                List.of(StaticProviderPort.pending(
                        ProviderModule.MATRIX,
                        "synapse-homeserver",
                        "Chat adapter candidate.",
                        Set.of("chat.read"),
                        Set.of("direct-flutter-provider-api"),
                        List.of("synapse", "slack", "microsoft-teams"),
                        Map.of())),
                capabilityService(),
                new InMemoryProviderSelectionRepository());

        ProviderRegistryResponse response = registry.status();

        assertThat(response.providerConfigSource()).isEqualTo("admin-control-plane-selected-provider-mappings");
        assertThat(response.bootstrapDefaultsAreSuggestionsOnly()).isTrue();
        assertThat(response.adminSelectedMappingsRequired()).isTrue();
        assertThat(response.selectedProviderMappings()).isEmpty();
        ProviderCategoryStatusResponse chat = response.categories().stream()
                .filter(category -> category.category().equals("chat"))
                .findFirst()
                .orElseThrow();
        assertThat(chat.readiness()).isEqualTo(ProviderCategoryReadiness.MISCONFIGURED);
        assertThat(chat.selectedByAdmin()).isFalse();
        assertThat(chat.bootstrapSuggestionOnly()).isTrue();
        assertThat(chat.selectedProviderKey()).isEqualTo("awaiting_admin_selection");
        assertThat(chat.diagnostics()).containsEntry("selectionRequiredBeforeProviderUse", true);
        assertThat(chat.adapterEvidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.domain()).isEqualTo("chat");
            assertThat(evidence.adapterKey()).isEqualTo("synapse-homeserver");
            assertThat(evidence.configured()).isFalse();
            assertThat(evidence.failClosed()).isTrue();
            assertThat(evidence.supportSafeDiagnostics())
                    .containsEntry("secretsReturned", false)
                    .containsEntry("rawProviderErrorsReturned", false)
                    .doesNotContainKeys("endpoint", "rawProviderError", "authorization");
        });
        ProviderCategoryStatusResponse weaver = response.categories().stream()
                .filter(category -> category.category().equals("weaver"))
                .findFirst()
                .orElseThrow();
        assertThat(weaver.providerCandidates()).containsExactly("openclaw-derived-profile");
        assertThat(response.providers().get(0).enabled()).isFalse();
        assertThat(response.providers().get(0).diagnostics()).containsEntry("selectedByAdmin", false);
    }

    @Test
    void adminSelectionBecomesSourceOfTruthWithoutExposingSecrets() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(new ProviderSelection(
                "chat",
                "slack",
                "external_existing_provider",
                "secretref://weave/provider/slack",
                "actor:admin",
                Instant.parse("2026-05-24T18:00:00Z"),
                true,
                true,
                true,
                List.of("Thread and channel semantics require migration dry-run.")));
        ProviderRegistry registry = new ProviderRegistry(
                List.of(StaticProviderPort.pending(
                        ProviderModule.MATRIX,
                        "synapse-homeserver",
                        "Chat adapter candidate.",
                        Set.of("chat.read"),
                        Set.of("direct-flutter-provider-api"),
                        List.of("synapse", "slack", "microsoft-teams"),
                        Map.of("rawProviderErrorsReturned", false))),
                capabilityService(),
                selections);

        ProviderRegistryResponse response = registry.status();

        assertThat(response.selectedProviderMappings()).extracting(ProviderSelection::providerKey).containsExactly("slack");
        ProviderCategoryStatusResponse chat = response.categories().stream()
                .filter(category -> category.category().equals("chat"))
                .findFirst()
                .orElseThrow();
        assertThat(chat.readiness()).isEqualTo(ProviderCategoryReadiness.READY);
        assertThat(chat.providerRealityLevel()).isEqualTo(ProviderRealityLevel.CONTRACT_ONLY);
        assertThat(chat.memberCapabilityState()).isEqualTo("coming_later");
        assertThat(chat.realityLevelRemediation()).contains("Contract-only");
        assertThat(chat.selectedByAdmin()).isTrue();
        assertThat(chat.selectedProviderKey()).isEqualTo("slack");
        assertThat(chat.choiceModel()).isEqualTo("external_existing_provider");
        assertThat(chat.lossyMappingNotes()).contains("Thread and channel semantics require migration dry-run.");
        assertThat(response.providers().get(0).enabled()).isTrue();
        assertThat(response.providers().get(0).configured()).isFalse();
        assertThat(response.providers().get(0).providerRealityLevel()).isEqualTo(ProviderRealityLevel.CONTRACT_ONLY);
        assertThat(response.providers().get(0).diagnostics())
                .containsEntry("selectedByAdmin", true)
                .containsEntry("secretsReturned", false)
                .containsEntry("rawProviderErrorsReturned", false);
    }

    @Test
    void localLiveSelectionMarksRecommendedDogfoodAdapterConfigured() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(new ProviderSelection(
                "chat",
                "synapse-homeserver",
                "recommended_self_hosted_default",
                "secretref://weave/provider/synapse-homeserver/signing-key",
                "actor:local-live-bootstrap",
                Instant.parse("2026-01-01T00:00:00Z"),
                true,
                true,
                false,
                List.of()));
        ProviderRegistry registry = new ProviderRegistry(
                List.of(StaticProviderPort.pending(
                        ProviderModule.MATRIX,
                        "synapse-homeserver",
                        "Chat adapter candidate.",
                        Set.of("chat.read"),
                        Set.of("direct-flutter-provider-api"),
                        List.of("synapse", "slack", "microsoft-teams"),
                        Map.of())),
                capabilityService(),
                selections,
                "local-live");

        ProviderRegistryResponse response = registry.status();

        ProviderStatusResponse provider = response.providers().get(0);
        assertThat(provider.enabled()).isTrue();
        assertThat(provider.configured()).isTrue();
        assertThat(provider.state()).isEqualTo(ProviderState.CONFIGURED);
        assertThat(provider.readiness()).isEqualTo("configured");
        assertThat(provider.diagnostics())
                .containsEntry("selectedByAdmin", true)
                .containsEntry("choiceModel", "recommended_self_hosted_default")
                .containsEntry("secretsReturned", false);
        ProviderCategoryStatusResponse chat = response.categories().stream()
                .filter(category -> category.category().equals("chat"))
                .findFirst()
                .orElseThrow();
        assertThat(chat.readiness()).isEqualTo(ProviderCategoryReadiness.READY);
        assertThat(chat.adapterEvidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.configured()).isTrue();
            assertThat(evidence.reachable()).isTrue();
            assertThat(evidence.health()).isEqualTo("configured");
        });
    }

    private WorkspaceCapabilityService capabilityService() {
        WorkspaceCapabilityService service = Mockito.mock(WorkspaceCapabilityService.class);
        when(service.snapshot()).thenReturn(new WorkspaceCapabilitiesResponse(
                capability(), capability(), capability(), capability(), capability(),
                new WorkspaceCapabilityStatusResponse(
                        false,
                        WorkspaceCapabilityReadiness.UNAVAILABLE,
                        WorkspaceCapabilityPolicyState.DISABLED,
                        "test-profile",
                        "Weaver disabled.",
                        List.of())));
        return service;
    }

    private WorkspaceCapabilityStatusResponse capability() {
        return new WorkspaceCapabilityStatusResponse(
                true,
                WorkspaceCapabilityReadiness.READY,
                WorkspaceCapabilityPolicyState.ALLOWED,
                "test-profile",
                "Ready through Weave.",
                List.of("test.capability"));
    }
}

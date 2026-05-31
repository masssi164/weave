package com.massimotter.weave.backend.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DomainAdapterRegistryMapperTest {

    private static final Map<String, List<String>> CORE_DOMAIN_OBJECTS = Map.of(
            "chat", List.of("Space", "Conversation", "Message", "Thread", "Reaction", "Attachment", "Membership", "Presence"),
            "files", List.of("Drive", "Node", "Folder", "File", "Version", "Share", "Permission", "Lock", "EditSession"),
            "calendar", List.of("Calendar", "Event", "Attendee", "Recurrence", "Availability", "Resource"),
            "boards-tasks", List.of("Board", "List", "Task", "Status", "Assignee", "Comment", "Attachment", "Dependency", "CustomField"),
            "meetings-calls", List.of("Meeting", "Participant", "Recording", "Captions", "MediaSession"));

    private static final Map<String, List<String>> MIXED_PROVIDER_POSTURE = Map.of(
            "identity-idm", List.of("keycloak-realm", "entra-id", "generic-oidc", "generic-saml"),
            "chat", List.of("synapse-homeserver", "microsoft-teams", "slack"),
            "files", List.of("nextcloud-files", "sharepoint"),
            "boards-tasks", List.of("openproject-primary", "microsoft-planner"));

    @Test
    void enabledDomainWithMultipleActiveAdaptersIsInvalidAndFailClosed() {
        var status = new DomainAdapterStatusResponse(
                "chat",
                "chat",
                true,
                "synapse-homeserver",
                ProviderCategoryReadiness.MISCONFIGURED,
                "fail closed",
                List.of(
                        candidate("synapse-homeserver", true, true, ProviderCategoryReadiness.READY),
                        candidate("slack", true, true, ProviderCategoryReadiness.READY)),
                List.of("enabled domain must have exactly one active adapter"),
                true,
                true);

        assertThat(status.singleActiveAdapterValid()).isFalse();
        assertThat(status.activeAdapter()).isNull();
        assertThat(status.failClosed()).isTrue();
        assertThat(status.supportSafe()).isTrue();
    }

    @Test
    void disabledDomainNeverExposesActiveAdapter() {
        var status = new DomainAdapterStatusResponse(
                "chat",
                "chat",
                false,
                "synapse-homeserver",
                ProviderCategoryReadiness.DISABLED,
                "disabled",
                List.of(candidate("synapse-homeserver", true, true, ProviderCategoryReadiness.READY)),
                List.of("disabled domain must not expose an active adapter"),
                true,
                true);

        assertThat(status.activeAdapter()).isNull();
        assertThat(status.singleActiveAdapterValid()).isFalse();
    }

    @Test
    void mapperSelectsExactlyOneSelfHostedDefaultForEnabledDomain() {
        var category = category("files", ProviderCategoryReadiness.READY);

        var status = DomainAdapterRegistryMapper.fromCategory(category);

        assertThat(status.singleActiveAdapterValid()).isTrue();
        assertThat(status.activeAdapter()).isEqualTo("nextcloud-files");
        assertThat(status.candidates()).filteredOn(DomainAdapterCandidateResponse::active).hasSize(1);
        assertThat(status.candidates()).filteredOn(DomainAdapterCandidateResponse::active)
                .allMatch(DomainAdapterCandidateResponse::configured);
        assertThat(status.candidates()).allMatch(DomainAdapterCandidateResponse::supportSafe);
    }

    @Test
    void mapperUsesAdminSelectedAdapterWhenItIsInTheContract() {
        var category = new ProviderCategoryStatusResponse(
                "chat",
                "chat",
                ProviderCapabilityContracts.contract("chat", Set.of(ProviderModule.MATRIX)),
                ProviderCategoryReadiness.READY,
                WorkspaceCapabilityPolicyState.ALLOWED,
                "Chat is available through Weave.",
                List.of("matrix"),
                List.of("synapse-homeserver", "microsoft-teams", "slack"),
                "synapse-homeserver",
                "recommended_self_hosted_default",
                true,
                false,
                List.of(),
                List.of(),
                Map.of("allFailClosed", true, "secretsReturned", false, "rawProviderErrorsReturned", false));

        var status = DomainAdapterRegistryMapper.fromCategory(category);

        assertThat(status.singleActiveAdapterValid()).isTrue();
        assertThat(status.activeAdapter()).isEqualTo("synapse-homeserver");
        assertThat(status.candidates()).filteredOn(DomainAdapterCandidateResponse::active)
                .singleElement()
                .extracting(DomainAdapterCandidateResponse::adapterKey)
                .isEqualTo("synapse-homeserver");
    }

    @Test
    void categoryContractCarriesAntiSiloDomainAdapterFit() {
        var contract = ProviderCapabilityContracts.contract("chat", Set.of(ProviderModule.MATRIX));

        assertThat(contract.stableMemberImpactStates()).containsExactly("usable", "disabled", "degraded", "policy-blocked");
        assertThat(contract.canonicalObjects()).contains("Conversation", "Message", "Membership");
        assertThat(contract.externalAdapters()).contains("microsoft-teams", "slack");
        assertThat(contract.lossyMappingRisks()).contains("Slack broadcast/thread semantics", "Teams channel permissions");
        assertThat(contract.sourceOfTruth()).contains("selected chat provider owns message history");
        assertThat(contract.replacementRequirement()).contains("dry-run");
        assertThat(contract.choiceModels()).extracting(ProviderChoiceModelResponse::choiceModel).contains("hybrid_composite");
    }

    @Test
    void coreProductDomainsCarryExecutableAdapterFitContracts() {
        CORE_DOMAIN_OBJECTS.forEach((category, canonicalObjects) -> {
            var contract = ProviderCapabilityContracts.contract(category, modulesFor(category));

            assertThat(contract.stableMemberImpactStates()).containsExactly("usable", "disabled", "degraded", "policy-blocked");
            assertThat(contract.canonicalObjects()).containsExactlyInAnyOrderElementsOf(canonicalObjects);
            assertThat(contract.sourceOfTruth()).isNotBlank();
            assertThat(contract.lossyMappingRisks()).isNotEmpty();
            assertThat(contract.exportDeleteExpectation()).containsIgnoringCase("export");
            assertThat(contract.exportDeleteExpectation()).containsPattern("(?i)delete|deletion|retention|archive");
            assertThat(contract.replacementRequirement()).containsIgnoringCase("dry-run");
            assertThat(contract.choiceModels())
                    .extracting(ProviderChoiceModelResponse::choiceModel)
                    .contains("recommended_self_hosted_default", "external_existing_provider", "managed_cloud_provider", "hybrid_composite");
            assertThat(contract.adminSelectable()).isTrue();
            assertThat(contract.normalMembersConfigureProviders()).isFalse();
        });
    }

    @Test
    void providerRegistryAdapterFitSupportsMixedProviderPostureWithoutMemberProviderIds() {
        var domains = DomainAdapterRegistryMapper.fromCategories(List.of(
                category("identity-idm", ProviderCategoryReadiness.READY, ProviderModule.IDENTITY_REALM),
                category("chat", ProviderCategoryReadiness.READY, ProviderModule.MATRIX),
                category("files", ProviderCategoryReadiness.READY, ProviderModule.FILES),
                category("calendar", ProviderCategoryReadiness.READY, ProviderModule.CALENDAR),
                category("boards-tasks", ProviderCategoryReadiness.READY, ProviderModule.BOARDS),
                category("meetings-calls", ProviderCategoryReadiness.READY, ProviderModule.MEETINGS)), null).domains();

        assertThat(domains).extracting(DomainAdapterStatusResponse::domain)
                .contains("chat", "files", "calendar", "boards-tasks", "meetings-calls");
        assertThat(domains).allSatisfy(domain -> {
            assertThat(domain.singleActiveAdapterValid()).isTrue();
            assertThat(domain.supportSafe()).isTrue();
            assertThat(domain.candidates()).allSatisfy(candidate -> {
                assertThat(candidate.supportSafe()).isTrue();
                assertThat(candidate.diagnostics())
                        .containsEntry("secretsReturned", false)
                        .containsEntry("rawProviderErrorsReturned", false);
            });
        });
        MIXED_PROVIDER_POSTURE.forEach((category, adapterKeys) -> assertThat(domains)
                .filteredOn(domain -> domain.domain().equals(category))
                .singleElement()
                .satisfies(domain -> assertThat(domain.candidates())
                        .extracting(DomainAdapterCandidateResponse::adapterKey)
                        .containsAll(adapterKeys)));
        assertThat(domains.toString()).doesNotContain("Bearer ", "access_token", "secretref://", "https://tenant");
    }

    @Test
    void mapperDoesNotMarkMisconfiguredActiveAdapterAsConfigured() {
        var category = category("files", ProviderCategoryReadiness.MISCONFIGURED);

        var status = DomainAdapterRegistryMapper.fromCategory(category);

        assertThat(status.candidates())
                .filteredOn(DomainAdapterCandidateResponse::active)
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.adapterKey()).isEqualTo("nextcloud-files");
                    assertThat(candidate.configured()).isFalse();
                    assertThat(candidate.readiness()).isEqualTo(ProviderCategoryReadiness.MISCONFIGURED);
                });
    }

    private ProviderCategoryStatusResponse category(String category, ProviderCategoryReadiness readiness) {
        return category(category, readiness, ProviderModule.FILES);
    }

    private ProviderCategoryStatusResponse category(String category, ProviderCategoryReadiness readiness, ProviderModule module) {
        return new ProviderCategoryStatusResponse(
                category,
                category,
                ProviderCapabilityContracts.contract(category, Set.of(module)),
                readiness,
                WorkspaceCapabilityPolicyState.ALLOWED,
                "Files are available through Weave.",
                List.of("files"),
                List.of("nextcloud-files", "sharepoint"),
                "nextcloud-files",
                "recommended_self_hosted_default",
                true,
                false,
                List.of(),
                List.of(),
                Map.of("allFailClosed", true, "secretsReturned", false, "rawProviderErrorsReturned", false));
    }

    private Set<ProviderModule> modulesFor(String category) {
        return switch (category) {
            case "chat" -> Set.of(ProviderModule.MATRIX);
            case "files" -> Set.of(ProviderModule.FILES);
            case "calendar" -> Set.of(ProviderModule.CALENDAR);
            case "boards-tasks" -> Set.of(ProviderModule.BOARDS);
            case "meetings-calls" -> Set.of(ProviderModule.MEETINGS);
            default -> Set.of();
        };
    }

    private DomainAdapterCandidateResponse candidate(
            String key,
            boolean active,
            boolean configured,
            ProviderCategoryReadiness readiness) {
        return new DomainAdapterCandidateResponse(
                key,
                active ? "recommended_self_hosted_default" : "external_or_managed_candidate",
                active,
                configured,
                readiness,
                List.of("dry-run"),
                List.of("support-safe"),
                true,
                Map.of("secretsReturned", false, "rawProviderErrorsReturned", false));
    }
}

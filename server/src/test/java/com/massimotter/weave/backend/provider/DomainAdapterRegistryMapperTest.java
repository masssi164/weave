package com.massimotter.weave.backend.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DomainAdapterRegistryMapperTest {

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
        return new ProviderCategoryStatusResponse(
                category,
                category,
                ProviderCapabilityContracts.contract(category, Set.of(ProviderModule.FILES)),
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

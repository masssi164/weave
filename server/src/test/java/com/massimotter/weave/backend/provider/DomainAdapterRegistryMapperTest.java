package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainAdapterRegistryMapperTest {

    @Test
    void enabledDomainWithMultipleActiveAdaptersIsInvalidAndFailClosed() {
        var status = new DomainAdapterStatusResponse(
                "chat",
                "chat",
                true,
                null,
                ProviderCategoryReadiness.MISCONFIGURED,
                "fail closed",
                List.of(
                        candidate("synapse-homeserver", true),
                        candidate("slack", true)),
                List.of("enabled domain must have exactly one active adapter"),
                true,
                true);

        assertThat(status.singleActiveAdapterValid()).isFalse();
        assertThat(status.failClosed()).isTrue();
        assertThat(status.supportSafe()).isTrue();
    }

    @Test
    void mapperSelectsExactlyOneSelfHostedDefaultForEnabledDomain() {
        var category = new ProviderCategoryStatusResponse(
                "files",
                "files",
                ProviderCapabilityContracts.contract("files", Set.of(ProviderModule.FILES)),
                ProviderCategoryReadiness.READY,
                WorkspaceCapabilityPolicyState.ALLOWED,
                "Files are available through Weave.",
                List.of("files"),
                List.of("nextcloud-files", "sharepoint"),
                Map.of("allFailClosed", true, "secretsReturned", false, "rawProviderErrorsReturned", false));

        var status = DomainAdapterRegistryMapper.fromCategory(category);

        assertThat(status.singleActiveAdapterValid()).isTrue();
        assertThat(status.activeAdapter()).isEqualTo("nextcloud-files");
        assertThat(status.candidates()).filteredOn(DomainAdapterCandidateResponse::active).hasSize(1);
        assertThat(status.candidates()).allMatch(DomainAdapterCandidateResponse::supportSafe);
    }

    private DomainAdapterCandidateResponse candidate(String key, boolean active) {
        return new DomainAdapterCandidateResponse(
                key,
                active ? "recommended_self_hosted_default" : "external_or_managed_candidate",
                active,
                active,
                active ? ProviderCategoryReadiness.READY : ProviderCategoryReadiness.DISABLED,
                List.of("dry-run"),
                List.of("support-safe"),
                true,
                Map.of("secretsReturned", false, "rawProviderErrorsReturned", false));
    }
}

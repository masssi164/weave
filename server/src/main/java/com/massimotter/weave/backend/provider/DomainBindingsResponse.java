package com.massimotter.weave.backend.provider;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Generic Admin domain binding/readiness view across selected Weave domains. Provider internals stay out of member APIs.")
public record DomainBindingsResponse(
        String releaseStatus,
        String providerConfigSource,
        boolean transitionPlansAreSecondaryArtifacts,
        boolean memberProviderInternalsExposed,
        boolean supportSafe,
        Instant generatedAt,
        List<DomainBindingResponse> bindings) {

    public DomainBindingsResponse {
        releaseStatus = releaseStatus == null || releaseStatus.isBlank()
                ? "domain-binding-provider-connection-v1"
                : releaseStatus.trim();
        providerConfigSource = providerConfigSource == null || providerConfigSource.isBlank()
                ? ProviderRegistry.PROVIDER_CONFIG_SOURCE
                : providerConfigSource.trim();
        transitionPlansAreSecondaryArtifacts = true;
        memberProviderInternalsExposed = false;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        supportSafe = supportSafe && bindings.stream().allMatch(DomainBindingResponse::supportSafe);
    }
}

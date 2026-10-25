package com.massimotter.weave.backend.provider;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Admin/server-owned domain adapter registry. It enforces one active adapter per enabled Weave domain and redacts provider internals from member surfaces.")
public record DomainAdapterRegistryResponse(
        String releaseStatus,
        boolean singleActiveAdapterEnforced,
        boolean memberProviderConfigurationAllowed,
        boolean supportSafe,
        Instant generatedAt,
        List<DomainAdapterStatusResponse> domains) {

    public DomainAdapterRegistryResponse {
        releaseStatus = releaseStatus == null || releaseStatus.isBlank()
                ? "domain-adapter-registry-preview"
                : releaseStatus.trim();
        domains = domains == null ? List.of() : List.copyOf(domains);
        singleActiveAdapterEnforced = singleActiveAdapterEnforced
                && domains.stream().allMatch(DomainAdapterStatusResponse::singleActiveAdapterValid);
        supportSafe = supportSafe && domains.stream().allMatch(DomainAdapterStatusResponse::supportSafe);
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
    }
}

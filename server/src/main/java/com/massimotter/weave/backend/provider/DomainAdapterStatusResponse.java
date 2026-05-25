package com.massimotter.weave.backend.provider;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One provider-neutral Weave domain with exactly one active adapter when enabled, or explicit fail-closed/disabled posture.")
public record DomainAdapterStatusResponse(
        String domain,
        String label,
        boolean enabled,
        String activeAdapter,
        ProviderCategoryReadiness readiness,
        String memberImpact,
        List<DomainAdapterCandidateResponse> candidates,
        List<String> violations,
        boolean failClosed,
        boolean supportSafe) {

    public DomainAdapterStatusResponse {
        domain = requireText(domain, "domain");
        label = requireText(label, "label");
        readiness = readiness == null ? ProviderCategoryReadiness.DISABLED : readiness;
        memberImpact = requireText(memberImpact, "memberImpact");
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        violations = violations == null ? List.of() : List.copyOf(violations);
        long activeCount = candidates.stream().filter(DomainAdapterCandidateResponse::active).count();
        if (!enabled || activeCount != 1) {
            activeAdapter = null;
        } else if (activeAdapter == null || activeAdapter.isBlank()) {
            activeAdapter = candidates.stream()
                    .filter(DomainAdapterCandidateResponse::active)
                    .findFirst()
                    .map(DomainAdapterCandidateResponse::adapterKey)
                    .orElse(null);
        }
        activeAdapter = activeAdapter == null || activeAdapter.isBlank() ? null : activeAdapter.trim();
    }

    public boolean singleActiveAdapterValid() {
        long activeCount = candidates.stream().filter(DomainAdapterCandidateResponse::active).count();
        return enabled ? activeCount == 1 : activeCount == 0;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

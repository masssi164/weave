package com.massimotter.weave.backend.devops.discovery;

import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryContract;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.List;
import java.util.Set;

/** Concrete discovery declarations owned by devops; shared policy contains no feature catalog. */
public final class DevopsDiscoveryMetadata {
    private DevopsDiscoveryMetadata() {}

    public static ProviderCategoryFragment releaseEvidenceCategory() {
        return new ProviderCategoryFragment("devops", 8, "release-evidence", "release evidence",
                "devops", false, null,
                ProviderRealityLevel.RELEASE_READY, Set.of(ProviderModule.RELEASE),
                new ProviderCategoryContract(
                    List.of("release_evidence.read", "release_evidence.manage", "release_notes.draft"),
                    List.of("release-evidence"),
                    List.of("weave-health-facade", "provider-stack-health"),
                    List.of("ReadinessCard", "Diagnostic", "SupportBundle", "BackupJob", "RestoreDrill", "EvidenceItem", "AuditRef"),
                    "Weave release evidence owns canonical release posture; external systems provide source evidence",
                    List.of("CI provider retention", "artifact redaction", "release-note category mapping"),
                    "export release notes and evidence refs; artifact deletion follows provider retention",
                    "release evidence replacement requires artifact/redaction/category dry-run"));
    }
}

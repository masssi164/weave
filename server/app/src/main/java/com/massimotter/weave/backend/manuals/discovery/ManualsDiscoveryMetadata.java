package com.massimotter.weave.backend.manuals.discovery;

import com.massimotter.weave.backend.discovery.catalog.ProviderCategorySelectionPolicy;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryContract;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.List;
import java.util.Set;

/** Concrete discovery declarations owned by manuals; shared policy contains no feature catalog. */
public final class ManualsDiscoveryMetadata {
    private ManualsDiscoveryMetadata() {}

    public static ProviderCategoryFragment manualsHelpCategory() {
        return new ProviderCategoryFragment("manuals",
                7,
                "manuals-help",
                "manuals/help",
                "manuals",
                false,
                null,
                ProviderRealityLevel.RELEASE_READY,
                Set.of(),
                ProviderCategorySelectionPolicy.metadataOnly(),
                new ProviderCategoryContract(
                    List.of("manuals.read", "manuals.admin", "help.search", "help.embed"),
                    List.of("mkdocs-material-embedded"),
                    List.of("confluence-space", "gitbook", "notion-wiki"),
                    List.of(
                        "ReadinessCard",
                        "Diagnostic",
                        "SupportBundle",
                        "BackupJob",
                        "RestoreDrill",
                        "EvidenceItem",
                        "AuditRef"),
                    "selected docs/help source owns content; Weave owns embedded accessible presentation and context linking",
                    List.of("search relevance", "permissions", "embedded accessibility", "stale content"),
                    "export pages/search index where supported; delete follows source repository policy",
                    "manuals replacement requires accessibility/search/permission dry-run"));
    }
}

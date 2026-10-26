package com.massimotter.weave.backend.office.discovery;

import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainRegistry;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryContract;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Concrete discovery declarations owned by office; shared policy contains no feature catalog. */
public final class OfficeDiscoveryMetadata {
    private OfficeDiscoveryMetadata() {}

    public static ProviderCategoryFragment documentsCollaborationCategory() {
        return new ProviderCategoryFragment("office", 5, "documents-collaboration", "documents/collaboration",
                null, true, "Document collaboration is available only through backend-owned launch/capability facades.",
                ProviderRealityLevel.CONTRACT_ONLY, Set.of(ProviderModule.OFFICE),
                new ProviderCategoryContract(
                    List.of("documents.view", "documents.edit", "documents.comment", "documents.collaborate"),
                    List.of("onlyoffice"),
                    List.of("collabora", "microsoft-365-office", "google-workspace-docs"),
                    List.of("Document", "EditSession", "Comment", "Suggestion", "CoauthorPresence", "Version", "Export"),
                    "document editor owns edit session; storage provider owns file bytes and versions",
                    List.of("co-edit locks", "comments", "track changes", "format fidelity", "license/edition constraints"),
                    "export document file and versions through storage provider; editor session data export depends on adapter",
                    "docs replacement requires WOPI/session/format fidelity dry-run and license/commercial-use risk note"));
    }

    public static CanonicalDomainContribution documentsDomain() {
        return new CanonicalDomainContribution("office", 4, CanonicalDomainRegistry.entry(
                "documents",
                "Documents",
                "Document collaboration sessions, editor launch, comments, format fidelity, locks, and storage/editor boundaries.",
                List.of("Document", "EditSession", "Comment", "Suggestion", "CoauthorPresence", "Version", "Export"),
                List.of("documents-collaboration"),
                CanonicalDomainRegistry.standardCapabilities("documents"),
                Map.of(
                    "collabora", "contract_only",
                    "onlyoffice", "contract_only",
                    "microsoft-365-office", "contract_only",
                    "google-workspace-docs", "contract_only")));
    }
}

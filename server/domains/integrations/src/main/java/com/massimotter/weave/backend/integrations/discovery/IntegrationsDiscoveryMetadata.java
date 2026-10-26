package com.massimotter.weave.backend.integrations.discovery;

import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryContract;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.List;
import java.util.Set;

/** Concrete discovery declarations owned by integrations; shared policy contains no feature catalog. */
public final class IntegrationsDiscoveryMetadata {
    private IntegrationsDiscoveryMetadata() {}

    public static ProviderCategoryFragment documentsFragment() {
        return new ProviderCategoryFragment("integrations", 5, "documents-collaboration", "documents/collaboration",
                null, true, "Document collaboration is available only through backend-owned launch/capability facades.", ProviderRealityLevel.CONTRACT_ONLY,
                Set.of(ProviderModule.CONTACTS, ProviderModule.FORMS),
                new ProviderCategoryContract(List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "document editor owns edit session; storage provider owns file bytes and versions",
                        List.of(),
                        "export document file and versions through storage provider; editor session data export depends on adapter",
                        "docs replacement requires WOPI/session/format fidelity dry-run and license/commercial-use risk note"));
    }
}

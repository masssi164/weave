package com.massimotter.weave.backend.decisions.discovery;

import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainRegistry;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryContract;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Concrete discovery declarations owned by decisions; shared policy contains no feature catalog. */
public final class DecisionsDiscoveryMetadata {
    private DecisionsDiscoveryMetadata() {}

    public static ProviderCategoryFragment decisionsEvidenceCategory() {
        return new ProviderCategoryFragment("decisions", 6, "decisions-evidence", "decisions/evidence",
                "decisions", false, null,
                ProviderRealityLevel.RELEASE_READY, Set.of(),
                new ProviderCategoryContract(
                    List.of("decisions.read", "decisions.record", "evidence.attach", "evidence.audit_read"),
                    List.of("weave-decision-ledger"),
                    List.of("openproject-wiki", "nextcloud-docs", "github-issues"),
                    List.of("Decision", "Proposal", "Approval", "Rationale", "DecisionLink", "EvidenceRef", "Supersession"),
                    "Weave owns canonical decision records unless imported source is explicitly declared authoritative",
                    List.of("external page permissions", "source citation drift", "status mapping"),
                    "export decision records, source refs, and audit refs",
                    "decision import/replacement requires citation and permission dry-run"));
    }

    public static CanonicalDomainContribution decisionsDomain() {
        return new CanonicalDomainContribution("decisions", 8, CanonicalDomainRegistry.entry(
                "decisions",
                "Decisions",
                "Weave-owned decisions and evidence references across chat, files, boards, calls, and calendar.",
                List.of("Decision", "Proposal", "Approval", "Rationale", "DecisionLink", "EvidenceRef", "Supersession"),
                List.of("decisions-evidence"),
                CanonicalDomainRegistry.standardCapabilities("decisions"),
                Map.of(
                    "weave-decision-ledger", "contract_only",
                    "openproject-wiki", "contract_only",
                    "nextcloud-docs", "contract_only",
                    "github-issues", "contract_only")));
    }
}

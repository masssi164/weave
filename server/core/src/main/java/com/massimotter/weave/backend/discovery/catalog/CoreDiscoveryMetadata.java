package com.massimotter.weave.backend.discovery.catalog;

import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Concrete discovery declarations owned by core; shared policy contains no feature catalog. */
public final class CoreDiscoveryMetadata {
    private CoreDiscoveryMetadata() {}

    public static ProviderCategoryFragment adminControlPlaneCategory() {
        return new ProviderCategoryFragment("core", 9, "admin-control-plane", "admin control plane",
                "admin-control-plane", false, null,
                ProviderRealityLevel.RELEASE_READY, Set.of(),
                new ProviderCategoryContract(
                    List.of("admin_control_plane.readiness_read", "admin_control_plane.adapter_select", "admin_control_plane.support_bundle"),
                    List.of("weave-health-facade"),
                    List.of("provider-stack-health", "release-evidence"),
                    List.of("ReadinessCard", "Diagnostic", "SupportBundle", "BackupJob", "RestoreDrill", "EvidenceItem", "AuditRef"),
                    "Weave admin control plane owns readiness and policy explanations; external tools may mirror or ticket events",
                    List.of("redaction vs usefulness", "ticket synchronization", "delegated operator scope"),
                    "export support-safe readiness/audit records; delete follows audit retention policy",
                    "admin tooling replacement requires policy/readiness/audit dry-run and delegated-scope review"));
    }

    public static CanonicalDomainContribution peopleDomain() {
        return new CanonicalDomainContribution("core", 0, CanonicalDomainRegistry.entry(
                "people",
                "People",
                "Provider-neutral people/profile directory separate from authentication credentials and email primary keys.",
                List.of("Person", "Membership", "Profile", "ContactMethod", "Team", "Guest", "ServiceAccountRef"),
                List.of(),
                CanonicalDomainRegistry.standardCapabilities("people"),
                Map.of("people-weave-owned", "release_ready")));
    }

    public static CanonicalDomainContribution spacesDomain() {
        return new CanonicalDomainContribution("core", 1, CanonicalDomainRegistry.entry(
                "spaces",
                "Spaces",
                "Cross-domain work context anchor binding chat, files, calendar, boards, calls, decisions, and optional agent context.",
                List.of("Space", "SpaceMembership", "RoleBinding", "LinkedChannel", "LinkedBoard", "LinkedCalendar", "LinkedFileRoot", "DecisionLedgerRef"),
                List.of(),
                CanonicalDomainRegistry.standardCapabilities("spaces"),
                Map.of("spaces-weave-owned", "release_ready")));
    }

    public static CanonicalDomainContribution healthDomain() {
        return new CanonicalDomainContribution("core", 10, CanonicalDomainRegistry.entry(
                "health",
                "Health",
                "Support-safe provider readiness, risk notes, support bundle references, migration runs, and secret reference state.",
                List.of("ReadinessCard", "Diagnostic", "SupportBundle", "BackupJob", "RestoreDrill", "EvidenceItem", "AuditRef"),
                List.of("admin-control-plane", "release-evidence", "manuals-help"),
                CanonicalDomainRegistry.standardCapabilities("health"),
                Map.of(
                    "weave-health-facade", "contract_only",
                    "provider-stack-health", "contract_only",
                    "release-evidence", "contract_only")));
    }
}

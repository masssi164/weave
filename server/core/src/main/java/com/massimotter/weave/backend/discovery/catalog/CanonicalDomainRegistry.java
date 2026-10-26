package com.massimotter.weave.backend.discovery.catalog;

import com.massimotter.weave.backend.discovery.api.CanonicalDomainRegistryEntryResponse;
import com.massimotter.weave.backend.discovery.api.CanonicalDomainRegistryResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CanonicalDomainRegistry {

    public static final String REGISTRY_VERSION = "canonical-domain-registry-v1";

    public static final List<String> MEMBER_STATES = List.of(
            "available",
            "disabled_by_policy",
            "not_configured",
            "degraded",
            "unavailable",
            "coming_later");

    public static final List<String> ADMIN_STATES = List.of(
            "provider_not_configured",
            "secret_missing",
            "ready",
            "degraded",
            "dry_run_required",
            "lossy_mapping_pending",
            "apply_blocked",
            "migration_ready");

    public static final List<String> LOSS_CLASSES = List.of(
            "lossless_canonical",
            "lossless_extension",
            "archive_only",
            "lossy_with_report",
            "blocked_nonportable",
            "provider_unexportable");

    public static final List<String> PROVIDER_REALITY_LEVELS = List.of(
            "contract_only",
            "configured",
            "live_read",
            "live_write",
            "migration_dry_run",
            "migration_apply_ready",
            "rollback_ready",
            "release_ready");

    private static final List<String> STANDARD_CAPABILITIES = List.of(
            "read", "search", "export", "import", "dryRun", "apply", "write", "delete", "adminConfigure");

    private static final List<String> SOURCE_OF_TRUTH_MODES = List.of(
            "weave_owned",
            "selected_provider_owned",
            "hybrid_composite",
            "external_existing_provider",
            "archive_only");

    private static final List<String> PORTABILITY_REQUIREMENTS = List.of(
            "source_of_truth_declared",
            "export_delete_boundary_declared",
            "provider_mapping_refs_required",
            "loss_classification_required",
            "dry_run_required_before_apply",
            "support_safe_migration_evidence_required",
            "audit_ref_required");

    private static final List<String> ADAPTER_MANIFEST_REQUIREMENTS = List.of(
            "supported_domain_key",
            "supported_capabilities",
            "canonical_object_coverage",
            "source_of_truth_mode",
            "export_manifest_support",
            "import_manifest_support",
            "lossy_mapping_report_support",
            "conflict_report_support",
            "secret_ref_only",
            "support_safe_diagnostics");

    private final List<CanonicalDomainRegistryEntryResponse> domains;

    public CanonicalDomainRegistry(List<CanonicalDomainContribution> contributions) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        java.util.Set<String> aliases = new java.util.HashSet<>();
        java.util.Set<String> owners = new java.util.HashSet<>();
        var ordered = contributions.stream().sorted(java.util.Comparator.comparingInt(CanonicalDomainContribution::order)
                .thenComparing(value -> value.entry().key())).toList();
        for (var value : ordered) {
            if (!owners.add(value.owner() + ":" + value.entry().key()) || !keys.add(value.entry().key())) {
                throw new IllegalArgumentException("Duplicate canonical domain ownership: " + value.entry().key());
            }
            for (String alias : value.entry().compatibilityAliases()) {
                if (!aliases.add(alias)) throw new IllegalArgumentException("Duplicate canonical alias: " + alias);
            }
        }
        if (keys.stream().anyMatch(aliases::contains)) {
            throw new IllegalArgumentException("Canonical aliases must not shadow installed domain keys");
        }
        this.domains = ordered.stream().map(CanonicalDomainContribution::entry).toList();
    }

    public CanonicalDomainRegistryResponse snapshot() {
        return new CanonicalDomainRegistryResponse(REGISTRY_VERSION, MEMBER_STATES, ADMIN_STATES, LOSS_CLASSES,
                PROVIDER_REALITY_LEVELS, domains, aliases(domains), true, false);
    }

    public List<CanonicalDomainRegistryEntryResponse> domains() {
        return domains;
    }

    /** Shared registry vocabulary; object/provider declarations are supplied by their actual owners. */
    public static CanonicalDomainRegistryEntryResponse entry(String key, String displayName, String purpose,
            List<String> canonicalObjects, List<String> aliases, List<String> capabilityKeys,
            Map<String, String> providerRealityLevels) {
        return new CanonicalDomainRegistryEntryResponse(key, 1, displayName, purpose, canonicalObjects,
                capabilityKeys, MEMBER_STATES, ADMIN_STATES, SOURCE_OF_TRUTH_MODES, PORTABILITY_REQUIREMENTS,
                ADAPTER_MANIFEST_REQUIREMENTS, aliases, providerRealityLevels);
    }

    public static List<String> standardCapabilities(String key) {
        return STANDARD_CAPABILITIES.stream().map(capability -> key + "." + capability).toList();
    }

    private static Map<String, String> aliases(List<CanonicalDomainRegistryEntryResponse> domains) {
        Map<String, String> aliases = new LinkedHashMap<>();
        for (var domain : domains) {
            for (String alias : domain.compatibilityAliases()) aliases.put(alias, domain.key());
        }
        return aliases;
    }
}

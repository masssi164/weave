package com.massimotter.weave.backend.files.discovery;

import com.massimotter.weave.backend.discovery.catalog.ProviderCategorySelectionPolicy;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainRegistry;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryContract;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Concrete discovery declarations owned by files; shared policy contains no feature catalog. */
public final class FilesDiscoveryMetadata {
    private FilesDiscoveryMetadata() {}

    public static ProviderCategoryFragment filesCategory() {
        return new ProviderCategoryFragment("files",
                1,
                "files",
                "files",
                "files",
                true,
                null,
                ProviderRealityLevel.RELEASE_READY,
                Set.of(ProviderModule.FILES),
                new ProviderCategorySelectionPolicy(true, false, "blocked-unverified",
                        "files-provider-activation-unverified",
                        "Files provider activation requires a verified organization-scoped replacement run."),
                new ProviderCategoryContract(
                    List.of("files.read", "files.upload", "files.download", "files.delete"),
                    List.of("weave-native"),
                    List.of("nextcloud-files", "sharepoint", "onedrive", "s3-compatible", "smb"),
                    List.of("WeaveDrive", "WeaveFolder", "WeaveFile", "WeaveVersion", "WeaveShare", "WeavePermission", "WeaveLock", "WeaveQuota", "ProviderRef"),
                    "selected storage provider owns file bytes and native permissions; Weave owns canonical references and member impact states",
                    List.of("public links", "provider-specific shares", "version history", "locks", "external users", "storage quotas"),
                    "export file tree, permissions, versions where available; delete follows provider and retention policy",
                    "files replacement requires dry-run for permissions, versions, links, storage quota, and binary transfer feasibility"));
    }

    public static CanonicalDomainContribution filesDomain() {
        return new CanonicalDomainContribution("files", 3, CanonicalDomainRegistry.entry(
                "files",
                "Files",
                "Storage, file tree, versions, shares, permissions, locks, checksums, trash, and binary portability facade.",
                List.of("WeaveDrive", "WeaveFolder", "WeaveFile", "WeaveVersion", "WeaveShare", "WeavePermission", "WeaveLock", "WeaveQuota", "ProviderRef"),
                List.of(),
                CanonicalDomainRegistry.standardCapabilities("files"),
                Map.of(
                    "nextcloud-files", "release_ready",
                    "sharepoint", "contract_only",
                    "onedrive", "contract_only",
                    "s3-compatible", "live_write",
                    "smb", "live_write")));
    }
}

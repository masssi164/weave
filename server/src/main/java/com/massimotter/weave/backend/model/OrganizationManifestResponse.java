package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Authenticated organization manifest consumed by member Weave clients after org URL discovery and SSO.")
public record OrganizationManifestResponse(
        @Schema(description = "Contract version for the member-client manifest.", example = "org-manifest-v1")
        String manifestVersion,
        @Schema(description = "Stable organization identifier; not a provider tenant secret.", example = "weave-dogfood")
        String organizationId,
        @Schema(description = "Member-visible organization name.", example = "Weave Dogfood")
        String displayName,
        @Schema(description = "The organization auth URL or invite/deep-link origin the member client may use for SSO.")
        String organizationAuthUrl,
        @Schema(description = "When this support-safe manifest was generated.")
        Instant generatedAt,
        @Schema(description = "True when the manifest excludes provider secrets, endpoint rotation data, and raw diagnostics.")
        boolean supportSafe,
        @Schema(description = "False: member clients must not receive raw provider setup or endpoint rotation configuration.")
        boolean providerConfigurationExposed,
        @Schema(description = "False: member clients must not receive admin/provider diagnostics.")
        boolean diagnosticsExposed,
        @Schema(description = "Control plane that owns provider/tool/agent whitelisting.", example = "organization-admin-console")
        String whitelistingOwner,
        @Schema(description = "Responsibilities owned by the member Weave Client.")
        List<String> clientResponsibilities,
        @Schema(description = "Responsibilities owned by the Organization/Admin Console.")
        List<String> adminConsoleResponsibilities,
        @Schema(description = "Stable member-visible capability states keyed by provider-neutral Weave domain. Values are available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later.")
        Map<String, CapabilityManifestState> memberCapabilityStates,
        @Schema(description = "Effective member capability snapshot; provider setup and diagnostics stay out of this contract.")
        WorkspaceCapabilitiesResponse capabilities) {

    public OrganizationManifestResponse {
        clientResponsibilities = clientResponsibilities == null ? List.of() : List.copyOf(clientResponsibilities);
        adminConsoleResponsibilities = adminConsoleResponsibilities == null ? List.of() : List.copyOf(adminConsoleResponsibilities);
        memberCapabilityStates = memberCapabilityStates == null ? Map.of() : Map.copyOf(memberCapabilityStates);
    }
}

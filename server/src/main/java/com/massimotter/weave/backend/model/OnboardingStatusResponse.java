package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "First-run status for authenticated Weave users and admin onboarding flows.")
public record OnboardingStatusResponse(
        @Schema(description = "Canonical identity and profile data needed for first-run routing.")
        Identity identity,
        @Schema(description = "Invite or activation status derived from trusted identity claims.")
        InviteStatus invite,
        @Schema(description = "Product role mapping for first-run access decisions.")
        Access access,
        @Schema(description = "Profile completeness needed before landing in the full workspace.")
        ProfileStatus profile,
        @Schema(description = "Provisioning status for downstream collaboration modules.")
        ModuleProvisioning moduleProvisioning,
        @Schema(description = "True when invite, profile, and required modules are ready.")
        boolean firstRunComplete,
        @Schema(description = "Frontend-safe next steps for statuses that are not ready.")
        List<String> actions) {

    public record Identity(
            String userId,
            String username,
            String email,
            boolean emailVerified,
            String displayName,
            String locale,
            String timezone,
            List<String> roles,
            List<String> groups) {
    }

    public record InviteStatus(
            @Schema(description = "active, pending, or disabled.", example = "active")
            String status,
            String message,
            String action) {
    }

    public record Access(
            @Schema(description = "Highest MVP product role for routing: owner, admin, member, guest, or unassigned.")
            String primaryRole,
            List<String> roles,
            List<String> groups,
            boolean canAdministerWorkspace,
            boolean canInviteUsers,
            boolean canUseWorkspaceModules) {
    }

    public record ProfileStatus(
            @Schema(description = "ready or pending.", example = "ready")
            String status,
            List<String> missing,
            String message,
            String action) {
    }

    public record ModuleProvisioning(
            ModuleStatus identity,
            ModuleStatus profile,
            ModuleStatus matrix,
            ModuleStatus nextcloud) {
    }

    public record ModuleStatus(
            OnboardingProvisioningState state,
            String message,
            String action) {
    }
}

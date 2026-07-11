package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.OnboardingStatusProperties;
import com.massimotter.weave.backend.config.PlatformContractProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.OnboardingProvisioningState;
import com.massimotter.weave.backend.model.OnboardingStatusResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class OnboardingStatusService {

    private static final List<String> ROLE_PRIORITY = List.of("owner", "admin", "member", "guest");

    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final WeaveSecurityProperties securityProperties;
    private final WorkspaceCapabilityProperties workspaceProperties;
    private final PlatformContractProperties platformProperties;
    private final OnboardingStatusProperties onboardingProperties;
    private final MemberInvitationService memberInvitationService;

    @Autowired
    public OnboardingStatusService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties securityProperties,
            WorkspaceCapabilityProperties workspaceProperties,
            PlatformContractProperties platformProperties,
            OnboardingStatusProperties onboardingProperties,
            MemberInvitationService memberInvitationService) {
        this.resourceServerProperties = resourceServerProperties;
        this.securityProperties = securityProperties;
        this.workspaceProperties = workspaceProperties;
        this.platformProperties = platformProperties;
        this.onboardingProperties = onboardingProperties;
        this.memberInvitationService = memberInvitationService;
    }

    OnboardingStatusService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties securityProperties,
            WorkspaceCapabilityProperties workspaceProperties,
            PlatformContractProperties platformProperties,
            OnboardingStatusProperties onboardingProperties) {
        this(resourceServerProperties, securityProperties, workspaceProperties, platformProperties,
                onboardingProperties, null);
    }

    public OnboardingStatusResponse status(Jwt jwt) {
        OrganizationIdentityContext organizationIdentity = OrganizationIdentityContextFactory.fromJwt(jwt);
        String username = firstText(jwt.getClaimAsString("preferred_username"), organizationIdentity.subject());
        String email = jwt.getClaimAsString("email");
        boolean emailVerified = emailVerified(jwt);
        String displayName = firstText(jwt.getClaimAsString("name"), username);
        String locale = firstText(jwt.getClaimAsString("locale"), "en");
        String timezone = firstText(jwt.getClaimAsString("timezone"), "UTC");
        List<String> roles = organizationIdentity.roles();
        List<String> groups = organizationIdentity.groups();

        OnboardingStatusResponse.Identity identity = new OnboardingStatusResponse.Identity(
                organizationIdentity.accountId(),
                username,
                email,
                emailVerified,
                displayName,
                locale,
                timezone,
                roles,
                groups);
        OnboardingStatusResponse.InviteStatus invite = inviteStatus(jwt, email, emailVerified);
        OnboardingStatusResponse.Access access = access(roles, groups);
        OnboardingStatusResponse.ProfileStatus profile = profileStatus(username, email, emailVerified, displayName, locale, timezone);
        OnboardingStatusResponse.ModuleProvisioning moduleProvisioning = moduleProvisioning(profile);

        List<String> actions = actions(invite, profile, moduleProvisioning);
        boolean firstRunComplete = "active".equals(invite.status())
                && "ready".equals(profile.status())
                && moduleProvisioning.identity().state() == OnboardingProvisioningState.READY
                && moduleProvisioning.profile().state() == OnboardingProvisioningState.READY
                && moduleProvisioning.matrix().state() == OnboardingProvisioningState.READY
                && moduleProvisioning.nextcloud().state() == OnboardingProvisioningState.READY;

        return new OnboardingStatusResponse(
                identity,
                invite,
                access,
                profile,
                moduleProvisioning,
                firstRunComplete,
                actions);
    }

    private OnboardingStatusResponse.InviteStatus inviteStatus(Jwt jwt, String email, boolean emailVerified) {
        if (!hasText(email) || !emailVerified) {
            return new OnboardingStatusResponse.InviteStatus(
                    "pending",
                    "The account can authenticate, but email verification is not complete yet.",
                    "Verify the email address in the identity provider, then sign in again.");
        }
        if (memberInvitationService != null) memberInvitationService.reconcileAuthenticated(jwt);
        return new OnboardingStatusResponse.InviteStatus(
                "active",
                "The invite has been accepted and the account is active for Weave.",
                null);
    }

    private OnboardingStatusResponse.Access access(List<String> roles, List<String> groups) {
        String primaryRole = ROLE_PRIORITY.stream()
                .filter(roles::contains)
                .findFirst()
                .orElse("unassigned");
        return new OnboardingStatusResponse.Access(
                primaryRole,
                roles,
                groups,
                "owner".equals(primaryRole) || "admin".equals(primaryRole),
                "owner".equals(primaryRole) || "admin".equals(primaryRole),
                List.of("owner", "admin", "member").contains(primaryRole));
    }

    private OnboardingStatusResponse.ProfileStatus profileStatus(
            String username,
            String email,
            boolean emailVerified,
            String displayName,
            String locale,
            String timezone) {
        List<String> missing = new ArrayList<>();
        if (!hasText(username)) {
            missing.add("username");
        }
        if (!hasText(email)) {
            missing.add("email");
        }
        if (hasText(email) && !emailVerified) {
            missing.add("email_verified");
        }
        if (!hasText(displayName)) {
            missing.add("display_name");
        }
        if (!hasText(locale)) {
            missing.add("locale");
        }
        if (!hasText(timezone)) {
            missing.add("timezone");
        }

        if (missing.isEmpty()) {
            return new OnboardingStatusResponse.ProfileStatus(
                    "ready",
                    List.of(),
                    "The Weave profile has the required first-run identity fields.",
                    null);
        }
        return new OnboardingStatusResponse.ProfileStatus(
                "pending",
                List.copyOf(missing),
                "The Weave profile is missing required first-run fields.",
                "Complete the missing profile fields before using the full workspace.");
    }

    private OnboardingStatusResponse.ModuleProvisioning moduleProvisioning(
            OnboardingStatusResponse.ProfileStatus profile) {
        OnboardingStatusResponse.ModuleStatus identity = new OnboardingStatusResponse.ModuleStatus(
                OnboardingProvisioningState.READY,
                "Identity is available from the authenticated OIDC/SAML session.",
                null);
        OnboardingProvisioningState profileState = "ready".equals(profile.status())
                ? OnboardingProvisioningState.READY
                : OnboardingProvisioningState.PENDING;
        OnboardingStatusResponse.ModuleStatus profileModule = new OnboardingStatusResponse.ModuleStatus(
                profileState,
                profileState == OnboardingProvisioningState.READY
                        ? "The product profile is ready for module synchronization."
                        : "The product profile is waiting for required fields before module synchronization.",
                profile.action());
        OnboardingStatusResponse.ModuleStatus matrix = moduleStatus(
                "Matrix chat",
                onboardingProperties.matrix().provisioningState(),
                workspaceProperties.chat(),
                workspaceProperties.chat().dependencyUrl(),
                "Enable WEAVE_WORKSPACE_CHAT_ENABLED and configure WEAVE_MATRIX_BASE_URL before first-run chat provisioning; the member facade stays on the API origin.");
        WorkspaceCapabilityProperties.Capability nextcloudCapability = workspaceProperties.files().enabled()
                ? workspaceProperties.files()
                : workspaceProperties.calendar();
        String nextcloudDependencyUrl = firstText(nextcloudCapability.dependencyUrl(), platformProperties.nextcloudBaseUrl());
        OnboardingStatusResponse.ModuleStatus nextcloud = moduleStatus(
                "Nextcloud files/calendar",
                onboardingProperties.nextcloud().provisioningState(),
                nextcloudCapability,
                nextcloudDependencyUrl,
                "Enable files or calendar and configure WEAVE_NEXTCLOUD_BASE_URL before first-run Nextcloud provisioning.");
        return new OnboardingStatusResponse.ModuleProvisioning(identity, profileModule, matrix, nextcloud);
    }

    private OnboardingStatusResponse.ModuleStatus moduleStatus(
            String label,
            OnboardingProvisioningState explicitState,
            WorkspaceCapabilityProperties.Capability capability,
            String dependencyUrl,
            String notConfiguredAction) {
        OnboardingProvisioningState state = explicitState != null
                ? explicitState
                : derivedProvisioningState(capability, dependencyUrl);
        String message = switch (state) {
            case NOT_CONFIGURED -> label + " provisioning is not configured for this workspace runtime.";
            case PENDING -> label + " provisioning is pending and may still be completing.";
            case READY -> label + " provisioning is ready for this user.";
            case DEGRADED -> label + " provisioning is available but degraded.";
            case FAILED -> label + " provisioning failed or is blocked by the backend runtime contract.";
        };
        String action = switch (state) {
            case NOT_CONFIGURED -> notConfiguredAction;
            case PENDING -> "Wait briefly, then retry. If this persists, ask a workspace admin to check module provisioning.";
            case READY -> null;
            case DEGRADED -> "Check the configured module route and downstream service health.";
            case FAILED -> "Ask a workspace admin to inspect backend auth and module provisioning diagnostics.";
        };
        return new OnboardingStatusResponse.ModuleStatus(state, message, action);
    }

    private OnboardingProvisioningState derivedProvisioningState(
            WorkspaceCapabilityProperties.Capability capability,
            String dependencyUrl) {
        if (capability == null || !capability.enabled()) {
            return OnboardingProvisioningState.NOT_CONFIGURED;
        }
        if (shellAccessReadiness() == WorkspaceCapabilityReadiness.BLOCKED) {
            return OnboardingProvisioningState.FAILED;
        }
        if (capability.readiness() != null) {
            return switch (capability.readiness()) {
                case READY -> OnboardingProvisioningState.READY;
                case DEGRADED -> OnboardingProvisioningState.DEGRADED;
                case BLOCKED -> OnboardingProvisioningState.FAILED;
                case UNAVAILABLE -> OnboardingProvisioningState.NOT_CONFIGURED;
            };
        }
        if (hasText(dependencyUrl)) {
            return OnboardingProvisioningState.READY;
        }
        return OnboardingProvisioningState.DEGRADED;
    }

    private WorkspaceCapabilityReadiness shellAccessReadiness() {
        if (!workspaceProperties.shellAccess().enabled()) {
            return WorkspaceCapabilityReadiness.UNAVAILABLE;
        }
        boolean hasIssuer = hasText(resourceServerProperties.getJwt().getIssuerUri());
        boolean hasAudience = hasText(securityProperties.requiredAudience());
        boolean hasClientId = hasText(securityProperties.clientId());
        return hasIssuer && hasAudience && hasClientId
                ? WorkspaceCapabilityReadiness.READY
                : WorkspaceCapabilityReadiness.BLOCKED;
    }

    private List<String> actions(
            OnboardingStatusResponse.InviteStatus invite,
            OnboardingStatusResponse.ProfileStatus profile,
            OnboardingStatusResponse.ModuleProvisioning modules) {
        return Stream.of(
                        invite.action(),
                        profile.action(),
                        modules.identity().action(),
                        modules.profile().action(),
                        modules.matrix().action(),
                        modules.nextcloud().action())
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private boolean emailVerified(Jwt jwt) {
        Object value = jwt.getClaims().get("email_verified");
        return value instanceof Boolean verified && verified;
    }

    private String firstText(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

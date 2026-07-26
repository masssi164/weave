package com.massimotter.weave.backend.model.identity;

import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ProviderInvitation;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntent;
import java.time.Instant;
import java.util.List;

public record MemberInvitationResponse(
        String invitationHandle,
        String organizationId,
        String email,
        String displayName,
        String lifecycleStatus,
        String provisioningStatus,
        String requestedRole,
        List<String> capabilities,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {

    public static MemberInvitationResponse from(
            String invitationHandle, ProviderInvitation invitation, ProvisioningIntent intent) {
        return new MemberInvitationResponse(invitationHandle, intent.tenantId(),
                invitation.email(), invitation.displayName(), invitation.lifecycleStatus(),
                intent.status().name().toLowerCase(), intent.requestedRole(), intent.requestedCapabilities(),
                invitation.expiresAt(), invitation.createdAt(), intent.updatedAt());
    }

    public static MemberInvitationResponse withoutProvisioning(
            String invitationHandle, ProviderInvitation invitation, String organizationId) {
        return new MemberInvitationResponse(invitationHandle, organizationId, invitation.email(),
                invitation.displayName(), invitation.lifecycleStatus(), "not_requested", null, List.of(),
                invitation.expiresAt(), invitation.createdAt(), null);
    }
}

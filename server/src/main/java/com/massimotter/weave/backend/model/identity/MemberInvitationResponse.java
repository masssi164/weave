package com.massimotter.weave.backend.model.identity;

import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ProviderInvitation;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntent;
import java.time.Instant;
import java.util.List;

public record MemberInvitationResponse(
        String providerInvitationId,
        String organizationId,
        String email,
        String displayName,
        String lifecycleStatus,
        String provisioningStatus,
        String requestedRole,
        List<String> organizationGroups,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {

    public static MemberInvitationResponse from(ProviderInvitation invitation, ProvisioningIntent intent) {
        return new MemberInvitationResponse(invitation.providerInvitationId(), intent.organizationId(),
                invitation.email(), invitation.displayName(), invitation.lifecycleStatus(),
                intent.status().name().toLowerCase(), intent.requestedRole(), intent.organizationGroups(),
                invitation.expiresAt(), invitation.createdAt(), intent.updatedAt());
    }

    public static MemberInvitationResponse withoutProvisioning(ProviderInvitation invitation, String organizationId) {
        return new MemberInvitationResponse(invitation.providerInvitationId(), organizationId, invitation.email(),
                invitation.displayName(), invitation.lifecycleStatus(), "not_requested", null, List.of(),
                invitation.expiresAt(), invitation.createdAt(), null);
    }
}

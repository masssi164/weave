package com.massimotter.weave.backend.model.identity;

import com.massimotter.weave.backend.identity.invitation.MemberInvitation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MemberInvitationResponse(
        UUID invitationId,
        String organizationId,
        String email,
        String displayName,
        String role,
        List<String> workspaceIds,
        String status,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {

    public static MemberInvitationResponse from(MemberInvitation invitation) {
        return new MemberInvitationResponse(invitation.invitationId(), invitation.organizationId(),
                invitation.invitedEmail(), invitation.displayName(), invitation.requestedRole(),
                invitation.workspaceIds(), invitation.status().name().toLowerCase(), invitation.expiresAt(),
                invitation.createdAt(), invitation.updatedAt());
    }
}

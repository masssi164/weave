package com.massimotter.weave.backend.identity.invitation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MemberInvitation(
        UUID invitationId,
        String tenantId,
        String organizationId,
        String invitedEmail,
        String displayName,
        String requestedRole,
        List<String> workspaceIds,
        MemberInvitationStatus status,
        String providerInvitationId,
        String invitedBySubject,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {

    public MemberInvitation {
        workspaceIds = workspaceIds == null ? List.of() : List.copyOf(workspaceIds);
    }

    public MemberInvitation withProviderInvitation(String providerId, Instant now) {
        return new MemberInvitation(invitationId, tenantId, organizationId, invitedEmail, displayName,
                requestedRole, workspaceIds, MemberInvitationStatus.SENT, providerId,
                invitedBySubject, expiresAt, createdAt, now);
    }

    public MemberInvitation withStatus(MemberInvitationStatus nextStatus, Instant now) {
        return new MemberInvitation(invitationId, tenantId, organizationId, invitedEmail, displayName,
                requestedRole, workspaceIds, nextStatus, providerInvitationId,
                invitedBySubject, expiresAt, createdAt, now);
    }
}

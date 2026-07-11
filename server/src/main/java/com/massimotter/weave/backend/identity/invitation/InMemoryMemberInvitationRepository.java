package com.massimotter.weave.backend.identity.invitation;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "weave.identity.invitations.storage-mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryMemberInvitationRepository implements MemberInvitationRepository {
    private final Map<UUID, MemberInvitation> invitations = new ConcurrentHashMap<>();

    @Override
    public MemberInvitation save(MemberInvitation invitation) {
        invitations.put(invitation.invitationId(), invitation);
        return invitation;
    }

    @Override
    public Optional<MemberInvitation> findById(UUID invitationId) {
        return Optional.ofNullable(invitations.get(invitationId));
    }

    @Override
    public Optional<MemberInvitation> findPendingByEmail(String tenantId, String organizationId, String email) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        return invitations.values().stream()
                .filter(invitation -> invitation.tenantId().equals(tenantId))
                .filter(invitation -> invitation.organizationId().equals(organizationId))
                .filter(invitation -> invitation.invitedEmail().equalsIgnoreCase(normalizedEmail))
                .filter(invitation -> invitation.status() == MemberInvitationStatus.PENDING
                        || invitation.status() == MemberInvitationStatus.SENT)
                .max(Comparator.comparing(MemberInvitation::createdAt));
    }

    @Override
    public List<MemberInvitation> findByOrganization(String tenantId, String organizationId) {
        return invitations.values().stream()
                .filter(invitation -> invitation.tenantId().equals(tenantId))
                .filter(invitation -> invitation.organizationId().equals(organizationId))
                .sorted(Comparator.comparing(MemberInvitation::createdAt).reversed())
                .toList();
    }

    @Override
    public void markApplied(MemberInvitation invitation) {
        invitations.put(invitation.invitationId(), invitation);
    }
}

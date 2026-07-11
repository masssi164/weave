package com.massimotter.weave.backend.identity.invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberInvitationRepository {
    MemberInvitation save(MemberInvitation invitation);
    Optional<MemberInvitation> findById(UUID invitationId);
    Optional<MemberInvitation> findPendingByEmail(String tenantId, String organizationId, String email);
    List<MemberInvitation> findByOrganization(String tenantId, String organizationId);
    void markApplied(MemberInvitation invitation);
}

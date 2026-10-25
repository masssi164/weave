package com.massimotter.weave.backend.identity.invitation.port;

import com.massimotter.weave.backend.identity.invitation.domain.ProvisioningIntent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProvisioningIntentRepository {
    ProvisioningIntent save(ProvisioningIntent intent);
    Optional<ProvisioningIntent> findById(UUID intentId);
    Optional<ProvisioningIntent> findByProviderInvitationId(String providerInvitationId);
    List<ProvisioningIntent> findByEmail(String tenantId, String organizationId, String email);
    List<ProvisioningIntent> findPendingByEmail(String tenantId, String organizationId, String email);
    List<ProvisioningIntent> findByActorAndCorrelation(String tenantId, String organizationId,
            String invitedByIssuer, String invitedBySubject, String auditCorrelation);
    List<ProvisioningIntent> findPendingByActor(
            String tenantId,
            String organizationId,
            String invitedByIssuer,
            String invitedBySubject);
}

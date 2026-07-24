package com.massimotter.weave.backend.identity.invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProvisioningIntentRepository {
    ProvisioningIntent save(ProvisioningIntent intent);
    Optional<ProvisioningIntent> findById(UUID intentId);
    Optional<ProvisioningIntent> findByProviderInvitationId(String providerInvitationId);
    List<ProvisioningIntent> findPendingByEmail(String tenantId, String organizationId, String email);
}

package com.massimotter.weave.backend.persistence.jpa.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvisioningIntentJpaRepository
    extends JpaRepository<ProvisioningIntentJpaEntity, UUID> {
  Optional<ProvisioningIntentJpaEntity> findByProviderInvitationId(String providerInvitationId);

  List<ProvisioningIntentJpaEntity>
      findByTenantIdAndOrganizationIdAndInvitedEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
          String tenantId, String organizationId, String invitedEmail, String status);
}

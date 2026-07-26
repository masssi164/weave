package com.massimotter.weave.backend.persistence.jpa.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvisioningIntentJpaRepository
    extends JpaRepository<ProvisioningIntentEntity, UUID> {
  Optional<ProvisioningIntentEntity> findByProviderInvitationId(String providerInvitationId);

  List<ProvisioningIntentEntity>
      findByTenantIdAndOrganizationIdAndInvitedEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
          String tenantId, String organizationId, String invitedEmail, String status);

  List<ProvisioningIntentEntity>
      findByOrganizationIdAndInvitedEmailSha256AndStatusOrderByCreatedAtDesc(
          String organizationId, String invitedEmailSha256, String status);
}

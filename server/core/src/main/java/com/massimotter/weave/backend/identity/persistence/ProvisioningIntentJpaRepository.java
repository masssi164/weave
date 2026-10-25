package com.massimotter.weave.backend.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvisioningIntentJpaRepository
    extends JpaRepository<ProvisioningIntentJpaEntity, UUID> {
  List<ProvisioningIntentJpaEntity>
      findByTenantIdAndOrganizationIdAndInvitedByIssuerAndInvitedBySubjectAndAuditCorrelation(
          String tenantId, String organizationId, String invitedByIssuer, String invitedBySubject,
          String auditCorrelation);

  Optional<ProvisioningIntentJpaEntity> findByProviderInvitationId(String providerInvitationId);

  List<ProvisioningIntentJpaEntity> findByTenantIdAndOrganizationIdAndInvitedEmailIgnoreCase(
      String tenantId, String organizationId, String invitedEmail);

  List<ProvisioningIntentJpaEntity>
      findByTenantIdAndOrganizationIdAndInvitedEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
          String tenantId, String organizationId, String invitedEmail, String status);

  List<ProvisioningIntentJpaEntity>
      findByTenantIdAndOrganizationIdAndInvitedByIssuerAndInvitedBySubjectAndStatusOrderByCreatedAtDesc(
          String tenantId,
          String organizationId,
          String invitedByIssuer,
          String invitedBySubject,
          String status);
}

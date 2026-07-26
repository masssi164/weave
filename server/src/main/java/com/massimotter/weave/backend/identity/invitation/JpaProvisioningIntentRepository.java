package com.massimotter.weave.backend.identity.invitation;

import com.massimotter.weave.backend.persistence.jpa.identity.ProvisioningIntentJpaEntity;
import com.massimotter.weave.backend.persistence.jpa.identity.ProvisioningIntentJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class JpaProvisioningIntentRepository implements ProvisioningIntentRepository {

    private final ProvisioningIntentJpaRepository intents;

    public JpaProvisioningIntentRepository(ProvisioningIntentJpaRepository intents) {
        this.intents = intents;
    }

    @Override
    @Transactional
    public ProvisioningIntent save(ProvisioningIntent intent) {
        intents.saveAndFlush(toEntity(intent));
        return intent;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProvisioningIntent> findById(UUID id) {
        return intents.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProvisioningIntent> findByProviderInvitationId(String id) {
        return intents.findByProviderInvitationId(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProvisioningIntent> findPendingByEmail(String tenant, String org, String email) {
        return intents
                .findByTenantIdAndOrganizationIdAndInvitedEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        tenant, org, email, ProvisioningIntentStatus.PENDING.name())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private ProvisioningIntentJpaEntity toEntity(ProvisioningIntent value) {
        return new ProvisioningIntentJpaEntity(
                value.intentId(),
                value.tenantId(),
                value.organizationId(),
                value.invitedEmail(),
                value.invitedEmailSha256(),
                value.requestedRole(),
                value.providerInvitationId(),
                value.invitedByIssuer(),
                value.invitedBySubject(),
                value.auditCorrelation(),
                value.status().name(),
                value.appliedSubject(),
                value.failureCode(),
                value.expiresAt(),
                value.createdAt(),
                value.updatedAt());
    }

    private ProvisioningIntent toDomain(ProvisioningIntentJpaEntity value) {
        return new ProvisioningIntent(
                value.intentId(),
                value.tenantId(),
                value.organizationId(),
                value.invitedEmail(),
                value.invitedEmailSha256(),
                value.requestedRole(),
                value.providerInvitationId(),
                value.invitedByIssuer(),
                value.invitedBySubject(),
                value.auditCorrelation(),
                ProvisioningIntentStatus.valueOf(value.status()),
                value.appliedSubject(),
                value.failureCode(),
                value.expiresAt(),
                value.createdAt(),
                value.updatedAt());
    }

}

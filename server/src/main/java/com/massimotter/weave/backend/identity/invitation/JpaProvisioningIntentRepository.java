package com.massimotter.weave.backend.identity.invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Objects.requireNonNull;

/**
 * JPA adapter for invite-first provisioning work state.
 *
 * <p>The intent is versioned in place and is not an organization-membership authorization source.
 */
@Repository
@Transactional(readOnly = true)
public class JpaProvisioningIntentRepository implements ProvisioningIntentRepository {

    private final ProvisioningIntentJpaRepository intents;

    public JpaProvisioningIntentRepository(ProvisioningIntentJpaRepository intents) {
        this.intents = requireNonNull(intents, "intents");
    }

    @Override
    @Transactional
    public ProvisioningIntent save(ProvisioningIntent intent) {
        ProvisioningIntent requested = requireNonNull(intent, "intent");
        ProvisioningIntentJpaEntity entity = intents.findById(requested.intentId())
                .orElseGet(() -> ProvisioningIntentJpaEntity.create(requested));
        entity.apply(requested);
        return intents.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<ProvisioningIntent> findById(UUID id) {
        return intents.findById(requireNonNull(id, "id"))
                .map(ProvisioningIntentJpaEntity::toDomain);
    }

    @Override
    public Optional<ProvisioningIntent> findByProviderInvitationId(String id) {
        return intents.findByProviderInvitationId(requireNonNull(id, "id"))
                .map(ProvisioningIntentJpaEntity::toDomain);
    }

    @Override
    public List<ProvisioningIntent> findPendingByEmail(
            String tenant,
            String organization,
            String email) {
        return intents
                .findByTenantIdAndOrganizationIdAndInvitedEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        tenant,
                        organization,
                        email,
                        ProvisioningIntentStatus.PENDING)
                .stream()
                .map(ProvisioningIntentJpaEntity::toDomain)
                .toList();
    }

}

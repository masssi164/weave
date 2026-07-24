package com.massimotter.weave.backend.identity.invitation;

import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Objects.requireNonNull;

/**
 * JPA adapter for invite-first provisioning work state.
 *
 * <p>The intent is versioned in place; the event receipt is an immutable deduplication ledger.
 * Neither is an organization-membership authorization source.
 */
@Repository
@Transactional(readOnly = true)
public class JpaProvisioningIntentRepository implements ProvisioningIntentRepository {

    private final ProvisioningIntentJpaRepository intents;
    private final IdentityEventReceiptJpaRepository eventReceipts;

    public JpaProvisioningIntentRepository(
            ProvisioningIntentJpaRepository intents,
            IdentityEventReceiptJpaRepository eventReceipts) {
        this.intents = requireNonNull(intents, "intents");
        this.eventReceipts = requireNonNull(eventReceipts, "eventReceipts");
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

    @Override
    public List<ProvisioningIntent> findPendingByEmailHash(
            String organization,
            String hash) {
        return intents
                .findByOrganizationIdAndInvitedEmailSha256AndStatusOrderByCreatedAtDesc(
                        organization,
                        hash,
                        ProvisioningIntentStatus.PENDING)
                .stream()
                .map(ProvisioningIntentJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public boolean recordEventOnce(String eventId, Instant occurredAt) {
        String safeEventId = requireNonNull(eventId, "eventId");
        if (eventReceipts.existsById(safeEventId)) {
            return false;
        }
        try {
            eventReceipts.saveAndFlush(IdentityEventReceiptJpaEntity.create(
                    safeEventId,
                    requireNonNull(occurredAt, "occurredAt"),
                    Instant.now()));
            return true;
        } catch (DataIntegrityViolationException | PersistenceException duplicateOrFailure) {
            if (eventReceipts.existsById(safeEventId)) {
                return false;
            }
            throw duplicateOrFailure;
        }
    }

}

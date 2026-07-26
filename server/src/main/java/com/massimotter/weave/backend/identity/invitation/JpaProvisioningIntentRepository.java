package com.massimotter.weave.backend.identity.invitation;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.persistence.jpa.identity.KeycloakEventReceiptEntity;
import com.massimotter.weave.backend.persistence.jpa.identity.KeycloakEventReceiptJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.identity.ProvisioningIntentEntity;
import com.massimotter.weave.backend.persistence.jpa.identity.ProvisioningIntentJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

public class JpaProvisioningIntentRepository implements ProvisioningIntentRepository {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final ProvisioningIntentJpaRepository intents;
    private final KeycloakEventReceiptJpaRepository eventReceipts;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JpaProvisioningIntentRepository(
            ProvisioningIntentJpaRepository intents,
            KeycloakEventReceiptJpaRepository eventReceipts,
            ObjectMapper objectMapper,
            Clock clock) {
        this.intents = intents;
        this.eventReceipts = eventReceipts;
        this.objectMapper = objectMapper;
        this.clock = clock;
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

    @Override
    @Transactional(readOnly = true)
    public List<ProvisioningIntent> findPendingByEmailHash(String org, String hash) {
        return intents
                .findByOrganizationIdAndInvitedEmailSha256AndStatusOrderByCreatedAtDesc(
                        org, hash, ProvisioningIntentStatus.PENDING.name())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public boolean recordEventOnce(String eventId, Instant occurredAt) {
        try {
            eventReceipts.saveAndFlush(new KeycloakEventReceiptEntity(eventId, occurredAt, clock.instant()));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    private ProvisioningIntentEntity toEntity(ProvisioningIntent value) {
        return new ProvisioningIntentEntity(
                value.intentId(),
                value.tenantId(),
                value.organizationId(),
                value.invitedEmail(),
                value.invitedEmailSha256(),
                value.requestedRole(),
                json(value.requestedCapabilities()),
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

    private ProvisioningIntent toDomain(ProvisioningIntentEntity value) {
        return new ProvisioningIntent(
                value.intentId(),
                value.tenantId(),
                value.organizationId(),
                value.invitedEmail(),
                value.invitedEmailSha256(),
                value.requestedRole(),
                strings(value.requestedCapabilities()),
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

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException failure) {
            throw new IllegalStateException("Could not serialize requested capabilities", failure);
        }
    }

    private List<String> strings(String value) {
        try {
            return objectMapper.readValue(value, STRINGS);
        } catch (JacksonException failure) {
            throw new IllegalStateException("Could not read requested capabilities", failure);
        }
    }
}

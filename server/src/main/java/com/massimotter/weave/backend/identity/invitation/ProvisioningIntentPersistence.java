package com.massimotter.weave.backend.identity.invitation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "weave_identity_provisioning_intents")
class ProvisioningIntentJpaEntity {

    @Id
    @Column(name = "intent_id", nullable = false, updatable = false)
    private UUID intentId;

    @Column(name = "tenant_id", nullable = false, length = 160, updatable = false)
    private String tenantId;

    @Column(name = "organization_id", nullable = false, length = 160, updatable = false)
    private String organizationId;

    @Column(name = "invited_email", nullable = false, length = 320, updatable = false)
    private String invitedEmail;

    @Column(name = "invited_email_sha256", nullable = false, length = 64, updatable = false)
    private String invitedEmailSha256;

    @Column(name = "requested_role", nullable = false, length = 32, updatable = false)
    private String requestedRole;

    @Column(name = "provider_invitation_id", length = 200)
    private String providerInvitationId;

    @Column(name = "invited_by_issuer", nullable = false, length = 500, updatable = false)
    private String invitedByIssuer;

    @Column(name = "invited_by_subject", nullable = false, length = 255, updatable = false)
    private String invitedBySubject;

    @Column(name = "audit_correlation", nullable = false, length = 255, updatable = false)
    private String auditCorrelation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProvisioningIntentStatus status;

    @Column(name = "applied_subject", length = 255)
    private String appliedSubject;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ProvisioningIntentJpaEntity() {
    }

    static ProvisioningIntentJpaEntity create(ProvisioningIntent intent) {
        ProvisioningIntentJpaEntity entity = new ProvisioningIntentJpaEntity();
        entity.intentId = intent.intentId();
        entity.tenantId = intent.tenantId();
        entity.organizationId = intent.organizationId();
        entity.invitedEmail = intent.invitedEmail();
        entity.invitedEmailSha256 = intent.invitedEmailSha256();
        entity.requestedRole = intent.requestedRole();
        entity.invitedByIssuer = intent.invitedByIssuer();
        entity.invitedBySubject = intent.invitedBySubject();
        entity.auditCorrelation = intent.auditCorrelation();
        entity.expiresAt = utc(intent.expiresAt());
        entity.createdAt = utc(intent.createdAt());
        entity.status = ProvisioningIntentStatus.PENDING;
        entity.updatedAt = utc(intent.createdAt());
        return entity;
    }

    void apply(ProvisioningIntent intent) {
        requireImmutableDefinition(intent);
        if (providerInvitationId != null
                && !Objects.equals(providerInvitationId, intent.providerInvitationId())) {
            throw new IllegalArgumentException("provider invitation identity cannot be rewritten");
        }
        if (status != ProvisioningIntentStatus.PENDING && status != intent.status()) {
            throw new IllegalArgumentException("terminal provisioning intent cannot transition");
        }
        if (intent.updatedAt().isBefore(updatedAt.toInstant())) {
            throw new IllegalArgumentException("provisioning intent update time cannot move backwards");
        }
        providerInvitationId = intent.providerInvitationId();
        status = intent.status();
        appliedSubject = intent.appliedSubject();
        failureCode = intent.failureCode();
        updatedAt = utc(intent.updatedAt());
    }

    ProvisioningIntent toDomain() {
        return new ProvisioningIntent(
                intentId,
                tenantId,
                organizationId,
                invitedEmail,
                invitedEmailSha256,
                requestedRole,
                providerInvitationId,
                invitedByIssuer,
                invitedBySubject,
                auditCorrelation,
                status,
                appliedSubject,
                failureCode,
                expiresAt.toInstant(),
                createdAt.toInstant(),
                updatedAt.toInstant());
    }

    private void requireImmutableDefinition(ProvisioningIntent intent) {
        if (!Objects.equals(tenantId, intent.tenantId())
                || !Objects.equals(organizationId, intent.organizationId())
                || !Objects.equals(invitedEmail, intent.invitedEmail())
                || !Objects.equals(invitedEmailSha256, intent.invitedEmailSha256())
                || !Objects.equals(requestedRole, intent.requestedRole())
                || !Objects.equals(invitedByIssuer, intent.invitedByIssuer())
                || !Objects.equals(invitedBySubject, intent.invitedBySubject())
                || !Objects.equals(auditCorrelation, intent.auditCorrelation())
                || !expiresAt.toInstant().equals(intent.expiresAt())
                || !createdAt.toInstant().equals(intent.createdAt())) {
            throw new IllegalArgumentException(
                    "provisioning intent immutable definition cannot be rewritten");
        }
    }

    private static OffsetDateTime utc(Instant value) {
        return requireNonNull(value).atOffset(ZoneOffset.UTC);
    }

    private static <T> T requireNonNull(T value) {
        return Objects.requireNonNull(value, "provisioning intent timestamp");
    }
}

interface ProvisioningIntentJpaRepository
        extends JpaRepository<ProvisioningIntentJpaEntity, UUID> {

    java.util.Optional<ProvisioningIntentJpaEntity> findByProviderInvitationId(
            String providerInvitationId);

    List<ProvisioningIntentJpaEntity>
            findByTenantIdAndOrganizationIdAndInvitedEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                    String tenantId,
                    String organizationId,
                    String invitedEmail,
                    ProvisioningIntentStatus status);

}

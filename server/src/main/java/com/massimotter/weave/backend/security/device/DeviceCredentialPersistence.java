package com.massimotter.weave.backend.security.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "weave_device_credentials")
class DeviceCredentialJpaEntity {

    @Id
    @Column(name = "credential_id", nullable = false, length = 160)
    private String credentialId;

    @Column(name = "domain", nullable = false, length = 40, updatable = false)
    private String domain;

    @Column(name = "tenant_id", nullable = false, length = 160, updatable = false)
    private String tenantId;

    @Column(name = "principal_ref", nullable = false, length = 255, updatable = false)
    private String principalRef;

    @Column(name = "subject_ref", nullable = false, length = 255, updatable = false)
    private String subjectRef;

    @Column(name = "username", nullable = false, length = 255, updatable = false)
    private String username;

    @Column(name = "client_type", nullable = false, length = 80, updatable = false)
    private String clientType;

    @Column(name = "label", nullable = false, length = 255, updatable = false)
    private String label;

    @Column(name = "capabilities_json", nullable = false, updatable = false)
    private String capabilitiesJson;

    @Column(name = "secret_hash", nullable = false, updatable = false)
    private String secretHash;

    @Column(name = "issued_at_utc", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at_utc", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at_utc")
    private OffsetDateTime revokedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DeviceCredentialJpaEntity() {
    }

    static DeviceCredentialJpaEntity create(DeviceCredential credential, String capabilitiesJson) {
        DeviceCredentialJpaEntity entity = new DeviceCredentialJpaEntity();
        entity.credentialId = credential.credentialId();
        entity.domain = credential.domain();
        entity.tenantId = credential.tenantId();
        entity.principalRef = credential.principalRef();
        entity.subjectRef = credential.subject();
        entity.username = credential.username();
        entity.clientType = credential.clientType();
        entity.label = credential.label();
        entity.capabilitiesJson = capabilitiesJson;
        entity.secretHash = credential.secretHash();
        entity.issuedAt = credential.issuedAt().atOffset(ZoneOffset.UTC);
        entity.expiresAt = credential.expiresAt().atOffset(ZoneOffset.UTC);
        entity.revokedAt = offset(credential.revokedAt());
        return entity;
    }

    void applyRetry(DeviceCredential credential, String incomingCapabilitiesJson) {
        if (!sameDefinition(credential, incomingCapabilitiesJson)) {
            throw new IllegalArgumentException(
                    "credential id is already bound to another immutable credential");
        }
        if (revokedAt != null
                && credential.revokedAt() != null
                && !revokedAt.toInstant().equals(credential.revokedAt())) {
            throw new IllegalArgumentException("credential revocation cannot be rewritten");
        }
        if (credential.revokedAt() != null) {
            revokedAt = offset(credential.revokedAt());
        }
    }

    private boolean sameDefinition(DeviceCredential credential, String incomingCapabilitiesJson) {
        return Objects.equals(domain, credential.domain())
                && Objects.equals(tenantId, credential.tenantId())
                && Objects.equals(principalRef, credential.principalRef())
                && Objects.equals(subjectRef, credential.subject())
                && Objects.equals(username, credential.username())
                && Objects.equals(clientType, credential.clientType())
                && Objects.equals(label, credential.label())
                && Objects.equals(capabilitiesJson, incomingCapabilitiesJson)
                && Objects.equals(secretHash, credential.secretHash())
                && issuedAt.toInstant().equals(credential.issuedAt())
                && expiresAt.toInstant().equals(credential.expiresAt());
    }

    private static OffsetDateTime offset(java.time.Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    String credentialId() {
        return credentialId;
    }

    String domain() {
        return domain;
    }

    String tenantId() {
        return tenantId;
    }

    String principalRef() {
        return principalRef;
    }

    String subjectRef() {
        return subjectRef;
    }

    String username() {
        return username;
    }

    String clientType() {
        return clientType;
    }

    String label() {
        return label;
    }

    String capabilitiesJson() {
        return capabilitiesJson;
    }

    String secretHash() {
        return secretHash;
    }

    OffsetDateTime issuedAt() {
        return issuedAt;
    }

    OffsetDateTime expiresAt() {
        return expiresAt;
    }

    OffsetDateTime revokedAt() {
        return revokedAt;
    }
}

interface DeviceCredentialJpaRepository
        extends JpaRepository<DeviceCredentialJpaEntity, String> {

    List<DeviceCredentialJpaEntity> findByDomainAndPrincipalRefOrderByIssuedAtAsc(
            String domain,
            String principalRef);
}

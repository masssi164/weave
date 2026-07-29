package com.massimotter.weave.backend.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

@Entity
@Table(name = "weave_organization_bootstrap")
public class OrganizationBootstrapEntity {
    @Id
    @Column(name = "organization_id", nullable = false, length = 255)
    private String organizationId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "bootstrap_mode", nullable = false, length = 64)
    private String bootstrapMode;

    @Column(name = "actor_primary_identity_key", nullable = false, length = 512)
    private String actorPrimaryIdentityKey;

    @Column(name = "retained_admin_primary_identity_keys_json", nullable = false, length = Integer.MAX_VALUE)
    private String retainedAdminPrimaryIdentityKeysJson;

    @Column(name = "bootstrapped_at", nullable = false)
    private OffsetDateTime bootstrappedAt;

    protected OrganizationBootstrapEntity() {
    }

    public OrganizationBootstrapEntity(
            String organizationId,
            String bootstrapMode,
            String actorPrimaryIdentityKey,
            String retainedAdminPrimaryIdentityKeysJson,
            OffsetDateTime bootstrappedAt) {
        this.organizationId = organizationId;
        this.bootstrapMode = bootstrapMode;
        this.actorPrimaryIdentityKey = actorPrimaryIdentityKey;
        this.retainedAdminPrimaryIdentityKeysJson = retainedAdminPrimaryIdentityKeysJson;
        this.bootstrappedAt = bootstrappedAt;
    }

    public String organizationId() {
        return organizationId;
    }

    public String bootstrapMode() {
        return bootstrapMode;
    }

    public String actorPrimaryIdentityKey() {
        return actorPrimaryIdentityKey;
    }

    public String retainedAdminPrimaryIdentityKeysJson() {
        return retainedAdminPrimaryIdentityKeysJson;
    }

    public OffsetDateTime bootstrappedAt() {
        return bootstrappedAt;
    }
}

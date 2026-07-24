package com.massimotter.weave.backend.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "weave_identity_realm_dry_run_evidence")
public class IdentityRealmEvidenceEntity {
    @Id
    @Column(name = "dry_run_id", nullable = false, length = 255)
    private String dryRunId;

    @Column(name = "audit_ref", nullable = false, length = 255)
    private String auditRef;

    @Column(name = "provider_key", nullable = false, length = 128)
    private String providerKey;

    @Column(name = "realm_id", nullable = false, length = 255)
    private String realmId;

    @Column(name = "report_json", nullable = false, columnDefinition = "text")
    private String reportJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected IdentityRealmEvidenceEntity() {
    }

    public IdentityRealmEvidenceEntity(
            String dryRunId,
            String auditRef,
            String providerKey,
            String realmId,
            String reportJson,
            OffsetDateTime createdAt) {
        this.dryRunId = dryRunId;
        this.auditRef = auditRef;
        this.providerKey = providerKey;
        this.realmId = realmId;
        this.reportJson = reportJson;
        this.createdAt = createdAt;
    }

    public String dryRunId() { return dryRunId; }
    public String auditRef() { return auditRef; }
    public String providerKey() { return providerKey; }
    public String realmId() { return realmId; }
    public String reportJson() { return reportJson; }
    public OffsetDateTime createdAt() { return createdAt; }
}

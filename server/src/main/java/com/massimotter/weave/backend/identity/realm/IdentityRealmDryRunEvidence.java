package com.massimotter.weave.backend.identity.realm;

import java.time.Instant;

public record IdentityRealmDryRunEvidence(
        String dryRunId,
        String auditRef,
        String providerKey,
        String realmId,
        IdentityRealmDryRunReport report,
        Instant createdAt) {
}

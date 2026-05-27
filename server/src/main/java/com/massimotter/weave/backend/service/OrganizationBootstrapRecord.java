package com.massimotter.weave.backend.service;

import java.time.Instant;
import java.util.List;

public record OrganizationBootstrapRecord(
        String organizationId,
        String bootstrapMode,
        String actorPrimaryIdentityKey,
        List<String> retainedAdminPrimaryIdentityKeys,
        Instant bootstrappedAt) {

    public OrganizationBootstrapRecord {
        retainedAdminPrimaryIdentityKeys = retainedAdminPrimaryIdentityKeys == null ? List.of() : List.copyOf(retainedAdminPrimaryIdentityKeys);
    }
}

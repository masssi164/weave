package com.massimotter.weave.backend.service;

import java.time.Instant;
import java.util.List;

public record OrganizationBootstrapRecord(
        String organizationId,
        String bootstrapMode,
        String actorPrimaryIdentityKey,
        List<String> retainedAdminSubjectKeys,
        Instant bootstrappedAt) {

    public OrganizationBootstrapRecord {
        retainedAdminSubjectKeys = retainedAdminSubjectKeys == null ? List.of() : List.copyOf(retainedAdminSubjectKeys);
    }
}

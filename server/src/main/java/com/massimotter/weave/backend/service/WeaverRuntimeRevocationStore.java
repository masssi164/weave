package com.massimotter.weave.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WeaverRuntimeRevocationStore {

    List<RevocationRecord> recordsForUser(String userRef);

    Optional<RevocationRecord> recordForProfile(String runtimeProfileHash);

    void record(RevocationRecord record);

    record RevocationRecord(
            String userRef,
            String runtimeProfileHash,
            String signature,
            int revocationGeneration,
            String reason,
            String actor,
            String scope,
            Instant revokedAt,
            String evidenceRef) {}
}

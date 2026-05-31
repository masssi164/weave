package com.massimotter.weave.backend.service.migration;

import java.time.Instant;
import java.util.Optional;

interface MigrationRunEvidenceRepository {

    void save(MigrationRunEvidence evidence);

    Optional<MigrationRunEvidence> findCurrent(String runId, String domainKey, Instant now);
}

package com.massimotter.weave.backend.portability.port;

import com.massimotter.weave.backend.portability.domain.MigrationRunEvidence;

import java.time.Instant;
import java.util.Optional;

public interface MigrationRunEvidenceRepository {

    void save(MigrationRunEvidence evidence);

    Optional<MigrationRunEvidence> findCurrent(String runId, String domainKey, Instant now);
}

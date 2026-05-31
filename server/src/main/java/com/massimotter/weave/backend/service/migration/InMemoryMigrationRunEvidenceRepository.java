package com.massimotter.weave.backend.service.migration;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
class InMemoryMigrationRunEvidenceRepository implements MigrationRunEvidenceRepository {

    private final Map<String, MigrationRunEvidence> evidenceByRunAndDomain = new ConcurrentHashMap<>();

    @Override
    public void save(MigrationRunEvidence evidence) {
        evidenceByRunAndDomain.put(key(evidence.runId(), evidence.domainKey()), evidence);
    }

    @Override
    public Optional<MigrationRunEvidence> findCurrent(String runId, String domainKey, Instant now) {
        return Optional.ofNullable(evidenceByRunAndDomain.get(key(runId, domainKey)))
                .filter(evidence -> !evidence.expired(now));
    }

    void clear() {
        evidenceByRunAndDomain.clear();
    }

    private String key(String runId, String domainKey) {
        return runId + "::" + domainKey;
    }
}

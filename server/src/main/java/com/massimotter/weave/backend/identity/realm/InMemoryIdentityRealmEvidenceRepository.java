package com.massimotter.weave.backend.identity.realm;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryIdentityRealmEvidenceRepository implements IdentityRealmEvidenceRepository {

    private final Map<String, IdentityRealmDryRunEvidence> dryRuns = new ConcurrentHashMap<>();

    @Override
    public void save(IdentityRealmDryRunEvidence evidence) {
        if (evidence != null && evidence.dryRunId() != null && !evidence.dryRunId().isBlank()) {
            dryRuns.put(evidence.dryRunId(), evidence);
        }
    }

    @Override
    public Optional<IdentityRealmDryRunEvidence> findDryRun(String dryRunId) {
        if (dryRunId == null || dryRunId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(dryRuns.get(dryRunId));
    }
}

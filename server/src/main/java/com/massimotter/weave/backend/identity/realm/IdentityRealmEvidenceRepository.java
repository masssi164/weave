package com.massimotter.weave.backend.identity.realm;

import java.util.Optional;

public interface IdentityRealmEvidenceRepository {

    void save(IdentityRealmDryRunEvidence evidence);

    Optional<IdentityRealmDryRunEvidence> findDryRun(String dryRunId);
}

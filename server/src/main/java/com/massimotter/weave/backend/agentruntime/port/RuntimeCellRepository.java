package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuntimeCellRepository {
    RuntimeCell insert(RuntimeCell cell);

    Optional<RuntimeCell> findByPerson(String organizationRef, String personRef);

    Optional<RuntimeCell> findByCellRef(String cellRef);

    List<RuntimeCell> findAll();

    RuntimeCell acquireLease(String cellRef, UUID leaseId, Instant now, Instant expiresAt);

    RuntimeCell renewLease(String cellRef, UUID leaseId, long fencingEpoch, Instant now, Instant expiresAt);

    RuntimeCell observe(String cellRef, UUID leaseId, long fencingEpoch, RuntimeCellState observedState,
            String auditRef, Instant now);

    RuntimeCell revoke(
            String organizationRef, String personRef, String entitlementRevision, String auditRef, Instant now);
}

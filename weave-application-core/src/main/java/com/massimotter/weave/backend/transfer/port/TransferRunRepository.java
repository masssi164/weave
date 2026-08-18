package com.massimotter.weave.backend.transfer.port;

import java.util.Optional;

import com.massimotter.weave.backend.transfer.domain.TransferRun;

/** Persistence port for resumable transfer state. A JPA adapter is added under the persistence track. */
public interface TransferRunRepository {

    Optional<TransferRun> findById(TransferRun.Id id);

    /**
     * Persists {@code run} only when the currently stored state revision equals
     * {@code expectedPreviousRevision}. Revision zero denotes creation.
     */
    void save(TransferRun run, long expectedPreviousRevision);
}

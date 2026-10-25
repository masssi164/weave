package com.massimotter.weave.backend.transfer.adapter;

import com.massimotter.weave.backend.transfer.domain.TransferRun;
import com.massimotter.weave.backend.transfer.port.TransferRunRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Objects.requireNonNull;

/** PostgreSQL/JPA adapter for durable resumable canonical transfer state. */
@Repository
@Transactional(readOnly = true)
public class JpaTransferRunRepository implements TransferRunRepository {

    private final TransferRunJpaRepository runs;

    public JpaTransferRunRepository(TransferRunJpaRepository runs) {
        this.runs = requireNonNull(runs, "runs");
    }

    @Override
    public Optional<TransferRun> findById(TransferRun.Id id) {
        return runs.findById(requireNonNull(id, "id").value())
                .map(TransferRunJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(TransferRun run, long expectedPreviousRevision) {
        TransferRun safeRun = requireNonNull(run, "run");
        if (expectedPreviousRevision < 0) {
            throw new IllegalArgumentException("expected previous revision must not be negative");
        }
        if (safeRun.stateRevision() != expectedPreviousRevision + 1) {
            throw new IllegalArgumentException(
                    "transfer run revision must advance exactly once: expected "
                            + (expectedPreviousRevision + 1)
                            + " but found "
                            + safeRun.stateRevision());
        }

        TransferRunJpaEntity current = runs.lockById(safeRun.id().value()).orElse(null);
        long actualRevision = current == null ? 0 : current.stateRevision();
        if (actualRevision != expectedPreviousRevision) {
            throw new StaleTransferRunException(
                    safeRun.id(),
                    expectedPreviousRevision,
                    actualRevision);
        }

        TransferRunJpaEntity entity = current == null
                ? TransferRunJpaEntity.from(safeRun)
                : current;
        if (current != null) {
            current.apply(safeRun);
        }
        runs.saveAndFlush(entity);
    }

    public static final class StaleTransferRunException extends RuntimeException {
        public StaleTransferRunException(
                TransferRun.Id id,
                long expected,
                long actual) {
            super("transfer run changed for " + id.value()
                    + ": expected revision " + expected
                    + " but found " + actual);
        }
    }
}

package com.massimotter.weave.backend.operation.adapter;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

import static java.util.Objects.requireNonNull;

/**
 * Reviewed native-query exception for PostgreSQL/H2 work claiming.
 *
 * <p>The query shape is static; all values are bound parameters. {@code SKIP LOCKED} is required
 * to let multiple reconcilers claim disjoint work without convoying.
 */
@Repository
class OperationIntentLeaseNativeRepository {

    private static final String LOCK_CANDIDATES = """
            select operation_ref
            from weave_operation_intents
            where intent_state in ('AMBIGUOUS', 'RECONCILING')
              and (reconciliation_lease_until_utc is null
                   or reconciliation_lease_until_utc < :now)
              and reconciliation_attempts < reconciliation_max_attempts
            order by updated_at_utc, operation_ref
            for update skip locked
            limit :limit
            """;

    private final EntityManager entityManager;

    OperationIntentLeaseNativeRepository(EntityManager entityManager) {
        this.entityManager = requireNonNull(entityManager, "entityManager");
    }

    @SuppressWarnings("unchecked")
    List<String> lockCandidateRefs(Instant now, int limit) {
        return entityManager.createNativeQuery(LOCK_CANDIDATES)
                .setParameter("now", OperationPersistenceTime.utc(now))
                .setParameter("limit", limit)
                .getResultList();
    }
}

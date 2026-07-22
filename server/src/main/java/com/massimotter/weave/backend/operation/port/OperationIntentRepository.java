package com.massimotter.weave.backend.operation.port;

import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationOutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OperationIntentRepository {

    Optional<OperationIntent> findByOperationRef(String operationRef);

    Optional<OperationIntent> findByIdempotencyKey(String organizationRef, String idempotencyKey);

    CreateResult create(OperationIntent intent, OperationOutboxEvent event);

    OperationIntent update(OperationIntent expected, OperationIntent updated, OperationOutboxEvent event);

    List<OperationIntent> leaseReconciliationBatch(Instant now, int limit, Instant leaseUntil);

    record CreateResult(OperationIntent intent, boolean created) {
    }
}

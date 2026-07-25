package com.massimotter.weave.backend.operation.adapter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationOutboxEvent;
import com.massimotter.weave.backend.operation.port.OperationIntentRepository;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static java.util.Objects.requireNonNull;

/**
 * Durable intent/outbox adapter.
 *
 * <p>Create, state transition, and outbox append share one transaction. Unique-key races are
 * reconciled in a fresh transaction so an idempotent retry returns the committed winner.
 */
@Repository
public class JpaOperationIntentRepository
        implements OperationIntentRepository {

    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() {
            };

    private final OperationIntentJpaRepository intents;
    private final OperationOutboxJpaRepository outbox;
    private final OperationIntentLeaseNativeRepository leases;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public JpaOperationIntentRepository(
            OperationIntentJpaRepository intents,
            OperationOutboxJpaRepository outbox,
            OperationIntentLeaseNativeRepository leases,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.intents = requireNonNull(intents, "intents");
        this.outbox = requireNonNull(outbox, "outbox");
        this.leases = requireNonNull(leases, "leases");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper");
        this.transactions = new TransactionTemplate(
                requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public Optional<OperationIntent> findByOperationRef(String operationRef) {
        return intents.findById(operationRef)
                .map(entity -> entity.toDomain(this::stringList));
    }

    @Override
    public Optional<OperationIntent> findByIdempotencyKey(
            String organizationRef,
            String idempotencyKey) {
        return intents
                .findByOrganizationRefAndIdempotencyKey(
                        organizationRef,
                        idempotencyKey)
                .map(entity -> entity.toDomain(this::stringList));
    }

    @Override
    public CreateResult create(
            OperationIntent intent,
            OperationOutboxEvent event) {
        OperationIntent requested = requireNonNull(intent, "intent");
        OperationOutboxEvent requestedEvent = requireNonNull(event, "event");
        Optional<OperationIntent> existing =
                findByIdempotencyKey(
                        requested.organizationRef(),
                        requested.idempotencyKey());
        if (existing.isPresent()) {
            return new CreateResult(existing.orElseThrow(), false);
        }
        try {
            return transactions.execute(status -> {
                OperationIntentJpaEntity stored = OperationIntentJpaEntity.create(
                        requested,
                        json(requested.objectRefs()));
                intents.saveAndFlush(stored);
                outbox.saveAndFlush(OperationOutboxJpaEntity.create(requestedEvent));
                return new CreateResult(stored.toDomain(this::stringList), true);
            });
        } catch (DataIntegrityViolationException | PersistenceException duplicateOrFailure) {
            return transactions.execute(status -> new CreateResult(
                    findByIdempotencyKey(
                                    requested.organizationRef(),
                                    requested.idempotencyKey())
                            .orElseThrow(() -> duplicateOrFailure),
                    false));
        }
    }

    @Override
    public OperationIntent update(
            OperationIntent expected,
            OperationIntent intent,
            OperationOutboxEvent event) {
        return transactions.execute(status -> {
            OperationIntentJpaEntity stored = intents.findById(intent.operationRef())
                    .orElseThrow(() -> new ConcurrentOperationUpdateException(
                            intent.operationRef()));
            if (!stored.updatedAt().equals(expected.updatedAt())) {
                throw new ConcurrentOperationUpdateException(intent.operationRef());
            }
            stored.applyTransition(intent);
            outbox.save(OperationOutboxJpaEntity.create(event));
            intents.flush();
            outbox.flush();
            return stored.toDomain(this::stringList);
        });
    }

    @Override
    public List<OperationIntent> leaseReconciliationBatch(
            Instant now,
            int limit,
            Instant leaseUntil) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return transactions.execute(status -> leases
                .lockCandidateRefs(now, bounded)
                .stream()
                .map(ref -> intents.findById(ref).orElseThrow())
                .peek(entity -> entity.leaseForReconciliation(now, leaseUntil))
                .map(entity -> entity.toDomain(this::stringList))
                .toList());
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "operation object references are not serializable",
                    exception);
        }
    }

    private List<String> stringList(String value) {
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "stored operation object references are invalid",
                    exception);
        }
    }

    public static final class ConcurrentOperationUpdateException
            extends RuntimeException {
        public ConcurrentOperationUpdateException(String operationRef) {
            super("operation intent changed concurrently: " + operationRef);
        }
    }
}

package com.massimotter.weave.backend.operation.adapter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationOutboxEvent;
import com.massimotter.weave.backend.operation.port.OperationIntentRepository;
import com.massimotter.weave.backend.files.application.NativeFilesCleanupOutboxRepository;
import com.massimotter.weave.backend.files.application.NativeFilesCleanupOutboxRepository.CleanupLease;
import com.massimotter.weave.backend.files.application.NativeFilesCleanupOutboxRepository.RetryOutcome;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
        implements OperationIntentRepository, NativeFilesCleanupOutboxRepository {

    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() {
            };

    private final OperationIntentJpaRepository intents;
    private final OperationOutboxJpaRepository outbox;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public JpaOperationIntentRepository(
            OperationIntentJpaRepository intents,
            OperationOutboxJpaRepository outbox,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.intents = requireNonNull(intents, "intents");
        this.outbox = requireNonNull(outbox, "outbox");
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

    public boolean existsForOrganizationDomain(
            String organizationRef,
            String domain) {
        return intents.existsByOrganizationRefAndDomain(
                organizationRef,
                domain);
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
        return transactions.execute(status -> transitionWithinTransaction(expected, intent, event));
    }

    /** Inserts a CREATED intent as part of a caller-owned composite domain transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OperationIntent insertCreatedWithoutOutbox(OperationIntent intent) {
        OperationIntent requested = requireNonNull(intent, "intent");
        if (requested.state() != OperationIntent.State.CREATED) {
            throw new IllegalArgumentException("composite intent insert requires CREATED state");
        }
        OperationIntentJpaEntity stored = OperationIntentJpaEntity.create(
                requested,
                json(requested.objectRefs()));
        intents.saveAndFlush(stored);
        return stored.toDomain(this::stringList);
    }

    /** Appends one transition/outbox pair inside the caller's composite transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OperationIntent transitionWithinTransaction(
            OperationIntent expected,
            OperationIntent intent,
            OperationOutboxEvent event) {
        OperationIntent requested = requireNonNull(intent, "intent");
        OperationIntentJpaEntity stored = intents.findById(requested.operationRef())
                .orElseThrow(() -> new ConcurrentOperationUpdateException(
                        requested.operationRef()));
        if (!stored.updatedAt().equals(requireNonNull(expected, "expected").updatedAt())) {
            throw new ConcurrentOperationUpdateException(requested.operationRef());
        }
        stored.applyTransition(requested);
        outbox.save(OperationOutboxJpaEntity.create(requireNonNull(event, "event")));
        intents.flush();
        outbox.flush();
        return stored.toDomain(this::stringList);
    }

    /** Updates a composite intent without publishing an outbox effect. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OperationIntent transitionWithoutOutbox(
            OperationIntent expected,
            OperationIntent intent) {
        OperationIntent requested = requireNonNull(intent, "intent");
        OperationIntentJpaEntity stored = intents.findById(requested.operationRef())
                .orElseThrow(() -> new ConcurrentOperationUpdateException(
                        requested.operationRef()));
        if (!stored.updatedAt().equals(requireNonNull(expected, "expected").updatedAt())) {
            throw new ConcurrentOperationUpdateException(requested.operationRef());
        }
        stored.applyTransition(requested);
        intents.flush();
        return stored.toDomain(this::stringList);
    }

    /** Reads the durable outbox linkage used by a composite-operation commit probe. */
    public List<OutboxLink> outboxLinks(String operationRef) {
        return outbox.findByOperationRefOrderBySequenceId(operationRef).stream()
                .map(entity -> new OutboxLink(entity.outboxRef(), entity.eventType()))
                .toList();
    }

    @Override
    public List<CleanupLease> leaseBatch(
            Instant now,
            Instant leaseUntil,
            String leaseOwner,
            int limit,
            int maximumAttempts) {
        Instant requiredNow = requireNonNull(now, "now");
        Instant requiredUntil = requireNonNull(leaseUntil, "leaseUntil");
        String requiredOwner = leaseValue(leaseOwner, "leaseOwner");
        if (!requiredUntil.isAfter(requiredNow)) {
            throw new IllegalArgumentException("outbox leaseUntil must be after now");
        }
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        int boundedAttempts = boundedAttempts(maximumAttempts);
        return transactions.execute(status -> {
            List<OperationOutboxJpaEntity> candidates = outbox.lockNativeFilesCleanupCandidates(
                    OperationPersistenceTime.utc(requiredNow),
                    boundedAttempts,
                    PageRequest.of(0, boundedLimit));
            List<OperationOutboxJpaEntity> leased = candidates.stream()
                    .filter(entity -> entity.eligibleForCleanupLease(
                            requiredNow,
                            boundedAttempts))
                    .toList();
            leased.forEach(entity -> entity.lease(
                    requiredUntil,
                    UUID.randomUUID().toString(),
                    requiredOwner,
                    boundedAttempts));
            outbox.flush();
            return leased.stream()
                    .map(entity -> new CleanupLease(
                            entity.sequenceId(),
                            entity.outboxRef(),
                            entity.operationRef(),
                            entity.eventType(),
                            entity.attemptCount(),
                            entity.leaseToken(),
                            entity.leaseOwner(),
                            entity.leaseUntil()))
                    .toList();
        });
    }

    @Override
    public boolean markDelivered(CleanupLease lease, Instant deliveredAt) {
        CleanupLease requiredLease = requireNonNull(lease, "lease");
        Instant requiredAt = requireNonNull(deliveredAt, "deliveredAt");
        return Boolean.TRUE.equals(transactions.execute(status -> outbox
                .lockBySequenceIdAndOutboxRef(
                        requiredLease.sequenceId(),
                        requiredLease.outboxRef())
                .filter(entity -> owns(entity, requiredLease)
                        && entity.leaseActiveAt(requiredAt))
                .map(entity -> {
                    entity.delivered(requiredAt);
                    outbox.flush();
                    return true;
                })
                .orElse(false)));
    }

    @Override
    public RetryOutcome retry(
            CleanupLease lease,
            Instant settledAt,
            Instant retryAt,
            String diagnosticCode,
            int maximumAttempts) {
        CleanupLease requiredLease = requireNonNull(lease, "lease");
        Instant requiredSettlement = requireNonNull(settledAt, "settledAt");
        Instant requiredRetryAt = requireNonNull(retryAt, "retryAt");
        if (requiredRetryAt.isBefore(requiredSettlement)) {
            throw new IllegalArgumentException(
                    "outbox retryAt must not precede settledAt");
        }
        String requiredDiagnostic = diagnosticCode(diagnosticCode);
        int boundedAttempts = boundedAttempts(maximumAttempts);
        return transactions.execute(status -> outbox
                .lockBySequenceIdAndOutboxRef(
                        requiredLease.sequenceId(),
                        requiredLease.outboxRef())
                .filter(entity -> owns(entity, requiredLease)
                        && entity.leaseActiveAt(requiredSettlement))
                .map(entity -> {
                    RetryOutcome outcome;
                    if (entity.attemptCount() >= boundedAttempts) {
                        entity.failClosed(requiredDiagnostic);
                        outcome = RetryOutcome.FAILED_CLOSED;
                    } else {
                        entity.retryAt(requiredRetryAt, requiredDiagnostic);
                        outcome = RetryOutcome.REQUEUED;
                    }
                    outbox.flush();
                    return outcome;
                })
                .orElse(RetryOutcome.STALE_LEASE));
    }

    @Override
    public List<OperationIntent> leaseReconciliationBatch(
            Instant now,
            int limit,
            Instant leaseUntil) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return transactions.execute(status -> intents
                .lockReconciliationCandidates(
                        List.of(
                                OperationIntent.State.AMBIGUOUS.name(),
                                OperationIntent.State.RECONCILING.name()),
                        OperationPersistenceTime.utc(now),
                        PageRequest.of(0, bounded))
                .stream()
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

    private boolean owns(OperationOutboxJpaEntity entity, CleanupLease lease) {
        return entity.ownsLease(
                lease.outboxRef(),
                lease.operationRef(),
                lease.eventType(),
                lease.attemptCount(),
                lease.leaseToken(),
                lease.leaseOwner(),
                lease.leaseUntil());
    }

    private String leaseValue(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException("outbox " + field + " is invalid");
        }
        return value;
    }

    private String diagnosticCode(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9._-]{0,119}")) {
            throw new IllegalArgumentException("outbox diagnosticCode is invalid");
        }
        return value;
    }

    private int boundedAttempts(int maximumAttempts) {
        if (maximumAttempts < 1 || maximumAttempts > 100_000) {
            throw new IllegalArgumentException(
                    "maximum outbox delivery attempts must be between 1 and 100000");
        }
        return maximumAttempts;
    }

    public static final class ConcurrentOperationUpdateException
            extends RuntimeException {
        public ConcurrentOperationUpdateException(String operationRef) {
            super("operation intent changed concurrently: " + operationRef);
        }
    }

    public record OutboxLink(String outboxRef, String eventType) {
    }
}

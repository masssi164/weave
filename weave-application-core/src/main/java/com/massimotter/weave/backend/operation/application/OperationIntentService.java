package com.massimotter.weave.backend.operation.application;

import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationIntent.Actor;
import com.massimotter.weave.backend.operation.domain.OperationIntent.Projection;
import com.massimotter.weave.backend.operation.domain.OperationIntent.Reconciliation;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ReconciliationOutcome;
import com.massimotter.weave.backend.operation.domain.OperationIntent.State;
import com.massimotter.weave.backend.operation.domain.OperationOutboxEvent;
import com.massimotter.weave.backend.operation.port.OperationIntentRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Coordinates durable intent transitions; provider I/O belongs between these short transactional calls. */
public final class OperationIntentService {

    private final OperationIntentRepository repository;
    private final Clock clock;

    public OperationIntentService(OperationIntentRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public BeginResult begin(BeginCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return repository.findByIdempotencyKey(command.organizationRef(), command.idempotencyKey())
                .map(existing -> equivalentRetry(existing, command))
                .orElseGet(() -> create(command));
    }

    /** Builds the exact CREATED intent that a composite domain transaction may persist. */
    public OperationIntent prepare(BeginCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = Instant.now(clock);
        String operationRef = operationRef(command.organizationRef(), command.idempotencyKey());
        String outboxRef = "outbox:" + UUID.nameUUIDFromBytes(
                (operationRef + "\noutbox").getBytes(StandardCharsets.UTF_8));
        return new OperationIntent(
                operationRef,
                command.idempotencyKey(),
                command.organizationRef(),
                command.actor(),
                command.domain(),
                command.projection(),
                command.actionDigest(),
                command.canonicalArgumentsDigest(),
                command.objectRefs(),
                command.policyRevision(),
                command.entitlementRevision(),
                command.providerBindingRevision(),
                State.CREATED,
                outboxRef,
                null,
                null,
                null,
                null,
                now,
                now);
    }

    public Optional<OperationIntent> findExisting(String organizationRef, String idempotencyKey) {
        return repository.findByIdempotencyKey(organizationRef, idempotencyKey);
    }

    /** Validates a create race or retry against the same canonical command. */
    public BeginResult requireEquivalent(OperationIntent existing, BeginCommand command) {
        return equivalentRetry(
                Objects.requireNonNull(existing, "existing must not be null"),
                Objects.requireNonNull(command, "command must not be null"));
    }

    /**
     * Builds the Files-native terminal success and its single reserved outbox event.
     * Persistence belongs to the same transaction as metadata, journal, and stream head.
     */
    public PreparedTransition prepareNativeSuccess(
            OperationIntent current,
            String resultDigest,
            String auditRef) {
        requireState(current, State.CREATED, State.RECONCILING);
        Reconciliation reconciliation = current.state() == State.RECONCILING
                ? new Reconciliation(
                        current.reconciliation().attempts(),
                        ReconciliationOutcome.CONFIRMED_APPLIED,
                        Instant.now(clock),
                        resultDigest)
                : null;
        return preparedTerminal(
                current,
                State.SUCCEEDED,
                reconciliation,
                resultDigest,
                auditRef,
                "operation.succeeded");
    }

    /** Builds a proven non-commit terminal transition using the reserved cleanup outbox ref. */
    public PreparedTransition prepareNativeFailure(
            OperationIntent current,
            boolean denied,
            String resultDigest,
            String auditRef) {
        requireState(current, State.CREATED, State.RECONCILING);
        return preparedTerminal(
                current,
                denied ? State.DENIED : State.FAILED,
                current.reconciliation(),
                resultDigest,
                auditRef,
                denied ? "operation.denied" : "operation.failed");
    }

    /**
     * Builds the no-outbox ambiguity transition used after an uncertain native Files effect.
     * The caller persists it in a separate composite transaction after probing for commit.
     */
    public OperationIntent prepareNativeAmbiguous(
            OperationIntent current,
            String providerCorrelationHash) {
        requireState(current, State.CREATED);
        return nativeNonterminal(
                current,
                State.AMBIGUOUS,
                providerCorrelationHash,
                new Reconciliation(0, ReconciliationOutcome.PENDING, null, null));
    }

    /** Builds one bounded no-outbox reconciliation attempt for native Files. */
    public OperationIntent prepareNativeReconciliation(OperationIntent current) {
        requireState(current, State.AMBIGUOUS);
        Reconciliation previous = current.reconciliation();
        if (previous.attempts() >= 5) {
            throw new IllegalOperationTransitionException(
                    current.operationRef(), current.state(), List.of(State.RECONCILING));
        }
        return nativeNonterminal(
                current,
                State.RECONCILING,
                current.providerCorrelationHash(),
                new Reconciliation(
                        previous.attempts() + 1,
                        ReconciliationOutcome.PENDING,
                        Instant.now(clock),
                        previous.resultDigest()));
    }

    public OperationIntent markDispatching(OperationIntent current) {
        requireState(current, State.CREATED);
        return update(current, State.DISPATCHING, null, null, null, "operation.dispatching");
    }

    public OperationIntent markAmbiguous(OperationIntent current, String providerCorrelationHash) {
        requireState(current, State.DISPATCHING);
        return update(
                current,
                State.AMBIGUOUS,
                providerCorrelationHash,
                new Reconciliation(0, ReconciliationOutcome.PENDING, null, null),
                null,
                "operation.ambiguous");
    }

    public OperationIntent beginReconciliation(OperationIntent current) {
        requireState(current, State.AMBIGUOUS);
        Reconciliation previous = current.reconciliation();
        Reconciliation reconciliation = new Reconciliation(
                previous.attempts() + 1,
                ReconciliationOutcome.PENDING,
                Instant.now(clock),
                previous.resultDigest());
        return update(current, State.RECONCILING, current.providerCorrelationHash(), reconciliation,
                null, "operation.reconciling");
    }

    public OperationIntent succeed(OperationIntent current, String resultDigest, String auditRef) {
        requireState(current, State.DISPATCHING, State.RECONCILING);
        Reconciliation reconciliation = current.state() == State.RECONCILING
                ? new Reconciliation(
                        current.reconciliation().attempts(),
                        ReconciliationOutcome.CONFIRMED_APPLIED,
                        Instant.now(clock),
                        resultDigest)
                : null;
        return update(current, State.SUCCEEDED, current.providerCorrelationHash(), reconciliation,
                new Terminal(resultDigest, auditRef), "operation.succeeded");
    }

    public OperationIntent deny(OperationIntent current, String resultDigest, String auditRef) {
        requireState(current, State.CREATED);
        return update(current, State.DENIED, null, null, new Terminal(resultDigest, auditRef), "operation.denied");
    }

    public OperationIntent fail(OperationIntent current, String resultDigest, String auditRef) {
        requireState(current, State.CREATED, State.DISPATCHING, State.RECONCILING);
        return update(current, State.FAILED, current.providerCorrelationHash(), current.reconciliation(),
                new Terminal(resultDigest, auditRef), "operation.failed");
    }

    private BeginResult create(BeginCommand command) {
        OperationIntent intent = prepare(command);
        Instant now = intent.createdAt();
        String outboxRef = intent.outboxRef();
        var result = repository.create(
                intent,
                event(outboxRef, intent.operationRef(), "operation.created", now));
        if (!result.created()) {
            return equivalentRetry(result.intent(), command);
        }
        return new BeginResult(result.intent(), false);
    }

    private BeginResult equivalentRetry(OperationIntent existing, BeginCommand command) {
        boolean equivalent = existing.organizationRef().equals(command.organizationRef())
                && existing.actor().equals(command.actor())
                && existing.domain().equals(command.domain())
                && existing.projection().equals(command.projection())
                && existing.actionDigest().equals(command.actionDigest())
                && existing.canonicalArgumentsDigest().equals(command.canonicalArgumentsDigest())
                && existing.objectRefs().equals(command.objectRefs())
                && existing.policyRevision().equals(command.policyRevision())
                && existing.entitlementRevision().equals(command.entitlementRevision())
                && existing.providerBindingRevision() == command.providerBindingRevision();
        if (!equivalent) {
            throw new IdempotencyKeyConflictException(existing.operationRef());
        }
        return new BeginResult(existing, true);
    }

    private PreparedTransition preparedTerminal(
            OperationIntent current,
            State state,
            Reconciliation reconciliation,
            String resultDigest,
            String auditRef,
            String eventType) {
        Instant now = Instant.now(clock);
        OperationIntent updated = new OperationIntent(
                current.operationRef(), current.idempotencyKey(), current.organizationRef(), current.actor(),
                current.domain(), current.projection(), current.actionDigest(), current.canonicalArgumentsDigest(),
                current.objectRefs(), current.policyRevision(), current.entitlementRevision(),
                current.providerBindingRevision(), state, current.outboxRef(), current.providerCorrelationHash(),
                reconciliation, resultDigest, auditRef, current.createdAt(), now);
        return new PreparedTransition(
                updated,
                event(current.outboxRef(), current.operationRef(), eventType, now));
    }

    private OperationIntent nativeNonterminal(
            OperationIntent current,
            State state,
            String providerCorrelationHash,
            Reconciliation reconciliation) {
        return new OperationIntent(
                current.operationRef(),
                current.idempotencyKey(),
                current.organizationRef(),
                current.actor(),
                current.domain(),
                current.projection(),
                current.actionDigest(),
                current.canonicalArgumentsDigest(),
                current.objectRefs(),
                current.policyRevision(),
                current.entitlementRevision(),
                current.providerBindingRevision(),
                state,
                current.outboxRef(),
                providerCorrelationHash,
                reconciliation,
                null,
                null,
                current.createdAt(),
                Instant.now(clock));
    }

    private String operationRef(String organizationRef, String idempotencyKey) {
        return "operation:" + UUID.nameUUIDFromBytes(
                (organizationRef + "\n" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
    }

    private OperationIntent update(
            OperationIntent current,
            State state,
            String providerCorrelationHash,
            Reconciliation reconciliation,
            Terminal terminal,
            String eventType) {
        Instant now = Instant.now(clock);
        OperationIntent updated = new OperationIntent(
                current.operationRef(), current.idempotencyKey(), current.organizationRef(), current.actor(),
                current.domain(), current.projection(), current.actionDigest(), current.canonicalArgumentsDigest(),
                current.objectRefs(), current.policyRevision(), current.entitlementRevision(),
                current.providerBindingRevision(), state, current.outboxRef(), providerCorrelationHash,
                reconciliation, terminal == null ? null : terminal.resultDigest(),
                terminal == null ? null : terminal.auditRef(), current.createdAt(), now);
        String eventRef = "outbox:" + UUID.randomUUID();
        return repository.update(current, updated, event(eventRef, current.operationRef(), eventType, now));
    }

    private OperationOutboxEvent event(String outboxRef, String operationRef, String eventType, Instant now) {
        return new OperationOutboxEvent(
                outboxRef,
                operationRef,
                eventType,
                "{\"operationRef\":\"" + operationRef + "\",\"eventType\":\"" + eventType + "\"}",
                now);
    }

    private void requireState(OperationIntent intent, State... allowed) {
        Objects.requireNonNull(intent, "intent must not be null");
        if (List.of(allowed).contains(intent.state())) {
            return;
        }
        throw new IllegalOperationTransitionException(intent.operationRef(), intent.state(), List.of(allowed));
    }

    public record BeginCommand(
            String idempotencyKey,
            String organizationRef,
            Actor actor,
            String domain,
            Projection projection,
            String actionDigest,
            String canonicalArgumentsDigest,
            List<String> objectRefs,
            String policyRevision,
            String entitlementRevision,
            long providerBindingRevision) {

        public BeginCommand {
            objectRefs = objectRefs == null ? List.of() : List.copyOf(objectRefs);
        }
    }

    public record BeginResult(OperationIntent intent, boolean retry) {
    }

    public record PreparedTransition(OperationIntent intent, OperationOutboxEvent outboxEvent) {
        public PreparedTransition {
            intent = Objects.requireNonNull(intent, "intent must not be null");
            outboxEvent = Objects.requireNonNull(outboxEvent, "outboxEvent must not be null");
        }
    }

    private record Terminal(String resultDigest, String auditRef) {
    }

    public static final class IdempotencyKeyConflictException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        private final String operationRef;

        public IdempotencyKeyConflictException(String operationRef) {
            super("idempotency key is already bound to different canonical arguments");
            this.operationRef = operationRef;
        }

        public String operationRef() {
            return operationRef;
        }
    }

    public static final class IllegalOperationTransitionException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        public IllegalOperationTransitionException(String operationRef, State state, List<State> allowed) {
            super("operation " + operationRef + " cannot transition from " + state + "; expected " + allowed);
        }
    }
}

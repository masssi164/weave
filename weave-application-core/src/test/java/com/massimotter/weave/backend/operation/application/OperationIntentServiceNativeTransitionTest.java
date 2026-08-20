package com.massimotter.weave.backend.operation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.massimotter.weave.backend.operation.application.OperationIntentService.IllegalOperationTransitionException;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationIntent.HumanActor;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ProtocolProjection;
import com.massimotter.weave.backend.operation.domain.OperationIntent.Reconciliation;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ReconciliationOutcome;
import com.massimotter.weave.backend.operation.domain.OperationIntent.State;
import com.massimotter.weave.backend.operation.domain.OperationOutboxEvent;
import com.massimotter.weave.backend.operation.port.OperationIntentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OperationIntentServiceNativeTransitionTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-20T10:00:00Z");
    private static final Instant TRANSITION_AT = Instant.parse("2026-08-20T10:00:05Z");
    private static final String CORRELATION =
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String RESULT =
            "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private final OperationIntentService service = new OperationIntentService(
            new UnusedRepository(),
            Clock.fixed(TRANSITION_AT, ZoneOffset.UTC));

    @Test
    void nativeAmbiguityAndReconciliationRemainOutboxFreeAndBounded() {
        OperationIntent created = intent(State.CREATED, null, null, null, null);

        OperationIntent ambiguous = service.prepareNativeAmbiguous(created, CORRELATION);
        assertEquals(State.AMBIGUOUS, ambiguous.state());
        assertEquals(CORRELATION, ambiguous.providerCorrelationHash());
        assertEquals(0, ambiguous.reconciliation().attempts());
        assertEquals(ReconciliationOutcome.PENDING, ambiguous.reconciliation().outcome());
        assertNull(ambiguous.resultDigest());
        assertNull(ambiguous.auditRef());
        assertEquals(created.outboxRef(), ambiguous.outboxRef());

        OperationIntent reconciling = service.prepareNativeReconciliation(ambiguous);
        assertEquals(State.RECONCILING, reconciling.state());
        assertEquals(1, reconciling.reconciliation().attempts());
        assertEquals(TRANSITION_AT, reconciling.reconciliation().lastAttemptAt());
        assertNull(reconciling.resultDigest());
        assertNull(reconciling.auditRef());

        OperationIntent exhausted = intent(
                State.AMBIGUOUS,
                CORRELATION,
                new Reconciliation(5, ReconciliationOutcome.PENDING, TRANSITION_AT, null),
                null,
                null);
        assertThrows(
                IllegalOperationTransitionException.class,
                () -> service.prepareNativeReconciliation(exhausted));
    }

    @Test
    void nativeTerminalTransitionsReuseTheSingleReservedOutboxReference() {
        OperationIntent reconciling = intent(
                State.RECONCILING,
                CORRELATION,
                new Reconciliation(2, ReconciliationOutcome.PENDING, TRANSITION_AT, null),
                null,
                null);

        var success = service.prepareNativeSuccess(reconciling, RESULT, "audit:native:success");

        assertEquals(State.SUCCEEDED, success.intent().state());
        assertEquals(ReconciliationOutcome.CONFIRMED_APPLIED, success.intent().reconciliation().outcome());
        assertEquals(RESULT, success.intent().reconciliation().resultDigest());
        assertEquals(reconciling.outboxRef(), success.outboxEvent().outboxRef());
        assertEquals("operation.succeeded", success.outboxEvent().eventType());

        OperationIntent created = intent(State.CREATED, null, null, null, null);
        var failure = service.prepareNativeFailure(created, true, RESULT, "audit:native:denied");

        assertEquals(State.DENIED, failure.intent().state());
        assertEquals(created.outboxRef(), failure.outboxEvent().outboxRef());
        assertEquals("operation.denied", failure.outboxEvent().eventType());
    }

    private OperationIntent intent(
            State state,
            String providerCorrelationHash,
            Reconciliation reconciliation,
            String resultDigest,
            String auditRef) {
        return new OperationIntent(
                "operation:native-transition",
                "idempotency-native-transition",
                "org:test",
                new HumanActor("person:alice", "subject:alice"),
                "files",
                new ProtocolProjection("webdav", "webdav-put", "weave.webdav.files/v1"),
                "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                List.of("file-path:sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"),
                "policy:1",
                "entitlement:1",
                1,
                state,
                "outbox:native-transition",
                providerCorrelationHash,
                reconciliation,
                resultDigest,
                auditRef,
                CREATED_AT,
                CREATED_AT);
    }

    private static final class UnusedRepository implements OperationIntentRepository {
        @Override
        public Optional<OperationIntent> findByOperationRef(String operationRef) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<OperationIntent> findByIdempotencyKey(String organizationRef, String idempotencyKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CreateResult create(OperationIntent intent, OperationOutboxEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationIntent update(
                OperationIntent expected,
                OperationIntent updated,
                OperationOutboxEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<OperationIntent> leaseReconciliationBatch(
                Instant now,
                int limit,
                Instant leaseUntil) {
            throw new UnsupportedOperationException();
        }
    }
}

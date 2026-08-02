package com.massimotter.weave.backend.operation.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Canonical durable mutation intent. This type deliberately has no Spring or provider dependencies. */
public record OperationIntent(
        String operationRef,
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
        long providerBindingRevision,
        State state,
        String outboxRef,
        String providerCorrelationHash,
        Reconciliation reconciliation,
        String resultDigest,
        String auditRef,
        Instant createdAt,
        Instant updatedAt) {

    public static final String VERSION = "weave.operation-intent/v2";

    public OperationIntent {
        operationRef = required(operationRef, "operationRef");
        idempotencyKey = bounded(idempotencyKey, "idempotencyKey", 16, 128);
        organizationRef = required(organizationRef, "organizationRef");
        actor = Objects.requireNonNull(actor, "actor must not be null");
        domain = required(domain, "domain");
        projection = Objects.requireNonNull(projection, "projection must not be null");
        actionDigest = digest(actionDigest, "actionDigest");
        canonicalArgumentsDigest = digest(canonicalArgumentsDigest, "canonicalArgumentsDigest");
        objectRefs = objectRefs == null ? List.of() : List.copyOf(objectRefs);
        policyRevision = required(policyRevision, "policyRevision");
        entitlementRevision = required(entitlementRevision, "entitlementRevision");
        if (providerBindingRevision < 1) {
            throw new IllegalArgumentException("providerBindingRevision must be positive");
        }
        state = Objects.requireNonNull(state, "state must not be null");
        outboxRef = required(outboxRef, "outboxRef");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if ((state == State.AMBIGUOUS || state == State.RECONCILING) && reconciliation == null) {
            throw new IllegalArgumentException("ambiguous and reconciling intents require reconciliation state");
        }
        if (state.terminal() && (resultDigest == null || auditRef == null)) {
            throw new IllegalArgumentException("terminal intents require resultDigest and auditRef");
        }
        if (providerCorrelationHash != null) {
            providerCorrelationHash = digest(providerCorrelationHash, "providerCorrelationHash");
        }
        if (resultDigest != null) {
            resultDigest = digest(resultDigest, "resultDigest");
        }
    }

    public enum State {
        CREATED,
        DISPATCHING,
        AMBIGUOUS,
        RECONCILING,
        SUCCEEDED,
        DENIED,
        FAILED;

        public boolean terminal() {
            return this == SUCCEEDED || this == DENIED || this == FAILED;
        }
    }

    public enum ReconciliationOutcome {
        NOT_REQUIRED,
        PENDING,
        CONFIRMED_APPLIED,
        CONFIRMED_NOT_APPLIED,
        MANUAL_REVIEW
    }

    public record Reconciliation(int attempts, ReconciliationOutcome outcome, Instant lastAttemptAt, String resultDigest) {
        public Reconciliation {
            if (attempts < 0) {
                throw new IllegalArgumentException("reconciliation attempts must not be negative");
            }
            outcome = Objects.requireNonNull(outcome, "reconciliation outcome must not be null");
            if (resultDigest != null) {
                resultDigest = digest(resultDigest, "reconciliation.resultDigest");
            }
        }
    }

    public sealed interface Actor permits HumanActor, WorkloadActor {
        String personRef();
    }

    public record HumanActor(String personRef, String subjectRef) implements Actor {
        public HumanActor {
            personRef = required(personRef, "personRef");
            subjectRef = required(subjectRef, "subjectRef");
        }
    }

    public record WorkloadActor(
            String personRef,
            String cellRef,
            String clientRef,
            long profileRevision,
            long fencingEpoch) implements Actor {
        public WorkloadActor {
            personRef = required(personRef, "personRef");
            cellRef = required(cellRef, "cellRef");
            clientRef = required(clientRef, "clientRef");
            if (profileRevision < 1 || fencingEpoch < 1) {
                throw new IllegalArgumentException("profileRevision and fencingEpoch must be positive");
            }
        }
    }

    public sealed interface Projection permits ProtocolProjection, AdminApiProjection, McpProjection, InternalProjection {
        String kind();
    }

    public record ProtocolProjection(String protocol, String operation, String profileVersion) implements Projection {
        public ProtocolProjection {
            protocol = required(protocol, "protocol");
            operation = required(operation, "operation");
            profileVersion = required(profileVersion, "profileVersion");
        }

        @Override public String kind() { return "protocol"; }
    }

    public record AdminApiProjection(String operationId, String contractVersion) implements Projection {
        public AdminApiProjection {
            operationId = required(operationId, "operationId");
            contractVersion = required(contractVersion, "contractVersion");
        }

        @Override public String kind() { return "admin-api"; }
    }

    public record McpProjection(String toolName, String toolContractVersion) implements Projection {
        public McpProjection {
            toolName = required(toolName, "toolName");
            toolContractVersion = required(toolContractVersion, "toolContractVersion");
        }

        @Override public String kind() { return "mcp"; }
    }

    public record InternalProjection(String useCase, String contractVersion) implements Projection {
        public InternalProjection {
            useCase = required(useCase, "useCase");
            contractVersion = required(contractVersion, "contractVersion");
        }

        @Override public String kind() { return "internal"; }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String bounded(String value, String field, int min, int max) {
        String normalized = required(value, field);
        if (normalized.length() < min || normalized.length() > max) {
            throw new IllegalArgumentException(field + " length is outside the accepted range");
        }
        return normalized;
    }

    private static String digest(String value, String field) {
        String normalized = required(value, field);
        if (!normalized.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException(field + " must be a sha256 digest");
        }
        return normalized;
    }
}

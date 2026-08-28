package com.massimotter.weave.backend.runner.application;

import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.TaskError;
import com.massimotter.weave.backend.runner.domain.RunnerControl.TaskState;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Durable task queue boundary used by the Runner control API. */
public interface RunnerTaskStore {

    Pattern SHA256 = Pattern.compile("sha256:[a-f0-9]{64}");
    Pattern IDEMPOTENCY_KEY = Pattern.compile("[^\\s]{16,256}");
    Pattern TRACEPARENT =
            Pattern.compile("[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
    Pattern CANCELLATION_REASON = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    void enqueue(NewTask task);

    Optional<Lease> claim(Claim claim);

    LeaseDirective heartbeat(Heartbeat heartbeat);

    CancellationDisposition requestCancellation(CancellationRequest request);

    CompletionDisposition complete(Completion completion);

    Optional<TaskSnapshot> find(UUID taskId);

    enum CompletionDisposition {
        APPLIED,
        IDEMPOTENT_REPLAY
    }

    enum CancellationDisposition {
        APPLIED,
        IDEMPOTENT_REPLAY
    }

    record NewTask(
            UUID taskId,
            String organizationRef,
            CapabilityRef capability,
            String bundleDigest,
            String idempotencyKey,
            String payloadJson,
            String contextRefsJson,
            String resourceGrantsJson,
            int priority,
            Instant createdAt,
            Instant availableAt,
            Instant deadline,
            String traceparent) {

        public NewTask {
            taskId = Objects.requireNonNull(taskId, "taskId");
            organizationRef = bounded(required(organizationRef, "organizationRef"), 256, "organizationRef");
            capability = Objects.requireNonNull(capability, "capability");
            bundleDigest = digest(bundleDigest, "bundleDigest");
            idempotencyKey = required(idempotencyKey, "idempotencyKey");
            if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
                throw new IllegalArgumentException("idempotencyKey has an invalid format");
            }
            payloadJson = json(payloadJson, 1024 * 1024, "payloadJson");
            contextRefsJson = jsonArray(contextRefsJson, 256 * 1024, "contextRefsJson");
            resourceGrantsJson = jsonArray(resourceGrantsJson, 256 * 1024, "resourceGrantsJson");
            if (priority < -1000 || priority > 1000) {
                throw new IllegalArgumentException("priority is outside the supported bound");
            }
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            availableAt = Objects.requireNonNull(availableAt, "availableAt");
            deadline = Objects.requireNonNull(deadline, "deadline");
            if (availableAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("availableAt must not precede createdAt");
            }
            if (!deadline.isAfter(availableAt)) {
                throw new IllegalArgumentException("deadline must be after availableAt");
            }
            traceparent = validateTraceparent(traceparent);
        }
    }

    record Claim(
            String organizationRef,
            RunnerId runnerId,
            String bundleDigest,
            Set<CapabilityRef> capabilities,
            Instant now,
            Duration leaseDuration) {

        public Claim {
            organizationRef = bounded(required(organizationRef, "organizationRef"), 256, "organizationRef");
            runnerId = Objects.requireNonNull(runnerId, "runnerId");
            bundleDigest = digest(bundleDigest, "bundleDigest");
            capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
            if (capabilities.isEmpty()
                    || capabilities.size() > 128
                    || capabilities.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("capabilities must contain between one and 128 values");
            }
            now = Objects.requireNonNull(now, "now");
            leaseDuration = validateLeaseDuration(leaseDuration);
        }

        public Set<String> capabilityCoordinates() {
            return capabilities.stream()
                    .map(CapabilityRef::coordinate)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    record Lease(
            UUID taskId,
            UUID leaseId,
            long fencingToken,
            RunnerId runnerId,
            CapabilityRef capability,
            String bundleDigest,
            int attempt,
            String idempotencyKey,
            String payloadJson,
            String contextRefsJson,
            String resourceGrantsJson,
            Instant issuedAt,
            Instant expiresAt,
            Instant deadline,
            String traceparent) {

        public Lease {
            taskId = Objects.requireNonNull(taskId, "taskId");
            leaseId = Objects.requireNonNull(leaseId, "leaseId");
            if (fencingToken < 1) {
                throw new IllegalArgumentException("fencingToken must be positive");
            }
            runnerId = Objects.requireNonNull(runnerId, "runnerId");
            capability = Objects.requireNonNull(capability, "capability");
            bundleDigest = digest(bundleDigest, "bundleDigest");
            if (attempt < 1 || attempt > 100) {
                throw new IllegalArgumentException("attempt must be between one and 100");
            }
            idempotencyKey = required(idempotencyKey, "idempotencyKey");
            payloadJson = json(payloadJson, 1024 * 1024, "payloadJson");
            contextRefsJson = jsonArray(contextRefsJson, 256 * 1024, "contextRefsJson");
            resourceGrantsJson = jsonArray(resourceGrantsJson, 256 * 1024, "resourceGrantsJson");
            issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
            expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            deadline = Objects.requireNonNull(deadline, "deadline");
            if (!expiresAt.isAfter(issuedAt) || deadline.isBefore(expiresAt)) {
                throw new IllegalArgumentException("lease times are invalid");
            }
            traceparent = validateTraceparent(traceparent);
        }
    }

    record Heartbeat(
            UUID taskId,
            UUID leaseId,
            long fencingToken,
            RunnerId runnerId,
            Instant observedAt,
            Duration leaseDuration) {

        public Heartbeat {
            taskId = Objects.requireNonNull(taskId, "taskId");
            leaseId = Objects.requireNonNull(leaseId, "leaseId");
            if (fencingToken < 1) {
                throw new IllegalArgumentException("fencingToken must be positive");
            }
            runnerId = Objects.requireNonNull(runnerId, "runnerId");
            observedAt = Objects.requireNonNull(observedAt, "observedAt");
            leaseDuration = validateLeaseDuration(leaseDuration);
        }
    }

    record LeaseDirective(
            UUID leaseId,
            long fencingToken,
            Instant expiresAt,
            boolean cancelRequested) {

        public LeaseDirective {
            leaseId = Objects.requireNonNull(leaseId, "leaseId");
            if (fencingToken < 1) {
                throw new IllegalArgumentException("fencingToken must be positive");
            }
            expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    record CancellationRequest(
            UUID taskId,
            String organizationRef,
            String reasonCode,
            Instant requestedAt) {

        public CancellationRequest {
            taskId = Objects.requireNonNull(taskId, "taskId");
            organizationRef = bounded(required(organizationRef, "organizationRef"), 256, "organizationRef");
            reasonCode = required(reasonCode, "reasonCode");
            if (!CANCELLATION_REASON.matcher(reasonCode).matches()) {
                throw new IllegalArgumentException("reasonCode has an invalid format");
            }
            requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        }
    }

    record Completion(
            UUID taskId,
            UUID leaseId,
            long fencingToken,
            TaskState state,
            String outcomeDigest,
            String resultJson,
            TaskError error,
            Instant completedAt) {

        public Completion {
            taskId = Objects.requireNonNull(taskId, "taskId");
            leaseId = Objects.requireNonNull(leaseId, "leaseId");
            if (fencingToken < 1) {
                throw new IllegalArgumentException("fencingToken must be positive");
            }
            state = Objects.requireNonNull(state, "state");
            if (!state.terminal()) {
                throw new IllegalArgumentException("completion state must be terminal");
            }
            outcomeDigest = digest(outcomeDigest, "outcomeDigest");
            resultJson = resultJson == null ? null : json(resultJson, 16 * 1024 * 1024, "resultJson");
            if ((state == TaskState.FAILED) != (error != null)) {
                throw new IllegalArgumentException("only failed completions require an error");
            }
            if (state == TaskState.SUCCEEDED && resultJson == null) {
                throw new IllegalArgumentException("successful completions require resultJson");
            }
            completedAt = Objects.requireNonNull(completedAt, "completedAt");
        }
    }

    record TaskSnapshot(
            UUID taskId,
            TaskState state,
            int attempt,
            long fencingToken,
            UUID leaseId,
            RunnerId runnerId,
            Instant leaseExpiresAt,
            boolean cancelRequested,
            String outcomeDigest) {

        public TaskSnapshot {
            taskId = Objects.requireNonNull(taskId, "taskId");
            state = Objects.requireNonNull(state, "state");
            if (attempt < 0 || fencingToken < 0) {
                throw new IllegalArgumentException("task counters must not be negative");
            }
            if ((leaseId == null) != (runnerId == null)) {
                throw new IllegalArgumentException("leaseId and runnerId must be present together");
            }
            outcomeDigest = outcomeDigest == null ? null : digest(outcomeDigest, "outcomeDigest");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not be blank or padded");
        }
        return value;
    }

    private static String bounded(String value, int maximum, String field) {
        if (value.length() > maximum) {
            throw new IllegalArgumentException(field + " exceeds the supported bound");
        }
        return value;
    }

    private static String digest(String value, String field) {
        String normalized = required(value, field);
        if (!SHA256.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a sha256 digest");
        }
        return normalized;
    }

    private static String json(String value, int maximum, String field) {
        String normalized = bounded(required(value, field), maximum, field);
        char first = normalized.charAt(0);
        if (first != '{' && first != '[' && first != '"' && !normalized.equals("true")
                && !normalized.equals("false") && !normalized.equals("null")
                && first != '-' && !Character.isDigit(first)) {
            throw new IllegalArgumentException(field + " is not a JSON value");
        }
        return normalized;
    }

    private static String jsonArray(String value, int maximum, String field) {
        String normalized = json(value, maximum, field);
        if (!normalized.startsWith("[")) {
            throw new IllegalArgumentException(field + " must be a JSON array");
        }
        return normalized;
    }

    private static Duration validateLeaseDuration(Duration value) {
        Duration normalized = Objects.requireNonNull(value, "leaseDuration");
        if (normalized.compareTo(Duration.ofSeconds(5)) < 0
                || normalized.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("leaseDuration must be between five seconds and five minutes");
        }
        return normalized;
    }

    private static String validateTraceparent(String value) {
        if (value != null && !TRACEPARENT.matcher(value).matches()) {
            throw new IllegalArgumentException("traceparent has an invalid format");
        }
        return value;
    }
}

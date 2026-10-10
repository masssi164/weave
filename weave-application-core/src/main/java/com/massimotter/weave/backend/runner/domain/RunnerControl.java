package com.massimotter.weave.backend.runner.domain;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Framework-free control-plane values shared by Runner application ports. */
public final class RunnerControl {

    private static final Pattern RUNNER_ID = Pattern.compile("runner_[A-Za-z0-9_-]{8,128}");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern VERSION = Pattern.compile(
            "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
                    + "(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?");
    private static final Pattern DIGEST = Pattern.compile("sha256:[a-f0-9]{64}");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[^\\s]{16,256}");
    private static final Pattern TRACEPARENT =
            Pattern.compile("[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");

    private RunnerControl() {}

    public enum RunnerState { ENROLLING, ONLINE, DEGRADED, OFFLINE, REVOKED }

    public enum CapabilityEffect { READ_ONLY, IDEMPOTENT_WRITE, NON_IDEMPOTENT_WRITE }

    public enum TaskState {
        READY, LEASED, RUNNING, SUCCEEDED, FAILED, CANCELLED;

        public boolean terminal() {
            return this == SUCCEEDED || this == FAILED || this == CANCELLED;
        }
    }

    public record RunnerId(String value) {
        public RunnerId {
            value = required(value, "runnerId");
            if (!RUNNER_ID.matcher(value).matches()) {
                throw new IllegalArgumentException("runnerId has an invalid format");
            }
        }
    }

    public record CapabilityId(String value) {
        public CapabilityId {
            value = identifier(value, "capabilityId");
        }
    }

    public record CapabilityRef(CapabilityId id, String version) {
        public CapabilityRef {
            id = Objects.requireNonNull(id, "id");
            version = semanticVersion(version, "version");
        }

        public String coordinate() {
            return id.value() + "@" + version;
        }
    }

    /** Public capability metadata; local handler paths and credentials are never included. */
    public record CapabilityDescriptor(
            CapabilityRef capability,
            String title,
            String description,
            CapabilityEffect effect,
            String inputSchemaJson,
            String inputSchemaDigest,
            String outputSchemaJson,
            String outputSchemaDigest,
            Duration timeout,
            long maximumOutputBytes,
            Set<String> artifactTypes) {

        public CapabilityDescriptor {
            capability = Objects.requireNonNull(capability, "capability");
            title = bounded(required(title, "title"), 160, "title");
            description = bounded(description == null ? "" : description.strip(), 1000, "description");
            effect = Objects.requireNonNull(effect, "effect");
            inputSchemaJson = schemaDocument(inputSchemaJson, "inputSchemaJson");
            inputSchemaDigest = sha256(inputSchemaDigest, "inputSchemaDigest");
            outputSchemaJson = schemaDocument(outputSchemaJson, "outputSchemaJson");
            outputSchemaDigest = sha256(outputSchemaDigest, "outputSchemaDigest");
            timeout = Objects.requireNonNull(timeout, "timeout");
            if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalArgumentException("timeout must be positive and at most one hour");
            }
            if (maximumOutputBytes < 1024 || maximumOutputBytes > 16L * 1024L * 1024L) {
                throw new IllegalArgumentException("maximumOutputBytes is outside the supported bound");
            }
            artifactTypes = immutableStrings(artifactTypes, 32, 160, "artifactTypes");
        }
    }

    public record RunnerRegistration(
            RunnerId id,
            String organizationRef,
            String runnerVersion,
            String bundleDigest,
            List<CapabilityDescriptor> capabilities,
            RunnerState state,
            int capacity,
            Instant observedAt,
            Map<String, String> labels) {

        public RunnerRegistration {
            id = Objects.requireNonNull(id, "id");
            organizationRef = bounded(required(organizationRef, "organizationRef"), 256, "organizationRef");
            runnerVersion = bounded(required(runnerVersion, "runnerVersion"), 96, "runnerVersion");
            bundleDigest = sha256(bundleDigest, "bundleDigest");
            capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
            if (capabilities.size() > 128 || capabilities.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("capabilities exceed the supported bound");
            }
            Set<String> coordinates = new HashSet<>();
            for (CapabilityDescriptor descriptor : capabilities) {
                if (!coordinates.add(descriptor.capability().coordinate())) {
                    throw new IllegalArgumentException("duplicate capability coordinate");
                }
            }
            state = Objects.requireNonNull(state, "state");
            if (capacity < 1 || capacity > 1024) {
                throw new IllegalArgumentException("capacity must be between one and 1024");
            }
            observedAt = Objects.requireNonNull(observedAt, "observedAt");
            labels = immutableStringMap(labels, 32, 256, "labels");
        }

        public boolean offers(CapabilityRef requested) {
            Objects.requireNonNull(requested, "requested");
            return capabilities.stream().anyMatch(value -> value.capability().equals(requested));
        }
    }

    public record ResourceGrant(URI resourceId, Set<String> operations, String expectedVersion) {
        public ResourceGrant {
            resourceId = Objects.requireNonNull(resourceId, "resourceId");
            if (!"weave".equals(resourceId.getScheme())
                    || !"resource".equals(resourceId.getHost())
                    || resourceId.getPath() == null
                    || resourceId.getPath().length() < 2
                    || resourceId.getQuery() != null
                    || resourceId.getFragment() != null) {
                throw new IllegalArgumentException("resourceId must use weave://resource/{id}");
            }
            operations = immutableIdentifiers(operations, 32, "operations");
            if (operations.isEmpty()) {
                throw new IllegalArgumentException("operations must not be empty");
            }
            expectedVersion = expectedVersion == null
                    ? null
                    : bounded(required(expectedVersion, "expectedVersion"), 256, "expectedVersion");
        }
    }

    public record TaskLease(
            UUID taskId,
            UUID leaseId,
            long fencingToken,
            RunnerId runnerId,
            CapabilityRef capability,
            String bundleDigest,
            int attempt,
            String idempotencyKey,
            Object payload,
            List<URI> contextRefs,
            List<ResourceGrant> resourceGrants,
            Instant issuedAt,
            Instant expiresAt,
            Instant deadline,
            String traceparent) {

        public TaskLease {
            taskId = Objects.requireNonNull(taskId, "taskId");
            leaseId = Objects.requireNonNull(leaseId, "leaseId");
            if (fencingToken < 1) {
                throw new IllegalArgumentException("fencingToken must be positive");
            }
            runnerId = Objects.requireNonNull(runnerId, "runnerId");
            capability = Objects.requireNonNull(capability, "capability");
            bundleDigest = sha256(bundleDigest, "bundleDigest");
            if (attempt < 1 || attempt > 100) {
                throw new IllegalArgumentException("attempt must be between one and 100");
            }
            idempotencyKey = required(idempotencyKey, "idempotencyKey");
            if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
                throw new IllegalArgumentException("idempotencyKey has an invalid format");
            }
            contextRefs = List.copyOf(contextRefs == null ? List.of() : contextRefs);
            if (contextRefs.size() > 128 || contextRefs.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("contextRefs exceed the supported bound");
            }
            for (URI contextRef : contextRefs) {
                if (!"weave".equals(contextRef.getScheme())
                        || contextRef.getHost() == null
                        || contextRef.getPath() == null
                        || contextRef.getPath().length() < 2
                        || contextRef.getQuery() != null
                        || contextRef.getFragment() != null) {
                    throw new IllegalArgumentException("contextRef must be an opaque Weave URI");
                }
            }
            resourceGrants = List.copyOf(resourceGrants == null ? List.of() : resourceGrants);
            if (resourceGrants.size() > 128 || resourceGrants.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("resourceGrants exceed the supported bound");
            }
            issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
            expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            if (!expiresAt.isAfter(issuedAt)) {
                throw new IllegalArgumentException("expiresAt must be after issuedAt");
            }
            if (deadline != null && deadline.isBefore(expiresAt)) {
                throw new IllegalArgumentException("deadline must not precede the lease expiry");
            }
            if (traceparent != null && !TRACEPARENT.matcher(traceparent).matches()) {
                throw new IllegalArgumentException("traceparent has an invalid format");
            }
        }

        public boolean activeAt(Instant instant) {
            Instant checked = Objects.requireNonNull(instant, "instant");
            return !checked.isBefore(issuedAt) && checked.isBefore(expiresAt);
        }

        public void requireFence(UUID presentedLeaseId, long presentedFencingToken) {
            if (!leaseId.equals(presentedLeaseId) || fencingToken != presentedFencingToken) {
                throw new StaleTaskLeaseException(taskId);
            }
        }
    }

    public record ArtifactManifest(
            UUID artifactId,
            String name,
            String artifactType,
            String mediaType,
            long size,
            String digest) {

        public ArtifactManifest {
            artifactId = Objects.requireNonNull(artifactId, "artifactId");
            name = name == null ? null : bounded(required(name, "name"), 256, "name");
            artifactType = artifactType == null
                    ? null
                    : bounded(required(artifactType, "artifactType"), 160, "artifactType");
            mediaType = bounded(required(mediaType, "mediaType"), 160, "mediaType");
            if (!mediaType.contains("/") || mediaType.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("mediaType is invalid");
            }
            if (size < 0 || size > 1024L * 1024L * 1024L) {
                throw new IllegalArgumentException("artifact size is outside the supported bound");
            }
            digest = sha256(digest, "digest");
        }
    }

    public record TaskError(String code, String message, boolean retryable) {
        public TaskError {
            code = required(code, "code");
            if (!code.matches("[A-Z][A-Z0-9_]{1,63}")) {
                throw new IllegalArgumentException("error code is invalid");
            }
            message = bounded(required(message, "message"), 1000, "message");
        }
    }

    public record TaskCompletion(
            UUID taskId,
            UUID leaseId,
            long fencingToken,
            CapabilityRef capability,
            TaskState state,
            Object result,
            List<ArtifactManifest> artifacts,
            TaskError error,
            Instant completedAt) {

        public TaskCompletion {
            taskId = Objects.requireNonNull(taskId, "taskId");
            leaseId = Objects.requireNonNull(leaseId, "leaseId");
            if (fencingToken < 1) {
                throw new IllegalArgumentException("fencingToken must be positive");
            }
            capability = Objects.requireNonNull(capability, "capability");
            state = Objects.requireNonNull(state, "state");
            if (!state.terminal()) {
                throw new IllegalArgumentException("completion state must be terminal");
            }
            artifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
            if (artifacts.size() > 128 || artifacts.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("artifacts exceed the supported bound");
            }
            if ((state == TaskState.FAILED) != (error != null)) {
                throw new IllegalArgumentException("only failed completions require an error");
            }
            completedAt = Objects.requireNonNull(completedAt, "completedAt");
        }
    }

    public static final class StaleTaskLeaseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public StaleTaskLeaseException(UUID taskId) {
            super("The task lease is stale for task " + Objects.requireNonNull(taskId, "taskId"));
        }
    }

    static String identifier(String value, String field) {
        String normalized = required(value, field);
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return normalized;
    }

    static String sha256(String value, String field) {
        String normalized = required(value, field);
        if (!DIGEST.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a sha256 digest");
        }
        return normalized;
    }

    static String required(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not be blank or padded");
        }
        return value;
    }

    static String bounded(String value, int maximum, String field) {
        if (value.length() > maximum) {
            throw new IllegalArgumentException(field + " exceeds the supported bound");
        }
        return value;
    }

    private static String schemaDocument(String value, String field) {
        String normalized = bounded(required(value, field), 262144, field);
        if (!(normalized.startsWith("{") || normalized.equals("true") || normalized.equals("false"))) {
            throw new IllegalArgumentException(field + " is not a JSON Schema document");
        }
        return normalized;
    }

    private static String semanticVersion(String value, String field) {
        String normalized = required(value, field);
        if (!VERSION.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " is not a semantic version");
        }
        return normalized;
    }

    private static Set<String> immutableIdentifiers(Set<String> values, int maximumItems, String field) {
        Set<String> result = new HashSet<>();
        for (String value : values == null ? Set.<String>of() : values) {
            if (!result.add(identifier(value, field))) {
                throw new IllegalArgumentException(field + " contains duplicates");
            }
        }
        if (result.size() > maximumItems) {
            throw new IllegalArgumentException(field + " exceeds the supported bound");
        }
        return Set.copyOf(result);
    }

    private static Set<String> immutableStrings(Set<String> values, int maximumItems, int maximumLength, String field) {
        Set<String> result = new HashSet<>();
        for (String value : values == null ? Set.<String>of() : values) {
            if (!result.add(bounded(required(value, field), maximumLength, field))) {
                throw new IllegalArgumentException(field + " contains duplicates");
            }
        }
        if (result.size() > maximumItems) {
            throw new IllegalArgumentException(field + " exceeds the supported bound");
        }
        return Set.copyOf(result);
    }

    private static Map<String, String> immutableStringMap(Map<String, String> values, int maximumItems, int maximumValueLength, String field) {
        Map<String, String> result = Map.copyOf(values == null ? Map.of() : values);
        if (result.size() > maximumItems) {
            throw new IllegalArgumentException(field + " exceeds the supported bound");
        }
        result.forEach((key, value) -> {
            identifier(key, field + " key");
            bounded(required(value, field + " value"), maximumValueLength, field + " value");
        });
        return result;
    }
}

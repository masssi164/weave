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
    private static final Pattern IDENTIFIER =
            Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern VERSION =
            Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
                    + "(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?");
    private static final Pattern DIGEST = Pattern.compile("sha256:[a-f0-9]{64}");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[^\\s]{16,256}");
    private static final Pattern OPERATION =
            Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern ARTIFACT_PATH =
            Pattern.compile("(?!/)(?!.*(?:^|/)\\.\\.(?:/|$))[^\\u0000\\r\\n]{1,512}");

    private RunnerControl() {}

    public enum RunnerState {
        ENROLLING,
        ONLINE,
        DEGRADED,
        OFFLINE,
        REVOKED
    }

    public enum CapabilityEffect {
        READ_ONLY,
        IDEMPOTENT_WRITE,
        NON_IDEMPOTENT_WRITE
    }

    public enum TaskState {
        READY,
        LEASED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED;

        public boolean terminal() {
            return this == SUCCEEDED || this == FAILED || this == CANCELLED;
        }
    }

    public enum ObservationSourceKind {
        DECLARATION,
        OPENAPI,
        ASYNCAPI,
        SBOM,
        OTEL,
        RUNTIME,
        CUSTOM
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
            version = required(version, "version");
            if (!VERSION.matcher(version).matches()) {
                throw new IllegalArgumentException("capability version is not semantic versioning");
            }
        }

        public String coordinate() {
            return id.value() + "@" + version;
        }
    }

    /** Public capability metadata; local handler paths and secrets never enter this model. */
    public record CapabilityDescriptor(
            CapabilityRef capability,
            String title,
            String description,
            CapabilityEffect effect,
            String inputSchemaDigest,
            String outputSchemaDigest,
            Duration timeout,
            long maximumOutputBytes,
            Set<String> artifactTypes) {

        public CapabilityDescriptor {
            capability = Objects.requireNonNull(capability, "capability");
            title = bounded(required(title, "title"), 160, "title");
            description = bounded(description == null ? "" : description.strip(), 1000, "description");
            effect = Objects.requireNonNull(effect, "effect");
            inputSchemaDigest = digest(inputSchemaDigest, "inputSchemaDigest");
            outputSchemaDigest = digest(outputSchemaDigest, "outputSchemaDigest");
            timeout = Objects.requireNonNull(timeout, "timeout");
            if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalArgumentException("timeout must be between one millisecond and one hour");
            }
            if (maximumOutputBytes < 1024 || maximumOutputBytes > 16L * 1024L * 1024L) {
                throw new IllegalArgumentException("maximumOutputBytes is outside the supported bound");
            }
            artifactTypes = immutableIdentifiers(artifactTypes, 32, "artifactTypes");
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
            bundleDigest = digest(bundleDigest, "bundleDigest");
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
                    || resourceId.getHost() != null
                    || resourceId.getPath() == null
                    || !resourceId.getPath().startsWith("/resource/")) {
                throw new IllegalArgumentException("resourceId must use weave://resource/{id}");
            }
            operations = immutableOperations(operations);
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
            Map<String, Object> payload,
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
            bundleDigest = digest(bundleDigest, "bundleDigest");
            if (attempt < 1 || attempt > 100) {
                throw new IllegalArgumentException("attempt must be between one and 100");
            }
            idempotencyKey = required(idempotencyKey, "idempotencyKey");
            if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
                throw new IllegalArgumentException("idempotencyKey has an invalid format");
            }
            payload = immutableObjectMap(payload, 256, "payload");
            contextRefs = List.copyOf(contextRefs == null ? List.of() : contextRefs);
            if (contextRefs.size() > 128 || contextRefs.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("contextRefs exceed the supported bound");
            }
            for (URI contextRef : contextRefs) {
                if (!"weave".equals(contextRef.getScheme())) {
                    throw new IllegalArgumentException("contextRef must use the weave scheme");
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
            traceparent = traceparent == null ? null : bounded(required(traceparent, "traceparent"), 55, "traceparent");
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
            String path,
            String name,
            String artifactType,
            String mediaType,
            long size,
            String digest) {

        public ArtifactManifest {
            path = required(path, "path");
            if (!ARTIFACT_PATH.matcher(path).matches()) {
                throw new IllegalArgumentException("artifact path is unsafe");
            }
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
            digest = digest(digest, "digest");
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

    public record ObservedEntity(
            String localKey,
            String kind,
            String displayName,
            Set<String> aliases,
            Map<String, Object> attributes,
            List<Evidence> evidence) {

        public ObservedEntity {
            localKey = bounded(required(localKey, "localKey"), 512, "localKey");
            kind = identifier(kind, "kind");
            displayName = displayName == null
                    ? null
                    : bounded(required(displayName, "displayName"), 256, "displayName");
            aliases = immutableStrings(aliases, 32, 512, "aliases");
            attributes = immutableObjectMap(attributes, 64, "attributes");
            evidence = immutableEvidence(evidence);
        }
    }

    public record ObservedRelation(
            String fromLocalKey,
            String predicate,
            String toLocalKey,
            double confidence,
            Map<String, Object> attributes,
            List<Evidence> evidence) {

        public ObservedRelation {
            fromLocalKey = bounded(required(fromLocalKey, "fromLocalKey"), 512, "fromLocalKey");
            predicate = identifier(predicate, "predicate");
            toLocalKey = bounded(required(toLocalKey, "toLocalKey"), 512, "toLocalKey");
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException("confidence must be between zero and one");
            }
            attributes = immutableObjectMap(attributes, 32, "attributes");
            evidence = immutableEvidence(evidence);
        }
    }

    public record Evidence(String kind, String reference, String digest) {
        public Evidence {
            kind = identifier(kind, "evidence kind");
            reference = bounded(required(reference, "evidence reference"), 1024, "evidence reference");
            digest = digest == null ? null : digest(digest, "evidence digest");
        }
    }

    public record ObservationBatch(
            RunnerId runnerId,
            String detectorId,
            String detectorVersion,
            ObservationSourceKind sourceKind,
            String scope,
            Instant observedAt,
            Duration ttl,
            List<ObservedEntity> entities,
            List<ObservedRelation> relations,
            String batchDigest) {

        public ObservationBatch {
            runnerId = Objects.requireNonNull(runnerId, "runnerId");
            detectorId = identifier(detectorId, "detectorId");
            detectorVersion = bounded(required(detectorVersion, "detectorVersion"), 96, "detectorVersion");
            sourceKind = Objects.requireNonNull(sourceKind, "sourceKind");
            scope = scope == null ? null : bounded(required(scope, "scope"), 256, "scope");
            observedAt = Objects.requireNonNull(observedAt, "observedAt");
            ttl = Objects.requireNonNull(ttl, "ttl");
            if (ttl.compareTo(Duration.ofSeconds(30)) < 0 || ttl.compareTo(Duration.ofDays(30)) > 0) {
                throw new IllegalArgumentException("ttl must be between 30 seconds and 30 days");
            }
            entities = List.copyOf(entities == null ? List.of() : entities);
            relations = List.copyOf(relations == null ? List.of() : relations);
            if (entities.size() > 4096 || entities.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("entities exceed the supported bound");
            }
            if (relations.size() > 8192 || relations.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("relations exceed the supported bound");
            }
            Set<String> keys = new HashSet<>();
            for (ObservedEntity entity : entities) {
                if (!keys.add(entity.localKey())) {
                    throw new IllegalArgumentException("duplicate observed entity localKey");
                }
            }
            for (ObservedRelation relation : relations) {
                if (!keys.contains(relation.fromLocalKey()) || !keys.contains(relation.toLocalKey())) {
                    throw new IllegalArgumentException("observed relation references an unknown localKey");
                }
            }
            batchDigest = batchDigest == null ? null : digest(batchDigest, "batchDigest");
        }

        public Instant expiresAt() {
            return observedAt.plus(ttl);
        }
    }

    public static final class StaleTaskLeaseException extends RuntimeException {
        public StaleTaskLeaseException(UUID taskId) {
            super("The task lease is stale for task " + Objects.requireNonNull(taskId, "taskId"));
        }
    }

    private static String identifier(String value, String field) {
        String normalized = required(value, field);
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return normalized;
    }

    private static String digest(String value, String field) {
        String normalized = required(value, field);
        if (!DIGEST.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a sha256 digest");
        }
        return normalized;
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

    private static Set<String> immutableIdentifiers(Set<String> values, int maximumItems, String field) {
        Set<String> result = new HashSet<>();
        for (String value : values == null ? Set.<String>of() : values) {
            result.add(identifier(value, field));
        }
        if (result.size() > maximumItems) {
            throw new IllegalArgumentException(field + " exceeds the supported bound");
        }
        return Set.copyOf(result);
    }

    private static Set<String> immutableOperations(Set<String> values) {
        if (values == null || values.isEmpty() || values.size() > 32) {
            throw new IllegalArgumentException("operations must contain between one and 32 values");
        }
        Set<String> result = new HashSet<>();
        for (String value : values) {
            String operation = required(value, "operation");
            if (!OPERATION.matcher(operation).matches() || !result.add(operation)) {
                throw new IllegalArgumentException("operation is invalid or duplicated");
            }
        }
        return Set.copyOf(result);
    }

    private static Set<String> immutableStrings(
            Set<String> values,
            int maximumItems,
            int maximumLength,
            String field) {
        Set<String> result = new HashSet<>();
        for (String value : values == null ? Set.<String>of() : values) {
            result.add(bounded(required(value, field), maximumLength, field));
        }
        if (result.size() > maximumItems) {
            throw new IllegalArgumentException(field + " exceeds the supported bound");
        }
        return Set.copyOf(result);
    }

    private static Map<String, String> immutableStringMap(
            Map<String, String> values,
            int maximumItems,
            int maximumValueLength,
            String field) {
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

    private static Map<String, Object> immutableObjectMap(
            Map<String, Object> values,
            int maximumItems,
            String field) {
        Map<String, Object> result = Map.copyOf(values == null ? Map.of() : values);
        if (result.size() > maximumItems) {
            throw new IllegalArgumentException(field + " exceeds the supported bound");
        }
        result.forEach((key, value) -> {
            bounded(required(key, field + " key"), 128, field + " key");
            if (value == null) {
                throw new IllegalArgumentException(field + " values must not be null");
            }
        });
        return result;
    }

    private static List<Evidence> immutableEvidence(List<Evidence> values) {
        List<Evidence> result = List.copyOf(values == null ? List.of() : values);
        if (result.size() > 32 || result.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("evidence exceeds the supported bound");
        }
        return result;
    }
}

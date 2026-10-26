package com.massimotter.weave.backend.runner.domain;

import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Evidence-based observations emitted by deterministic Runner detectors. */
public final class TopologyObservation {

    private TopologyObservation() {}

    public enum SourceKind { DECLARATION, OPENAPI, ASYNCAPI, SBOM, OTEL, RUNTIME, CUSTOM }

    public enum EvidenceKind { DECLARATION, DOCUMENT, SCHEMA, SBOM, TRACE, RUNTIME, HASH, CUSTOM }

    public record Evidence(EvidenceKind kind, String reference, String digest) {
        public Evidence {
            kind = Objects.requireNonNull(kind, "kind");
            reference = RunnerControl.bounded(
                    RunnerControl.required(reference, "evidence reference"),
                    1024,
                    "evidence reference");
            digest = digest == null ? null : RunnerControl.sha256(digest, "evidence digest");
        }
    }

    public record Entity(
            String localKey,
            String kind,
            String displayName,
            Set<String> aliases,
            Map<String, Object> attributes,
            List<Evidence> evidence) {

        public Entity {
            localKey = RunnerControl.bounded(RunnerControl.required(localKey, "localKey"), 512, "localKey");
            kind = RunnerControl.identifier(kind, "entity kind");
            displayName = displayName == null
                    ? null
                    : RunnerControl.bounded(RunnerControl.required(displayName, "displayName"), 256, "displayName");
            aliases = Set.copyOf(aliases == null ? Set.of() : aliases);
            if (aliases.size() > 32
                    || aliases.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 512)) {
                throw new IllegalArgumentException("aliases exceed the supported bound");
            }
            attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
            if (attributes.size() > 64
                    || attributes.keySet().stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 128)) {
                throw new IllegalArgumentException("attributes exceed the supported bound");
            }
            evidence = immutableEvidence(evidence);
        }
    }

    public record Relation(
            String fromLocalKey,
            String predicate,
            String toLocalKey,
            double confidence,
            Map<String, Object> attributes,
            List<Evidence> evidence) {

        public Relation {
            fromLocalKey = RunnerControl.bounded(
                    RunnerControl.required(fromLocalKey, "fromLocalKey"), 512, "fromLocalKey");
            predicate = RunnerControl.identifier(predicate, "predicate");
            toLocalKey = RunnerControl.bounded(
                    RunnerControl.required(toLocalKey, "toLocalKey"), 512, "toLocalKey");
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException("confidence must be between zero and one");
            }
            attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
            if (attributes.size() > 32
                    || attributes.keySet().stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 128)) {
                throw new IllegalArgumentException("attributes exceed the supported bound");
            }
            evidence = immutableEvidence(evidence);
        }
    }

    public record Batch(
            RunnerId runnerId,
            String detectorId,
            String detectorVersion,
            SourceKind sourceKind,
            String scope,
            Instant observedAt,
            Duration ttl,
            List<Entity> entities,
            List<Relation> relations,
            String batchDigest) {

        public Batch {
            runnerId = Objects.requireNonNull(runnerId, "runnerId");
            detectorId = RunnerControl.identifier(detectorId, "detectorId");
            detectorVersion = RunnerControl.bounded(
                    RunnerControl.required(detectorVersion, "detectorVersion"), 96, "detectorVersion");
            sourceKind = Objects.requireNonNull(sourceKind, "sourceKind");
            scope = scope == null ? null : RunnerControl.bounded(RunnerControl.required(scope, "scope"), 256, "scope");
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
            for (Entity entity : entities) {
                if (!keys.add(entity.localKey())) {
                    throw new IllegalArgumentException("duplicate observed entity localKey");
                }
            }
            for (Relation relation : relations) {
                if (!keys.contains(relation.fromLocalKey()) || !keys.contains(relation.toLocalKey())) {
                    throw new IllegalArgumentException("observed relation references an unknown localKey");
                }
            }
            batchDigest = batchDigest == null ? null : RunnerControl.sha256(batchDigest, "batchDigest");
        }

        public Instant expiresAt() {
            return observedAt.plus(ttl);
        }
    }

    private static List<Evidence> immutableEvidence(List<Evidence> values) {
        List<Evidence> result = List.copyOf(values == null ? List.of() : values);
        if (result.size() > 32 || result.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("evidence exceeds the supported bound");
        }
        return result;
    }
}

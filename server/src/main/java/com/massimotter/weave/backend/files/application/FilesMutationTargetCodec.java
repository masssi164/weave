package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.files.port.FilesMutationPlan.Target;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Fence;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Exact RFC 8785 codec for adapter-private {@code FilesMutationTarget v1} records. */
@Component
public final class FilesMutationTargetCodec {

    private static final DateTimeFormatter CANONICAL_INSTANT =
            new DateTimeFormatterBuilder().appendInstant(6).toFormatter();

    private final ObjectMapper objectMapper;

    public FilesMutationTargetCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public String targetsDigest(List<Target> targets) {
        return digest(canonicalTargets(targets));
    }

    public String fencesDigest(List<Fence> fences) {
        return digest(canonicalFences(fences));
    }

    private String digest(byte[] canonical) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required for Files mutation plans", impossible);
        }
    }

    public byte[] canonicalTargets(List<Target> targets) {
        List<Target> ordered = targets == null ? List.of() : List.copyOf(targets);
        List<Map<String, Object>> projections = new ArrayList<>(ordered.size());
        for (int ordinal = 0; ordinal < ordered.size(); ordinal++) {
            Target target = Objects.requireNonNull(ordered.get(ordinal), "target must not be null");
            if (target.targetOrdinal() != ordinal) {
                throw new IllegalArgumentException("target ordinals must be contiguous and ordered");
            }
            projections.add(projection(target));
        }
        try {
            String json = objectMapper.writeValueAsString(projections);
            return new JsonCanonicalizer(json).getEncodedUTF8();
        } catch (JacksonException | IOException exception) {
            throw new IllegalArgumentException("Files mutation targets are not serializable", exception);
        }
    }

    public String canonicalTargetsJson(List<Target> targets) {
        return new String(canonicalTargets(targets), StandardCharsets.UTF_8);
    }

    public byte[] canonicalFences(List<Fence> fences) {
        List<Fence> ordered = fences == null ? List.of() : List.copyOf(fences);
        List<Map<String, Object>> projections = new ArrayList<>(ordered.size());
        for (int ordinal = 0; ordinal < ordered.size(); ordinal++) {
            Fence fence = Objects.requireNonNull(ordered.get(ordinal), "fence must not be null");
            if (fence.fenceOrdinal() != ordinal) {
                throw new IllegalArgumentException("fence ordinals must be contiguous and ordered");
            }
            if (!constantEquals(fenceSnapshotDigest(fence), fence.snapshotDigest())) {
                throw new IllegalArgumentException("fence snapshot digest does not match RFC 8785 projection");
            }
            projections.add(fenceProjection(fence));
        }
        try {
            String json = objectMapper.writeValueAsString(projections);
            return new JsonCanonicalizer(json).getEncodedUTF8();
        } catch (JacksonException | IOException exception) {
            throw new IllegalArgumentException("Files mutation fences are not serializable", exception);
        }
    }

    public String canonicalFencesJson(List<Fence> fences) {
        return new String(canonicalFences(fences), StandardCharsets.UTF_8);
    }

    private Map<String, Object> fenceProjection(Fence fence) {
        Map<String, Object> value = fenceSnapshotProjection(fence);
        value.put("snapshotDigest", fence.snapshotDigest());
        return value;
    }

    private String fenceSnapshotDigest(Fence fence) {
        try {
            String json = objectMapper.writeValueAsString(fenceSnapshotProjection(fence));
            return digest(new JsonCanonicalizer(json).getEncodedUTF8());
        } catch (JacksonException | IOException exception) {
            throw new IllegalArgumentException("Files mutation fence is not serializable", exception);
        }
    }

    private Map<String, Object> fenceSnapshotProjection(Fence fence) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("fenceVersion", fence.fenceVersion());
        value.put("fenceOrdinal", fence.fenceOrdinal());
        value.put("fenceRole", fence.fenceRole().name());
        value.put("canonicalPath", fence.canonicalPath());
        value.put("expectedPresence", fence.expectedPresence().name());
        value.put("expectedFileRef", fence.expectedFileRef());
        value.put("expectedObjectKind", fence.expectedObjectKind() == null
                ? null
                : fence.expectedObjectKind().name());
        value.put("expectedLifecycleState", fence.expectedLifecycleState() == null
                ? null
                : fence.expectedLifecycleState().name());
        value.put("expectedRowVersion", fence.expectedRowVersion());
        value.put("expectedStrongEtag", fence.expectedStrongEtag());
        value.put("expectedSubtreeDigest", fence.expectedSubtreeDigest());
        return value;
    }

    private boolean constantEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private Map<String, Object> projection(Target target) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("targetVersion", target.targetVersion());
        value.put("targetOrdinal", target.targetOrdinal());
        value.put("changeKind", target.changeKind().name());
        value.put("sourceFileRef", target.sourceFileRef());
        value.put("targetFileRef", target.targetFileRef());
        value.put("sourcePath", target.sourcePath());
        value.put("targetPath", target.targetPath());
        value.put("objectKind", target.objectKind().name());
        value.put("resultLifecycleState", target.resultLifecycleState().name());
        value.put("sourceReadBlobBinding", target.sourceReadBlobBinding());
        value.put("sourceSize", target.sourceSize());
        value.put("sourceMediaType", target.sourceMediaType());
        value.put("sourceContentDigest", target.sourceContentDigest());
        value.put("sourceFileVersion", target.sourceFileVersion());
        value.put("sourceStrongEtag", target.sourceStrongEtag());
        value.put("sourceModifiedAt", instant(target.sourceModifiedAt()));
        value.put("sourceHidden", target.sourceHidden());
        value.put("sourceObservedAt", instant(target.sourceObservedAt()));
        value.put("sourceLifecycleState", target.sourceLifecycleState() == null
                ? null
                : target.sourceLifecycleState().name());
        value.put("resultBlobBinding", target.resultBlobBinding());
        value.put("resultSize", target.resultSize());
        value.put("resultMediaType", target.resultMediaType());
        value.put("resultContentDigest", target.resultContentDigest());
        value.put("resultFileVersion", target.resultFileVersion());
        value.put("resultStrongEtag", target.resultStrongEtag());
        value.put("resultModifiedAt", instant(target.resultModifiedAt()));
        value.put("resultHidden", target.resultHidden());
        value.put("resultObservedAt", instant(target.resultObservedAt()));
        return value;
    }

    private String instant(Instant value) {
        return value == null ? null : CANONICAL_INSTANT.format(value);
    }
}

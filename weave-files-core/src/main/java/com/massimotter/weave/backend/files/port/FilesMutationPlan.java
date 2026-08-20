package com.massimotter.weave.backend.files.port;

import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persistence-private native Files mutation plan.
 *
 * <p>The records in this type are application-port values. They are never canonical Files
 * metadata, member responses, portability payloads, audit payloads, or support evidence.</p>
 */
public final class FilesMutationPlan {

    public static final String VERSION = "weave.files-mutation-plan/v1";
    public static final String TARGET_VERSION = "weave.files-mutation-target/v1";
    public static final String FENCE_VERSION = "weave.files-mutation-fence/v1";
    public static final long JSON_SAFE_INTEGER_MAX = 9_007_199_254_740_991L;
    private static final Comparator<String> UTF8_UNSIGNED = FilesMutationPlan::compareUtf8;

    private FilesMutationPlan() {
    }

    public enum OperationKind {
        PUT,
        MKCOL,
        COPY,
        MOVE,
        DELETE
    }

    /** Protocol-neutral persisted form of an HTTP entity-tag condition. */
    public static final class EntityTagCondition {
        private static final String NOT_SUPPLIED = "NOT_SUPPLIED";
        private static final String ANY = "ANY";
        private static final String SET_PREFIX = "ETAG_SET:";

        private final String canonicalValue;
        private final List<String> entityTags;

        private EntityTagCondition(String canonicalValue, List<String> entityTags) {
            this.canonicalValue = canonicalValue;
            this.entityTags = List.copyOf(entityTags);
        }

        public static EntityTagCondition notSupplied() {
            return new EntityTagCondition(NOT_SUPPLIED, List.of());
        }

        public static EntityTagCondition parseHeader(String header) {
            if (header == null) {
                return notSupplied();
            }
            String value = header.trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("entity-tag condition must not be blank");
            }
            if ("*".equals(value)) {
                return new EntityTagCondition(ANY, List.of());
            }
            List<String> tags = parseEntityTagList(value);
            if (tags.isEmpty()) {
                throw new IllegalArgumentException("entity-tag condition must contain an entity tag");
            }
            List<String> ordered = new ArrayList<>(new LinkedHashSet<>(tags));
            ordered.sort(UTF8_UNSIGNED);
            return new EntityTagCondition(SET_PREFIX + jsonArray(ordered), ordered);
        }

        public static EntityTagCondition parseCanonical(String value) {
            String required = required(value, "entityTagCondition");
            if (NOT_SUPPLIED.equals(required)) {
                return notSupplied();
            }
            if (ANY.equals(required)) {
                return new EntityTagCondition(ANY, List.of());
            }
            if (!required.startsWith(SET_PREFIX)) {
                throw new IllegalArgumentException("unknown canonical entity-tag condition");
            }
            List<String> tags = parseJsonStringArray(required.substring(SET_PREFIX.length()));
            EntityTagCondition condition = parseHeader(String.join(",", tags));
            if (!condition.canonicalValue.equals(required)) {
                throw new IllegalArgumentException("entity-tag condition is not canonical");
            }
            return condition;
        }

        public String canonicalValue() {
            return canonicalValue;
        }

        public boolean supplied() {
            return !NOT_SUPPLIED.equals(canonicalValue);
        }

        public boolean any() {
            return ANY.equals(canonicalValue);
        }

        public boolean matches(String currentStrongEtag, boolean strongComparison) {
            if (!supplied()) {
                return true;
            }
            if (any()) {
                return currentStrongEtag != null;
            }
            if (currentStrongEtag == null) {
                return false;
            }
            String currentOpaque = weakOpaque(currentStrongEtag);
            return entityTags.stream().anyMatch(tag -> {
                if (strongComparison && (tag.startsWith("W/") || currentStrongEtag.startsWith("W/"))) {
                    return false;
                }
                return strongComparison
                        ? tag.equals(currentStrongEtag)
                        : weakOpaque(tag).equals(currentOpaque);
            });
        }

        public boolean requiresAbsence() {
            return any();
        }

        @Override public boolean equals(Object candidate) {
            return this == candidate || candidate instanceof EntityTagCondition other
                    && canonicalValue.equals(other.canonicalValue);
        }

        @Override public int hashCode() {
            return canonicalValue.hashCode();
        }

        @Override public String toString() {
            return canonicalValue;
        }
    }

    public enum FenceRole {
        REQUEST_TARGET,
        SOURCE_MEMBER,
        DESTINATION_TARGET,
        DESTINATION_MEMBER
    }

    public enum ExpectedPresence {
        ABSENT,
        PRESENT
    }

    /** Exact adapter-private Tx1 path observation. */
    public record Fence(
            int fenceOrdinal,
            FenceRole fenceRole,
            String canonicalPath,
            ExpectedPresence expectedPresence,
            String expectedFileRef,
            Kind expectedObjectKind,
            Lifecycle expectedLifecycleState,
            Long expectedRowVersion,
            String expectedStrongEtag,
            String expectedSubtreeDigest,
            String snapshotDigest) {

        public Fence {
            if (fenceOrdinal < 0) {
                throw new IllegalArgumentException("fenceOrdinal must not be negative");
            }
            fenceRole = Objects.requireNonNull(fenceRole, "fenceRole must not be null");
            canonicalPath = path(canonicalPath, "canonicalPath");
            requirePath(canonicalPath, "canonicalPath");
            expectedPresence = Objects.requireNonNull(expectedPresence, "expectedPresence must not be null");
            expectedFileRef = optional(expectedFileRef);
            expectedStrongEtag = optional(expectedStrongEtag);
            expectedSubtreeDigest = optionalDigest(expectedSubtreeDigest, "expectedSubtreeDigest");
            snapshotDigest = digest(snapshotDigest, "snapshotDigest");
            if (expectedPresence == ExpectedPresence.ABSENT) {
                if (expectedFileRef != null
                        || expectedObjectKind != null
                        || expectedLifecycleState != null
                        || expectedRowVersion != null
                        || expectedStrongEtag != null
                        || expectedSubtreeDigest != null) {
                    throw new IllegalArgumentException("an absent fence cannot carry an object snapshot");
                }
            } else if (expectedFileRef == null
                    || expectedObjectKind == null
                    || expectedLifecycleState == null
                    || expectedRowVersion == null
                    || expectedRowVersion < 0) {
                throw new IllegalArgumentException("a present fence requires a complete object snapshot");
            }
            String actual = fenceSnapshotDigest(
                    fenceOrdinal,
                    fenceRole,
                    canonicalPath,
                    expectedPresence,
                    expectedFileRef,
                    expectedObjectKind,
                    expectedLifecycleState,
                    expectedRowVersion,
                    expectedStrongEtag,
                    expectedSubtreeDigest);
            if (!actual.equals(snapshotDigest)) {
                throw new IllegalArgumentException("fence snapshot digest does not match its members");
            }
        }

        public static Fence absent(int ordinal, FenceRole role, String path) {
            return new Fence(
                    ordinal, role, path, ExpectedPresence.ABSENT,
                    null, null, null, null, null, null,
                    fenceSnapshotDigest(
                            ordinal, role, path, ExpectedPresence.ABSENT,
                            null, null, null, null, null, null));
        }

        public static Fence present(
                int ordinal,
                FenceRole role,
                String path,
                String fileRef,
                Kind objectKind,
                Lifecycle lifecycle,
                long rowVersion,
                String strongEtag,
                String subtreeDigest) {
            return new Fence(
                    ordinal, role, path, ExpectedPresence.PRESENT,
                    fileRef, objectKind, lifecycle, rowVersion, strongEtag, subtreeDigest,
                    fenceSnapshotDigest(
                            ordinal, role, path, ExpectedPresence.PRESENT,
                            fileRef, objectKind, lifecycle, rowVersion, strongEtag, subtreeDigest));
        }

        public String fenceVersion() {
            return FENCE_VERSION;
        }
    }

    public record Membership(String canonicalPath, String fileRef) {
        public Membership {
            canonicalPath = path(canonicalPath, "canonicalPath");
            requirePath(canonicalPath, "canonicalPath");
            fileRef = required(fileRef, "fileRef");
        }
    }

    /** Complete target set before its RFC 8785 digest is attached and committed. */
    public record Draft(
            String operationRef,
            String organizationRef,
            String spaceRef,
            String canonicalArgumentsDigest,
            OperationKind operationKind,
            long providerBindingRevision,
            EntityTagCondition ifMatchCondition,
            EntityTagCondition ifNoneMatchCondition,
            boolean destinationMustRemainAbsent,
            List<Target> targets,
            List<Fence> fences) {

        public Draft {
            operationRef = required(operationRef, "operationRef");
            organizationRef = required(organizationRef, "organizationRef");
            spaceRef = required(spaceRef, "spaceRef");
            canonicalArgumentsDigest = digest(canonicalArgumentsDigest, "canonicalArgumentsDigest");
            operationKind = Objects.requireNonNull(operationKind, "operationKind must not be null");
            if (providerBindingRevision < 1) {
                throw new IllegalArgumentException("providerBindingRevision must be positive");
            }
            ifMatchCondition = Objects.requireNonNull(ifMatchCondition, "ifMatchCondition must not be null");
            ifNoneMatchCondition = Objects.requireNonNull(ifNoneMatchCondition, "ifNoneMatchCondition must not be null");
            if (destinationMustRemainAbsent
                    && operationKind != OperationKind.COPY
                    && operationKind != OperationKind.MOVE) {
                throw new IllegalArgumentException("only COPY or MOVE may require an absent destination");
            }
            targets = targets == null ? List.of() : List.copyOf(targets);
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("a Files mutation plan requires at least one target");
            }
            Set<String> targetIds = new HashSet<>();
            for (int index = 0; index < targets.size(); index++) {
                Target target = Objects.requireNonNull(targets.get(index), "target must not be null");
                if (target.targetOrdinal() != index) {
                    throw new IllegalArgumentException("target ordinals must be contiguous from zero");
                }
                if (!targetIds.add(target.targetFileRef())) {
                    throw new IllegalArgumentException("a logical commit may target a FileId only once");
                }
            }
            fences = fences == null ? List.of() : List.copyOf(fences);
            if (fences.isEmpty()) {
                throw new IllegalArgumentException("a Files mutation plan requires at least one fence");
            }
            for (int index = 0; index < fences.size(); index++) {
                Fence fence = Objects.requireNonNull(fences.get(index), "fence must not be null");
                if (fence.fenceOrdinal() != index) {
                    throw new IllegalArgumentException("fence ordinals must be contiguous from zero");
                }
            }
            validateFenceSet(operationKind, destinationMustRemainAbsent, fences);
        }

        public Sealed seal(String targetsDigest, String fencesDigest, Instant sealedAt) {
            return new Sealed(this, targetsDigest, fencesDigest, sealedAt);
        }
    }

    /** The only plan form allowed to cross the pre-blob access guard. */
    public record Sealed(Draft draft, String targetsDigest, String fencesDigest, Instant sealedAt) {
        public Sealed {
            draft = Objects.requireNonNull(draft, "draft must not be null");
            targetsDigest = digest(targetsDigest, "targetsDigest");
            fencesDigest = digest(fencesDigest, "fencesDigest");
            sealedAt = canonicalInstant(sealedAt, "sealedAt");
        }

        public String operationRef() {
            return draft.operationRef();
        }

        public String organizationRef() {
            return draft.organizationRef();
        }

        public String spaceRef() {
            return draft.spaceRef();
        }

        public String canonicalArgumentsDigest() {
            return draft.canonicalArgumentsDigest();
        }

        public OperationKind operationKind() {
            return draft.operationKind();
        }

        public long providerBindingRevision() {
            return draft.providerBindingRevision();
        }

        public EntityTagCondition ifMatchCondition() {
            return draft.ifMatchCondition();
        }

        public EntityTagCondition ifNoneMatchCondition() {
            return draft.ifNoneMatchCondition();
        }

        public boolean destinationMustRemainAbsent() {
            return draft.destinationMustRemainAbsent();
        }

        public List<Target> targets() {
            return draft.targets();
        }

        public int targetCount() {
            return draft.targets().size();
        }

        public List<Fence> fences() {
            return draft.fences();
        }

        public int fenceCount() {
            return draft.fences().size();
        }
    }

    /** Exact {@code weave.files-mutation-target/v1} projection before JSON serialization. */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public record Target(
            int targetOrdinal,
            ChangeKind changeKind,
            String sourceFileRef,
            String targetFileRef,
            String sourcePath,
            String targetPath,
            Kind objectKind,
            Lifecycle resultLifecycleState,
            String sourceReadBlobBinding,
            Long sourceSize,
            String sourceMediaType,
            String sourceContentDigest,
            String sourceFileVersion,
            String sourceStrongEtag,
            Instant sourceModifiedAt,
            Boolean sourceHidden,
            Instant sourceObservedAt,
            Lifecycle sourceLifecycleState,
            String resultBlobBinding,
            long resultSize,
            String resultMediaType,
            String resultContentDigest,
            String resultFileVersion,
            String resultStrongEtag,
            Instant resultModifiedAt,
            boolean resultHidden,
            Instant resultObservedAt) {

        public Target {
            if (targetOrdinal < 0) {
                throw new IllegalArgumentException("targetOrdinal must not be negative");
            }
            changeKind = Objects.requireNonNull(changeKind, "changeKind must not be null");
            sourceFileRef = optional(sourceFileRef);
            targetFileRef = required(targetFileRef, "targetFileRef");
            sourcePath = path(sourcePath, "sourcePath");
            targetPath = path(targetPath, "targetPath");
            objectKind = Objects.requireNonNull(objectKind, "objectKind must not be null");
            resultLifecycleState = Objects.requireNonNull(
                    resultLifecycleState,
                    "resultLifecycleState must not be null");
            sourceReadBlobBinding = optional(sourceReadBlobBinding);
            sourceMediaType = optional(sourceMediaType);
            sourceContentDigest = optionalDigest(sourceContentDigest, "sourceContentDigest");
            sourceFileVersion = optional(sourceFileVersion);
            sourceStrongEtag = optional(sourceStrongEtag);
            sourceModifiedAt = optionalCanonicalInstant(sourceModifiedAt, "sourceModifiedAt");
            sourceObservedAt = optionalCanonicalInstant(sourceObservedAt, "sourceObservedAt");
            resultBlobBinding = optional(resultBlobBinding);
            boundedSize(resultSize, "resultSize");
            resultMediaType = optional(resultMediaType);
            resultContentDigest = optionalDigest(resultContentDigest, "resultContentDigest");
            resultFileVersion = optional(resultFileVersion);
            resultStrongEtag = optional(resultStrongEtag);
            resultModifiedAt = canonicalInstant(resultModifiedAt, "resultModifiedAt");
            resultObservedAt = canonicalInstant(resultObservedAt, "resultObservedAt");

            if (sourceReadBlobBinding == null) {
                if (sourceSize != null
                        || sourceMediaType != null
                        || sourceContentDigest != null
                        || sourceFileVersion != null
                        || sourceStrongEtag != null
                        || sourceModifiedAt != null
                        || sourceHidden != null
                        || sourceObservedAt != null
                        || sourceLifecycleState != null) {
                    throw new IllegalArgumentException(
                            "source content snapshot requires a source blob binding");
                }
            } else {
                if (sourceSize == null
                        || sourceContentDigest == null
                        || sourceFileVersion == null
                        || sourceStrongEtag == null
                        || sourceModifiedAt == null
                        || sourceHidden == null
                        || sourceObservedAt == null
                        || sourceLifecycleState != Lifecycle.ACTIVE) {
                    throw new IllegalArgumentException(
                            "source blob binding requires a complete active source snapshot");
                }
                boundedSize(sourceSize, "sourceSize");
            }

            if (objectKind == Kind.COLLECTION) {
                if (sourceReadBlobBinding != null
                        || resultBlobBinding != null
                        || resultSize != 0
                        || resultMediaType != null
                        || resultContentDigest != null
                        || resultFileVersion != null
                        || resultStrongEtag != null) {
                    throw new IllegalArgumentException("collections cannot carry content fields");
                }
            } else if (resultBlobBinding == null
                    || resultContentDigest == null
                    || resultFileVersion == null
                    || resultStrongEtag == null) {
                throw new IllegalArgumentException("file targets require a complete result content snapshot");
            }

            switch (changeKind) {
                case CREATED, CONTENT_UPDATED -> requirePath(targetPath, "targetPath");
                case COPIED, MOVED -> {
                    required(sourceFileRef, "sourceFileRef");
                    requirePath(sourcePath, "sourcePath");
                    requirePath(targetPath, "targetPath");
                }
                case TOMBSTONED -> {
                    requirePath(sourcePath, "sourcePath");
                    if (targetPath != null || resultLifecycleState != Lifecycle.TOMBSTONED) {
                        throw new IllegalArgumentException(
                                "tombstones require a null target path and TOMBSTONED lifecycle");
                    }
                }
            }
            if (changeKind != ChangeKind.TOMBSTONED
                    && resultLifecycleState != Lifecycle.ACTIVE) {
                throw new IllegalArgumentException("non-tombstone targets must be ACTIVE");
            }
        }

        public String targetVersion() {
            return TARGET_VERSION;
        }
    }

    private static void boundedSize(long value, String field) {
        if (value < 0 || value > JSON_SAFE_INTEGER_MAX) {
            throw new IllegalArgumentException(field + " is outside the supported JSON integer range");
        }
    }

    private static void validateFenceSet(
            OperationKind operationKind,
            boolean destinationMustRemainAbsent,
            List<Fence> fences) {
        for (int index = 1; index < fences.size(); index++) {
            Fence previous = fences.get(index - 1);
            Fence current = fences.get(index);
            int ordering = Comparator.comparing(Fence::fenceRole)
                    .thenComparing(Fence::canonicalPath, UTF8_UNSIGNED)
                    .thenComparing(fence -> fence.expectedFileRef() == null ? "" : fence.expectedFileRef(), UTF8_UNSIGNED)
                    .compare(previous, current);
            if (ordering >= 0) {
                throw new IllegalArgumentException("fences must use the closed deterministic role/path/FileId order");
            }
        }
        List<Fence> request = fences.stream()
                .filter(fence -> fence.fenceRole() == FenceRole.REQUEST_TARGET)
                .toList();
        List<Fence> destination = fences.stream()
                .filter(fence -> fence.fenceRole() == FenceRole.DESTINATION_TARGET)
                .toList();
        if (request.size() != 1) {
            throw new IllegalArgumentException("a Files mutation plan requires exactly one request-target fence");
        }
        boolean treeOperation = operationKind == OperationKind.DELETE
                || operationKind == OperationKind.COPY
                || operationKind == OperationKind.MOVE;
        if ((operationKind == OperationKind.COPY || operationKind == OperationKind.MOVE)
                != (destination.size() == 1)) {
            throw new IllegalArgumentException("COPY and MOVE require exactly one destination-target fence");
        }
        if (!treeOperation && fences.size() != 1) {
            throw new IllegalArgumentException("PUT and MKCOL permit only a request-target fence");
        }
        Fence requestRoot = request.getFirst();
        if (operationKind == OperationKind.MKCOL
                && requestRoot.expectedPresence() != ExpectedPresence.ABSENT) {
            throw new IllegalArgumentException("MKCOL requires an absent request-target fence");
        }
        if (treeOperation
                && (requestRoot.expectedPresence() != ExpectedPresence.PRESENT
                        || requestRoot.expectedSubtreeDigest() == null)) {
            throw new IllegalArgumentException("tree mutations require a present digested source-root fence");
        }
        if (!treeOperation && requestRoot.expectedSubtreeDigest() != null) {
            throw new IllegalArgumentException("non-tree request fences cannot carry a subtree digest");
        }
        fences.stream()
                .filter(fence -> fence.fenceRole() == FenceRole.SOURCE_MEMBER)
                .forEach(fence -> requireMemberFence(fence, requestRoot.canonicalPath(), "source"));
        if (!destination.isEmpty()) {
            Fence destinationRoot = destination.getFirst();
            if (destinationMustRemainAbsent
                    && destinationRoot.expectedPresence() != ExpectedPresence.ABSENT) {
                throw new IllegalArgumentException("Overwrite false requires an absent destination fence");
            }
            if (destinationRoot.expectedPresence() == ExpectedPresence.PRESENT
                    && destinationRoot.expectedSubtreeDigest() == null) {
                throw new IllegalArgumentException("a present destination root requires a membership digest");
            }
            fences.stream()
                    .filter(fence -> fence.fenceRole() == FenceRole.DESTINATION_MEMBER)
                    .forEach(fence -> requireMemberFence(fence, destinationRoot.canonicalPath(), "destination"));
        }
    }

    private static void requireMemberFence(Fence fence, String rootPath, String role) {
        if (fence.expectedPresence() != ExpectedPresence.PRESENT
                || fence.expectedSubtreeDigest() != null
                || !fence.canonicalPath().startsWith(rootPath + "/")) {
            throw new IllegalArgumentException(role + " member fence is not a present descendant");
        }
    }

    public static String subtreeMembershipDigest(List<Membership> members) {
        List<Membership> ordered = members == null ? List.of() : members.stream()
                .sorted(Comparator.comparing(Membership::canonicalPath, UTF8_UNSIGNED)
                        .thenComparing(Membership::fileRef, UTF8_UNSIGNED))
                .toList();
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < ordered.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            Membership member = ordered.get(index);
            json.append("{\"canonicalPath\":")
                    .append(jsonString(member.canonicalPath()))
                    .append(",\"fileRef\":")
                    .append(jsonString(member.fileRef()))
                    .append('}');
        }
        return sha256(json.append(']').toString());
    }

    private static String fenceSnapshotDigest(
            int ordinal,
            FenceRole role,
            String canonicalPath,
            ExpectedPresence presence,
            String fileRef,
            Kind objectKind,
            Lifecycle lifecycle,
            Long rowVersion,
            String strongEtag,
            String subtreeDigest) {
        String json = "{\"canonicalPath\":" + jsonString(canonicalPath)
                + ",\"expectedFileRef\":" + jsonValue(fileRef)
                + ",\"expectedLifecycleState\":" + jsonValue(lifecycle == null ? null : lifecycle.name())
                + ",\"expectedObjectKind\":" + jsonValue(objectKind == null ? null : objectKind.name())
                + ",\"expectedPresence\":" + jsonString(presence.name())
                + ",\"expectedRowVersion\":" + (rowVersion == null ? "null" : rowVersion)
                + ",\"expectedStrongEtag\":" + jsonValue(strongEtag)
                + ",\"expectedSubtreeDigest\":" + jsonValue(subtreeDigest)
                + ",\"fenceOrdinal\":" + ordinal
                + ",\"fenceRole\":" + jsonString(role.name())
                + ",\"fenceVersion\":" + jsonString(FENCE_VERSION)
                + '}';
        return sha256(json);
    }

    private static String sha256(String value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }

    private static List<String> parseEntityTagList(String value) {
        List<String> tags = new ArrayList<>();
        int cursor = 0;
        while (cursor < value.length()) {
            while (cursor < value.length() && isOws(value.charAt(cursor))) {
                cursor++;
            }
            int start = cursor;
            if (cursor + 2 <= value.length() && value.startsWith("W/", cursor)) {
                cursor += 2;
            }
            if (cursor >= value.length() || value.charAt(cursor) != '"') {
                throw new IllegalArgumentException("invalid entity-tag syntax");
            }
            cursor++;
            while (cursor < value.length() && value.charAt(cursor) != '"') {
                char character = value.charAt(cursor++);
                if (character == 0x7f
                        || character < 0x21
                        || Character.isSurrogate(character)) {
                    throw new IllegalArgumentException("invalid entity-tag character");
                }
            }
            if (cursor >= value.length()) {
                throw new IllegalArgumentException("unterminated entity tag");
            }
            cursor++;
            tags.add(value.substring(start, cursor));
            while (cursor < value.length() && isOws(value.charAt(cursor))) {
                cursor++;
            }
            if (cursor == value.length()) {
                break;
            }
            if (value.charAt(cursor++) != ',') {
                throw new IllegalArgumentException("invalid entity-tag list separator");
            }
            if (cursor == value.length()) {
                throw new IllegalArgumentException("entity-tag list must not end with a comma");
            }
        }
        return tags;
    }

    private static List<String> parseJsonStringArray(String value) {
        if (value.length() < 2 || value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']') {
            throw new IllegalArgumentException("invalid canonical entity-tag array");
        }
        List<String> values = new ArrayList<>();
        int cursor = 1;
        while (cursor < value.length() - 1) {
            if (value.charAt(cursor) != '"') {
                throw new IllegalArgumentException("invalid canonical entity-tag array");
            }
            cursor++;
            StringBuilder decoded = new StringBuilder();
            while (cursor < value.length() - 1 && value.charAt(cursor) != '"') {
                char character = value.charAt(cursor++);
                if (character == '\\') {
                    if (cursor >= value.length() - 1) {
                        throw new IllegalArgumentException("invalid JSON escape");
                    }
                    char escaped = value.charAt(cursor++);
                    if (escaped != '"' && escaped != '\\') {
                        throw new IllegalArgumentException("non-canonical JSON escape");
                    }
                    decoded.append(escaped);
                } else {
                    decoded.append(character);
                }
            }
            if (cursor >= value.length() - 1 || value.charAt(cursor++) != '"') {
                throw new IllegalArgumentException("invalid canonical entity-tag array");
            }
            values.add(decoded.toString());
            if (cursor < value.length() - 1 && value.charAt(cursor++) != ',') {
                throw new IllegalArgumentException("invalid canonical entity-tag array");
            }
        }
        return values;
    }

    private static String jsonArray(List<String> values) {
        return values.stream().map(FilesMutationPlan::jsonString)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String jsonValue(String value) {
        return value == null ? "null" : jsonString(value);
    }

    private static String jsonString(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    private static String weakOpaque(String etag) {
        return etag.startsWith("W/") ? etag.substring(2) : etag;
    }

    private static boolean isOws(char character) {
        return character == ' ' || character == '\t';
    }

    private static int compareUtf8(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(leftBytes.length, rightBytes.length);
        for (int index = 0; index < length; index++) {
            int comparison = Integer.compare(Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
    }

    private static Instant optionalCanonicalInstant(Instant value, String field) {
        return value == null ? null : canonicalInstant(value, field);
    }

    private static Instant canonicalInstant(Instant value, String field) {
        Instant required = Objects.requireNonNull(value, field + " must not be null");
        if (!required.equals(required.truncatedTo(ChronoUnit.MICROS))) {
            throw new IllegalArgumentException(field + " must use microsecond precision");
        }
        return required;
    }

    private static String digest(String value, String field) {
        String normalized = required(value, field);
        if (!normalized.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException(field + " must be a lowercase sha256 digest");
        }
        return normalized;
    }

    private static String optionalDigest(String value, String field) {
        return value == null ? null : digest(value, field);
    }

    private static String path(String value, String field) {
        String normalized = optional(value);
        if (normalized != null && !normalized.startsWith("/")) {
            throw new IllegalArgumentException(field + " must be an absolute canonical path");
        }
        return normalized;
    }

    private static void requirePath(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String required(String value, String field) {
        String normalized = optional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}

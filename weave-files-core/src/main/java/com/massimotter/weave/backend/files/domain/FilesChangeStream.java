package com.massimotter.weave.backend.files.domain;

import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Canonical commit-ordered Files change stream values. */
public final class FilesChangeStream {

    private FilesChangeStream() {
    }

    public enum ChangeKind {
        CREATED,
        CONTENT_UPDATED,
        COPIED,
        MOVED,
        TOMBSTONED
    }

    public record StreamHead(
            String organizationRef,
            String spaceRef,
            long latestRevision,
            long resetRequiredFloor,
            Instant updatedAt) {

        public StreamHead {
            organizationRef = required(organizationRef, "organizationRef");
            spaceRef = required(spaceRef, "spaceRef");
            if (latestRevision < 0
                    || resetRequiredFloor < 0
                    || resetRequiredFloor > latestRevision) {
                throw new IllegalArgumentException("Files stream revisions are invalid");
            }
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public record FileChange(
            String organizationRef,
            String spaceRef,
            long revision,
            String operationRef,
            ChangeKind changeKind,
            FileId fileId,
            FileId sourceFileId,
            FilePath sourcePath,
            FilePath targetPath,
            Kind objectKind,
            Lifecycle lifecycle,
            long providerBindingRevision,
            long resultingSize,
            String resultingMediaType,
            String resultingContentDigest,
            FileVersion resultingFileVersion,
            String resultingEtag,
            Instant resultingModifiedAt,
            boolean resultingHidden,
            Instant resultingObservedAt,
            long rangeStart,
            long rangeEnd,
            Instant committedAt) {

        public FileChange {
            organizationRef = required(organizationRef, "organizationRef");
            spaceRef = required(spaceRef, "spaceRef");
            operationRef = required(operationRef, "operationRef");
            changeKind = Objects.requireNonNull(changeKind, "changeKind must not be null");
            fileId = Objects.requireNonNull(fileId, "fileId must not be null");
            objectKind = Objects.requireNonNull(objectKind, "objectKind must not be null");
            lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
            if (revision < 1
                    || rangeStart < 1
                    || rangeEnd < rangeStart
                    || revision < rangeStart
                    || revision > rangeEnd) {
                throw new IllegalArgumentException("Files change revision range is invalid");
            }
            if (providerBindingRevision < 1) {
                throw new IllegalArgumentException("providerBindingRevision must be positive");
            }
            if (resultingSize < 0) {
                throw new IllegalArgumentException("resultingSize must not be negative");
            }
            resultingMediaType = optional(resultingMediaType);
            resultingContentDigest = optionalDigest(resultingContentDigest);
            resultingFileVersion = resultingFileVersion == null
                    ? FileVersion.unknown()
                    : resultingFileVersion;
            resultingEtag = optional(resultingEtag);
            resultingModifiedAt = Objects.requireNonNull(
                    resultingModifiedAt,
                    "resultingModifiedAt must not be null");
            resultingObservedAt = Objects.requireNonNull(
                    resultingObservedAt,
                    "resultingObservedAt must not be null");
            committedAt = Objects.requireNonNull(committedAt, "committedAt must not be null");
            if (objectKind == Kind.COLLECTION) {
                if (resultingSize != 0
                        || resultingMediaType != null
                        || resultingContentDigest != null
                        || resultingFileVersion.known()
                        || resultingEtag != null) {
                    throw new IllegalArgumentException("collection changes cannot carry content fields");
                }
            } else if (resultingContentDigest == null
                    || !resultingFileVersion.known()
                    || resultingEtag == null) {
                throw new IllegalArgumentException("file changes require a complete immutable result snapshot");
            }
            if (changeKind == ChangeKind.TOMBSTONED) {
                if (sourcePath == null || targetPath != null || lifecycle != Lifecycle.TOMBSTONED) {
                    throw new IllegalArgumentException("tombstone change paths or lifecycle are invalid");
                }
            } else if (lifecycle != Lifecycle.ACTIVE || targetPath == null) {
                throw new IllegalArgumentException("active change requires a resulting target path");
            }
            if ((changeKind == ChangeKind.COPIED || changeKind == ChangeKind.MOVED)
                    && (sourceFileId == null || sourcePath == null)) {
                throw new IllegalArgumentException("copy and move changes require source identity and path");
            }
        }
    }

    public record ChangePage(
            StreamHead head,
            long capturedHighWater,
            List<FileChange> changes,
            String continuation) {

        public ChangePage {
            head = Objects.requireNonNull(head, "head must not be null");
            if (capturedHighWater < head.resetRequiredFloor()
                    || capturedHighWater > head.latestRevision()) {
                throw new IllegalArgumentException("captured high-water is outside the stream head");
            }
            changes = changes == null ? List.of() : List.copyOf(changes);
            continuation = optional(continuation);
        }
    }

    public static final class ResetRequiredException extends RuntimeException {
        private final long resetRequiredFloor;
        private final long latestRevision;

        public ResetRequiredException(long resetRequiredFloor, long latestRevision) {
            super("the Files change cursor is below the retained history floor");
            this.resetRequiredFloor = resetRequiredFloor;
            this.latestRevision = latestRevision;
        }

        public long resetRequiredFloor() {
            return resetRequiredFloor;
        }

        public long latestRevision() {
            return latestRevision;
        }
    }

    private static String optionalDigest(String value) {
        String normalized = optional(value);
        if (normalized != null && !normalized.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("content digest must be a lowercase sha256 digest");
        }
        return normalized;
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

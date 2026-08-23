package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.files.domain.FilesChangeStream.FileChange;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.StreamHead;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import java.util.List;
import java.util.Objects;

/** Atomic read boundary for one canonical RFC 6578 Files synchronization capture. */
public interface NativeFilesWebDavSyncRepository {

    Capture captureInitial(
            FilesScope scope,
            FilePath collectionPath,
            DescendantDepth depth,
            int maximumJournalRows);

    Capture captureDelta(
            FilesScope scope,
            FilePath collectionPath,
            DescendantDepth depth,
            FileId expectedCollectionId,
            String expectedStreamRef,
            long afterRevision,
            int maximumJournalRows);

    enum DescendantDepth {
        ONE,
        INFINITE
    }

    record ScopeState(
            FilesScope scope,
            FileId collectionId,
            FilePath collectionPath,
            String streamRef,
            StreamHead head) {

        public ScopeState {
            scope = Objects.requireNonNull(scope, "scope");
            collectionId = Objects.requireNonNull(collectionId, "collectionId");
            collectionPath = Objects.requireNonNull(collectionPath, "collectionPath");
            streamRef = required(streamRef, "streamRef");
            head = Objects.requireNonNull(head, "head");
            if (!head.organizationRef().equals(scope.organizationRef())
                    || !head.spaceRef().equals(scope.spaceRef())) {
                throw new IllegalArgumentException("stream head does not match its Files scope");
            }
        }
    }

    record Capture(
            ScopeState state,
            long capturedHighWater,
            List<FileChange> changes,
            boolean sourceTruncated) {

        public Capture {
            state = Objects.requireNonNull(state, "state");
            if (capturedHighWater < state.head().resetRequiredFloor()
                    || capturedHighWater > state.head().latestRevision()) {
                throw new IllegalArgumentException("captured high-water is outside the Files stream head");
            }
            changes = List.copyOf(changes == null ? List.of() : changes);
        }
    }

    final class SyncCollectionNotFoundException extends RuntimeException {
        public SyncCollectionNotFoundException() {
            super("The Files synchronization collection is unavailable.");
        }
    }

    final class InvalidSyncStateException extends RuntimeException {
        public InvalidSyncStateException() {
            super("The Files synchronization state is invalid.");
        }
    }

    final class SyncReadCapacityException extends RuntimeException {
        public SyncReadCapacityException() {
            super("The Files synchronization journal exceeds the bounded read capacity.");
        }
    }

    final class CorruptSyncStateException extends RuntimeException {
        public CorruptSyncStateException(String message) {
            super(message);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must not be blank or padded");
        }
        return value;
    }
}

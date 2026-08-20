package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.application.FilesChangeReadException.Code.CORRUPT_STREAM;
import static com.massimotter.weave.backend.files.application.FilesChangeReadException.Code.INVALID_AFTER_REVISION;
import static com.massimotter.weave.backend.files.application.FilesChangeReadException.Code.INVALID_LIMIT;
import static com.massimotter.weave.backend.files.application.FilesChangeReadException.Code.STREAM_NOT_PROVISIONED;

import com.massimotter.weave.backend.files.application.FilesChangeCursorCodec.CursorState;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangePage;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.FileChange;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.ResetRequiredException;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.StreamHead;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Captured-high-water reader for the native Files commit journal. */
@Service
public final class NativeFilesChangeReader {

    public static final int MAXIMUM_LIMIT = 100;

    private final NativeFilesChangeRepository repository;
    private final FilesChangeCursorCodec cursors;

    public NativeFilesChangeReader(
            NativeFilesChangeRepository repository,
            FilesChangeCursorCodec cursors) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
    }

    public ChangePage firstPage(
            String organizationRef,
            String spaceRef,
            long afterRevision,
            int limit) {
        Scope scope = new Scope(organizationRef, spaceRef);
        requireLimit(limit);
        if (afterRevision < 0) {
            throw new FilesChangeReadException(
                    INVALID_AFTER_REVISION,
                    "The Files afterRevision is invalid.");
        }
        StreamHead head = requireHead(scope);
        if (afterRevision > head.latestRevision()) {
            throw new FilesChangeReadException(
                    INVALID_AFTER_REVISION,
                    "The Files afterRevision is beyond the current stream head.");
        }
        requireRetained(afterRevision, head);
        return page(scope, head, head.latestRevision(), afterRevision, limit);
    }

    public ChangePage continuePage(
            String organizationRef,
            String spaceRef,
            String continuation,
            int limit) {
        Scope scope = new Scope(organizationRef, spaceRef);
        requireLimit(limit);
        CursorState cursor = cursors.decode(continuation);
        if (!scope.organizationRef().equals(cursor.organizationRef())
                || !scope.spaceRef().equals(cursor.spaceRef())
                || limit != cursor.limit()) {
            throw FilesChangeReadException.invalidContinuation();
        }
        StreamHead head = requireHead(scope);
        if (cursor.capturedHighWater() > head.latestRevision()) {
            throw FilesChangeReadException.invalidContinuation();
        }
        requireRetained(cursor.lastReturnedRevision(), head);
        return page(
                scope,
                head,
                cursor.capturedHighWater(),
                cursor.lastReturnedRevision(),
                limit);
    }

    private ChangePage page(
            Scope scope,
            StreamHead head,
            long capturedHighWater,
            long afterRevision,
            int limit) {
        if (afterRevision == capturedHighWater) {
            return new ChangePage(head, capturedHighWater, List.of(), null);
        }
        List<FileChange> changes = repository.findChanges(
                scope.organizationRef(),
                scope.spaceRef(),
                afterRevision,
                capturedHighWater,
                limit);
        List<FileChange> verified = changes == null ? List.of() : List.copyOf(changes);
        if (verified.isEmpty() || verified.size() > limit) {
            throw corrupt();
        }
        long expected = afterRevision;
        for (FileChange change : verified) {
            if (change == null
                    || !scope.organizationRef().equals(change.organizationRef())
                    || !scope.spaceRef().equals(change.spaceRef())
                    || expected == Long.MAX_VALUE
                    || change.revision() != expected + 1
                    || change.revision() > capturedHighWater) {
                throw corrupt();
            }
            expected = change.revision();
        }
        if (verified.size() < limit && expected < capturedHighWater) {
            throw corrupt();
        }
        String continuation = expected < capturedHighWater
                ? cursors.encode(new CursorState(
                        scope.organizationRef(),
                        scope.spaceRef(),
                        capturedHighWater,
                        expected,
                        limit))
                : null;
        return new ChangePage(head, capturedHighWater, verified, continuation);
    }

    private StreamHead requireHead(Scope scope) {
        StreamHead head = repository.findHead(scope.organizationRef(), scope.spaceRef())
                .orElseThrow(() -> new FilesChangeReadException(
                        STREAM_NOT_PROVISIONED,
                        "The Files change stream is not provisioned."));
        if (!scope.organizationRef().equals(head.organizationRef())
                || !scope.spaceRef().equals(head.spaceRef())) {
            throw corrupt();
        }
        return head;
    }

    private static void requireRetained(long effectiveAfterRevision, StreamHead head) {
        if (effectiveAfterRevision < head.resetRequiredFloor()) {
            throw new ResetRequiredException(
                    head.resetRequiredFloor(),
                    head.latestRevision());
        }
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new FilesChangeReadException(
                    INVALID_LIMIT,
                    "The Files change page limit must be between 1 and 100.");
        }
    }

    private static FilesChangeReadException corrupt() {
        return new FilesChangeReadException(
                CORRUPT_STREAM,
                "The Files change stream failed its integrity check.");
    }

    private record Scope(String organizationRef, String spaceRef) {
        private Scope {
            organizationRef = required(organizationRef, "organizationRef");
            spaceRef = required(spaceRef, "spaceRef");
        }

        private static String required(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value.trim();
        }
    }
}

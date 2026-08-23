package com.massimotter.weave.backend.service.files;

import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.TOMBSTONED;
import static com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind.CONTENT_UPDATED;
import static com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind.CREATED;
import static com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind.MOVED;
import static com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind.TOMBSTONED;
import static com.massimotter.weave.backend.files.domain.FilesDomain.Kind.COLLECTION;
import static com.massimotter.weave.backend.files.domain.FilesDomain.Kind.FILE;
import static com.massimotter.weave.backend.service.files.FilesWebDavSyncProjector.Disposition.CHANGED;
import static com.massimotter.weave.backend.service.files.FilesWebDavSyncProjector.Disposition.REMOVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.FileChange;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.service.files.FilesWebDavSyncProjector.CorruptSyncSourceException;
import com.massimotter.weave.backend.service.files.FilesWebDavSyncProjector.LimitCannotPreserveAtomicityException;
import com.massimotter.weave.backend.service.files.FilesWebDavSyncProjector.SyncPage;
import com.massimotter.weave.backend.service.files.WebDavSyncRequest.SyncLevel;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FilesWebDavSyncProjectorTest {

    private static final String DIGEST = "sha256:" + "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-23T18:00:00Z");

    @Test
    void moveWithinScopeProducesRemovedOldHrefAndChangedNewHref() {
        SyncPage page = FilesWebDavSyncProjector.delta(
                "/Team",
                SyncLevel.ONE,
                0,
                1,
                List.of(change(1, 1, 1, "move", MOVED, "file-a",
                        "/Team/old.txt", "/Team/new.txt", FILE, ACTIVE)),
                10,
                false);

        assertThat(page.representedRevision()).isEqualTo(1);
        assertThat(page.truncated()).isFalse();
        assertThat(page.members())
                .extracting(member -> member.hrefPath() + ":" + member.disposition())
                .containsExactly("/Team/new.txt:CHANGED", "/Team/old.txt:REMOVED");
    }

    @Test
    void movesAcrossScopeExposeOnlyTheApplicableHrefEffect() {
        SyncPage page = FilesWebDavSyncProjector.delta(
                "/Team",
                SyncLevel.INFINITE,
                0,
                2,
                List.of(
                        change(1, 1, 1, "move-in", MOVED, "file-a",
                                "/Elsewhere/a.txt", "/Team/a.txt", FILE, ACTIVE),
                        change(2, 2, 2, "move-out", MOVED, "file-b",
                                "/Team/b.txt", "/Elsewhere/b.txt", FILE, ACTIVE)),
                10,
                false);

        assertThat(page.members())
                .extracting(member -> member.hrefPath() + ":" + member.disposition())
                .containsExactly("/Team/a.txt:CHANGED", "/Team/b.txt:REMOVED");
    }

    @Test
    void repeatedHrefEffectsCoalesceToTheLastVisibleState() {
        SyncPage updated = FilesWebDavSyncProjector.delta(
                "/Team",
                SyncLevel.ONE,
                0,
                2,
                List.of(
                        change(1, 1, 1, "create", CREATED, "file-a",
                                null, "/Team/a.txt", FILE, ACTIVE),
                        change(2, 2, 2, "update", CONTENT_UPDATED, "file-a",
                                null, "/Team/a.txt", FILE, ACTIVE)),
                10,
                false);
        assertThat(updated.members()).singleElement().satisfies(member -> {
            assertThat(member.hrefPath()).isEqualTo("/Team/a.txt");
            assertThat(member.disposition()).isEqualTo(CHANGED);
            assertThat(member.revision()).isEqualTo(2);
        });

        SyncPage removed = FilesWebDavSyncProjector.delta(
                "/Team",
                SyncLevel.ONE,
                0,
                2,
                List.of(
                        change(1, 1, 1, "create", CREATED, "file-a",
                                null, "/Team/a.txt", FILE, ACTIVE),
                        change(2, 2, 2, "delete", TOMBSTONED, "file-a",
                                "/Team/a.txt", null, FILE, TOMBSTONED)),
                10,
                false);
        assertThat(removed.members()).singleElement().satisfies(member -> {
            assertThat(member.hrefPath()).isEqualTo("/Team/a.txt");
            assertThat(member.disposition()).isEqualTo(REMOVED);
            assertThat(member.revision()).isEqualTo(2);
        });
    }

    @Test
    void infiniteSyncSuppressesRedundantDescendantRemovals() {
        SyncPage page = FilesWebDavSyncProjector.delta(
                "/Team",
                SyncLevel.INFINITE,
                0,
                2,
                List.of(
                        change(1, 1, 2, "delete-tree", TOMBSTONED, "folder-a",
                                "/Team/Folder", null, COLLECTION, TOMBSTONED),
                        change(2, 1, 2, "delete-tree", TOMBSTONED, "file-a",
                                "/Team/Folder/a.txt", null, FILE, TOMBSTONED)),
                10,
                false);

        assertThat(page.members()).singleElement().satisfies(member -> {
            assertThat(member.hrefPath()).isEqualTo("/Team/Folder");
            assertThat(member.disposition()).isEqualTo(REMOVED);
        });
    }

    @Test
    void unrelatedRangesAdvanceStateWithoutInventingResponses() {
        SyncPage page = FilesWebDavSyncProjector.delta(
                "/Team",
                SyncLevel.INFINITE,
                0,
                1,
                List.of(change(1, 1, 1, "outside", CREATED, "file-a",
                        null, "/Elsewhere/a.txt", FILE, ACTIVE)),
                0,
                false);

        assertThat(page.representedRevision()).isEqualTo(1);
        assertThat(page.members()).isEmpty();
        assertThat(page.truncated()).isFalse();
    }

    @Test
    void truncationStopsAtTheLastRepresentableCompleteRange() {
        SyncPage page = FilesWebDavSyncProjector.delta(
                "/Team",
                SyncLevel.ONE,
                0,
                2,
                List.of(
                        change(1, 1, 1, "create-a", CREATED, "file-a",
                                null, "/Team/a.txt", FILE, ACTIVE),
                        change(2, 2, 2, "create-b", CREATED, "file-b",
                                null, "/Team/b.txt", FILE, ACTIVE)),
                1,
                false);

        assertThat(page.representedRevision()).isEqualTo(1);
        assertThat(page.truncated()).isTrue();
        assertThat(page.members()).singleElement().satisfies(member ->
                assertThat(member.hrefPath()).isEqualTo("/Team/a.txt"));
    }

    @Test
    void clientLimitCannotSplitTheTwoHrefEffectsOfOneMove() {
        assertThatThrownBy(() -> FilesWebDavSyncProjector.delta(
                        "/Team",
                        SyncLevel.ONE,
                        0,
                        1,
                        List.of(change(1, 1, 1, "move", MOVED, "file-a",
                                "/Team/old.txt", "/Team/new.txt", FILE, ACTIVE)),
                        1,
                        false))
                .isInstanceOf(LimitCannotPreserveAtomicityException.class);
    }

    @Test
    void initialSyncUsesRangeBoundariesAndRejectsDuplicateCanonicalIds() {
        FileChange first = change(1, 1, 2, "create-pair", CREATED, "file-a",
                null, "/Team/a.txt", FILE, ACTIVE);
        FileChange second = change(2, 1, 2, "create-pair", CREATED, "file-b",
                null, "/Team/b.txt", FILE, ACTIVE);

        assertThatThrownBy(() -> FilesWebDavSyncProjector.initial(
                        "/Team", SyncLevel.ONE, 2, List.of(first, second), 1, false))
                .isInstanceOf(LimitCannotPreserveAtomicityException.class);

        SyncPage partial = FilesWebDavSyncProjector.initial(
                "/Team", SyncLevel.ONE, 9, List.of(first, second), 2, true);
        assertThat(partial.representedRevision()).isEqualTo(2);
        assertThat(partial.truncated()).isTrue();
        assertThat(partial.members()).allMatch(member -> member.disposition() == CHANGED);

        FileChange duplicate = change(3, 3, 3, "duplicate", CONTENT_UPDATED, "file-a",
                null, "/Team/duplicate.txt", FILE, ACTIVE);
        assertThatThrownBy(() -> FilesWebDavSyncProjector.initial(
                        "/Team", SyncLevel.ONE, 3, List.of(first, duplicate), 10, false))
                .isInstanceOf(CorruptSyncSourceException.class);
    }

    @Test
    void corruptGapsAndPartialMutationRangesFailClosed() {
        FileChange firstOfRange = change(1, 1, 2, "create-pair", CREATED, "file-a",
                null, "/Team/a.txt", FILE, ACTIVE);
        assertThatThrownBy(() -> FilesWebDavSyncProjector.delta(
                        "/Team", SyncLevel.ONE, 0, 2, List.of(firstOfRange), 10, true))
                .isInstanceOf(CorruptSyncSourceException.class);

        FileChange gap = change(2, 2, 2, "create-b", CREATED, "file-b",
                null, "/Team/b.txt", FILE, ACTIVE);
        assertThatThrownBy(() -> FilesWebDavSyncProjector.delta(
                        "/Team", SyncLevel.ONE, 0, 2, List.of(gap), 10, false))
                .isInstanceOf(CorruptSyncSourceException.class);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static FileChange change(
            long revision,
            long rangeStart,
            long rangeEnd,
            String operationRef,
            ChangeKind changeKind,
            String fileId,
            String sourcePath,
            String targetPath,
            Kind kind,
            Lifecycle lifecycle) {
        boolean collection = kind == COLLECTION;
        return new FileChange(
                "org-a",
                "space-a",
                revision,
                operationRef,
                changeKind,
                new FileId(fileId),
                changeKind == MOVED ? new FileId(fileId) : null,
                sourcePath == null ? null : new FilePath(sourcePath),
                targetPath == null ? null : new FilePath(targetPath),
                kind,
                lifecycle,
                1,
                collection ? 0 : revision,
                collection ? null : "text/plain",
                collection ? null : DIGEST,
                collection ? FileVersion.unknown() : new FileVersion("v" + revision),
                collection ? null : "\"etag-" + revision + "\"",
                NOW.plusSeconds(revision),
                false,
                NOW.plusSeconds(revision),
                rangeStart,
                rangeEnd,
                NOW.plusSeconds(revision));
    }
}

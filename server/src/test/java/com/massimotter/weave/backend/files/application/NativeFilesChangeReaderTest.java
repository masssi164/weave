package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind.CREATED;
import static com.massimotter.weave.backend.files.domain.FilesDomain.Kind.COLLECTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangePage;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.FileChange;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.ResetRequiredException;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.StreamHead;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NativeFilesChangeReaderTest {

    private static final String ORGANIZATION = "org-a";
    private static final String SPACE = "space-a";
    private static final Instant NOW = Instant.parse("2026-08-20T10:15:30Z");

    private InMemoryRepository repository;
    private NativeFilesChangeReader reader;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        FilesChangeCursorCodec codec = new FilesChangeCursorCodec(
                "0123456789abcdef0123456789abcdef0123456789abcdef"
                        .getBytes(StandardCharsets.US_ASCII));
        reader = new NativeFilesChangeReader(repository, codec);
    }

    @Test
    void capturesTheFirstHeadAndExcludesLaterCommitsFromEveryContinuation() {
        repository.publishThrough(5);

        ChangePage first = reader.firstPage(ORGANIZATION, SPACE, 0, 2);
        repository.publishThrough(7);
        ChangePage second = reader.continuePage(ORGANIZATION, SPACE, first.continuation(), 2);
        ChangePage third = reader.continuePage(ORGANIZATION, SPACE, second.continuation(), 2);

        assertThat(revisions(first)).containsExactly(1L, 2L);
        assertThat(revisions(second)).containsExactly(3L, 4L);
        assertThat(revisions(third)).containsExactly(5L);
        assertThat(first.capturedHighWater()).isEqualTo(5);
        assertThat(second.capturedHighWater()).isEqualTo(5);
        assertThat(third.capturedHighWater()).isEqualTo(5);
        assertThat(second.head().latestRevision()).isEqualTo(7);
        assertThat(third.continuation()).isNull();
    }

    @Test
    void acceptsEqualityAsACompletedEmptyReadButRejectsAFutureFirstPageRevision() {
        repository.publishThrough(3);

        ChangePage completed = reader.firstPage(ORGANIZATION, SPACE, 3, 10);

        assertThat(completed.changes()).isEmpty();
        assertThat(completed.continuation()).isNull();
        assertThatThrownBy(() -> reader.firstPage(ORGANIZATION, SPACE, 4, 10))
                .isInstanceOfSatisfying(FilesChangeReadException.class, failure ->
                        assertThat(failure.code()).isEqualTo(
                                FilesChangeReadException.Code.INVALID_AFTER_REVISION));
        assertThat(repository.pageReads).isZero();
    }

    @Test
    void returnsTypedResetRequiredWithoutReadingAPartialFirstOrContinuationPage() {
        repository.publishThrough(5);
        ChangePage first = reader.firstPage(ORGANIZATION, SPACE, 0, 2);
        repository.floor = 3;
        int readsBeforeReset = repository.pageReads;

        assertThatThrownBy(() -> reader.firstPage(ORGANIZATION, SPACE, 2, 2))
                .isInstanceOfSatisfying(ResetRequiredException.class, failure -> {
                    assertThat(failure.resetRequiredFloor()).isEqualTo(3);
                    assertThat(failure.latestRevision()).isEqualTo(5);
                });
        assertThatThrownBy(() -> reader.continuePage(
                        ORGANIZATION, SPACE, first.continuation(), 2))
                .isInstanceOfSatisfying(ResetRequiredException.class, failure -> {
                    assertThat(failure.resetRequiredFloor()).isEqualTo(3);
                    assertThat(failure.latestRevision()).isEqualTo(5);
                });
        assertThat(repository.pageReads).isEqualTo(readsBeforeReset);
    }

    @Test
    void failsClosedForWrongScopeChangedLimitBadIntegrityAndFutureCapturedHead() {
        repository.publishThrough(5);
        ChangePage first = reader.firstPage(ORGANIZATION, SPACE, 0, 2);
        String token = first.continuation();

        assertInvalidContinuation(() -> reader.continuePage("org-b", SPACE, token, 2));
        assertInvalidContinuation(() -> reader.continuePage(ORGANIZATION, "space-b", token, 2));
        assertInvalidContinuation(() -> reader.continuePage(ORGANIZATION, SPACE, token, 3));
        assertInvalidContinuation(() -> reader.continuePage(
                ORGANIZATION,
                SPACE,
                token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A"),
                2));

        repository.latest = 4;
        assertInvalidContinuation(() -> reader.continuePage(ORGANIZATION, SPACE, token, 2));
    }

    @Test
    void rejectsUnboundedLimitsAndAnyGapInsteadOfReturningPartialHistory() {
        repository.publishThrough(4);

        assertThatThrownBy(() -> reader.firstPage(ORGANIZATION, SPACE, 0, 0))
                .isInstanceOfSatisfying(FilesChangeReadException.class, failure ->
                        assertThat(failure.code()).isEqualTo(FilesChangeReadException.Code.INVALID_LIMIT));
        assertThatThrownBy(() -> reader.firstPage(
                        ORGANIZATION, SPACE, 0, NativeFilesChangeReader.MAXIMUM_LIMIT + 1))
                .isInstanceOfSatisfying(FilesChangeReadException.class, failure ->
                        assertThat(failure.code()).isEqualTo(FilesChangeReadException.Code.INVALID_LIMIT));

        repository.changes.removeIf(change -> change.revision() == 2);
        assertThatThrownBy(() -> reader.firstPage(ORGANIZATION, SPACE, 0, 4))
                .isInstanceOfSatisfying(FilesChangeReadException.class, failure ->
                        assertThat(failure.code()).isEqualTo(FilesChangeReadException.Code.CORRUPT_STREAM));
    }

    private static List<Long> revisions(ChangePage page) {
        return page.changes().stream().map(FileChange::revision).toList();
    }

    private static void assertInvalidContinuation(ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(FilesChangeReadException.class, failure ->
                        assertThat(failure.code()).isEqualTo(
                                FilesChangeReadException.Code.INVALID_CONTINUATION));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }

    private static final class InMemoryRepository implements NativeFilesChangeRepository {
        private final List<FileChange> changes = new ArrayList<>();
        private long latest;
        private long floor;
        private int pageReads;

        void publishThrough(long revision) {
            for (long next = latest + 1; next <= revision; next++) {
                changes.add(change(next));
            }
            latest = revision;
        }

        @Override
        public Optional<StreamHead> findHead(String organizationRef, String spaceRef) {
            if (!ORGANIZATION.equals(organizationRef) || !SPACE.equals(spaceRef)) {
                return Optional.of(new StreamHead(
                        organizationRef, spaceRef, latest, floor, NOW));
            }
            return Optional.of(new StreamHead(ORGANIZATION, SPACE, latest, floor, NOW));
        }

        @Override
        public List<FileChange> findChanges(
                String organizationRef,
                String spaceRef,
                long afterRevision,
                long capturedHighWater,
                int limit) {
            pageReads++;
            return changes.stream()
                    .filter(change -> change.revision() > afterRevision)
                    .filter(change -> change.revision() <= capturedHighWater)
                    .sorted(Comparator.comparingLong(FileChange::revision))
                    .limit(limit)
                    .toList();
        }

        private static FileChange change(long revision) {
            return new FileChange(
                    ORGANIZATION,
                    SPACE,
                    revision,
                    "operation-" + revision,
                    CREATED,
                    new FileId("file-" + revision),
                    null,
                    null,
                    new FilePath("/collection-" + revision),
                    COLLECTION,
                    ACTIVE,
                    1,
                    0,
                    null,
                    null,
                    FileVersion.unknown(),
                    null,
                    NOW,
                    false,
                    NOW,
                    revision,
                    revision,
                    NOW);
        }
    }
}

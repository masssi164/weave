package com.massimotter.weave.backend.service.files;

import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind.CONTENT_UPDATED;
import static com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind.COPIED;
import static com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind.CREATED;
import static com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind.MOVED;
import static com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind.TOMBSTONED;
import static com.massimotter.weave.backend.files.domain.FilesDomain.Kind.COLLECTION;

import com.massimotter.weave.backend.files.domain.FilesChangeStream.FileChange;
import com.massimotter.weave.backend.service.files.WebDavSyncRequest.SyncLevel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure RFC 6578 projection over bounded, complete canonical Files journal ranges. */
public final class FilesWebDavSyncProjector {

    private FilesWebDavSyncProjector() {}

    /**
     * Projects a bounded prefix of latest active snapshots captured at one stream high-water.
     *
     * <p>The repository must supply exactly the latest journal snapshot per FileId in the target
     * collection and requested level, ordered by revision, path, and FileId. If it truncates the
     * source, it must do so only after all candidate snapshots from the represented mutation range.
     */
    public static SyncPage initial(
            String collectionPath,
            SyncLevel syncLevel,
            long capturedHighWater,
            List<FileChange> latestSnapshots,
            int limit,
            boolean sourceTruncated) {
        String collection = requirePath(collectionPath);
        requireWindow(0, capturedHighWater, limit);
        List<FileChange> candidates = ordered(latestSnapshots);
        Set<String> fileIds = new HashSet<>();
        for (FileChange candidate : candidates) {
            if (candidate.revision() > capturedHighWater
                    || candidate.lifecycle() != ACTIVE
                    || candidate.targetPath() == null
                    || !member(collection, candidate.targetPath().value(), syncLevel)
                    || !fileIds.add(candidate.fileId().value())) {
                throw corrupt("initial Files sync source is not a unique active member snapshot");
            }
        }
        return boundedPage(
                0,
                capturedHighWater,
                initialRanges(candidates),
                syncLevel,
                limit,
                sourceTruncated);
    }

    /** Projects a complete ascending journal interval after one valid synchronization token. */
    public static SyncPage delta(
            String collectionPath,
            SyncLevel syncLevel,
            long afterRevision,
            long capturedHighWater,
            List<FileChange> changes,
            int limit,
            boolean sourceTruncated) {
        String collection = requirePath(collectionPath);
        requireWindow(afterRevision, capturedHighWater, limit);
        List<FileChange> ordered = ordered(changes);

        long previous = afterRevision;
        for (FileChange change : ordered) {
            if (change.revision() != previous + 1 || change.revision() > capturedHighWater) {
                throw corrupt("delta Files sync source is not gap-free and strictly ordered");
            }
            previous = change.revision();
        }
        if (ordered.isEmpty()) {
            if (sourceTruncated || afterRevision != capturedHighWater) {
                throw corrupt("delta Files sync source omits committed revisions");
            }
            return new SyncPage(capturedHighWater, List.of(), false);
        }
        if (ordered.getFirst().rangeStart() != ordered.getFirst().revision()) {
            throw corrupt("delta Files sync starts inside a mutation range");
        }
        if (!sourceTruncated && ordered.getLast().revision() != capturedHighWater) {
            throw corrupt("complete delta Files sync source does not reach its high-water");
        }

        return boundedPage(
                afterRevision,
                capturedHighWater,
                deltaRanges(ordered, collection, syncLevel),
                syncLevel,
                limit,
                sourceTruncated);
    }

    private static List<RangeEffects> initialRanges(List<FileChange> snapshots) {
        List<RangeEffects> ranges = new ArrayList<>();
        List<RawEffect> effects = new ArrayList<>();
        long rangeStart = -1;
        long rangeEnd = -1;
        String operationRef = null;

        for (FileChange snapshot : snapshots) {
            boolean sameRange = rangeStart == snapshot.rangeStart()
                    && rangeEnd == snapshot.rangeEnd()
                    && Objects.equals(operationRef, snapshot.operationRef());
            if (!effects.isEmpty() && !sameRange) {
                ranges.add(new RangeEffects(rangeStart, rangeEnd, operationRef, effects));
                effects = new ArrayList<>();
            }
            if (effects.isEmpty()) {
                rangeStart = snapshot.rangeStart();
                rangeEnd = snapshot.rangeEnd();
                operationRef = snapshot.operationRef();
            }
            effects.add(new RawEffect(
                    snapshot,
                    snapshot.targetPath().value(),
                    Disposition.CHANGED));
        }
        if (!effects.isEmpty()) {
            ranges.add(new RangeEffects(rangeStart, rangeEnd, operationRef, effects));
        }
        return List.copyOf(ranges);
    }

    private static List<RangeEffects> deltaRanges(
            List<FileChange> changes,
            String collection,
            SyncLevel level) {
        List<RangeEffects> ranges = new ArrayList<>();
        List<FileChange> current = new ArrayList<>();
        long rangeStart = -1;
        long rangeEnd = -1;
        String operationRef = null;

        for (FileChange change : changes) {
            boolean sameRange = rangeStart == change.rangeStart()
                    && rangeEnd == change.rangeEnd()
                    && Objects.equals(operationRef, change.operationRef());
            if (!current.isEmpty() && !sameRange) {
                ranges.add(completeDeltaRange(
                        rangeStart,
                        rangeEnd,
                        operationRef,
                        current,
                        collection,
                        level));
                current = new ArrayList<>();
            }
            if (current.isEmpty()) {
                rangeStart = change.rangeStart();
                rangeEnd = change.rangeEnd();
                operationRef = change.operationRef();
            }
            current.add(change);
        }
        if (!current.isEmpty()) {
            ranges.add(completeDeltaRange(
                    rangeStart,
                    rangeEnd,
                    operationRef,
                    current,
                    collection,
                    level));
        }
        return List.copyOf(ranges);
    }

    private static RangeEffects completeDeltaRange(
            long rangeStart,
            long rangeEnd,
            String operationRef,
            List<FileChange> changes,
            String collection,
            SyncLevel level) {
        if (changes.getFirst().revision() != rangeStart
                || changes.getLast().revision() != rangeEnd
                || changes.size() != rangeEnd - rangeStart + 1) {
            throw corrupt("Files sync source contains an incomplete mutation range");
        }
        List<RawEffect> effects = new ArrayList<>();
        for (FileChange change : changes) {
            if (change.rangeStart() != rangeStart
                    || change.rangeEnd() != rangeEnd
                    || !Objects.equals(change.operationRef(), operationRef)) {
                throw corrupt("Files sync source mixes mutation range identities");
            }
            projectDelta(collection, level, change, effects);
        }
        return new RangeEffects(rangeStart, rangeEnd, operationRef, effects);
    }

    private static void projectDelta(
            String collection,
            SyncLevel level,
            FileChange change,
            List<RawEffect> effects) {
        boolean sourceMember = change.sourcePath() != null
                && member(collection, change.sourcePath().value(), level);
        boolean targetMember = change.targetPath() != null
                && member(collection, change.targetPath().value(), level);

        if (change.changeKind() == CREATED
                || change.changeKind() == CONTENT_UPDATED
                || change.changeKind() == COPIED) {
            if (targetMember) {
                effects.add(changed(change, change.targetPath().value()));
            }
            return;
        }
        if (change.changeKind() == MOVED) {
            if (sourceMember && !Objects.equals(change.sourcePath(), change.targetPath())) {
                effects.add(removed(change, change.sourcePath().value()));
            }
            if (targetMember) {
                effects.add(changed(change, change.targetPath().value()));
            }
            return;
        }
        if (change.changeKind() == TOMBSTONED) {
            if (sourceMember) {
                effects.add(removed(change, change.sourcePath().value()));
            }
            return;
        }
        throw corrupt("Files sync source contains an unknown change kind");
    }

    private static SyncPage boundedPage(
            long startingRevision,
            long capturedHighWater,
            List<RangeEffects> ranges,
            SyncLevel syncLevel,
            int limit,
            boolean sourceTruncated) {
        LinkedHashMap<String, MemberChange> current = new LinkedHashMap<>();
        LinkedHashMap<String, MemberChange> best = null;
        long bestRevision = startingRevision;
        int bestRangeIndex = -1;

        for (int rangeIndex = 0; rangeIndex < ranges.size(); rangeIndex++) {
            RangeEffects range = ranges.get(rangeIndex);
            for (RawEffect effect : range.effects()) {
                current.remove(effect.hrefPath());
                current.put(effect.hrefPath(), effect.member());
            }
            LinkedHashMap<String, MemberChange> normalized = normalize(current, syncLevel);
            if (normalized.size() <= limit) {
                best = normalized;
                bestRevision = range.rangeEnd();
                bestRangeIndex = rangeIndex;
            }
        }

        if (ranges.isEmpty()) {
            if (sourceTruncated) {
                throw corrupt("Files sync source is truncated without a complete range");
            }
            return new SyncPage(capturedHighWater, List.of(), false);
        }
        if (best == null) {
            throw new LimitCannotPreserveAtomicityException();
        }

        boolean complete = bestRangeIndex == ranges.size() - 1 && !sourceTruncated;
        long representedRevision = complete ? capturedHighWater : bestRevision;
        if (!complete && representedRevision <= startingRevision) {
            throw new LimitCannotPreserveAtomicityException();
        }
        return new SyncPage(
                representedRevision,
                orderedMembers(best.values()),
                !complete);
    }

    private static LinkedHashMap<String, MemberChange> normalize(
            Map<String, MemberChange> changes,
            SyncLevel syncLevel) {
        List<MemberChange> removedCollections = syncLevel == SyncLevel.INFINITE
                ? changes.values().stream()
                        .filter(change -> change.disposition() == Disposition.REMOVED)
                        .filter(change -> change.journalChange().objectKind() == COLLECTION)
                        .toList()
                : List.of();
        LinkedHashMap<String, MemberChange> normalized = new LinkedHashMap<>();
        changes.forEach((href, change) -> {
            boolean redundantDescendantRemoval = change.disposition() == Disposition.REMOVED
                    && removedCollections.stream().anyMatch(collection ->
                            !collection.hrefPath().equals(href)
                                    && descendant(collection.hrefPath(), href));
            if (!redundantDescendantRemoval) {
                normalized.put(href, change);
            }
        });
        return normalized;
    }

    private static List<MemberChange> orderedMembers(Iterable<MemberChange> values) {
        List<MemberChange> ordered = new ArrayList<>();
        values.forEach(ordered::add);
        ordered.sort(Comparator
                .comparingLong(MemberChange::revision)
                .thenComparing(MemberChange::hrefPath, FilesWebDavSyncProjector::compareUtf8)
                .thenComparing(MemberChange::disposition));
        return List.copyOf(ordered);
    }

    private static List<FileChange> ordered(List<FileChange> changes) {
        List<FileChange> ordered = new ArrayList<>(changes == null ? List.of() : changes);
        if (ordered.stream().anyMatch(Objects::isNull)) {
            throw corrupt("Files sync source contains null");
        }
        ordered.sort(Comparator.comparingLong(FileChange::revision));
        return List.copyOf(ordered);
    }

    private static RawEffect changed(FileChange change, String path) {
        return new RawEffect(change, path, Disposition.CHANGED);
    }

    private static RawEffect removed(FileChange change, String path) {
        return new RawEffect(change, path, Disposition.REMOVED);
    }

    private static boolean member(String collection, String candidate, SyncLevel level) {
        if (candidate == null || candidate.equals(collection)) {
            return false;
        }
        String prefix = "/".equals(collection) ? "/" : collection + "/";
        if (!candidate.startsWith(prefix)) {
            return false;
        }
        String relative = candidate.substring(prefix.length());
        return !relative.isEmpty()
                && (level == SyncLevel.INFINITE || relative.indexOf('/') < 0);
    }

    private static boolean descendant(String collection, String candidate) {
        return candidate.startsWith(collection.endsWith("/") ? collection : collection + "/");
    }

    private static int compareUtf8(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        int shared = Math.min(leftBytes.length, rightBytes.length);
        for (int index = 0; index < shared; index++) {
            int comparison = Integer.compare(
                    Byte.toUnsignedInt(leftBytes[index]),
                    Byte.toUnsignedInt(rightBytes[index]));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
    }

    private static String requirePath(String path) {
        if (path == null || path.isBlank() || path.charAt(0) != '/') {
            throw new IllegalArgumentException("Files synchronization path must be absolute");
        }
        return FilePathCodec.normalizeProductPath(path);
    }

    private static void requireWindow(long afterRevision, long capturedHighWater, int limit) {
        if (afterRevision < 0 || capturedHighWater < afterRevision || limit < 0 || limit > 100) {
            throw new IllegalArgumentException("Files synchronization window is invalid");
        }
    }

    private static CorruptSyncSourceException corrupt(String message) {
        return new CorruptSyncSourceException(message);
    }

    public enum Disposition {
        CHANGED,
        REMOVED
    }

    public record MemberChange(
            String hrefPath,
            Disposition disposition,
            long revision,
            FileChange journalChange) {

        public MemberChange {
            hrefPath = requirePath(hrefPath);
            disposition = Objects.requireNonNull(disposition, "disposition");
            journalChange = Objects.requireNonNull(journalChange, "journalChange");
            if (revision != journalChange.revision()) {
                throw new IllegalArgumentException("member revision must match its journal change");
            }
        }
    }

    public record SyncPage(long representedRevision, List<MemberChange> members, boolean truncated) {
        public SyncPage {
            if (representedRevision < 0) {
                throw new IllegalArgumentException("representedRevision must not be negative");
            }
            members = List.copyOf(members == null ? List.of() : members);
        }
    }

    private record RawEffect(FileChange change, String hrefPath, Disposition disposition) {
        private RawEffect {
            change = Objects.requireNonNull(change, "change");
            hrefPath = requirePath(hrefPath);
            disposition = Objects.requireNonNull(disposition, "disposition");
        }

        MemberChange member() {
            return new MemberChange(hrefPath, disposition, change.revision(), change);
        }
    }

    private record RangeEffects(
            long rangeStart,
            long rangeEnd,
            String operationRef,
            List<RawEffect> effects) {

        private RangeEffects {
            if (rangeStart < 1
                    || rangeEnd < rangeStart
                    || operationRef == null
                    || operationRef.isBlank()) {
                throw corrupt("Files sync range is invalid");
            }
            effects = List.copyOf(effects == null ? List.of() : effects);
        }
    }

    public static final class LimitCannotPreserveAtomicityException extends RuntimeException {
        public LimitCannotPreserveAtomicityException() {
            super("The requested Files synchronization limit cannot preserve one mutation boundary.");
        }
    }

    public static final class CorruptSyncSourceException extends RuntimeException {
        public CorruptSyncSourceException(String message) {
            super(message);
        }
    }
}

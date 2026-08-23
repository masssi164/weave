package com.massimotter.weave.backend.files.adapter;

import static java.util.Objects.requireNonNull;

import com.massimotter.weave.backend.files.application.FilesRootIdentity;
import com.massimotter.weave.backend.files.application.FilesScope;
import com.massimotter.weave.backend.files.application.NativeFilesWebDavSyncRepository;
import com.massimotter.weave.backend.files.application.NativeFilesWebDavSyncRepository.Capture;
import com.massimotter.weave.backend.files.application.NativeFilesWebDavSyncRepository.CorruptSyncStateException;
import com.massimotter.weave.backend.files.application.NativeFilesWebDavSyncRepository.DescendantDepth;
import com.massimotter.weave.backend.files.application.NativeFilesWebDavSyncRepository.InvalidSyncStateException;
import com.massimotter.weave.backend.files.application.NativeFilesWebDavSyncRepository.ScopeState;
import com.massimotter.weave.backend.files.application.NativeFilesWebDavSyncRepository.SyncCollectionNotFoundException;
import com.massimotter.weave.backend.files.application.NativeFilesWebDavSyncRepository.SyncReadCapacityException;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.FileChange;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.StreamHead;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.schema.NativeFilesVolumeAuthority;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL/JPA synchronization capture over one repeatable-read canonical snapshot. */
@Repository
public class JpaNativeFilesWebDavSyncRepository implements NativeFilesWebDavSyncRepository {

    private static final int MAXIMUM_EFFORT_ROWS = 10_000;
    private static final int QUERY_TIMEOUT_MILLISECONDS = 10_000;

    private static final String INITIAL_LATEST = """
            with latest as (
                select change.*
                  from weave_files_changes change
                 where change.organization_ref = :organizationRef
                   and change.space_ref = :spaceRef
                   and change.revision <= :highWater
                   and change.revision = (
                       select max(newer.revision)
                         from weave_files_changes newer
                        where newer.organization_ref = change.organization_ref
                          and newer.space_ref = change.space_ref
                          and newer.file_ref = change.file_ref
                          and newer.revision <= :highWater)
            ), candidates as (
                select latest.*
                  from latest
                 where latest.lifecycle_state = 'ACTIVE'
                   and latest.target_path is not null
                   and latest.target_path <> :collectionPath
                   and substr(latest.target_path, 1, :prefixLength) = :pathPrefix
                   and (:infinite = true
                        or strpos(substr(latest.target_path, :childStart), '/') = 0)
            )
            """;

    private static final String INITIAL_STATS = INITIAL_LATEST + """
            , range_counts as (
                select range_start, range_end, operation_ref, count(*) as candidate_count
                  from candidates
                 group by range_start, range_end, operation_ref
            ), cumulative as (
                select range_end,
                       sum(candidate_count) over (
                           order by range_start, range_end, operation_ref) as cumulative_count
                  from range_counts
            )
            select (select count(*) from candidates) as total_candidates,
                   (select max(range_end)
                      from cumulative
                     where cumulative_count <= :maximumRows) as selected_range_end
            """;

    private static final String INITIAL_ROWS = INITIAL_LATEST + """
            select candidates.*
              from candidates
             where candidates.range_end <= :selectedRangeEnd
             order by candidates.revision,
                      candidates.target_path collate "C",
                      candidates.file_ref collate "C"
            """;

    private static final String DELTA_STATS = """
            with ranges as (
                select range_start,
                       range_end,
                       operation_ref,
                       count(*) as row_count,
                       min(revision) as minimum_revision,
                       max(revision) as maximum_revision
                  from weave_files_changes
                 where organization_ref = :organizationRef
                   and space_ref = :spaceRef
                   and revision > :afterRevision
                   and revision <= :highWater
                 group by range_start, range_end, operation_ref
            ), cumulative as (
                select range_end,
                       sum(row_count) over (
                           order by range_start, range_end, operation_ref) as cumulative_count
                  from ranges
            )
            select coalesce((select sum(row_count) from ranges), 0) as total_rows,
                   (select max(range_end)
                      from cumulative
                     where cumulative_count <= :maximumRows) as selected_range_end,
                   coalesce((select sum(
                       case
                         when minimum_revision = range_start
                          and maximum_revision = range_end
                          and row_count = range_end - range_start + 1
                         then 0 else 1 end)
                       from ranges), 0) as invalid_ranges
            """;

    private static final String DELTA_ROWS = """
            select change.*
              from weave_files_changes change
             where change.organization_ref = :organizationRef
               and change.space_ref = :spaceRef
               and change.revision > :afterRevision
               and change.revision <= :selectedRangeEnd
             order by change.revision
            """;

    private final FileObjectJpaRepository files;
    private final FilesStreamHeadJpaRepository heads;
    private final FilesChangeJpaRepository changes;
    private final FilesVolumeAuthorityJpaRepository volumeAuthorities;
    private final EntityManager entityManager;

    public JpaNativeFilesWebDavSyncRepository(
            FileObjectJpaRepository files,
            FilesStreamHeadJpaRepository heads,
            FilesChangeJpaRepository changes,
            FilesVolumeAuthorityJpaRepository volumeAuthorities,
            EntityManager entityManager) {
        this.files = requireNonNull(files, "files");
        this.heads = requireNonNull(heads, "heads");
        this.changes = requireNonNull(changes, "changes");
        this.volumeAuthorities = requireNonNull(volumeAuthorities, "volumeAuthorities");
        this.entityManager = requireNonNull(entityManager, "entityManager");
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Capture captureInitial(
            FilesScope scope,
            FilePath collectionPath,
            DescendantDepth depth,
            int maximumJournalRows) {
        FilesScope requiredScope = requireNonNull(scope, "scope");
        FilePath requiredPath = requireNonNull(collectionPath, "collectionPath");
        DescendantDepth requiredDepth = requireNonNull(depth, "depth");
        int effort = requireEffort(maximumJournalRows);
        ScopeState state = captureState(requiredScope, requiredPath);
        long highWater = state.head().latestRevision();
        if (highWater == 0) {
            return new Capture(state, 0, List.of(), false);
        }

        PathParameters paths = PathParameters.forCollection(requiredPath);
        Object[] stats = result(initialQuery(INITIAL_STATS, state, paths, requiredDepth, highWater)
                .setParameter("maximumRows", effort));
        long totalCandidates = number(stats[0]);
        if (totalCandidates == 0) {
            return new Capture(state, highWater, List.of(), false);
        }
        Long selectedRangeEnd = nullableNumber(stats[1]);
        if (selectedRangeEnd == null) {
            throw new SyncReadCapacityException();
        }

        List<FileChange> snapshots = initialRows(
                state,
                paths,
                requiredDepth,
                highWater,
                selectedRangeEnd);
        if (snapshots.isEmpty() || snapshots.size() > effort || snapshots.size() > totalCandidates) {
            throw corrupt("initial Files synchronization capture is inconsistent");
        }
        return new Capture(
                state,
                highWater,
                snapshots,
                snapshots.size() < totalCandidates);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Capture captureDelta(
            FilesScope scope,
            FilePath collectionPath,
            DescendantDepth depth,
            FileId expectedCollectionId,
            String expectedStreamRef,
            long afterRevision,
            int maximumJournalRows) {
        FilesScope requiredScope = requireNonNull(scope, "scope");
        FilePath requiredPath = requireNonNull(collectionPath, "collectionPath");
        requireNonNull(depth, "depth");
        FileId expectedId = requireNonNull(expectedCollectionId, "expectedCollectionId");
        String expectedGeneration = required(expectedStreamRef, "expectedStreamRef");
        int effort = requireEffort(maximumJournalRows);
        ScopeState state = captureState(requiredScope, requiredPath);

        if (!state.collectionId().equals(expectedId)
                || !state.streamRef().equals(expectedGeneration)
                || afterRevision < state.head().resetRequiredFloor()
                || afterRevision > state.head().latestRevision()) {
            throw new InvalidSyncStateException();
        }
        requireRangeBoundary(state, afterRevision);
        long highWater = state.head().latestRevision();
        if (afterRevision == highWater) {
            return new Capture(state, highWater, List.of(), false);
        }

        Query statsQuery = timed(entityManager.createNativeQuery(DELTA_STATS))
                .setParameter("organizationRef", requiredScope.organizationRef())
                .setParameter("spaceRef", requiredScope.spaceRef())
                .setParameter("afterRevision", afterRevision)
                .setParameter("highWater", highWater)
                .setParameter("maximumRows", effort);
        Object[] stats = result(statsQuery);
        long totalRows = number(stats[0]);
        Long selectedRangeEnd = nullableNumber(stats[1]);
        long invalidRanges = number(stats[2]);
        if (invalidRanges != 0 || totalRows <= 0) {
            throw corrupt("delta Files synchronization ranges are incomplete");
        }
        if (selectedRangeEnd == null) {
            throw new SyncReadCapacityException();
        }

        List<FileChange> selected = deltaRows(state, afterRevision, selectedRangeEnd);
        if (selected.isEmpty() || selected.size() > effort || selected.size() > totalRows) {
            throw corrupt("delta Files synchronization capture is inconsistent");
        }
        return new Capture(
                state,
                highWater,
                selected,
                selectedRangeEnd < highWater);
    }

    private ScopeState captureState(FilesScope scope, FilePath collectionPath) {
        FileId collectionId;
        if (collectionPath.root()) {
            collectionId = FilesRootIdentity.forScope(scope);
        } else {
            FileObjectJpaEntity collection = files
                    .findByIdOrganizationRefAndIdSpaceRefAndActivePathKey(
                            scope.organizationRef(),
                            scope.spaceRef(),
                            collectionPath.value())
                    .orElseThrow(SyncCollectionNotFoundException::new);
            if (collection.toStoredRecord().metadata().object().kind() != Kind.COLLECTION) {
                throw new SyncCollectionNotFoundException();
            }
            collectionId = collection.toStoredRecord().metadata().object().id();
        }

        StreamHead head = heads.findById(new FilesScopeId(
                        scope.organizationRef(), scope.spaceRef()))
                .map(FilesStreamHeadJpaEntity::toStreamHead)
                .orElseThrow(() -> corrupt("Files synchronization stream head is missing"));
        String generation = volumeAuthorities
                .findById(NativeFilesVolumeAuthority.AUTHORITY_KEY)
                .map(FilesVolumeAuthorityJpaEntity::generationRef)
                .orElseThrow(() -> corrupt("Files synchronization generation is missing"));
        return new ScopeState(scope, collectionId, collectionPath, generation, head);
    }

    private void requireRangeBoundary(ScopeState state, long revision) {
        if (revision == 0) {
            return;
        }
        FilesChangeJpaEntity boundary = changes.findById(new FilesChangeId(
                        state.scope().organizationRef(),
                        state.scope().spaceRef(),
                        revision))
                .orElseThrow(InvalidSyncStateException::new);
        if (boundary.rangeEnd() != revision) {
            throw new InvalidSyncStateException();
        }
    }

    private Query initialQuery(
            String sql,
            ScopeState state,
            PathParameters paths,
            DescendantDepth depth,
            long highWater) {
        return timed(entityManager.createNativeQuery(sql))
                .setParameter("organizationRef", state.scope().organizationRef())
                .setParameter("spaceRef", state.scope().spaceRef())
                .setParameter("highWater", highWater)
                .setParameter("collectionPath", state.collectionPath().value())
                .setParameter("pathPrefix", paths.prefix())
                .setParameter("prefixLength", paths.prefixLength())
                .setParameter("childStart", paths.childStart())
                .setParameter("infinite", depth == DescendantDepth.INFINITE);
    }

    @SuppressWarnings("unchecked")
    private List<FileChange> initialRows(
            ScopeState state,
            PathParameters paths,
            DescendantDepth depth,
            long highWater,
            long selectedRangeEnd) {
        Query query = timed(entityManager.createNativeQuery(INITIAL_ROWS, FilesChangeJpaEntity.class))
                .setParameter("organizationRef", state.scope().organizationRef())
                .setParameter("spaceRef", state.scope().spaceRef())
                .setParameter("highWater", highWater)
                .setParameter("collectionPath", state.collectionPath().value())
                .setParameter("pathPrefix", paths.prefix())
                .setParameter("prefixLength", paths.prefixLength())
                .setParameter("childStart", paths.childStart())
                .setParameter("infinite", depth == DescendantDepth.INFINITE)
                .setParameter("selectedRangeEnd", selectedRangeEnd);
        return ((List<FilesChangeJpaEntity>) query.getResultList()).stream()
                .map(FilesChangeJpaEntity::toFileChange)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<FileChange> deltaRows(
            ScopeState state,
            long afterRevision,
            long selectedRangeEnd) {
        Query query = timed(entityManager.createNativeQuery(DELTA_ROWS, FilesChangeJpaEntity.class))
                .setParameter("organizationRef", state.scope().organizationRef())
                .setParameter("spaceRef", state.scope().spaceRef())
                .setParameter("afterRevision", afterRevision)
                .setParameter("selectedRangeEnd", selectedRangeEnd);
        return ((List<FilesChangeJpaEntity>) query.getResultList()).stream()
                .map(FilesChangeJpaEntity::toFileChange)
                .toList();
    }

    private static Object[] result(Query query) {
        Object value = query.getSingleResult();
        if (!(value instanceof Object[] row)) {
            throw corrupt("Files synchronization query returned an invalid projection");
        }
        return row;
    }

    private static Query timed(Query query) {
        return query.setHint("jakarta.persistence.query.timeout", QUERY_TIMEOUT_MILLISECONDS);
    }

    private static int requireEffort(int maximumRows) {
        if (maximumRows < 1 || maximumRows > MAXIMUM_EFFORT_ROWS) {
            throw new IllegalArgumentException("maximumJournalRows must be between one and 10000");
        }
        return maximumRows;
    }

    private static long number(Object value) {
        if (!(value instanceof Number number)) {
            throw corrupt("Files synchronization query returned a non-numeric value");
        }
        return number.longValue();
    }

    private static Long nullableNumber(Object value) {
        return value == null ? null : number(value);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must not be blank or padded");
        }
        return value;
    }

    private static CorruptSyncStateException corrupt(String message) {
        return new CorruptSyncStateException(message);
    }

    private record PathParameters(
            String prefix,
            int prefixLength,
            int childStart) {

        static PathParameters forCollection(FilePath path) {
            String prefix = path.root() ? "/" : path.value() + "/";
            return new PathParameters(prefix, prefix.length(), prefix.length() + 1);
        }
    }
}

package com.massimotter.weave.backend.files.adapter;

import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.LockConflictException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcFilesAuthorityRepository implements FilesAuthorityRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcFilesAuthorityRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.transactions = new TransactionTemplate(
                java.util.Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
    }

    @Override
    public CanonicalFileRecord save(CanonicalFileRecord record) {
        jdbc.update("""
                insert into weave_files_objects
                  (organization_ref, space_ref, file_id, canonical_path, object_kind, byte_size, media_type,
                   modified_at_utc, hidden, version_token, content_digest, provider_binding_revision,
                   lifecycle_state, observed_at_utc)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (organization_ref, space_ref, file_id) do update set
                  canonical_path = excluded.canonical_path, object_kind = excluded.object_kind,
                  byte_size = excluded.byte_size, media_type = excluded.media_type,
                  modified_at_utc = excluded.modified_at_utc, hidden = excluded.hidden,
                  version_token = excluded.version_token, content_digest = excluded.content_digest,
                  provider_binding_revision = excluded.provider_binding_revision,
                  lifecycle_state = excluded.lifecycle_state, observed_at_utc = excluded.observed_at_utc
                """, record.organizationRef(), record.spaceRef(), record.object().id().value(),
                record.object().path().value(), record.object().kind().name(), record.object().size(),
                record.object().mediaType(), timestamp(record.object().modifiedAt()), record.object().hidden(),
                record.version().value(), record.contentDigest(), record.providerBindingRevision(),
                record.lifecycle().name(), timestamp(record.observedAt()));
        return findById(record.organizationRef(), record.spaceRef(), record.object().id()).orElseThrow();
    }

    @Override
    public Optional<CanonicalFileRecord> findByPath(String organizationRef, String spaceRef, FilePath path) {
        return files("canonical_path = ?", organizationRef, spaceRef, path.value());
    }

    @Override
    public Optional<CanonicalFileRecord> findById(String organizationRef, String spaceRef, FileId id) {
        return files("file_id = ?", organizationRef, spaceRef, id.value());
    }

    @Override
    public CanonicalFileRecord move(
            String organizationRef,
            String spaceRef,
            FileId id,
            FilePath expectedPath,
            FilePath destination,
            Instant movedAt) {
        int updated = jdbc.update("""
                update weave_files_objects set canonical_path = ?, modified_at_utc = ?, observed_at_utc = ?
                where organization_ref = ? and space_ref = ? and file_id = ? and canonical_path = ?
                  and lifecycle_state = 'ACTIVE'
                """, destination.value(), timestamp(movedAt), timestamp(movedAt), organizationRef, spaceRef,
                id.value(), expectedPath.value());
        if (updated != 1) {
            throw new StaleCanonicalFileException(id, expectedPath);
        }
        return findById(organizationRef, spaceRef, id).orElseThrow();
    }

    @Override
    public FileLockRecord acquireLock(FileLockRecord requested, Instant now) {
        return transactions.execute(status -> {
            StoredLock current = jdbc.query("""
                    select * from weave_file_locks where organization_ref = ? and space_ref = ?
                      and canonical_path = ? for update
                    """, this::storedLock, requested.organizationRef(), requested.spaceRef(),
                    requested.path().value()).stream().findFirst().orElse(null);
            if (current != null && current.releasedAt() == null && current.expiresAt().isAfter(now)) {
                throw new LockConflictException(requested.path());
            }
            long fence = current == null ? 1 : current.fence() + 1;
            if (current == null) {
                jdbc.update("""
                        insert into weave_file_locks
                          (organization_ref, space_ref, canonical_path, token_digest, owner_ref, fence,
                           expires_at_utc, created_at_utc, released_at_utc)
                        values (?, ?, ?, ?, ?, ?, ?, ?, null)
                        """, requested.organizationRef(), requested.spaceRef(), requested.path().value(),
                        requested.tokenDigest(), requested.ownerRef(), fence, timestamp(requested.expiresAt()),
                        timestamp(requested.createdAt()));
            } else {
                jdbc.update("""
                        update weave_file_locks set token_digest = ?, owner_ref = ?, fence = ?,
                          expires_at_utc = ?, created_at_utc = ?, released_at_utc = null
                        where organization_ref = ? and space_ref = ? and canonical_path = ?
                        """, requested.tokenDigest(), requested.ownerRef(), fence, timestamp(requested.expiresAt()),
                        timestamp(requested.createdAt()), requested.organizationRef(), requested.spaceRef(),
                        requested.path().value());
            }
            return activeLock(requested.organizationRef(), requested.spaceRef(), requested.path(), now).orElseThrow();
        });
    }

    @Override
    public Optional<FileLockRecord> activeLock(
            String organizationRef, String spaceRef, FilePath path, Instant now) {
        return jdbc.query("""
                select * from weave_file_locks where organization_ref = ? and space_ref = ?
                  and canonical_path = ? and released_at_utc is null and expires_at_utc > ?
                """, this::lock, organizationRef, spaceRef, path.value(), timestamp(now)).stream().findFirst();
    }

    @Override
    public List<FileLockRecord> activeLocks(String organizationRef, String spaceRef, Instant now) {
        return jdbc.query("""
                select * from weave_file_locks where organization_ref = ? and space_ref = ?
                  and released_at_utc is null and expires_at_utc > ?
                order by canonical_path
                """, this::lock, organizationRef, spaceRef, timestamp(now));
    }

    @Override
    public void releaseLock(
            String organizationRef, String spaceRef, FilePath path, String tokenDigest, String ownerRef) {
        int updated = jdbc.update("""
                update weave_file_locks set released_at_utc = current_timestamp
                where organization_ref = ? and space_ref = ? and canonical_path = ?
                  and token_digest = ? and owner_ref = ? and released_at_utc is null
                  and expires_at_utc > current_timestamp
                """, organizationRef, spaceRef, path.value(), tokenDigest, ownerRef);
        if (updated != 1) {
            throw new LockConflictException(path);
        }
    }

    @Override
    public void moveLock(
            String organizationRef,
            String spaceRef,
            FilePath source,
            FilePath destination,
            String tokenDigest,
            String ownerRef) {
        int updated = jdbc.update("""
                update weave_file_locks set canonical_path = ?
                where organization_ref = ? and space_ref = ? and canonical_path = ?
                  and token_digest = ? and owner_ref = ? and released_at_utc is null
                  and expires_at_utc > current_timestamp
                """, destination.value(), organizationRef, spaceRef, source.value(), tokenDigest, ownerRef);
        if (updated != 1) {
            throw new LockConflictException(source);
        }
    }

    private Optional<CanonicalFileRecord> files(
            String predicate, String organizationRef, String spaceRef, String value) {
        return jdbc.query("select * from weave_files_objects where organization_ref = ? and space_ref = ? and "
                        + predicate,
                this::file, organizationRef, spaceRef, value).stream().findFirst();
    }

    private CanonicalFileRecord file(ResultSet rs, int row) throws SQLException {
        Instant modifiedAt = instant(rs, "modified_at_utc");
        FileObject object = new FileObject(
                new FileId(rs.getString("file_id")), new FilePath(rs.getString("canonical_path")),
                Kind.valueOf(rs.getString("object_kind")), rs.getLong("byte_size"), rs.getString("media_type"),
                modifiedAt, rs.getBoolean("hidden"));
        return new CanonicalFileRecord(
                rs.getString("organization_ref"), rs.getString("space_ref"), object,
                new FileVersion(rs.getString("version_token")), rs.getString("content_digest"),
                rs.getLong("provider_binding_revision"), Lifecycle.valueOf(rs.getString("lifecycle_state")),
                instant(rs, "observed_at_utc"));
    }

    private FileLockRecord lock(ResultSet rs, int row) throws SQLException {
        return new FileLockRecord(
                rs.getString("organization_ref"), rs.getString("space_ref"),
                new FilePath(rs.getString("canonical_path")), rs.getString("token_digest"),
                rs.getString("owner_ref"), rs.getLong("fence"), instant(rs, "expires_at_utc"),
                instant(rs, "created_at_utc"));
    }

    private StoredLock storedLock(ResultSet rs, int row) throws SQLException {
        return new StoredLock(
                rs.getLong("fence"), instant(rs, "expires_at_utc"), instant(rs, "released_at_utc"));
    }

    private OffsetDateTime timestamp(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record StoredLock(long fence, Instant expiresAt, Instant releasedAt) {
    }

    public static final class StaleCanonicalFileException extends RuntimeException {
        public StaleCanonicalFileException(FileId id, FilePath expectedPath) {
            super("canonical file changed before move: " + id.value() + " at " + expectedPath.value());
        }
    }
}

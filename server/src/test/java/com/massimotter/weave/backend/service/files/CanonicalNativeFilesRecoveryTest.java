package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.files.adapter.FilesAuthorityJpaTestFactory;
import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesProviderPort.FilesRequestScope;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CanonicalNativeFilesRecoveryTest {

    private static final Instant NOW = Instant.parse("2026-08-19T08:00:00Z");
    private static final FilesRequestScope ALPHA =
            new FilesRequestScope("org:alpha", "space:home", 4);
    private static final FilesRequestScope BETA =
            new FilesRequestScope("org:beta", "space:home", 2);

    @TempDir
    Path temporaryDirectory;

    private DataSource dataSource;
    private FilesAuthorityRepository authority;
    private FilesystemBlobStore blobs;
    private WeaveNativeFilesProperties properties;

    @BeforeEach
    void setUp() {
        dataSource = JpaTestDatabase.entityFirstDataSource("canonical_native_files_recovery");
        authority = FilesAuthorityJpaTestFactory.create(dataSource);
        properties = new WeaveNativeFilesProperties(
                temporaryDirectory.resolve("private-blobs"),
                1024 * 1024,
                100);
        blobs = new FilesystemBlobStore(properties);
    }

    @AfterEach
    void closeBlobStore() {
        blobs.closeOperator();
    }

    @Test
    void restartReadsCanonicalMetadataAndImmutableBlobContent() {
        byte[] content = "native file".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FilesProviderPort first = composition(authority).scoped(ALPHA);

        var written = first.write(new FileWrite(
                new FilePath("/readme.txt"),
                content,
                "text/plain"));
        FilesProviderPort restarted = composition(
                FilesAuthorityJpaTestFactory.create(dataSource)).scoped(ALPHA);

        assertThat(restarted.read(written.id()).bytes()).isEqualTo(content);
        assertThat(restarted.find(new FilePath("/readme.txt"))).isPresent();
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).hasSize(1);
    }

    @Test
    void canonicalTenantScopesCannotReadEachOthersFileIds() {
        FilesProviderPort alpha = composition(authority).scoped(ALPHA);
        FilesProviderPort beta = composition(authority).scoped(BETA);
        var written = alpha.write(new FileWrite(
                new FilePath("/private.txt"),
                new byte[] {1, 2},
                "text/plain"));

        assertThatThrownBy(() -> beta.read(written.id()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void failedMetadataActivationLeavesNoCanonicalFile() {
        byte[] content = "pending".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FilesProviderPort failing = composition(
                new FailNextActivationAuthority(authority)).scoped(ALPHA);

        assertThatThrownBy(() -> failing.write(new FileWrite(
                new FilePath("/pending.txt"),
                content,
                "text/plain")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("injected metadata failure");
        assertThat(authority.findByPath(
                ALPHA.organizationRef(),
                ALPHA.spaceRef(),
                new FilePath("/pending.txt")))
                .isEmpty();
    }

    @Test
    void reconciliationRemovesBlobPublishedBeforeFailedActivation() {
        FilesProviderPort failing = composition(
                new FailNextActivationAuthority(authority)).scoped(ALPHA);

        assertThatThrownBy(() -> failing.write(new FileWrite(
                new FilePath("/orphan.txt"),
                new byte[] {7, 8, 9},
                "application/octet-stream")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).hasSize(1);

        var report = composition(authority).reconcile(ALPHA);

        assertThat(report.orphanBlobsDeleted()).isEqualTo(1);
        assertThat(report.inconsistentMetadataRecords()).isZero();
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).isEmpty();
    }

    private CanonicalNativeFilesComposition composition(
            FilesAuthorityRepository repository) {
        return new CanonicalNativeFilesComposition(
                repository,
                blobs,
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties.reconciliationLimit());
    }

    private BlobScope blobScope(FilesRequestScope scope) {
        return new BlobScope(scope.organizationRef(), scope.spaceRef());
    }

    private static final class FailNextActivationAuthority
            implements FilesAuthorityRepository {

        private final FilesAuthorityRepository delegate;
        private boolean fail = true;

        private FailNextActivationAuthority(FilesAuthorityRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public CanonicalFileRecord save(CanonicalFileRecord record) {
            return delegate.save(record);
        }

        @Override
        public CanonicalFileRecord activate(CanonicalFileRecord record) {
            if (fail) {
                fail = false;
                throw new IllegalStateException(
                        "injected metadata failure before activation");
            }
            return delegate.activate(record);
        }

        @Override
        public Optional<CanonicalFileRecord> findByPath(
                String organizationRef,
                String spaceRef,
                FilePath path) {
            return delegate.findByPath(organizationRef, spaceRef, path);
        }

        @Override
        public Optional<CanonicalFileRecord> findById(
                String organizationRef,
                String spaceRef,
                FileId id) {
            return delegate.findById(organizationRef, spaceRef, id);
        }

        @Override
        public List<CanonicalFileRecord> activeFiles(
                String organizationRef,
                String spaceRef) {
            return delegate.activeFiles(organizationRef, spaceRef);
        }

        @Override
        public List<CanonicalFileRecord> replace(
                List<CanonicalFileRecord> tombstones,
                List<CanonicalFileRecord> activations) {
            return delegate.replace(tombstones, activations);
        }

        @Override
        public CanonicalFileRecord move(
                String organizationRef,
                String spaceRef,
                FileId id,
                FilePath expectedPath,
                FilePath destination,
                Instant movedAt) {
            return delegate.move(
                    organizationRef,
                    spaceRef,
                    id,
                    expectedPath,
                    destination,
                    movedAt);
        }

        @Override
        public FileLockRecord acquireLock(
                FileLockRecord requested,
                Instant now) {
            return delegate.acquireLock(requested, now);
        }

        @Override
        public Optional<FileLockRecord> activeLock(
                String organizationRef,
                String spaceRef,
                FilePath path,
                Instant now) {
            return delegate.activeLock(organizationRef, spaceRef, path, now);
        }

        @Override
        public List<FileLockRecord> activeLocks(
                String organizationRef,
                String spaceRef,
                Instant now) {
            return delegate.activeLocks(organizationRef, spaceRef, now);
        }

        @Override
        public void releaseLock(
                String organizationRef,
                String spaceRef,
                FilePath path,
                String tokenDigest,
                String ownerRef,
                Instant now) {
            delegate.releaseLock(
                    organizationRef,
                    spaceRef,
                    path,
                    tokenDigest,
                    ownerRef,
                    now);
        }

        @Override
        public void moveLock(
                String organizationRef,
                String spaceRef,
                FilePath source,
                FilePath destination,
                String tokenDigest,
                String ownerRef,
                Instant now) {
            delegate.moveLock(
                    organizationRef,
                    spaceRef,
                    source,
                    destination,
                    tokenDigest,
                    ownerRef,
                    now);
        }
    }
}

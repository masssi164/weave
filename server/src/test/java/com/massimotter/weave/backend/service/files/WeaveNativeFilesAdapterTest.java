package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.adapter.FilesAuthorityJpaTestFactory;
import com.massimotter.weave.backend.files.application.FilesDigests;
import com.massimotter.weave.backend.files.application.FilesMutationTargetCodec;
import com.massimotter.weave.backend.files.application.FilesScope;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository;
import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesProviderPort.FilesRequestScope;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class WeaveNativeFilesAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-09T08:00:00Z");
    private static final FilesRequestScope ALPHA = new FilesRequestScope("org:alpha", "space:home", 4);
    private static final FilesRequestScope BETA = new FilesRequestScope("org:beta", "space:home", 2);

    @TempDir
    Path temporaryDirectory;

    private DataSource dataSource;
    private FilesAuthorityRepository authority;
    private FilesystemBlobStore blobs;

    @BeforeEach
    void setUp() {
        dataSource = JpaTestDatabase.entityFirstDataSource("weave_native_files");
        authority = FilesAuthorityJpaTestFactory.create(dataSource);
        blobs = new FilesystemBlobStore(properties());
    }

    @Test
    void writeReadAndRestartKeepCanonicalMetadataAndBlobContent() {
        byte[] content = "native file".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FilesProviderPort first = adapter(authority).scoped(ALPHA);

        var written = first.write(new FileWrite(new FilePath("/readme.txt"), content, "text/plain"));
        FilesProviderPort restarted = adapter(FilesAuthorityJpaTestFactory.create(dataSource)).scoped(ALPHA);

        assertThat(restarted.read(written.id()).bytes()).isEqualTo(content);
        assertThat(restarted.find(new FilePath("/readme.txt"))).isPresent();
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).hasSize(1);
    }

    @Test
    void tenantScopesCannotReadEachOthersCanonicalFileIds() {
        FilesProviderPort alpha = adapter(authority).scoped(ALPHA);
        FilesProviderPort beta = adapter(authority).scoped(BETA);
        var written = alpha.write(new FileWrite(new FilePath("/private.txt"), new byte[] {1, 2}, "text/plain"));

        assertThatThrownBy(() -> beta.read(written.id())).isInstanceOf(RuntimeException.class);
    }

    @Test
    void metadataFailureDoesNotActivateAnUncommittedFile() {
        byte[] content = "pending".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FilesProviderPort failing = new WeaveNativeFilesAdapter(
                new FailNextMutationAuthority(authority, FailurePoint.SAVE),
                blobs,
                Clock.fixed(NOW, ZoneOffset.UTC),
                100).scoped(ALPHA);

        assertThatThrownBy(() -> failing.write(new FileWrite(new FilePath("/pending.txt"), content, "text/plain")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("injected metadata failure");
        assertThat(authority.findByPath(ALPHA.organizationRef(), ALPHA.spaceRef(), new FilePath("/pending.txt"))).isEmpty();
    }

    @Test
    void reconciliationRemovesBlobPublishedBeforeFailedActivation() {
        FilesProviderPort failing = new WeaveNativeFilesAdapter(
                new FailNextMutationAuthority(authority, FailurePoint.ACTIVATE),
                blobs,
                Clock.fixed(NOW, ZoneOffset.UTC),
                100).scoped(ALPHA);

        assertThatThrownBy(() -> failing.write(new FileWrite(
                new FilePath("/orphan.txt"),
                new byte[] {7, 8, 9},
                "application/octet-stream")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("injected metadata failure");
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).hasSize(1);

        var report = adapter(authority).reconcile(ALPHA);

        assertThat(report.orphanBlobsDeleted()).isEqualTo(1);
        assertThat(report.inconsistentMetadataRecords()).isZero();
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).isEmpty();
    }

    @Test
    void reconciliationUsesDurableMutationBindingsAsBlobProtection() {
        BlobReference protectedBinding = new BlobReference("v1/plans/created/result");
        BlobReference terminalBinding = new BlobReference("v1/plans/terminal/result");
        byte[] protectedContent = "protected".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] terminalContent = "terminal".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        blobs.put(
                blobScope(ALPHA),
                protectedBinding,
                protectedContent,
                FilesDigests.sha256(protectedContent));
        blobs.put(
                blobScope(ALPHA),
                terminalBinding,
                terminalContent,
                FilesDigests.sha256(terminalContent));
        NativeFilesMutationRepository mutations = mock(NativeFilesMutationRepository.class);
        when(mutations.protectedBindings(new FilesScope(
                ALPHA.organizationRef(), ALPHA.spaceRef())))
                .thenReturn(Set.of(protectedBinding));
        WeaveNativeFilesAdapter adapter = new WeaveNativeFilesAdapter(
                authority,
                blobs,
                Clock.fixed(NOW, ZoneOffset.UTC),
                100,
                mutations,
                new FilesMutationTargetCodec(
                        JsonMapper.builder().findAndAddModules().build()));

        var report = adapter.reconcile(ALPHA);

        assertThat(report.orphanBlobsDeleted()).isEqualTo(1);
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).containsExactly(protectedBinding);
    }

    @Test
    void moveAndCopyKeepCanonicalContentReachable() {
        byte[] content = "portable".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FilesProviderPort scoped = adapter(authority).scoped(ALPHA);
        scoped.write(new FileWrite(new FilePath("/source.txt"), content, "text/plain"));

        var moved = scoped.move(new FilePath("/source.txt"), new FilePath("/moved.txt"), false);
        var copied = scoped.copy(new FilePath("/moved.txt"), new FilePath("/copy.txt"), false);

        assertThat(scoped.read(moved.id()).bytes()).isEqualTo(content);
        assertThat(scoped.read(copied.id()).bytes()).isEqualTo(content);
    }

    @Test
    void replacingDestinationPublishesNewCanonicalVersionWithoutBlobCorruption() {
        byte[] original = "source".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] destination = "destination".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FilesProviderPort scoped = adapter(authority).scoped(ALPHA);
        scoped.write(new FileWrite(new FilePath("/source.txt"), original, "text/plain"));
        scoped.write(new FileWrite(new FilePath("/destination.txt"), destination, "text/plain"));

        var copied = scoped.copy(new FilePath("/source.txt"), new FilePath("/destination.txt"), true);

        assertThat(scoped.read(copied.id()).bytes()).isEqualTo(original);
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).hasSize(2);
    }

    @Test
    void unscopedNativeDataOperationsFailClosed() {
        assertThatThrownBy(() -> adapter(authority).find(new FilePath("/anything")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(exception.code()).isEqualTo("files-native-scope-required");
                });
    }

    private WeaveNativeFilesAdapter adapter(FilesAuthorityRepository repository) {
        return new WeaveNativeFilesAdapter(repository, blobs, Clock.fixed(NOW, ZoneOffset.UTC), 100);
    }

    private WeaveNativeFilesProperties properties() {
        return new WeaveNativeFilesProperties(temporaryDirectory.resolve("private-blobs"), 1024 * 1024, 100);
    }

    private BlobScope blobScope(FilesRequestScope scope) {
        return new BlobScope(scope.organizationRef(), scope.spaceRef());
    }

    private enum FailurePoint {
        SAVE,
        ACTIVATE
    }

    private static final class FailNextMutationAuthority implements FilesAuthorityRepository {
        private final FilesAuthorityRepository delegate;
        private final FailurePoint failurePoint;
        private boolean fail = true;

        private FailNextMutationAuthority(
                FilesAuthorityRepository delegate,
                FailurePoint failurePoint) {
            this.delegate = delegate;
            this.failurePoint = failurePoint;
        }

        @Override
        public StoredFileRecord save(StoredFileRecord record) {
            if (fail && failurePoint == FailurePoint.SAVE) {
                fail = false;
                throw new IllegalStateException("injected metadata failure before activation");
            }
            return delegate.save(record);
        }

        @Override
        public StoredFileRecord activate(StoredFileRecord record) {
            if (fail && failurePoint == FailurePoint.ACTIVATE) {
                fail = false;
                throw new IllegalStateException("injected metadata failure during activation");
            }
            if (failurePoint == FailurePoint.SAVE) {
                return save(record);
            }
            return delegate.activate(record);
        }

        @Override public Optional<StoredFileRecord> findByPath(String organizationRef, String spaceRef, FilePath path) { return delegate.findByPath(organizationRef, spaceRef, path); }
        @Override public Optional<StoredFileRecord> findById(String organizationRef, String spaceRef, FileId id) { return delegate.findById(organizationRef, spaceRef, id); }
        @Override public List<StoredFileRecord> activeFiles(String organizationRef, String spaceRef) { return delegate.activeFiles(organizationRef, spaceRef); }
        @Override public List<StoredFileRecord> replace(List<StoredFileRecord> tombstones, List<StoredFileRecord> activations) { return delegate.replace(tombstones, activations); }
        @Override public StoredFileRecord move(String organizationRef, String spaceRef, FileId id, FilePath expectedPath, FilePath destination, Instant movedAt) { return delegate.move(organizationRef, spaceRef, id, expectedPath, destination, movedAt); }
        @Override public FileLockRecord acquireLock(FileLockRecord requested, Instant now) { return delegate.acquireLock(requested, now); }
        @Override public Optional<FileLockRecord> activeLock(String organizationRef, String spaceRef, FilePath path, Instant now) { return delegate.activeLock(organizationRef, spaceRef, path, now); }
        @Override public List<FileLockRecord> activeLocks(String organizationRef, String spaceRef, Instant now) { return delegate.activeLocks(organizationRef, spaceRef, now); }
        @Override public void releaseLock(String organizationRef, String spaceRef, FilePath path, String tokenDigest, String ownerRef, Instant now) { delegate.releaseLock(organizationRef, spaceRef, path, tokenDigest, ownerRef, now); }
        @Override public void moveLock(String organizationRef, String spaceRef, FilePath source, FilePath destination, String tokenDigest, String ownerRef, Instant now) { delegate.moveLock(organizationRef, spaceRef, source, destination, tokenDigest, ownerRef, now); }
    }
}

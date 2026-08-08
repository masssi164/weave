package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.adapter.FilesAuthorityJpaTestFactory;
import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesProviderPort.FilesRequestScope;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeaveNativeFilesAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
    private static final FilesRequestScope ALPHA = new FilesRequestScope("org:alpha", "workspace-default", 4);
    private static final FilesRequestScope BRAVO = new FilesRequestScope("org:bravo", "workspace-default", 2);

    @TempDir
    Path temporaryDirectory;

    private javax.sql.DataSource dataSource;
    private FilesAuthorityRepository authority;
    private FilesystemBlobStore blobs;

    @BeforeEach
    void setUp() {
        dataSource = JpaTestDatabase.entityFirstDataSource("weave_native_files");
        authority = FilesAuthorityJpaTestFactory.create(dataSource);
        blobs = new FilesystemBlobStore(properties());
    }

    @Test
    void retryAndRestartConvergeOnOneCanonicalIdAndOneBlob() {
        FilesProviderPort first = adapter(authority).scoped(ALPHA);
        byte[] content = "one visible payload".getBytes(StandardCharsets.UTF_8);

        var initial = first.write(new FileWrite(new FilePath("/plan.md"), content, "text/markdown"));
        var afterAmbiguousResponseRetry = first.write(
                new FileWrite(new FilePath("/plan.md"), content, "text/markdown"));

        FilesProviderPort restarted = adapter(FilesAuthorityJpaTestFactory.create(dataSource)).scoped(ALPHA);
        var afterRestart = restarted.find(new FilePath("/plan.md")).orElseThrow();

        assertThat(afterAmbiguousResponseRetry.id()).isEqualTo(initial.id());
        assertThat(afterRestart.item().id()).isEqualTo(initial.id());
        assertThat(restarted.read(initial.id()).bytes()).isEqualTo(content);
        assertThat(authority.activeFiles(ALPHA.organizationRef(), ALPHA.spaceRef())).hasSize(1);
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).hasSize(1);
    }

    @Test
    void blobPublishFailureWindowIsRetryableAndOrphanReconciliationIsTenantScoped() {
        FilesAuthorityRepository failBeforeActivation = new FailNextSaveAuthority(authority);
        byte[] content = "published before metadata".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter(failBeforeActivation).scoped(ALPHA).write(
                new FileWrite(new FilePath("/retry.txt"), content, "text/plain")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("injected metadata failure");
        assertThat(authority.activeFiles(ALPHA.organizationRef(), ALPHA.spaceRef())).isEmpty();
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).hasSize(1);

        var recovered = adapter(authority);
        var stored = recovered.scoped(ALPHA).write(
                new FileWrite(new FilePath("/retry.txt"), content, "text/plain"));
        assertThat(recovered.scoped(ALPHA).read(stored.id()).bytes()).isEqualTo(content);
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).hasSize(1);

        var orphan = new BlobReference("v1/orphan/cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
        byte[] orphanBytes = {7, 8, 9};
        blobs.put(blobScope(ALPHA), orphan, orphanBytes, FilesystemBlobStore.digest(orphanBytes));
        var otherTenant = new BlobReference("v1/orphan/dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd");
        blobs.put(blobScope(BRAVO), otherTenant, orphanBytes, FilesystemBlobStore.digest(orphanBytes));

        var report = recovered.reconcile(ALPHA);
        assertThat(report.orphanBlobsDeleted()).isEqualTo(1);
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).hasSize(1);
        assertThat(blobs.inventory(blobScope(BRAVO), 10)).containsExactly(otherTenant);
    }

    @Test
    void metadataBlobMismatchAndCrossTenantIdsFailClosed() throws Exception {
        var nativeAdapter = adapter(authority);
        FilesProviderPort alpha = nativeAdapter.scoped(ALPHA);
        var stored = alpha.write(new FileWrite(new FilePath("/private.txt"),
                "alpha".getBytes(StandardCharsets.UTF_8), "text/plain"));

        assertThatThrownBy(() -> nativeAdapter.scoped(BRAVO).read(stored.id()))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("file-not-found");

        CanonicalFileRecord metadata = authority
                .findByPath(ALPHA.organizationRef(), ALPHA.spaceRef(), new FilePath("/private.txt"))
                .orElseThrow();
        Path blobPath = blobs.resolvedPathForTest(blobScope(ALPHA), new BlobReference(metadata.storageReference()));
        Files.write(blobPath, "tampered".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> alpha.read(stored.id()))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-metadata-blob-mismatch");
        assertThat(nativeAdapter.reconcile(ALPHA).inconsistentMetadataRecords()).isEqualTo(1);
    }

    @Test
    void movePreservesIdentityAndDeleteRejectsAStaleVersion() {
        FilesProviderPort scoped = adapter(authority).scoped(ALPHA);
        var created = scoped.write(new FileWrite(new FilePath("/draft.txt"),
                "draft".getBytes(StandardCharsets.UTF_8), "text/plain"));
        FileVersion originalVersion = scoped.find(new FilePath("/draft.txt")).orElseThrow().version();

        var moved = scoped.move(new FilePath("/draft.txt"), new FilePath("/final.txt"), false);

        assertThat(moved.id()).isEqualTo(created.id());
        assertThat(scoped.find(new FilePath("/draft.txt"))).isEmpty();
        assertThat(scoped.read(created.id()).bytes()).isEqualTo("draft".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> scoped.delete(new FilePath("/final.txt"), originalVersion))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-precondition-failed");
    }

    @Test
    void overwriteCopyDoesNotDeleteAnIdenticalRetainedBlob() {
        FilesProviderPort scoped = adapter(authority).scoped(ALPHA);
        byte[] content = "same content".getBytes(StandardCharsets.UTF_8);
        scoped.write(new FileWrite(new FilePath("/source.txt"), content, "text/plain"));
        scoped.write(new FileWrite(new FilePath("/destination.txt"), content, "text/plain"));

        var copied = scoped.copy(new FilePath("/source.txt"), new FilePath("/destination.txt"), true);

        assertThat(scoped.read(copied.id()).bytes()).isEqualTo(content);
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).hasSize(2);
    }

    @Test
    void unscopedNativeDataOperationsFailClosed() {
        assertThatThrownBy(() -> adapter(authority).find(new FilePath("/anything")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("require an organization/space scope");
    }

    private WeaveNativeFilesAdapter adapter(FilesAuthorityRepository repository) {
        return new WeaveNativeFilesAdapter(repository, blobs, Clock.fixed(NOW, ZoneOffset.UTC), 100);
    }

    private WeaveNativeFilesProperties properties() {
        return new WeaveNativeFilesProperties(
                "filesystem", temporaryDirectory.resolve("private-blobs"), 1024 * 1024, 100);
    }

    private BlobScope blobScope(FilesRequestScope scope) {
        return new BlobScope(scope.organizationRef(), scope.spaceRef());
    }

    private static final class FailNextSaveAuthority implements FilesAuthorityRepository {
        private final FilesAuthorityRepository delegate;
        private boolean fail = true;

        private FailNextSaveAuthority(FilesAuthorityRepository delegate) { this.delegate = delegate; }

        @Override public CanonicalFileRecord save(CanonicalFileRecord record) {
            if (fail) {
                fail = false;
                throw new IllegalStateException("injected metadata failure before activation");
            }
            return delegate.save(record);
        }
        @Override public Optional<CanonicalFileRecord> findByPath(String organizationRef, String spaceRef, FilePath path) { return delegate.findByPath(organizationRef, spaceRef, path); }
        @Override public Optional<CanonicalFileRecord> findById(String organizationRef, String spaceRef, FileId id) { return delegate.findById(organizationRef, spaceRef, id); }
        @Override public List<CanonicalFileRecord> activeFiles(String organizationRef, String spaceRef) { return delegate.activeFiles(organizationRef, spaceRef); }
        @Override public List<CanonicalFileRecord> replace(List<CanonicalFileRecord> tombstones, List<CanonicalFileRecord> activations) { return delegate.replace(tombstones, activations); }
        @Override public CanonicalFileRecord move(String organizationRef, String spaceRef, FileId id, FilePath expectedPath, FilePath destination, Instant movedAt) { return delegate.move(organizationRef, spaceRef, id, expectedPath, destination, movedAt); }
        @Override public FileLockRecord acquireLock(FileLockRecord requested, Instant now) { return delegate.acquireLock(requested, now); }
        @Override public Optional<FileLockRecord> activeLock(String organizationRef, String spaceRef, FilePath path, Instant now) { return delegate.activeLock(organizationRef, spaceRef, path, now); }
        @Override public List<FileLockRecord> activeLocks(String organizationRef, String spaceRef, Instant now) { return delegate.activeLocks(organizationRef, spaceRef, now); }
        @Override public void releaseLock(String organizationRef, String spaceRef, FilePath path, String tokenDigest, String ownerRef, Instant now) { delegate.releaseLock(organizationRef, spaceRef, path, tokenDigest, ownerRef, now); }
        @Override public void moveLock(String organizationRef, String spaceRef, FilePath source, FilePath destination, String tokenDigest, String ownerRef, Instant now) { delegate.moveLock(organizationRef, spaceRef, source, destination, tokenDigest, ownerRef, now); }
    }
}

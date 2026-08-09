package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.files.domain.FilesDomain.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileCopy;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileMove;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilesRequestScope;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
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

    private static final Instant NOW = Instant.parse("2026-08-09T08:00:00Z");
    private static final FilesRequestScope ALPHA = new FilesRequestScope("org:alpha", "space:home");
    private static final FilesRequestScope BETA = new FilesRequestScope("org:beta", "space:home");

    @TempDir
    Path temporaryDirectory;

    private InMemoryFilesAuthorityRepository authority;
    private FilesystemBlobStore blobs;

    @BeforeEach
    void setUp() {
        authority = new InMemoryFilesAuthorityRepository();
        blobs = new FilesystemBlobStore(properties());
    }

    @Test
    void writeReadAndRestartKeepCanonicalMetadataAndBlobContent() {
        byte[] content = "native file".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var first = adapter(authority).scoped(ALPHA);

        CanonicalFileRecord written = first.write(new FileWrite(new FilePath("/docs/readme.txt"), content, "text/plain"));
        var restarted = new WeaveNativeFilesAdapter(authority, new FilesystemBlobStore(properties()), Clock.fixed(NOW, ZoneOffset.UTC), 100).scoped(ALPHA);

        assertThat(restarted.read(written.id()).bytes()).isEqualTo(content);
        assertThat(restarted.find(new FilePath("/docs/readme.txt"))).contains(written.id());
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).hasSize(1);
    }

    @Test
    void tenantScopesCannotReadEachOthersCanonicalFileIds() {
        var alpha = adapter(authority).scoped(ALPHA);
        var beta = adapter(authority).scoped(BETA);
        CanonicalFileRecord written = alpha.write(new FileWrite(new FilePath("/private.txt"), new byte[] {1, 2}, "text/plain"));

        assertThatThrownBy(() -> beta.read(written.id()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void metadataFailureDoesNotActivateAnUncommittedFile() {
        byte[] content = "pending".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var failing = new WeaveNativeFilesAdapter(
                new FailNextSaveAuthority(authority),
                blobs,
                Clock.fixed(NOW, ZoneOffset.UTC),
                100).scoped(ALPHA);

        assertThatThrownBy(() -> failing.write(new FileWrite(new FilePath("/pending.txt"), content, "text/plain")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("injected metadata failure");
        assertThat(authority.findByPath(ALPHA, new FilePath("/pending.txt"))).isEmpty();
    }

    @Test
    void moveAndCopyKeepCanonicalBlobReferences() {
        byte[] content = "portable".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var scoped = adapter(authority).scoped(ALPHA);
        CanonicalFileRecord source = scoped.write(new FileWrite(new FilePath("/source.txt"), content, "text/plain"));

        CanonicalFileRecord moved = scoped.move(new FileMove(source.id(), new FilePath("/moved.txt"), false));
        CanonicalFileRecord copied = scoped.copy(new FileCopy(moved.id(), new FilePath("/copy.txt"), false));

        assertThat(scoped.read(moved.id()).bytes()).isEqualTo(content);
        assertThat(scoped.read(copied.id()).bytes()).isEqualTo(content);
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).hasSize(1);
    }

    @Test
    void replacingDestinationPublishesNewCanonicalVersionWithoutBlobCorruption() {
        byte[] original = "source".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] destination = "destination".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var scoped = adapter(authority).scoped(ALPHA);
        scoped.write(new FileWrite(new FilePath("/source.txt"), original, "text/plain"));
        scoped.write(new FileWrite(new FilePath("/destination.txt"), destination, "text/plain"));

        var copied = scoped.copy(new FilePath("/source.txt"), new FilePath("/destination.txt"), true);

        assertThat(scoped.read(copied.id()).bytes()).isEqualTo(original);
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
        return new WeaveNativeFilesProperties(temporaryDirectory.resolve("private-blobs"), 1024 * 1024, 100);
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

        @Override public Optional<CanonicalFileRecord> findById(FilesRequestScope scope, FileId id) { return delegate.findById(scope, id); }
        @Override public Optional<CanonicalFileRecord> findByPath(FilesRequestScope scope, FilePath path) { return delegate.findByPath(scope, path); }
        @Override public List<CanonicalFileRecord> list(FilesRequestScope scope, FilePath parent) { return delegate.list(scope, parent); }
        @Override public void delete(FilesRequestScope scope, FileId id) { delegate.delete(scope, id); }
    }
}

package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.adapter.FilesAuthorityJpaTestFactory;
import com.massimotter.weave.backend.files.application.CanonicalFilesQueries;
import com.massimotter.weave.backend.files.application.FilesDigests;
import com.massimotter.weave.backend.files.application.FilesScope;
import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesProviderPort.FilesRequestScope;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.files.port.StoredFileRecord.BlobBinding;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

/** Proves that the native runtime has no mutation path outside canonical Files use cases. */
class CanonicalNativeFilesCompositionTest {

    private static final Instant NOW = Instant.parse("2026-08-19T02:00:00Z");
    private static final FilesRequestScope SCOPE =
            new FilesRequestScope("org:canonical", "space:files", 7);

    @TempDir
    Path temporaryDirectory;

    private FilesystemBlobStore blobs;
    private FilesAuthorityRepository authority;
    private WeaveNativeFilesAdapter provider;

    @BeforeEach
    void setUp() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("canonical_native_files");
        authority = FilesAuthorityJpaTestFactory.create(dataSource);
        WeaveNativeFilesProperties properties = new WeaveNativeFilesProperties(
                temporaryDirectory.resolve("private-blobs"),
                1024 * 1024,
                100);
        blobs = new FilesystemBlobStore(properties);
        provider = new WeaveNativeFilesAdapter(
                authority,
                blobs,
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties.reconciliationLimit());
    }

    @AfterEach
    void closeBlobStore() {
        blobs.closeOperator();
    }

    @Test
    void everyCurrentFilesOperationRoutesThroughCanonicalUseCases() {
        FilesProviderPort files = provider.scoped(SCOPE);
        files.createCollection(new FilePath("/docs"));
        byte[] replacementContent = "replacement canonical content".getBytes(StandardCharsets.UTF_8);
        var replacement = seed(
                new FilePath("/docs/readme.txt"), replacementContent, "text/markdown");
        var copied = files.copy(
                new FilePath("/docs/readme.txt"),
                new FilePath("/docs/copy.txt"),
                false);
        var moved = files.move(
                new FilePath("/docs/copy.txt"),
                new FilePath("/moved.txt"),
                false);

        assertThat(replacement.mediaType()).isEqualTo("text/markdown");
        assertThat(read(replacement.id())).isEqualTo(replacementContent);
        assertThat(moved.id()).isEqualTo(copied.id());
        assertThat(read(moved.id())).isEqualTo(replacementContent);

        files.delete(new FilePath("/moved.txt"), null);
        assertThat(files.find(new FilePath("/moved.txt"))).isEmpty();

        var reconciliation = provider.reconcile(SCOPE);
        assertThat(reconciliation.inconsistentMetadataRecords()).isZero();
        assertThat(reconciliation.orphanBlobsDeleted()).isZero();
        assertThat(read(replacement.id())).isEqualTo(replacementContent);
    }

    @Test
    void canonicalFailuresRetainExistingSupportSafeBoundaryCodes() {
        FilesProviderPort files = provider.scoped(SCOPE);

        assertThatThrownBy(() -> files.createCollection(new FilePath("/missing/readme")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.code()).isEqualTo("files-native-parent-missing");
                    assertThat(exception.details()).containsEntry("diagnosticsRedacted", true);
                });

        assertThatThrownBy(() -> files.copy(
                new FilePath("/missing.txt"),
                new FilePath("/copy.txt"),
                false))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.code()).isEqualTo("file-not-found");
                });

        files.createCollection(new FilePath("/tree"));
        assertThatThrownBy(() -> files.move(
                new FilePath("/tree"),
                new FilePath("/tree/nested"),
                false))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.code()).isEqualTo("files-native-tree-conflict");
                });
    }

    @Test
    void unscopedProviderStillFailsClosed() {
        assertThatThrownBy(() -> provider.find(new FilePath("/anything")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception ->
                        assertThat(exception.code()).isEqualTo("files-native-scope-required"));
    }

    private FileObject seed(FilePath path, byte[] content, String mediaType) {
        String digest = FilesDigests.sha256(content);
        FileId id = new FileId("file:" + digest.substring("sha256:".length()));
        BlobReference reference = new BlobReference(
                "v1/test/" + digest.substring("sha256:".length()));
        blobs.putStream(
                new BlobScope(SCOPE.organizationRef(), SCOPE.spaceRef()),
                reference,
                new ByteArrayInputStream(content),
                content.length,
                digest);
        FileObject item = new FileObject(
                id, path, Kind.FILE, content.length, mediaType, NOW, false);
        authority.activate(new StoredFileRecord(
                new CanonicalFileRecord(
                        SCOPE.organizationRef(),
                        SCOPE.spaceRef(),
                        item,
                        new FileVersion(digest),
                        digest,
                        SCOPE.providerBindingRevision(),
                        Lifecycle.ACTIVE,
                        NOW),
                new BlobBinding(reference.value())));
        return item;
    }

    private byte[] read(FileId id) {
        var queries = new CanonicalFilesQueries(authority, blobs, 100);
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        queries.openRead(
                        new FilesScope(SCOPE.organizationRef(), SCOPE.spaceRef()), id)
                .transferTo(target);
        return target.toByteArray();
    }
}

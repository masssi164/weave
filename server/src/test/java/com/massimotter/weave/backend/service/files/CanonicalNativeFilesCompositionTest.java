package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.adapter.FilesAuthorityJpaTestFactory;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesProviderPort.FilesRequestScope;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
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
    private WeaveNativeFilesAdapter provider;

    @BeforeEach
    void setUp() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("canonical_native_files");
        FilesAuthorityRepository authority = FilesAuthorityJpaTestFactory.create(dataSource);
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
        byte[] firstContent = "first canonical content".getBytes(StandardCharsets.UTF_8);
        byte[] replacementContent = "replacement canonical content".getBytes(StandardCharsets.UTF_8);

        var first = files.write(new FileWrite(
                new FilePath("/docs/readme.txt"),
                firstContent,
                "text/plain"));
        var replacement = files.write(new FileWrite(
                new FilePath("/docs/readme.txt"),
                replacementContent,
                "text/markdown"));
        var copied = files.copy(
                new FilePath("/docs/readme.txt"),
                new FilePath("/docs/copy.txt"),
                false);
        var moved = files.move(
                new FilePath("/docs/copy.txt"),
                new FilePath("/moved.txt"),
                false);

        assertThat(replacement.id()).isEqualTo(first.id());
        assertThat(replacement.mediaType()).isEqualTo("text/markdown");
        assertThat(files.read(replacement.id()).bytes()).isEqualTo(replacementContent);
        assertThat(moved.id()).isEqualTo(copied.id());
        assertThat(files.read(moved.id()).bytes()).isEqualTo(replacementContent);

        files.delete(new FilePath("/moved.txt"), null);
        assertThat(files.find(new FilePath("/moved.txt"))).isEmpty();

        var reconciliation = provider.reconcile(SCOPE);
        assertThat(reconciliation.inconsistentMetadataRecords()).isZero();
        assertThat(reconciliation.orphanBlobsDeleted()).isGreaterThanOrEqualTo(1);
        assertThat(files.read(replacement.id()).bytes()).isEqualTo(replacementContent);
    }

    @Test
    void canonicalFailuresRetainExistingSupportSafeBoundaryCodes() {
        FilesProviderPort files = provider.scoped(SCOPE);

        assertThatThrownBy(() -> files.write(new FileWrite(
                new FilePath("/missing/readme.txt"),
                new byte[] {1},
                "application/octet-stream")))
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
}

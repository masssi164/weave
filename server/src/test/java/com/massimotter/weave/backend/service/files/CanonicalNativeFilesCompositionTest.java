package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.adapter.FilesAuthorityJpaTestFactory;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
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

class CanonicalNativeFilesCompositionTest {

    private static final Instant NOW = Instant.parse("2026-08-19T02:00:00Z");
    private static final FilesRequestScope SCOPE =
            new FilesRequestScope("org:canonical", "space:files", 7);

    @TempDir
    Path temporaryDirectory;

    private FilesystemBlobStore blobs;
    private CanonicalNativeFilesComposition composition;

    @BeforeEach
    void setUp() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("canonical_native_files");
        FilesAuthorityRepository authority = FilesAuthorityJpaTestFactory.create(dataSource);
        WeaveNativeFilesProperties properties = new WeaveNativeFilesProperties(
                temporaryDirectory.resolve("private-blobs"),
                1024 * 1024,
                100);
        blobs = new FilesystemBlobStore(properties);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        WeaveNativeFilesAdapter transitional = new WeaveNativeFilesAdapter(
                authority,
                blobs,
                clock,
                properties.reconciliationLimit());
        composition = new CanonicalNativeFilesComposition(
                transitional,
                authority,
                blobs,
                clock);
    }

    @AfterEach
    void closeBlobStore() {
        blobs.closeOperator();
    }

    @Test
    void createWriteAndReplaceUseCanonicalCommandsWhileReadsAndCopyRemainReachable() {
        FilesProviderPort files = composition.scoped(SCOPE);
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

        assertThat(replacement.id()).isEqualTo(first.id());
        assertThat(replacement.mediaType()).isEqualTo("text/markdown");
        assertThat(files.read(replacement.id()).bytes()).isEqualTo(replacementContent);
        assertThat(files.read(copied.id()).bytes()).isEqualTo(replacementContent);
        assertThat(blobs.inventory(
                new com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope(
                        SCOPE.organizationRef(), SCOPE.spaceRef()),
                10))
                .hasSize(3);
    }

    @Test
    void commandFailuresRetainExistingSupportSafeNativeErrorCodes() {
        FilesProviderPort files = composition.scoped(SCOPE);

        assertThatThrownBy(() -> files.write(new FileWrite(
                new FilePath("/missing/readme.txt"),
                new byte[] {1},
                "application/octet-stream")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(exception.code()).isEqualTo("files-native-parent-missing");
                    assertThat(exception.details()).containsEntry("diagnosticsRedacted", true);
                });
    }

    @Test
    void unscopedCompositionStillFailsClosedThroughTheEstablishedBoundary() {
        assertThatThrownBy(() -> composition.find(new FilePath("/anything")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception ->
                        assertThat(exception.code()).isEqualTo("files-native-scope-required"));
    }
}

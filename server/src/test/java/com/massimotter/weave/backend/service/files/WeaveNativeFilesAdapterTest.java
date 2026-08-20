package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.adapter.FilesAuthorityJpaTestFactory;
import com.massimotter.weave.backend.files.application.CanonicalFilesQueries;
import com.massimotter.weave.backend.files.application.FilesDigests;
import com.massimotter.weave.backend.files.application.FilesMutationTargetCodec;
import com.massimotter.weave.backend.files.application.FilesScope;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository;
import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesProviderPort.FilesRequestScope;
import com.massimotter.weave.backend.files.port.FilesStreamingCapabilityProfile;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.ContentProfile;
import com.massimotter.weave.backend.files.port.NativeFilesContentStore;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.files.port.StoredFileRecord.BlobBinding;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
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
        var written = seed(authority, ALPHA, new FilePath("/readme.txt"), content, "text/plain");
        FilesAuthorityRepository restartedAuthority = FilesAuthorityJpaTestFactory.create(dataSource);
        FilesProviderPort restarted = adapter(restartedAuthority).scoped(ALPHA);

        assertThat(read(restartedAuthority, ALPHA, written.id())).isEqualTo(content);
        assertThat(restarted.find(new FilePath("/readme.txt"))).isPresent();
        assertThat(blobs.inventory(blobScope(ALPHA), 10)).hasSize(1);
    }

    @Test
    void tenantScopesCannotReadEachOthersCanonicalFileIds() {
        FilesProviderPort beta = adapter(authority).scoped(BETA);
        seed(authority, ALPHA, new FilePath("/private.txt"), new byte[] {1, 2}, "text/plain");

        assertThat(beta.find(new FilePath("/private.txt"))).isEmpty();
    }

    @Test
    void readinessAndConformanceRequireTheObservedStreamingAuthority() {
        NativeFilesContentStore contentStore = mock(NativeFilesContentStore.class);
        when(contentStore.contentProfile()).thenReturn(new ContentProfile(1024, 65_536, 2, 2));
        WeaveNativeFilesAdapter adapter = new WeaveNativeFilesAdapter(
                authority,
                blobs,
                Clock.fixed(NOW, ZoneOffset.UTC),
                100,
                null,
                null,
                contentStore);

        assertThat(adapter.readiness().available()).isTrue();
        assertThat(adapter.contentProfile()).isEqualTo(new ContentProfile(1024, 65_536, 2, 2));
        assertThatCode(adapter::requireStreamingReady).doesNotThrowAnyException();
        assertThat(FilesStreamingCapabilityProfile.observe(adapter, adapter).qualified()).isTrue();
        assertThat(FilesStreamingCapabilityProfile.observe(adapter, adapter).limits())
                .isEqualTo(new ContentProfile(1024, 65_536, 2, 2));
        assertThat(adapter.conformanceProfile().supportedOperations())
                .contains(
                        "files.content_streaming_read",
                        "files.content_streaming_write");

        org.mockito.Mockito.doThrow(new IllegalStateException("authority generation changed"))
                .when(contentStore)
                .requireStreamingReady();
        assertThat(adapter.readiness().available()).isFalse();
        assertThat(adapter.readiness().supportSafeCode()).isEqualTo("files-native-streaming-not-ready");
        assertThat(FilesStreamingCapabilityProfile.observe(adapter, adapter).qualified()).isFalse();
    }

    @Test
    void corruptInspectionLatchesReadinessAcrossAnUnrelatedHealthyVerification() {
        NativeFilesContentStore contentStore = mock(NativeFilesContentStore.class);
        when(contentStore.contentProfile()).thenReturn(new ContentProfile(1024, 65_536, 2, 2));
        var expectedEgress = mock(FilesStreamingContentPort.Egress.class);
        when(contentStore.verify(any())).thenReturn(expectedEgress);
        WeaveNativeFilesAdapter adapter = new WeaveNativeFilesAdapter(
                authority,
                blobs,
                Clock.fixed(NOW, ZoneOffset.UTC),
                100,
                null,
                null,
                contentStore);
        FilePath corruptPath = new FilePath("/corrupt-a.txt");
        seedMetadataWithoutBinding(corruptPath, "corrupt".getBytes(), "text/plain");
        FileObject healthy = seed(
                authority,
                ALPHA,
                new FilePath("/healthy-b.txt"),
                "healthy".getBytes(),
                "text/plain");
        FilesStreamingContentPort streaming =
                (FilesStreamingContentPort) adapter.scoped(ALPHA);

        assertThat(adapter.readiness().available()).isTrue();
        assertThatThrownBy(() -> streaming.inspect(corruptPath))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status())
                            .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo("file-content-integrity-unavailable");
                });
        assertThat(streaming.verify(streaming.inspect(healthy.path())))
                .isSameAs(expectedEgress);

        assertThat(adapter.readiness().available()).isFalse();
        assertThat(adapter.readiness().supportSafeCode())
                .isEqualTo("files-native-streaming-not-ready");
    }

    @Test
    void cleanReconciliationIsTheOnlyProofThatClearsLatchedIntegrityDegradation() {
        NativeFilesContentStore contentStore = mock(NativeFilesContentStore.class);
        when(contentStore.contentProfile()).thenReturn(new ContentProfile(1024, 65_536, 2, 2));
        FileObject healthy = seed(
                authority,
                ALPHA,
                new FilePath("/healthy.txt"),
                "healthy".getBytes(),
                "text/plain");
        var unavailable = new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "file-content-integrity-unavailable",
                "The native Files content could not be verified.",
                Map.of("module", "files", "diagnosticsRedacted", true));
        when(contentStore.verify(any()))
                .thenThrow(unavailable)
                .thenReturn(mock(FilesStreamingContentPort.Egress.class));
        WeaveNativeFilesAdapter adapter = new WeaveNativeFilesAdapter(
                authority,
                blobs,
                Clock.fixed(NOW, ZoneOffset.UTC),
                100,
                null,
                null,
                contentStore);
        FilesStreamingContentPort streaming =
                (FilesStreamingContentPort) adapter.scoped(ALPHA);

        assertThatThrownBy(() -> streaming.verify(streaming.inspect(healthy.path())))
                .isSameAs(unavailable);
        assertThat(adapter.readiness().available()).isFalse();

        assertThat(adapter.reconcile(ALPHA).inconsistentMetadataRecords()).isZero();
        assertThat(adapter.readiness().available()).isTrue();
        assertThat(streaming.verify(streaming.inspect(healthy.path()))).isNotNull();
    }

    @Test
    void reconciliationUsesDurableMutationBindingsAsBlobProtection() {
        BlobReference protectedBinding = new BlobReference("v1/plans/created/result");
        BlobReference terminalBinding = new BlobReference("v1/plans/terminal/result");
        byte[] protectedContent = "protected".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] terminalContent = "terminal".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        blobs.putStream(
                blobScope(ALPHA),
                protectedBinding,
                new ByteArrayInputStream(protectedContent),
                protectedContent.length,
                FilesDigests.sha256(protectedContent));
        blobs.putStream(
                blobScope(ALPHA),
                terminalBinding,
                new ByteArrayInputStream(terminalContent),
                terminalContent.length,
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
        seed(authority, ALPHA, new FilePath("/source.txt"), content, "text/plain");

        var moved = scoped.move(new FilePath("/source.txt"), new FilePath("/moved.txt"), false);
        var copied = scoped.copy(new FilePath("/moved.txt"), new FilePath("/copy.txt"), false);

        assertThat(read(authority, ALPHA, moved.id())).isEqualTo(content);
        assertThat(read(authority, ALPHA, copied.id())).isEqualTo(content);
    }

    @Test
    void replacingDestinationPublishesNewCanonicalVersionWithoutBlobCorruption() {
        byte[] original = "source".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] destination = "destination".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FilesProviderPort scoped = adapter(authority).scoped(ALPHA);
        seed(authority, ALPHA, new FilePath("/source.txt"), original, "text/plain");
        seed(authority, ALPHA, new FilePath("/destination.txt"), destination, "text/plain");

        var copied = scoped.copy(new FilePath("/source.txt"), new FilePath("/destination.txt"), true);

        assertThat(read(authority, ALPHA, copied.id())).isEqualTo(original);
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

    private FileObject seed(
            FilesAuthorityRepository repository,
            FilesRequestScope scope,
            FilePath path,
            byte[] content,
            String mediaType) {
        String digest = FilesDigests.sha256(content);
        FileObject item = new FileObject(
                new FileId("file:" + digest.substring("sha256:".length())),
                path,
                Kind.FILE,
                content.length,
                mediaType,
                NOW,
                false);
        BlobReference reference = new BlobReference(
                "v1/test/" + digest.substring("sha256:".length()));
        blobs.putStream(
                blobScope(scope),
                reference,
                new ByteArrayInputStream(content),
                content.length,
                digest);
        repository.activate(new StoredFileRecord(
                metadata(scope, item, digest),
                new BlobBinding(reference.value())));
        return item;
    }

    private void seedMetadataWithoutBinding(
            FilePath path,
            byte[] content,
            String mediaType) {
        String digest = FilesDigests.sha256(content);
        FileObject item = new FileObject(
                new FileId("file:corrupt-a"),
                path,
                Kind.FILE,
                content.length,
                mediaType,
                NOW,
                false);
        authority.activate(new StoredFileRecord(metadata(ALPHA, item, digest), null));
    }

    private CanonicalFileRecord metadata(
            FilesRequestScope scope,
            FileObject item,
            String digest) {
        return new CanonicalFileRecord(
                scope.organizationRef(),
                scope.spaceRef(),
                item,
                new FileVersion(digest),
                digest,
                scope.providerBindingRevision(),
                Lifecycle.ACTIVE,
                NOW);
    }

    private byte[] read(
            FilesAuthorityRepository repository,
            FilesRequestScope scope,
            FileId id) {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        new CanonicalFilesQueries(repository, blobs, 100)
                .openRead(
                        new FilesScope(scope.organizationRef(), scope.spaceRef()),
                        id)
                .transferTo(target);
        return target.toByteArray();
    }
}

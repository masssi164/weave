package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReceipt;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.BlobStorePort.ContentTargetUnavailableException;
import com.massimotter.weave.backend.schema.NativeFilesVolumeAuthority;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemBlobStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyPublishesAndReadsTheSameOpaqueBlobAfterRestart() throws Exception {
        var scope = new BlobScope("org:alpha", "space:home");
        var reference = new BlobReference("v1/file/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        byte[] content = "native files".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = FilesystemBlobStore.digest(content);

        put(store(1024), scope, reference, content, digest);
        var restarted = store(1024);

        assertThat(read(restarted, scope, reference)).isEqualTo(content);
        assertThat(restarted.inventory(scope, 10)).containsExactly(reference);
        assertThat(put(restarted, scope, reference, content, digest).digest()).isEqualTo(digest);
        Path target = restarted.resolvedPathForTest(scope, reference);
        if (Files.getFileStore(target).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(target))
                    .isEqualTo(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            assertThat(Files.getPosixFilePermissions(target.getParent()))
                    .containsExactlyInAnyOrder(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE);
        }
    }

    @Test
    void streamsLargeContentWithoutWholeFilePortBuffering() {
        byte[] content = new byte[512 * 1024];
        for (int index = 0; index < content.length; index++) content[index] = (byte) (index % 251);
        var scope = new BlobScope("org:alpha", "space:streaming");
        var reference = new BlobReference("v1/file/cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
        String digest = FilesystemBlobStore.digest(content);
        var store = store(1024 * 1024);

        var receipt = store.putStream(scope, reference, new ByteArrayInputStream(content), content.length, digest);
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        store.readStream(scope, reference, target);

        assertThat(receipt.size()).isEqualTo(content.length);
        assertThat(receipt.digest()).isEqualTo(digest);
        assertThat(target.toByteArray()).isEqualTo(content);
    }

    @Test
    void preservesCallerTargetFailureAcrossTheStoredBlobReadBoundary() {
        byte[] content = "content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var scope = new BlobScope("org:alpha", "space:target-failure");
        var reference = new BlobReference(
                "v1/file/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        var store = store(1024);
        put(store, scope, reference, content, FilesystemBlobStore.digest(content));
        IOException targetFailure = new IOException("private target detail");

        assertThatThrownBy(() -> store.readStream(scope, reference, new OutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                        throw targetFailure;
                    }
                }))
                .isInstanceOf(ContentTargetUnavailableException.class)
                .hasCause(targetFailure);
    }

    @Test
    void rejectsStreamingSizeDigestAndBoundViolations() {
        var scope = new BlobScope("org:alpha", "space:streaming");
        var store = store(1024);
        byte[] content = new byte[512];
        Arrays.fill(content, (byte) 7);
        var sizeMismatch = new BlobReference("v1/file/dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd");
        var digestMismatch = new BlobReference("v1/file/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
        var overflow = new BlobReference("v1/file/ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

        assertThatThrownBy(() -> store.putStream(scope, sizeMismatch, new ByteArrayInputStream(content), content.length - 1, FilesystemBlobStore.digest(content)))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-blob-size-mismatch");
        assertThatThrownBy(() -> store.putStream(scope, digestMismatch, new ByteArrayInputStream(content), content.length, FilesystemBlobStore.digest(new byte[] {1})))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-blob-digest-mismatch");
        assertThatThrownBy(() -> store.putStream(scope, overflow, new ByteArrayInputStream(new byte[2048]), 2048, FilesystemBlobStore.digest(new byte[2048])))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-blob-too-large");
    }

    @Test
    void rejectsTraversalKeysDigestMismatchAndSymlinkSubstitution() throws Exception {
        assertThatThrownBy(() -> new BlobReference("../escape"))
                .isInstanceOf(IllegalArgumentException.class);

        var store = store(1024);
        var scope = new BlobScope("org:alpha", "space:home");
        var reference = new BlobReference("v1/file/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        assertThatThrownBy(() -> put(
                        store,
                        scope,
                        reference,
                        new byte[] {1},
                        FilesystemBlobStore.digest(new byte[] {2})))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-blob-digest-mismatch");

        Path target = store.resolvedPathForTest(scope, reference);
        Files.createDirectories(target.getParent());
        Path outside = temporaryDirectory.resolve("outside");
        Files.write(outside, new byte[] {9});
        Files.createSymbolicLink(target, outside);

        assertThatThrownBy(() -> read(store, scope, reference))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-path-containment-failed");
    }

    @Test
    void reservedVolumeMarkerIsOutsideBindingInventoryAndCleanup() throws Exception {
        Path root = temporaryDirectory.resolve("private-blobs");
        Files.createDirectories(root);
        Path marker = root.resolve(NativeFilesVolumeAuthority.MARKER_FILE_NAME);
        Files.writeString(marker, "adapter-private-authority-marker");
        var scope = new BlobScope("org:alpha", "space:home");
        var reference = new BlobReference(
                "v1/file/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        byte[] content = "payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var store = store(1024);

        assertThat(store.inventory(scope, 10)).isEmpty();
        put(store, scope, reference, content, FilesystemBlobStore.digest(content));
        assertThat(store.inventory(scope, 10)).containsExactly(reference);
        store.delete(scope, reference);

        assertThat(store.inventory(scope, 10)).isEmpty();
        assertThat(marker).hasContent("adapter-private-authority-marker");
        assertThatThrownBy(
                () -> new BlobReference(NativeFilesVolumeAuthority.MARKER_FILE_NAME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAStoredBlobThatGrowsBeyondTheBoundAfterStat() throws Exception {
        var scope = new BlobScope("org:alpha", "space:growing");
        var reference = new BlobReference(
                "v1/file/9999999999999999999999999999999999999999999999999999999999999999");
        AtomicBoolean grew = new AtomicBoolean();
        var store = store(
                1024,
                FilesystemBlobStore.DurabilitySync.system(),
                path -> {
                    if (grew.compareAndSet(false, true)) {
                        Files.write(path, new byte[] {1}, StandardOpenOption.APPEND);
                    }
                });
        Path target = store.resolvedPathForTest(scope, reference);
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[1024]);

        assertThatThrownBy(() -> store.readStream(
                        scope,
                        reference,
                        java.io.OutputStream.nullOutputStream()))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-blob-read-failed");
        assertThat(Files.size(target)).isEqualTo(1025);
        assertThatThrownBy(() -> store.receipt(scope, reference))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-blob-size-invalid");
    }

    @Test
    void propagatesFileAndDirectoryDurabilitySyncFailures() throws Exception {
        var scope = new BlobScope("org:alpha", "space:durability");
        byte[] content = "durable payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = FilesystemBlobStore.digest(content);
        var fileFailure = new BlobReference(
                "v1/file/7777777777777777777777777777777777777777777777777777777777777777");
        var directoryFailure = new BlobReference(
                "v1/file/8888888888888888888888888888888888888888888888888888888888888888");
        var layout = store(1024);
        Path fileFailureTarget = layout.resolvedPathForTest(scope, fileFailure);
        Path directoryFailureTarget = layout.resolvedPathForTest(scope, directoryFailure);
        Files.createDirectories(fileFailureTarget.getParent());
        Files.createDirectories(directoryFailureTarget.getParent());
        Files.createDirectories(layout.stagingPathForTest("layout.pending").getParent());

        assertThatThrownBy(() -> put(store(
                        1024,
                        path -> {
                            if (!Files.isDirectory(path)) {
                                throw new java.io.IOException("forced file sync failure");
                            }
                        },
                        ignored -> { }), scope, fileFailure, content, digest))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-blob-write-failed");

        assertThatThrownBy(() -> put(store(
                        1024,
                        path -> {
                            if (path.equals(directoryFailureTarget.getParent())) {
                                throw new java.io.IOException("forced directory sync failure");
                            }
                        },
                        ignored -> { }), scope, directoryFailure, content, digest))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-blob-write-failed");

        var recovered = store(1024);
        assertThat(put(recovered, scope, fileFailure, content, digest).digest())
                .isEqualTo(digest);
        assertThat(put(recovered, scope, directoryFailure, content, digest).digest())
                .isEqualTo(digest);
    }

    @Test
    void retryReprovesEveryExistingAncestorAfterAncestorSyncFailure() {
        var scope = new BlobScope("org:alpha", "space:ancestor-retry");
        var reference = new BlobReference(
                "v1/file/4444444444444444444444444444444444444444444444444444444444444444");
        byte[] content = "ancestor retry".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = FilesystemBlobStore.digest(content);
        var layout = store(1024);
        Path ancestor = layout.resolvedPathForTest(scope, reference)
                .getParent()
                .getParent();

        assertThatThrownBy(() -> put(store(
                        1024,
                        path -> {
                            if (path.equals(ancestor)) {
                                throw new java.io.IOException("forced ancestor sync failure");
                            }
                        },
                        ignored -> { }), scope, reference, content, digest))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-blob-write-failed");
        assertThat(ancestor).isDirectory();

        java.util.Set<Path> synced = new java.util.HashSet<>();
        var recovered = store(1024, synced::add, ignored -> { });
        assertThat(put(recovered, scope, reference, content, digest).digest())
                .isEqualTo(digest);
        assertThat(synced).contains(ancestor, ancestor.getParent());
    }

    @Test
    void existingReceiptReprovesStagingDirectoryDurabilityBeforePlanRetry() {
        var scope = new BlobScope("org:alpha", "space:receipt-staging-retry");
        var reference = new BlobReference(
                "v1/file/3333333333333333333333333333333333333333333333333333333333333333");
        byte[] content = "receipt staging retry"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = FilesystemBlobStore.digest(content);
        var published = store(1024);
        put(published, scope, reference, content, digest);
        Path staging = published.stagingPathForTest("placeholder").getParent();

        assertThatThrownBy(() -> store(
                        1024,
                        path -> {
                            if (path.equals(staging)) {
                                throw new java.io.IOException("forced staging retry sync failure");
                            }
                        },
                        ignored -> { })
                .receipt(scope, reference))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-blob-read-failed");

        java.util.Set<Path> synced = new java.util.HashSet<>();
        assertThat(store(1024, synced::add, ignored -> { }).receipt(scope, reference))
                .isPresent();
        assertThat(synced).contains(staging);
    }

    @Test
    void deleteRequiresDurableParentSyncAndARegularRetryReprovesAbsence() {
        var scope = new BlobScope("org:alpha", "space:delete-durability");
        var reference = new BlobReference(
                "v1/file/6666666666666666666666666666666666666666666666666666666666666666");
        byte[] content = "delete durably".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = FilesystemBlobStore.digest(content);
        var published = store(1024);
        put(published, scope, reference, content, digest);
        Path parent = published.resolvedPathForTest(scope, reference).getParent();

        assertThatThrownBy(() -> store(
                        1024,
                        path -> {
                            if (path.equals(parent)) {
                                throw new java.io.IOException("forced delete directory sync failure");
                            }
                        },
                        ignored -> { })
                .delete(scope, reference))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-blob-delete-failed");

        var recovered = store(1024);
        recovered.delete(scope, reference);
        assertThat(recovered.receipt(scope, reference)).isEmpty();
    }

    @Test
    void excludesActiveStagingAndScavengesOnlyUnlockedStaleEntries() throws Exception {
        var scope = new BlobScope("org:alpha", "space:staging");
        var store = store(1024);
        Path staleOwner = store.stagingPathForTest("stranded.lock");
        Path stale = store.stagingPathForTest("stranded.pending");
        Path activeOwner = store.stagingPathForTest("in-flight.lock");
        Path active = store.stagingPathForTest("in-flight.pending");
        Files.createDirectories(stale.getParent());
        Files.write(staleOwner, new byte[] {0});
        Files.write(stale, new byte[] {1});
        Files.write(activeOwner, new byte[] {0});
        Files.write(active, new byte[] {2});
        FileTime old = FileTime.from(Instant.now().minusSeconds(2 * 60 * 60));
        Files.setLastModifiedTime(staleOwner, old);
        Files.setLastModifiedTime(activeOwner, old);

        try (FileChannel activeChannel = FileChannel.open(
                        activeOwner,
                        StandardOpenOption.WRITE);
                var ignored = activeChannel.lock()) {
            assertThat(store.inventory(scope, 10)).isEmpty();
            assertThat(staleOwner).doesNotExist();
            assertThat(stale).doesNotExist();
            assertThat(activeOwner).exists();
            assertThat(active).exists();
        }

        assertThat(store.inventory(scope, 10)).isEmpty();
        assertThat(activeOwner).doesNotExist();
        assertThat(active).doesNotExist();
    }

    @Test
    void rejectsStagingNamespaceSymlinkWithoutCreatingOutsideDirectories() throws Exception {
        var scope = new BlobScope("org:alpha", "space:staging-symlink");
        var reference = new BlobReference(
                "v1/file/5555555555555555555555555555555555555555555555555555555555555555");
        byte[] content = "contained".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path root = temporaryDirectory.resolve("private-blobs");
        var store = store(1024);
        Path outside = temporaryDirectory.resolve("outside-staging");
        Files.createDirectories(outside);
        Files.createSymbolicLink(root.resolve(".weave-native-staging"), outside);

        assertThatThrownBy(() -> put(
                        store,
                        scope,
                        reference,
                        content,
                        FilesystemBlobStore.digest(content)))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-path-containment-failed");
        assertThat(outside.resolve("v1")).doesNotExist();
    }

    private BlobReceipt put(
            FilesystemBlobStore store,
            BlobScope scope,
            BlobReference reference,
            byte[] content,
            String digest) {
        return store.putStream(
                scope,
                reference,
                new ByteArrayInputStream(content),
                content.length,
                digest);
    }

    private byte[] read(
            FilesystemBlobStore store, BlobScope scope, BlobReference reference) {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        store.readStream(scope, reference, target);
        return target.toByteArray();
    }

    private FilesystemBlobStore store(long maximumBytes) {
        return new FilesystemBlobStore(new WeaveNativeFilesProperties(
                temporaryDirectory.resolve("private-blobs"), maximumBytes, 100));
    }

    private FilesystemBlobStore store(
            long maximumBytes,
            FilesystemBlobStore.DurabilitySync durabilitySync,
            FilesystemBlobStore.StoredReadObserver readObserver) {
        return new FilesystemBlobStore(
                new WeaveNativeFilesProperties(
                        temporaryDirectory.resolve("private-blobs"),
                        maximumBytes,
                        100),
                durabilitySync,
                readObserver);
    }
}

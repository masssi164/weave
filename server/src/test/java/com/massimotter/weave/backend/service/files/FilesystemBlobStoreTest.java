package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Set;
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

        store(1024).put(scope, reference, content, digest);
        var restarted = store(1024);

        assertThat(restarted.read(scope, reference)).isEqualTo(content);
        assertThat(restarted.inventory(scope, 10)).containsExactly(reference);
        assertThat(restarted.put(scope, reference, content, digest).digest()).isEqualTo(digest);
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
        assertThatThrownBy(() -> store.put(scope, reference, new byte[] {1}, FilesystemBlobStore.digest(new byte[] {2})))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-blob-digest-mismatch");

        Path target = store.resolvedPathForTest(scope, reference);
        Files.createDirectories(target.getParent());
        Path outside = temporaryDirectory.resolve("outside");
        Files.write(outside, new byte[] {9});
        Files.createSymbolicLink(target, outside);

        assertThatThrownBy(() -> store.read(scope, reference))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-path-containment-failed");
    }

    private FilesystemBlobStore store(long maximumBytes) {
        return new FilesystemBlobStore(new WeaveNativeFilesProperties(
                temporaryDirectory.resolve("private-blobs"), maximumBytes, 100));
    }
}

package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemBlobStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyPublishesAndReadsTheSameOpaqueBlobAfterRestart() {
        var scope = new BlobScope("org:alpha", "space:home");
        var reference = new BlobReference("v1/file/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        byte[] content = "native files".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = FilesystemBlobStore.digest(content);

        store().put(scope, reference, content, digest);
        var restarted = store();

        assertThat(restarted.read(scope, reference)).isEqualTo(content);
        assertThat(restarted.inventory(scope, 10)).containsExactly(reference);
        assertThat(restarted.put(scope, reference, content, digest).digest()).isEqualTo(digest);
        assertThat(restarted.inventory(scope, 10)).containsExactly(reference);
    }

    @Test
    void rejectsTraversalKeysDigestMismatchAndSymlinkSubstitution() throws Exception {
        assertThatThrownBy(() -> new BlobReference("../escape"))
                .isInstanceOf(IllegalArgumentException.class);

        var store = store();
        var scope = new BlobScope("org:alpha", "space:home");
        var reference = new BlobReference("v1/file/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        assertThatThrownBy(() -> store.put(scope, reference, new byte[] {1},
                FilesystemBlobStore.digest(new byte[] {2})))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-blob-digest-mismatch");

        Path target = store.resolvedPathForTest(scope, reference);
        Path outside = temporaryDirectory.resolve("outside");
        Files.write(outside, new byte[] {9});
        Files.createSymbolicLink(target, outside);

        assertThatThrownBy(() -> store.read(scope, reference))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-path-containment-failed");
    }

    private FilesystemBlobStore store() {
        return new FilesystemBlobStore(new WeaveNativeFilesProperties(
                "filesystem", temporaryDirectory.resolve("private-blobs"), 1024, 100));
    }
}

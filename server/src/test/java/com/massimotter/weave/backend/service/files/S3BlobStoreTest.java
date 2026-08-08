package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.massimotter.weave.backend.config.WeaveS3FilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

class S3BlobStoreTest {

    @Test
    void rejectsOversizedContentBeforeCallingTheRemoteStore() {
        S3Client client = mock(S3Client.class);
        S3BlobStore store = new S3BlobStore(configuredProperties(), client, 2);
        byte[] content = {1, 2, 3};

        assertThatThrownBy(() -> store.put(
                        new BlobScope("org:alpha", "space:home"),
                        new BlobReference("v1/file/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                        content,
                        FilesystemBlobStore.digest(content)))
                .isInstanceOf(ApiErrorException.class)
                .extracting(error -> ((ApiErrorException) error).code())
                .isEqualTo("files-native-blob-too-large");
        verifyNoInteractions(client);
    }

    private WeaveS3FilesProperties configuredProperties() {
        WeaveS3FilesProperties properties = new WeaveS3FilesProperties();
        properties.setEnabled(true);
        properties.setEndpoint(URI.create("http://127.0.0.1:9000"));
        properties.setBucket("weave-test");
        properties.setAccessKey("test-access-key");
        properties.setSecretKey("test-secret-key");
        return properties;
    }
}

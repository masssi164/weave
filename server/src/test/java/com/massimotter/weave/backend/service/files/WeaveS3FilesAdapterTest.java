package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.config.WeaveS3FilesProperties;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

class WeaveS3FilesAdapterTest {

    @Test
    void writesCanonicalPathToPrivateBucketAndReturnsProviderNeutralObject() {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag-write").build());
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .eTag("etag-write")
                .contentLength(4L)
                .contentType("text/plain")
                .lastModified(Instant.parse("2026-07-22T03:00:00Z"))
                .build());
        WeaveS3FilesAdapter adapter = new WeaveS3FilesAdapter(properties(), client);

        var stored = adapter.write(new FileWrite(
                new FilePath("/Team/notes.txt"), "core".getBytes(), "text/plain"));

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("weave-files");
        assertThat(request.getValue().key()).isEqualTo("Team/notes.txt");
        assertThat(stored.path().value()).isEqualTo("/Team/notes.txt");
        assertThat(stored.kind()).isEqualTo(Kind.FILE);
        assertThat(stored.id().value()).startsWith("files:").doesNotContain("weave-files", "minio");
    }

    @Test
    void listsOnlyDirectChildrenAndDeclaresExplicitPortabilityLimits() {
        S3Client client = mock(S3Client.class);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .commonPrefixes(CommonPrefix.builder().prefix("Team/Design/").build())
                .contents(List.of(
                        S3Object.builder().key("Team/readme.md").size(12L).eTag("etag-readme")
                                .lastModified(Instant.parse("2026-07-22T03:00:00Z")).build(),
                        S3Object.builder().key("Team/Design/.weave-collection").size(0L).build()))
                .build());
        WeaveS3FilesAdapter adapter = new WeaveS3FilesAdapter(properties(), client);

        var listing = adapter.list(new FilePath("/Team"));

        assertThat(listing.listing().children())
                .extracting(item -> item.path().value())
                .containsExactlyInAnyOrder("/Team/Design", "/Team/readme.md");
        assertThat(adapter.conformanceProfile().adapterKey()).isEqualTo("weave-s3-minio");
        assertThat(adapter.conformanceProfile().fieldMappings().get("share").name()).isEqualTo("UNSUPPORTED");
        assertThat(adapter.conformanceProfile().supportSafe()).isTrue();
    }

    private WeaveS3FilesProperties properties() {
        WeaveS3FilesProperties properties = new WeaveS3FilesProperties();
        properties.setEnabled(true);
        properties.setEndpoint(URI.create("http://minio.test:9000"));
        properties.setRegion("us-east-1");
        properties.setBucket("weave-files");
        properties.setAccessKey("test-access-key");
        properties.setSecretKey("test-secret-key");
        properties.setPathStyle(true);
        return properties;
    }
}

package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.config.WeaveS3FilesProperties;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers(disabledWithoutDocker = true)
class WeaveS3FilesAdapterMinioTest {

    private static final String ACCESS_KEY = "weave-test-access";
    private static final String SECRET_KEY = "weave-test-secret-key";
    private static final String BUCKET = "weave-files-test";

    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
            "minio/minio:RELEASE.2025-02-18T16-25-55Z")
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000).forStatusCode(200));

    private static S3Client bucketAdminClient;
    private static OpenDalS3ObjectStorageAdapter storage;
    private static WeaveS3FilesAdapter adapter;

    @BeforeAll
    static void prepareBucket() {
        URI endpoint = URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        bucketAdminClient = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        bucketAdminClient.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());

        WeaveS3FilesProperties properties = new WeaveS3FilesProperties();
        properties.setEnabled(true);
        properties.setEndpoint(endpoint);
        properties.setRegion("us-east-1");
        properties.setBucket(BUCKET);
        properties.setAccessKey(ACCESS_KEY);
        properties.setSecretKey(SECRET_KEY);
        properties.setPathStyle(true);

        storage = new OpenDalS3ObjectStorageAdapter(properties);
        adapter = new WeaveS3FilesAdapter(properties, storage);
    }

    @AfterAll
    static void closeInfrastructure() {
        if (storage != null) {
            storage.closeOperators();
        }
        if (bucketAdminClient != null) {
            bucketAdminClient.close();
        }
    }

    @Test
    void performsRealWriteReadCopyMoveListAndDeleteThroughOpenDalAgainstMinio() {
        assertThat(adapter.readiness().ready()).isTrue();

        adapter.createCollection(new FilePath("/Team"));
        adapter.createCollection(new FilePath("/Archive"));
        var written = adapter.write(new FileWrite(
                new FilePath("/Team/readme.md"),
                "portable-core".getBytes(StandardCharsets.UTF_8),
                "text/markdown"));

        assertThat(adapter.read(written.id()).bytes())
                .isEqualTo("portable-core".getBytes(StandardCharsets.UTF_8));
        assertThat(adapter.copy(
                new FilePath("/Team/readme.md"), new FilePath("/Archive/readme.md"), false).path().value())
                .isEqualTo("/Archive/readme.md");
        assertThat(adapter.move(
                new FilePath("/Team/readme.md"), new FilePath("/Team/moved.md"), false).path().value())
                .isEqualTo("/Team/moved.md");
        assertThat(adapter.find(new FilePath("/Team/readme.md"))).isEmpty();
        assertThat(adapter.list(new FilePath("/Team")).listing().children())
                .extracting(item -> item.path().value())
                .containsExactly("/Team/moved.md");

        adapter.delete(new FilePath("/Team/moved.md"), FileVersion.unknown());
        assertThat(adapter.find(new FilePath("/Team/moved.md"))).isEmpty();
        assertThat(adapter.find(new FilePath("/Archive/readme.md"))).isPresent();
    }
}

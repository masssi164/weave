package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.config.WeaveS3FilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Optional S3-compatible implementation of the same private native blob port. */
@Component
@Primary
@ConditionalOnProperty(
        name = "weave.files.native.blob-store",
        havingValue = WeaveNativeFilesProperties.S3_COMPATIBLE)
public final class S3BlobStore implements BlobStorePort {

    private final WeaveS3FilesProperties properties;
    private final S3Client client;

    @Autowired
    public S3BlobStore(WeaveS3FilesProperties properties) {
        this(properties, client(properties));
    }

    S3BlobStore(WeaveS3FilesProperties properties, S3Client client) {
        this.properties = java.util.Objects.requireNonNull(properties, "properties must not be null");
        this.client = java.util.Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public boolean configured() {
        return properties.configured();
    }

    @Override
    public BlobReceipt put(BlobScope scope, BlobReference reference, byte[] bytes, String expectedDigest) {
        ensureConfigured();
        byte[] content = bytes == null ? new byte[0] : bytes.clone();
        String actualDigest = FilesystemBlobStore.digest(content);
        if (!MessageDigest.isEqual(actualDigest.getBytes(StandardCharsets.US_ASCII),
                requiredDigest(expectedDigest).getBytes(StandardCharsets.US_ASCII))) {
            throw conflict("files-native-blob-digest-mismatch");
        }
        String key = key(scope, reference);
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .ifNoneMatch("*")
                            .metadata(Map.of("weave-sha256", actualDigest.substring("sha256:".length())))
                            .build(),
                    RequestBody.fromBytes(content));
            return new BlobReceipt(reference, actualDigest, content.length);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 409 || exception.statusCode() == 412) {
                byte[] existing = read(scope, reference);
                if (existing.length == content.length && MessageDigest.isEqual(existing, content)) {
                    return new BlobReceipt(reference, actualDigest, content.length);
                }
                throw conflict("files-native-blob-key-collision");
            }
            throw unavailable("files-native-blob-write-failed");
        } catch (SdkException exception) {
            throw unavailable("files-native-blob-write-failed");
        }
    }

    @Override
    public byte[] read(BlobScope scope, BlobReference reference) {
        ensureConfigured();
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key(scope, reference))
                            .build())
                    .asByteArray();
        } catch (NoSuchKeyException exception) {
            throw conflict("files-native-blob-missing");
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw conflict("files-native-blob-missing");
            }
            throw unavailable("files-native-blob-read-failed");
        } catch (SdkException exception) {
            throw unavailable("files-native-blob-read-failed");
        }
    }

    @Override
    public void delete(BlobScope scope, BlobReference reference) {
        ensureConfigured();
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key(scope, reference))
                    .build());
        } catch (SdkException exception) {
            throw unavailable("files-native-blob-delete-failed");
        }
    }

    @Override
    public List<BlobReference> inventory(BlobScope scope, int limit) {
        ensureConfigured();
        if (limit < 1) {
            throw new IllegalArgumentException("inventory limit must be positive");
        }
        String prefix = scopePrefix(scope);
        String continuation = null;
        List<BlobReference> result = new ArrayList<>();
        try {
            do {
                ListObjectsV2Response response = client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(properties.getBucket())
                        .prefix(prefix)
                        .continuationToken(continuation)
                        .maxKeys(Math.min(1000, limit + 1 - result.size()))
                        .build());
                response.contents().forEach(object -> {
                    if (result.size() >= limit) {
                        throw conflict("files-native-reconciliation-bound-exceeded");
                    }
                    result.add(new BlobReference(object.key().substring(prefix.length())));
                });
                continuation = response.nextContinuationToken();
            } while (continuation != null && !continuation.isBlank());
            return List.copyOf(result);
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (SdkException exception) {
            throw unavailable("files-native-blob-inventory-failed");
        }
    }

    private String key(BlobScope scope, BlobReference reference) {
        return scopePrefix(scope) + reference.value();
    }

    private String scopePrefix(BlobScope scope) {
        return "weave-native/v1/" + hash(scope.organizationRef()) + "/" + hash(scope.spaceRef()) + "/";
    }

    private String hash(String value) {
        return FilesystemBlobStore.digest(value.getBytes(StandardCharsets.UTF_8)).substring("sha256:".length());
    }

    private String requiredDigest(String digest) {
        if (digest == null || !digest.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("expected digest must be sha256");
        }
        return digest;
    }

    private void ensureConfigured() {
        if (!configured()) {
            throw unavailable("files-native-s3-not-configured");
        }
    }

    private ApiErrorException conflict(String code) {
        return new ApiErrorException(HttpStatus.CONFLICT, code,
                "The native Files blob state is inconsistent.",
                Map.of("module", "files", "adapter", "weave-native", "diagnosticsRedacted", true));
    }

    private ApiErrorException unavailable(String code) {
        return new ApiErrorException(HttpStatus.SERVICE_UNAVAILABLE, code,
                "The native Files blob store is temporarily unavailable.",
                Map.of("module", "files", "adapter", "weave-native", "diagnosticsRedacted", true));
    }

    private static S3Client client(WeaveS3FilesProperties properties) {
        URI endpoint = properties.getEndpoint() == null ? URI.create("http://127.0.0.1") : properties.getEndpoint();
        String region = properties.getRegion() == null || properties.getRegion().isBlank()
                ? "us-east-1" : properties.getRegion();
        String accessKey = properties.getAccessKey() == null ? "unconfigured" : properties.getAccessKey();
        String secretKey = properties.getSecretKey() == null ? "unconfigured" : properties.getSecretKey();
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.isPathStyle()).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}

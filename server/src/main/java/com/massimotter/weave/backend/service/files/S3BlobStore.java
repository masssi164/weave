package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.config.WeaveS3FilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.opendal.AsyncOperator;
import org.apache.opendal.Entry;
import org.apache.opendal.ListOptions;
import org.apache.opendal.OpenDALException;
import org.apache.opendal.Operator;
import org.apache.opendal.WriteOptions;
import org.apache.opendal.layer.ConcurrentLimitLayer;
import org.apache.opendal.layer.RetryLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** S3-compatible native blob store using the same Apache OpenDAL boundary as filesystem storage. */
@Component
@Primary
@ConditionalOnProperty(
        name = "weave.files.native.blob-store",
        havingValue = WeaveNativeFilesProperties.S3_COMPATIBLE)
public final class S3BlobStore implements BlobStorePort {

    private final WeaveS3FilesProperties properties;
    private final long maximumBlobBytes;
    private final AsyncOperator asyncOperator;
    private final Operator operator;

    @Autowired
    public S3BlobStore(
            WeaveS3FilesProperties properties,
            WeaveNativeFilesProperties nativeProperties) {
        this.properties = java.util.Objects.requireNonNull(properties, "properties must not be null");
        this.maximumBlobBytes = nativeProperties.maximumBlobBytes();
        ensureConfigured();
        try {
            asyncOperator = AsyncOperator.of("s3", configuration(properties))
                    .layer(RetryLayer.builder().maxTimes(4).jitter(true).build())
                    .layer(new ConcurrentLimitLayer(32));
            operator = asyncOperator.blocking();
            requireCapabilities();
        } catch (OpenDALException exception) {
            throw unavailable("files-native-opendal-s3-init-failed");
        }
    }

    @Override
    public boolean configured() {
        return properties.configured();
    }

    @Override
    public BlobReceipt put(BlobScope scope, BlobReference reference, byte[] bytes, String expectedDigest) {
        byte[] content = bytes == null ? new byte[0] : bytes.clone();
        requireWithinLimit(content.length, "files-native-blob-too-large");
        String actualDigest = FilesystemBlobStore.digest(content);
        if (!MessageDigest.isEqual(
                actualDigest.getBytes(StandardCharsets.US_ASCII),
                requiredDigest(expectedDigest).getBytes(StandardCharsets.US_ASCII))) {
            throw conflict("files-native-blob-digest-mismatch");
        }
        String key = key(scope, reference);
        try {
            WriteOptions options = WriteOptions.builder().ifNotExists(true).build();
            operator.write(key, content, options);
            return new BlobReceipt(reference, actualDigest, content.length);
        } catch (OpenDALException exception) {
            if (exception.getCode() == OpenDALException.Code.AlreadyExists
                    || exception.getCode() == OpenDALException.Code.ConditionNotMatch) {
                return verifyExisting(scope, reference, actualDigest, content.length);
            }
            throw map(exception, "files-native-blob-write-failed");
        }
    }

    @Override
    public byte[] read(BlobScope scope, BlobReference reference) {
        String key = key(scope, reference);
        try {
            long size = operator.stat(key).getContentLength();
            requireWithinLimit(size, "files-native-blob-size-invalid");
            byte[] content = operator.read(key);
            requireWithinLimit(content.length, "files-native-blob-size-invalid");
            return content;
        } catch (OpenDALException exception) {
            throw map(exception, "files-native-blob-read-failed");
        }
    }

    @Override
    public void delete(BlobScope scope, BlobReference reference) {
        try {
            operator.delete(key(scope, reference));
        } catch (OpenDALException exception) {
            throw map(exception, "files-native-blob-delete-failed");
        }
    }

    @Override
    public List<BlobReference> inventory(BlobScope scope, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("inventory limit must be positive");
        }
        String prefix = scopePrefix(scope);
        try {
            ListOptions options = ListOptions.builder().recursive(true).limit((long) limit + 1).build();
            List<BlobReference> values = operator.list(prefix, options).stream()
                    .filter(entry -> entry.getMetadata().isFile())
                    .map(Entry::getPath)
                    .map(path -> path.substring(prefix.length()))
                    .map(BlobReference::new)
                    .toList();
            if (values.size() > limit) {
                throw conflict("files-native-reconciliation-bound-exceeded");
            }
            return values;
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (OpenDALException exception) {
            if (exception.getCode() == OpenDALException.Code.NotFound) {
                return List.of();
            }
            throw map(exception, "files-native-blob-inventory-failed");
        }
    }

    private BlobReceipt verifyExisting(
            BlobScope scope,
            BlobReference reference,
            String expectedDigest,
            long expectedSize) {
        byte[] existing = read(scope, reference);
        if (existing.length != expectedSize || !MessageDigest.isEqual(
                FilesystemBlobStore.digest(existing).getBytes(StandardCharsets.US_ASCII),
                expectedDigest.getBytes(StandardCharsets.US_ASCII))) {
            throw conflict("files-native-blob-key-collision");
        }
        return new BlobReceipt(reference, expectedDigest, expectedSize);
    }

    private void requireCapabilities() {
        var capability = operator.info.capability;
        if (!capability.read || !capability.write || !capability.delete || !capability.list) {
            throw unavailable("files-native-opendal-capability-missing");
        }
        if (!capability.writeWithIfNotExists) {
            throw unavailable("files-native-opendal-conditional-write-required");
        }
    }

    private static Map<String, String> configuration(WeaveS3FilesProperties properties) {
        Map<String, String> configuration = new HashMap<>();
        configuration.put("bucket", properties.getBucket());
        configuration.put("region", properties.getRegion());
        configuration.put("endpoint", properties.getEndpoint().toString());
        configuration.put("access_key_id", properties.getAccessKey());
        configuration.put("secret_access_key", properties.getSecretKey());
        configuration.put("enable_virtual_host_style", Boolean.toString(!properties.isPathStyle()));
        return Map.copyOf(configuration);
    }

    private String key(BlobScope scope, BlobReference reference) {
        return scopePrefix(scope) + reference.value();
    }

    private String scopePrefix(BlobScope scope) {
        return "weave-native/v1/"
                + hash(scope.organizationRef())
                + "/"
                + hash(scope.spaceRef())
                + "/";
    }

    private String hash(String value) {
        return FilesystemBlobStore.digest(value.getBytes(StandardCharsets.UTF_8))
                .substring("sha256:".length());
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

    private void requireWithinLimit(long size, String code) {
        if (size < 0 || size > maximumBlobBytes) {
            HttpStatus status = "files-native-blob-too-large".equals(code)
                    ? HttpStatus.PAYLOAD_TOO_LARGE
                    : HttpStatus.CONFLICT;
            throw new ApiErrorException(status, code,
                    "The native Files blob exceeds its configured bound.",
                    Map.of("module", "files", "adapter", "weave-native", "diagnosticsRedacted", true));
        }
    }

    private ApiErrorException map(OpenDALException exception, String fallback) {
        return switch (exception.getCode()) {
            case NotFound -> conflict("files-native-blob-missing");
            case PermissionDenied -> new ApiErrorException(
                    HttpStatus.FORBIDDEN,
                    "files-native-blob-permission-denied",
                    "The native Files blob store denied the operation.",
                    Map.of("module", "files", "adapter", "weave-native", "diagnosticsRedacted", true));
            case AlreadyExists, ConditionNotMatch -> conflict("files-native-blob-key-collision");
            case RateLimited -> unavailable("files-native-blob-rate-limited");
            default -> unavailable(fallback);
        };
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

    @PreDestroy
    void closeOperator() {
        try {
            operator.close();
        } finally {
            asyncOperator.close();
        }
    }
}

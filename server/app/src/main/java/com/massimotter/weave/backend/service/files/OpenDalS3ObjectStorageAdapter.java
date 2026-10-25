package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.WeaveS3FilesProperties;
import com.massimotter.weave.backend.files.port.ObjectStoragePort;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.opendal.AsyncOperator;
import org.apache.opendal.ListOptions;
import org.apache.opendal.Metadata;
import org.apache.opendal.OpenDALException;
import org.apache.opendal.Operator;
import org.apache.opendal.WriteOptions;
import org.apache.opendal.layer.ConcurrentLimitLayer;
import org.apache.opendal.layer.RetryLayer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** OpenDAL-backed Infrastructure Adapter for the independently selectable S3 Files provider. */
@Component
@ConditionalOnProperty(name = "weave.files.s3.enabled", havingValue = "true")
@ConditionalOnExpression("'${weave.files.provider:weave-native}' == 'weave-s3-minio'")
public final class OpenDalS3ObjectStorageAdapter implements ObjectStoragePort {

    private final WeaveS3FilesProperties properties;
    private final AsyncOperator asyncOperator;
    private final Operator operator;

    public OpenDalS3ObjectStorageAdapter(WeaveS3FilesProperties properties) {
        this.properties = java.util.Objects.requireNonNull(properties, "properties must not be null");
        if (!properties.configured()) {
            asyncOperator = null;
            operator = null;
            return;
        }
        try {
            asyncOperator = AsyncOperator.of("s3", configuration(properties))
                    .layer(RetryLayer.builder().maxTimes(4).jitter(true).build())
                    .layer(new ConcurrentLimitLayer(32));
            operator = asyncOperator.blocking();
            requireCapabilities();
        } catch (OpenDALException exception) {
            throw mapped(exception, "initialize S3 object storage");
        }
    }

    @Override
    public boolean configured() {
        return properties.configured() && operator != null;
    }

    @Override
    public void check() {
        requireConfigured();
        try {
            operator.list("", ListOptions.builder().limit(1L).build());
        } catch (OpenDALException exception) {
            throw mapped(exception, "check S3 object storage");
        }
    }

    @Override
    public Optional<ObjectMetadata> stat(String key) {
        requireConfigured();
        try {
            return Optional.of(metadata(operator.stat(requiredKey(key))));
        } catch (OpenDALException exception) {
            if (exception.getCode() == OpenDALException.Code.NotFound) {
                return Optional.empty();
            }
            throw mapped(exception, "stat S3 object");
        }
    }

    @Override
    public List<ObjectEntry> list(String prefix) {
        requireConfigured();
        String normalized = prefix == null ? "" : prefix;
        try {
            return operator.list(normalized, ListOptions.builder().recursive(true).build()).stream()
                    .filter(entry -> entry.getMetadata().isFile())
                    .map(entry -> {
                        String key = entry.getPath();
                        Metadata value = operator.stat(key);
                        return new ObjectEntry(key, metadata(value));
                    })
                    .toList();
        } catch (OpenDALException exception) {
            if (exception.getCode() == OpenDALException.Code.NotFound) {
                return List.of();
            }
            throw mapped(exception, "list S3 objects");
        }
    }

    @Override
    public byte[] read(String key) {
        requireConfigured();
        try {
            return operator.read(requiredKey(key));
        } catch (OpenDALException exception) {
            throw mapped(exception, "read S3 object");
        }
    }

    @Override
    public void write(String key, byte[] bytes, String contentType) {
        requireConfigured();
        byte[] content = bytes == null ? new byte[0] : bytes;
        try {
            if (contentType != null && !contentType.isBlank() && operator.info.capability.writeWithContentType) {
                operator.write(requiredKey(key), content,
                        WriteOptions.builder().contentType(contentType).build());
            } else {
                operator.write(requiredKey(key), content);
            }
        } catch (OpenDALException exception) {
            throw mapped(exception, "write S3 object");
        }
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        requireConfigured();
        try {
            operator.copy(requiredKey(sourceKey), requiredKey(targetKey));
        } catch (OpenDALException exception) {
            throw mapped(exception, "copy S3 object");
        }
    }

    @Override
    public void delete(String key) {
        requireConfigured();
        try {
            operator.delete(requiredKey(key));
        } catch (OpenDALException exception) {
            if (exception.getCode() == OpenDALException.Code.NotFound) {
                return;
            }
            throw mapped(exception, "delete S3 object");
        }
    }

    private ObjectMetadata metadata(Metadata metadata) {
        return new ObjectMetadata(
                metadata.getContentLength(),
                metadata.contentType,
                metadata.etag,
                metadata.lastModified);
    }

    private void requireCapabilities() {
        var capability = operator.info.capability;
        if (!capability.stat || !capability.read || !capability.write
                || !capability.delete || !capability.copy || !capability.list) {
            throw new ObjectStorageException(
                    FailureCode.UNAVAILABLE,
                    "The configured S3 endpoint does not expose the required OpenDAL capabilities.",
                    null);
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new ObjectStorageException(FailureCode.UNAVAILABLE, "S3 object storage is not configured.", null);
        }
    }

    private String requiredKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("object key must not be blank");
        }
        return key;
    }

    private ObjectStorageException mapped(OpenDALException exception, String operation) {
        FailureCode code = switch (exception.getCode()) {
            case NotFound -> FailureCode.NOT_FOUND;
            case AlreadyExists, ConditionNotMatch -> FailureCode.CONFLICT;
            case PermissionDenied -> FailureCode.FORBIDDEN;
            default -> FailureCode.UNAVAILABLE;
        };
        return new ObjectStorageException(code, "Unable to " + operation + ".", exception);
    }

    private static Map<String, String> configuration(WeaveS3FilesProperties properties) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("bucket", properties.getBucket());
        values.put("region", properties.getRegion());
        values.put("endpoint", properties.getEndpoint().toString());
        values.put("access_key_id", properties.getAccessKey());
        values.put("secret_access_key", properties.getSecretKey());
        values.put("disable_config_load", "true");
        values.put("disable_ec2_metadata", "true");
        values.put("enable_virtual_host_style", Boolean.toString(!properties.isPathStyle()));
        return Map.copyOf(values);
    }

    @PreDestroy
    void closeOperators() {
        if (operator != null) {
            operator.close();
        }
        if (asyncOperator != null) {
            asyncOperator.close();
        }
    }
}

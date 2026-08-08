package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.WeaveS3FilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileContent;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileListing;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileQuota;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedListing;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile.MappingClass;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

@Component
@Primary
@ConditionalOnProperty(name = "weave.files.s3.enabled", havingValue = "true")
@ConditionalOnExpression("'${weave.files.provider:weave-native}' == 'weave-s3-minio'")
public class WeaveS3FilesAdapter implements FilesProviderPort {

    private static final String COLLECTION_MARKER = ".weave-collection";

    private final WeaveS3FilesProperties properties;
    private final S3Client client;

    public WeaveS3FilesAdapter(WeaveS3FilesProperties properties) {
        this(properties, client(properties));
    }

    WeaveS3FilesAdapter(WeaveS3FilesProperties properties, S3Client client) {
        this.properties = java.util.Objects.requireNonNull(properties, "properties must not be null");
        this.client = java.util.Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public boolean configured() {
        return properties.configured();
    }

    @Override
    public ProviderReadiness readiness() {
        if (!configured()) {
            return ProviderReadiness.degraded("files-s3-not-configured");
        }
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build());
            return ProviderReadiness.ready("files-s3-ready");
        } catch (RuntimeException exception) {
            return ProviderReadiness.degraded("files-s3-unavailable");
        }
    }

    @Override
    public ProviderConformanceProfile conformanceProfile() {
        return new ProviderConformanceProfile(
                "files",
                "weave-s3-minio",
                Set.of("list", "read", "write", "create_collection", "delete", "copy", "move", "versions"),
                Map.of(
                        "path", MappingClass.PORTABLE,
                        "content", MappingClass.PORTABLE,
                        "mediaType", MappingClass.PORTABLE,
                        "version", MappingClass.PORTABLE,
                        "lock", MappingClass.PORTABLE,
                        "share", MappingClass.UNSUPPORTED),
                true,
                true,
                true);
    }

    @Override
    public VersionedListing list(FilePath path) {
        ensureConfigured();
        String prefix = prefix(path);
        try {
            ListObjectsV2Response response = client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(properties.getBucket()).prefix(prefix).delimiter("/").build());
            List<FileObject> children = new ArrayList<>();
            Map<FilePath, FileVersion> versions = new LinkedHashMap<>();
            for (CommonPrefix child : response.commonPrefixes()) {
                FilePath childPath = pathFromKey(trimTrailingSlash(child.prefix()));
                FileObject object = object(childPath, Kind.COLLECTION, 0, null, null);
                children.add(object);
                versions.put(childPath, FileVersion.unknown());
            }
            for (S3Object stored : response.contents()) {
                if (stored.key().equals(prefix) || stored.key().endsWith("/" + COLLECTION_MARKER)) {
                    continue;
                }
                FilePath childPath = pathFromKey(stored.key());
                if (!parent(childPath).equals(path.value())) {
                    continue;
                }
                FileObject object = object(childPath, Kind.FILE, stored.size(), null, stored.lastModified());
                children.add(object);
                versions.put(childPath, version(stored.eTag()));
            }
            FileObject collection = object(path, Kind.COLLECTION, 0, null, null);
            return new VersionedListing(
                    new FileListing(path, children, FileQuota.unknown()), FileVersion.unknown(), versions);
        } catch (S3Exception exception) {
            throw mapped(exception, "list-files");
        } catch (SdkClientException exception) {
            throw unavailable("list-files");
        }
    }

    @Override
    public Optional<VersionedFile> find(FilePath path) {
        ensureConfigured();
        if (path.root()) {
            return Optional.of(new VersionedFile(object(path, Kind.COLLECTION, 0, null, null), FileVersion.unknown()));
        }
        try {
            HeadObjectResponse head = client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket()).key(key(path)).build());
            return Optional.of(new VersionedFile(
                    object(path, Kind.FILE, head.contentLength(), head.contentType(), head.lastModified()),
                    version(head.eTag())));
        } catch (NoSuchKeyException exception) {
            return findCollection(path);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return findCollection(path);
            }
            throw mapped(exception, "find-file");
        } catch (SdkClientException exception) {
            throw unavailable("find-file");
        }
    }

    private Optional<VersionedFile> findCollection(FilePath path) {
        try {
            client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket()).key(collectionMarker(path)).build());
            return Optional.of(new VersionedFile(
                    object(path, Kind.COLLECTION, 0, null, null), FileVersion.unknown()));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                ListObjectsV2Response listing = client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(properties.getBucket()).prefix(prefix(path)).maxKeys(1).build());
                return listing.keyCount() > 0
                        ? Optional.of(new VersionedFile(
                                object(path, Kind.COLLECTION, 0, null, null), FileVersion.unknown()))
                        : Optional.empty();
            }
            throw mapped(exception, "find-collection");
        } catch (SdkClientException exception) {
            throw unavailable("find-collection");
        }
    }

    @Override
    public FileContent read(FileId id) {
        ensureConfigured();
        FilePath path = new FilePath(FilePathCodec.pathFromId(id.value()));
        try {
            ResponseBytes<GetObjectResponse> bytes = client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.getBucket()).key(key(path)).build());
            GetObjectResponse response = bytes.response();
            return new FileContent(
                    object(path, Kind.FILE, bytes.asByteArray().length, response.contentType(), response.lastModified()),
                    bytes.asByteArray());
        } catch (S3Exception exception) {
            throw mapped(exception, "read-file");
        } catch (SdkClientException exception) {
            throw unavailable("read-file");
        }
    }

    @Override
    public FileObject write(FileWrite write) {
        ensureConfigured();
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(properties.getBucket()).key(key(write.path())).contentType(write.mediaType()).build(),
                    RequestBody.fromBytes(write.bytes()));
            return find(write.path()).map(VersionedFile::item).orElseGet(() -> object(
                    write.path(), Kind.FILE, write.bytes().length, write.mediaType(), Instant.now()));
        } catch (S3Exception exception) {
            throw mapped(exception, "write-file");
        } catch (SdkClientException exception) {
            throw unavailable("write-file");
        }
    }

    @Override
    public FileObject createCollection(FilePath path) {
        ensureConfigured();
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(properties.getBucket()).key(collectionMarker(path))
                            .contentType("application/x-weave-collection").build(),
                    RequestBody.empty());
            return object(path, Kind.COLLECTION, 0, null, Instant.now());
        } catch (S3Exception exception) {
            throw mapped(exception, "create-collection");
        } catch (SdkClientException exception) {
            throw unavailable("create-collection");
        }
    }

    @Override
    public FileObject copy(FilePath source, FilePath destination, boolean overwrite) {
        ensureConfigured();
        try {
            return copyInternal(source, destination, overwrite);
        } catch (S3Exception exception) {
            throw mapped(exception, "copy-file");
        } catch (SdkClientException exception) {
            throw unavailable("copy-file");
        }
    }

    private FileObject copyInternal(FilePath source, FilePath destination, boolean overwrite) {
        VersionedFile sourceObject = find(source).orElseThrow(() -> notFound("copy-file"));
        if (!overwrite && find(destination).isPresent()) {
            throw conflict("copy-file");
        }
        if (sourceObject.item().kind() == Kind.COLLECTION) {
            copyCollection(source, destination);
            return object(destination, Kind.COLLECTION, 0, null, Instant.now());
        }
        client.copyObject(CopyObjectRequest.builder()
                .destinationBucket(properties.getBucket()).destinationKey(key(destination))
                .sourceBucket(properties.getBucket()).sourceKey(key(source)).build());
        return find(destination).map(VersionedFile::item).orElseGet(() -> object(
                destination, Kind.FILE, sourceObject.item().size(), sourceObject.item().mediaType(), Instant.now()));
    }

    private void copyCollection(FilePath source, FilePath destination) {
        createCollection(destination);
        String sourcePrefix = prefix(source);
        for (S3Object stored : all(sourcePrefix)) {
            String suffix = stored.key().substring(sourcePrefix.length());
            client.copyObject(CopyObjectRequest.builder()
                    .destinationBucket(properties.getBucket()).destinationKey(prefix(destination) + suffix)
                    .sourceBucket(properties.getBucket()).sourceKey(stored.key()).build());
        }
    }

    @Override
    public FileObject move(FilePath source, FilePath destination, boolean overwrite) {
        FileObject copied = copy(source, destination, overwrite);
        delete(source, FileVersion.unknown());
        return copied;
    }

    @Override
    public void delete(FilePath path, FileVersion expectedVersion) {
        ensureConfigured();
        try {
            deleteInternal(path);
        } catch (S3Exception exception) {
            throw mapped(exception, "delete-file");
        } catch (SdkClientException exception) {
            throw unavailable("delete-file");
        }
    }

    private void deleteInternal(FilePath path) {
        VersionedFile current = find(path).orElseThrow(() -> notFound("delete-file"));
        if (current.item().kind() == Kind.COLLECTION) {
            for (S3Object stored : all(prefix(path))) {
                client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(properties.getBucket()).key(stored.key()).build());
            }
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket()).key(collectionMarker(path)).build());
            return;
        }
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getBucket()).key(key(path)).build());
    }

    private List<S3Object> all(String prefix) {
        List<S3Object> result = new ArrayList<>();
        String continuation = null;
        do {
            ListObjectsV2Response page = client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(properties.getBucket()).prefix(prefix).continuationToken(continuation).build());
            result.addAll(page.contents());
            continuation = page.isTruncated() ? page.nextContinuationToken() : null;
        } while (continuation != null);
        return result;
    }

    private void ensureConfigured() {
        if (!configured()) {
            throw new ApiErrorException(HttpStatus.SERVICE_UNAVAILABLE, "files-s3-not-configured",
                    "The Weave S3 Files adapter is not configured.", Map.of("module", "files"));
        }
    }

    private ApiErrorException mapped(S3Exception exception, String operation) {
        if (exception.statusCode() == 404) {
            return notFound(operation);
        }
        if (exception.statusCode() == 409 || exception.statusCode() == 412) {
            return conflict(operation);
        }
        return new ApiErrorException(HttpStatus.SERVICE_UNAVAILABLE, "files-s3-unavailable",
                "The Weave S3 Files adapter is temporarily unavailable.",
                Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
    }

    private ApiErrorException unavailable(String operation) {
        return new ApiErrorException(HttpStatus.SERVICE_UNAVAILABLE, "files-s3-unavailable",
                "The Weave S3 Files adapter is temporarily unavailable.",
                Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
    }

    private ApiErrorException notFound(String operation) {
        return new ApiErrorException(HttpStatus.NOT_FOUND, "file-not-found",
                "The requested file or folder was not found.", Map.of("module", "files", "operation", operation));
    }

    private ApiErrorException conflict(String operation) {
        return new ApiErrorException(HttpStatus.CONFLICT, "file-conflict",
                "The file operation conflicts with the current storage state.",
                Map.of("module", "files", "operation", operation));
    }

    private FileObject object(FilePath path, Kind kind, long size, String mediaType, Instant modifiedAt) {
        return new FileObject(new FileId(FilePathCodec.toId(path.value())), path, kind, size,
                kind == Kind.COLLECTION ? null : mediaType, modifiedAt, false);
    }

    private FileVersion version(String etag) {
        return etag == null || etag.isBlank() ? FileVersion.unknown() : new FileVersion(etag);
    }

    private String key(FilePath path) {
        return path.value().substring(1);
    }

    private String prefix(FilePath path) {
        return path.root() ? "" : key(path) + "/";
    }

    private String collectionMarker(FilePath path) {
        return prefix(path) + COLLECTION_MARKER;
    }

    private FilePath pathFromKey(String key) {
        return new FilePath("/" + key);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String parent(FilePath path) {
        int separator = path.value().lastIndexOf('/');
        return separator <= 0 ? "/" : path.value().substring(0, separator);
    }

    private static S3Client client(WeaveS3FilesProperties properties) {
        URI endpoint = properties.getEndpoint() == null ? URI.create("http://127.0.0.1") : properties.getEndpoint();
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                java.util.Objects.requireNonNullElse(properties.getAccessKey(), "not-configured"),
                                java.util.Objects.requireNonNullElse(properties.getSecretKey(), "not-configured"))))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.isPathStyle()).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}

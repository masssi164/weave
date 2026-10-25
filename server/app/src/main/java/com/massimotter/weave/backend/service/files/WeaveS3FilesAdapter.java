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
import com.massimotter.weave.backend.files.port.ObjectStoragePort;
import com.massimotter.weave.backend.files.port.ObjectStoragePort.ObjectEntry;
import com.massimotter.weave.backend.files.port.ObjectStoragePort.ObjectMetadata;
import com.massimotter.weave.backend.files.port.ObjectStoragePort.ObjectStorageException;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile.MappingClass;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Separate S3 Files Provider Adapter.
 *
 * <p>S3 remains a provider choice behind {@link FilesProviderPort}. Object-storage access is delegated
 * to {@link ObjectStoragePort}; the default infrastructure implementation uses OpenDAL's S3 service.</p>
 */
@Component
@Primary
@ConditionalOnProperty(name = "weave.files.s3.enabled", havingValue = "true")
@ConditionalOnExpression("'${weave.files.provider:weave-native}' == 'weave-s3-minio'")
public class WeaveS3FilesAdapter implements FilesProviderPort {

    private static final String COLLECTION_MARKER = ".weave-collection";

    private final WeaveS3FilesProperties properties;
    private final ObjectStoragePort storage;

    public WeaveS3FilesAdapter(WeaveS3FilesProperties properties, ObjectStoragePort storage) {
        this.properties = java.util.Objects.requireNonNull(properties, "properties must not be null");
        this.storage = java.util.Objects.requireNonNull(storage, "storage must not be null");
    }

    @Override
    public boolean configured() {
        return properties.configured() && storage.configured();
    }

    @Override
    public ProviderReadiness readiness() {
        if (!configured()) {
            return ProviderReadiness.degraded("files-s3-not-configured");
        }
        try {
            storage.check();
            return ProviderReadiness.ready("files-s3-ready");
        } catch (ObjectStorageException exception) {
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
            Map<FilePath, FileObject> children = new LinkedHashMap<>();
            Map<FilePath, FileVersion> versions = new LinkedHashMap<>();
            for (ObjectEntry entry : storage.list(prefix)) {
                if (!entry.key().startsWith(prefix)) {
                    continue;
                }
                String relative = entry.key().substring(prefix.length());
                if (relative.isBlank() || COLLECTION_MARKER.equals(relative)) {
                    continue;
                }
                int separator = relative.indexOf('/');
                if (separator >= 0) {
                    String segment = relative.substring(0, separator);
                    if (segment.isBlank()) {
                        continue;
                    }
                    FilePath childPath = child(path, segment);
                    children.putIfAbsent(childPath, object(childPath, Kind.COLLECTION, 0, null, null));
                    versions.putIfAbsent(childPath, FileVersion.unknown());
                    continue;
                }
                FilePath childPath = pathFromKey(entry.key());
                ObjectMetadata metadata = entry.metadata();
                children.put(childPath, object(childPath, Kind.FILE,
                        metadata.size(), metadata.contentType(), metadata.modifiedAt()));
                versions.put(childPath, version(metadata.version()));
            }
            return new VersionedListing(
                    new FileListing(path, List.copyOf(children.values()), FileQuota.unknown()),
                    FileVersion.unknown(),
                    Map.copyOf(versions));
        } catch (ObjectStorageException exception) {
            throw mapped(exception, "list-files");
        }
    }

    @Override
    public Optional<VersionedFile> find(FilePath path) {
        ensureConfigured();
        if (path.root()) {
            return Optional.of(new VersionedFile(
                    object(path, Kind.COLLECTION, 0, null, null), FileVersion.unknown()));
        }
        try {
            Optional<ObjectMetadata> file = storage.stat(key(path));
            if (file.isPresent()) {
                ObjectMetadata metadata = file.get();
                return Optional.of(new VersionedFile(
                        object(path, Kind.FILE, metadata.size(), metadata.contentType(), metadata.modifiedAt()),
                        version(metadata.version())));
            }
            return findCollection(path);
        } catch (ObjectStorageException exception) {
            throw mapped(exception, "find-file");
        }
    }

    private Optional<VersionedFile> findCollection(FilePath path) {
        if (storage.stat(collectionMarker(path)).isPresent() || !storage.list(prefix(path)).isEmpty()) {
            return Optional.of(new VersionedFile(
                    object(path, Kind.COLLECTION, 0, null, null), FileVersion.unknown()));
        }
        return Optional.empty();
    }

    @Override
    public FileContent read(FileId id) {
        ensureConfigured();
        FilePath path = new FilePath(FilePathCodec.pathFromId(id.value()));
        try {
            ObjectMetadata metadata = storage.stat(key(path)).orElseThrow(() -> notFound("read-file"));
            byte[] bytes = storage.read(key(path));
            return new FileContent(
                    object(path, Kind.FILE, bytes.length, metadata.contentType(), metadata.modifiedAt()),
                    bytes);
        } catch (ObjectStorageException exception) {
            throw mapped(exception, "read-file");
        }
    }

    @Override
    public FileObject write(FileWrite write) {
        ensureConfigured();
        try {
            storage.write(key(write.path()), write.bytes(), write.mediaType());
            return find(write.path()).map(VersionedFile::item).orElseGet(() -> object(
                    write.path(), Kind.FILE, write.bytes().length, write.mediaType(), Instant.now()));
        } catch (ObjectStorageException exception) {
            throw mapped(exception, "write-file");
        }
    }

    @Override
    public FileObject createCollection(FilePath path) {
        ensureConfigured();
        try {
            storage.write(collectionMarker(path), new byte[0], "application/x-weave-collection");
            return object(path, Kind.COLLECTION, 0, null, Instant.now());
        } catch (ObjectStorageException exception) {
            throw mapped(exception, "create-collection");
        }
    }

    @Override
    public FileObject copy(FilePath source, FilePath destination, boolean overwrite) {
        ensureConfigured();
        try {
            VersionedFile sourceObject = find(source).orElseThrow(() -> notFound("copy-file"));
            if (!overwrite && find(destination).isPresent()) {
                throw conflict("copy-file");
            }
            if (sourceObject.item().kind() == Kind.COLLECTION) {
                copyCollection(source, destination);
                return object(destination, Kind.COLLECTION, 0, null, Instant.now());
            }
            storage.copy(key(source), key(destination));
            return find(destination).map(VersionedFile::item).orElseGet(() -> object(
                    destination, Kind.FILE, sourceObject.item().size(), sourceObject.item().mediaType(), Instant.now()));
        } catch (ObjectStorageException exception) {
            throw mapped(exception, "copy-file");
        }
    }

    private void copyCollection(FilePath source, FilePath destination) {
        createCollection(destination);
        String sourcePrefix = prefix(source);
        for (ObjectEntry entry : storage.list(sourcePrefix)) {
            if (!entry.key().startsWith(sourcePrefix)) {
                continue;
            }
            String suffix = entry.key().substring(sourcePrefix.length());
            if (suffix.isBlank() || COLLECTION_MARKER.equals(suffix)) {
                continue;
            }
            storage.copy(entry.key(), prefix(destination) + suffix);
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
            VersionedFile current = find(path).orElseThrow(() -> notFound("delete-file"));
            if (current.item().kind() == Kind.COLLECTION) {
                for (ObjectEntry entry : storage.list(prefix(path))) {
                    storage.delete(entry.key());
                }
                storage.delete(collectionMarker(path));
                return;
            }
            storage.delete(key(path));
        } catch (ObjectStorageException exception) {
            throw mapped(exception, "delete-file");
        }
    }

    private void ensureConfigured() {
        if (!configured()) {
            throw new ApiErrorException(HttpStatus.SERVICE_UNAVAILABLE, "files-s3-not-configured",
                    "The Weave S3 Files adapter is not configured.", Map.of("module", "files"));
        }
    }

    private ApiErrorException mapped(ObjectStorageException exception, String operation) {
        return switch (exception.code()) {
            case NOT_FOUND -> notFound(operation);
            case CONFLICT -> conflict(operation);
            case FORBIDDEN -> new ApiErrorException(HttpStatus.FORBIDDEN, "files-s3-forbidden",
                    "The Weave S3 Files provider denied the operation.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
            case UNAVAILABLE -> unavailable(operation);
        };
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

    private FileVersion version(String value) {
        return value == null || value.isBlank() ? FileVersion.unknown() : new FileVersion(value);
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

    private FilePath child(FilePath parent, String segment) {
        return parent.root() ? new FilePath("/" + segment) : new FilePath(parent.value() + "/" + segment);
    }
}

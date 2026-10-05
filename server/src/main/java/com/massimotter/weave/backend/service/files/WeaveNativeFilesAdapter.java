package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.application.CanonicalFilesCommands;
import com.massimotter.weave.backend.files.application.CanonicalFilesQueries;
import com.massimotter.weave.backend.files.application.CanonicalFilesTreeCommands;
import com.massimotter.weave.backend.files.application.FilesApplicationException;
import com.massimotter.weave.backend.files.application.FilesCommandException;
import com.massimotter.weave.backend.files.application.FilesCommandScope;
import com.massimotter.weave.backend.files.application.FilesScope;
import com.massimotter.weave.backend.files.application.FilesTreeCommandException;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileContent;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedListing;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile.MappingClass;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.io.OutputStream;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Native Files boot composition over provider-independent canonical use cases.
 *
 * <p>This class owns no Files domain behavior. It binds the canonical query, create/write, and
 * tree-command services to the configured metadata and BlobStore adapters and translates only
 * application failures into the established Server boundary.</p>
 */
@Component
@Primary
@ConditionalOnExpression("'${weave.files.provider:weave-native}' != 'weave-s3-minio'")
public final class WeaveNativeFilesAdapter implements FilesProviderPort {

    public static final String ADAPTER_KEY = "weave-native";

    private final BlobStorePort blobs;
    private final CanonicalFilesQueries queries;
    private final CanonicalFilesCommands commands;
    private final CanonicalFilesTreeCommands treeCommands;

    @Autowired
    public WeaveNativeFilesAdapter(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            WeaveNativeFilesProperties properties) {
        this(
                authority,
                blobs,
                Clock.systemUTC(),
                properties.reconciliationLimit());
    }

    WeaveNativeFilesAdapter(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            Clock clock,
            int reconciliationLimit) {
        FilesAuthorityRepository requiredAuthority = Objects.requireNonNull(
                authority,
                "authority must not be null");
        this.blobs = Objects.requireNonNull(blobs, "blobs must not be null");
        Clock requiredClock = clock == null ? Clock.systemUTC() : clock;
        this.queries = new CanonicalFilesQueries(
                requiredAuthority,
                this.blobs,
                reconciliationLimit);
        this.commands = new CanonicalFilesCommands(
                requiredAuthority,
                this.blobs,
                requiredClock);
        this.treeCommands = new CanonicalFilesTreeCommands(
                requiredAuthority,
                this.blobs,
                requiredClock);
    }

    @Override
    public FilesProviderPort scoped(FilesRequestScope scope) {
        return new Scoped(Objects.requireNonNull(scope, "scope must not be null"));
    }

    @Override
    public boolean configured() {
        return blobs.configured();
    }

    @Override
    public ProviderReadiness readiness() {
        return configured()
                ? ProviderReadiness.ready("files-native-ready")
                : ProviderReadiness.degraded("files-native-blob-store-not-configured");
    }

    @Override
    public ProviderConformanceProfile conformanceProfile() {
        return new ProviderConformanceProfile(
                "files",
                ADAPTER_KEY,
                Set.of(
                        "list",
                        "read",
                        "write",
                        "create_collection",
                        "delete",
                        "copy",
                        "move",
                        "versions",
                        "locks"),
                Map.ofEntries(
                        Map.entry("canonicalId", MappingClass.PORTABLE),
                        Map.entry("path", MappingClass.PORTABLE),
                        Map.entry("content", MappingClass.PORTABLE),
                        Map.entry("mediaType", MappingClass.PORTABLE),
                        Map.entry("version", MappingClass.PORTABLE),
                        Map.entry("lock", MappingClass.PORTABLE),
                        Map.entry("share", MappingClass.UNSUPPORTED),
                        Map.entry("sourceOwner", MappingClass.UNSUPPORTED),
                        Map.entry("userGrant", MappingClass.UNSUPPORTED),
                        Map.entry("groupGrant", MappingClass.UNSUPPORTED),
                        Map.entry("inheritedGrant", MappingClass.UNSUPPORTED),
                        Map.entry("shareCondition", MappingClass.UNSUPPORTED),
                        Map.entry("effectiveAccess", MappingClass.UNSUPPORTED)),
                true,
                true,
                true);
    }

    @Override public VersionedListing list(FilePath path) { throw unscoped(); }
    @Override public Optional<VersionedFile> find(FilePath path) { throw unscoped(); }
    @Override public FileContent read(FileId id) { throw unscoped(); }
    @Override public FileObject write(FileWrite write) { throw unscoped(); }
    @Override public FileObject createCollection(FilePath path) { throw unscoped(); }
    @Override public FileObject copy(FilePath source, FilePath destination, boolean overwrite) { throw unscoped(); }
    @Override public FileObject move(FilePath source, FilePath destination, boolean overwrite) { throw unscoped(); }
    @Override public void delete(FilePath path, FileVersion expectedVersion) { throw unscoped(); }

    public void readTo(FilesRequestScope scope, FileId id, OutputStream target) {
        try {
            queries.readTo(queryScope(scope), id, target);
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "read-stream");
        }
    }

    public ReconciliationReport reconcile(FilesRequestScope scope) {
        try {
            CanonicalFilesQueries.ReconciliationReport report = queries.reconcile(queryScope(scope));
            return new ReconciliationReport(
                    report.activeMetadataRecords(),
                    report.inventoriedBlobs(),
                    report.orphanBlobsDeleted(),
                    report.inconsistentMetadataRecords());
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "reconcile");
        }
    }

    public record ReconciliationReport(
            int activeMetadataRecords,
            int inventoriedBlobs,
            int orphanBlobsDeleted,
            int inconsistentMetadataRecords) {
    }

    private VersionedListing list(FilesRequestScope scope, FilePath path) {
        try {
            return queries.list(queryScope(scope), path);
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "list");
        }
    }

    private Optional<VersionedFile> find(FilesRequestScope scope, FilePath path) {
        try {
            return queries.find(queryScope(scope), path);
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "find");
        }
    }

    private Optional<String> providerObjectRef(FilesRequestScope scope, FilePath path) {
        return find(scope, path).map(found -> found.item().id().value());
    }

    private FileContent read(FilesRequestScope scope, FileId id) {
        try {
            return queries.read(queryScope(scope), id);
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "read");
        }
    }

    private FileContent readBounded(FilesRequestScope scope, FileId id, int maxBytes) {
        try {
            return queries.readBounded(queryScope(scope), id, maxBytes);
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "read-bounded");
        }
    }

    private FileContent readBoundedIfVersion(
            FilesRequestScope scope, FileId id, int maxBytes, FileVersion expectedVersion) {
        try {
            return queries.readBoundedIfVersion(queryScope(scope), id, maxBytes, expectedVersion);
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "read-bounded");
        }
    }

    private FileObject write(FilesRequestScope scope, FileWrite write) {
        try {
            return commands.write(commandScope(scope), write);
        } catch (FilesCommandException exception) {
            throw commandFailure(exception);
        }
    }

    private FilesProviderPort.CreatedObject writeIfAbsent(FilesRequestScope scope, FileWrite write) {
        try {
            FileObject created = commands.writeIfAbsent(commandScope(scope), write);
            return new FilesProviderPort.CreatedObject(created, created.id().value());
        } catch (FilesCommandException exception) {
            throw commandFailure(exception);
        }
    }

    private FileObject createCollection(FilesRequestScope scope, FilePath path) {
        try {
            return commands.createCollection(commandScope(scope), path);
        } catch (FilesCommandException exception) {
            throw commandFailure(exception);
        }
    }

    private FilesProviderPort.CreatedObject createCollectionIfAbsent(
            FilesRequestScope scope, FilePath path) {
        FileObject created = createCollection(scope, path);
        return new FilesProviderPort.CreatedObject(created, created.id().value());
    }

    private FileObject copy(
            FilesRequestScope scope,
            FilePath source,
            FilePath destination,
            boolean overwrite) {
        try {
            return treeCommands.copy(commandScope(scope), source, destination, overwrite);
        } catch (FilesTreeCommandException exception) {
            throw treeFailure(exception, "copy");
        }
    }

    private FileObject move(
            FilesRequestScope scope,
            FilePath source,
            FilePath destination,
            boolean overwrite) {
        try {
            return treeCommands.move(commandScope(scope), source, destination, overwrite);
        } catch (FilesTreeCommandException exception) {
            throw treeFailure(exception, "move");
        }
    }

    private void delete(
            FilesRequestScope scope,
            FilePath path,
            FileVersion expectedVersion) {
        try {
            treeCommands.delete(commandScope(scope), path, expectedVersion);
        } catch (FilesTreeCommandException exception) {
            throw treeFailure(exception, "delete");
        }
    }

    private FilesScope queryScope(FilesRequestScope scope) {
        FilesRequestScope required = Objects.requireNonNull(scope, "scope must not be null");
        return new FilesScope(required.organizationRef(), required.spaceRef());
    }

    private FilesCommandScope commandScope(FilesRequestScope scope) {
        FilesRequestScope required = Objects.requireNonNull(scope, "scope must not be null");
        return new FilesCommandScope(
                required.organizationRef(),
                required.spaceRef(),
                required.providerBindingRevision());
    }

    private ApiErrorException queryFailure(
            FilesApplicationException exception,
            String operation) {
        return switch (exception.code()) {
            case NOT_FOUND -> notFound(operation, exception.getMessage());
            case NOT_A_COLLECTION -> conflict(
                    "files-native-not-a-collection", exception.getMessage());
            case NOT_A_FILE -> conflict(
                    "files-native-not-a-file", exception.getMessage());
            case VERSION_CHANGED -> precondition(exception.getMessage());
            case CONTENT_TOO_LARGE -> new ApiErrorException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "files-native-content-too-large",
                    "The native Files content exceeds the read limit.",
                    Map.of("module", "files", "adapter", ADAPTER_KEY, "diagnosticsRedacted", true));
            case INVALID_BLOB_REFERENCE -> conflict(
                    "files-native-metadata-blob-mismatch", exception.getMessage());
            case CONTENT_INTEGRITY_FAILED -> conflict(
                    "read-stream".equals(operation)
                            ? "files-native-content-digest-mismatch"
                            : "files-native-metadata-blob-mismatch",
                    exception.getMessage());
        };
    }

    private ApiErrorException commandFailure(FilesCommandException exception) {
        String code = switch (exception.code()) {
            case PATH_CONFLICT -> "files-native-path-conflict";
            case PARENT_MISSING -> "files-native-parent-missing";
            case PARENT_NOT_COLLECTION -> "files-native-parent-not-collection";
            case METADATA_CONFLICT -> "files-native-metadata-conflict";
        };
        return conflict(code, exception.getMessage());
    }

    private ApiErrorException treeFailure(
            FilesTreeCommandException exception,
            String operation) {
        String code = exception.code().name();
        if ("NOT_FOUND".equals(code)) {
            return notFound(operation, exception.getMessage());
        }
        if ("PRECONDITION_FAILED".equals(code)
                || "OVERWRITE_PRECONDITION_FAILED".equals(code)
                || "VERSION_PRECONDITION_FAILED".equals(code)) {
            return precondition(exception.getMessage());
        }
        if ("PARENT_MISSING".equals(code)) {
            return conflict("files-native-parent-missing", exception.getMessage());
        }
        if ("PARENT_NOT_COLLECTION".equals(code)) {
            return conflict("files-native-parent-not-collection", exception.getMessage());
        }
        if ("TREE_CONFLICT".equals(code)
                || "INVALID_TREE_OPERATION".equals(code)) {
            return conflict("files-native-tree-conflict", exception.getMessage());
        }
        if ("INVALID_BLOB_REFERENCE".equals(code)
                || "CONTENT_INTEGRITY_FAILED".equals(code)) {
            return conflict("files-native-metadata-blob-mismatch", exception.getMessage());
        }
        return conflict("files-native-metadata-conflict", exception.getMessage());
    }

    private ApiErrorException unscoped() {
        return conflict(
                "files-native-scope-required",
                "Native Files operations require an explicit organization/space scope.");
    }

    private ApiErrorException notFound(String operation, String message) {
        return new ApiErrorException(
                HttpStatus.NOT_FOUND,
                "file-not-found",
                message,
                Map.of(
                        "module", "files",
                        "operation", operation,
                        "diagnosticsRedacted", true));
    }

    private ApiErrorException precondition(String message) {
        return new ApiErrorException(
                HttpStatus.PRECONDITION_FAILED,
                "files-precondition-failed",
                message,
                Map.of(
                        "module", "files",
                        "adapter", ADAPTER_KEY,
                        "diagnosticsRedacted", true));
    }

    private ApiErrorException conflict(String code, String message) {
        return new ApiErrorException(
                HttpStatus.CONFLICT,
                code,
                message,
                Map.of(
                        "module", "files",
                        "adapter", ADAPTER_KEY,
                        "diagnosticsRedacted", true));
    }

    private final class Scoped implements FilesProviderPort {
        private final FilesRequestScope scope;

        private Scoped(FilesRequestScope scope) {
            this.scope = scope;
        }

        @Override public FilesProviderPort scoped(FilesRequestScope next) { return WeaveNativeFilesAdapter.this.scoped(next); }
        @Override public boolean configured() { return WeaveNativeFilesAdapter.this.configured(); }
        @Override public ProviderReadiness readiness() { return WeaveNativeFilesAdapter.this.readiness(); }
        @Override public ProviderConformanceProfile conformanceProfile() { return WeaveNativeFilesAdapter.this.conformanceProfile(); }
        @Override public VersionedListing list(FilePath path) { return WeaveNativeFilesAdapter.this.list(scope, path); }
        @Override public Optional<VersionedFile> find(FilePath path) { return WeaveNativeFilesAdapter.this.find(scope, path); }
        @Override public Optional<String> providerObjectRef(FilePath path) { return WeaveNativeFilesAdapter.this.providerObjectRef(scope, path); }
        @Override public boolean supportsStableObjectRefs() { return true; }
        @Override public FileContent read(FileId id) { return WeaveNativeFilesAdapter.this.read(scope, id); }
        @Override public FileContent readBounded(FileId id, int maxBytes) { return WeaveNativeFilesAdapter.this.readBounded(scope, id, maxBytes); }
        @Override public FileContent readBoundedIfVersion(FileId id, int maxBytes, FileVersion version) { return WeaveNativeFilesAdapter.this.readBoundedIfVersion(scope, id, maxBytes, version); }
        @Override public boolean supportsBoundedRead() { return true; }
        @Override public boolean supportsConditionalBoundedRead() { return true; }
        @Override public boolean supportsIdentityBoundConditionalRead() { return true; }
        @Override public FileObject write(FileWrite write) { return WeaveNativeFilesAdapter.this.write(scope, write); }
        @Override public CreatedObject writeIfAbsent(FileWrite write) { return WeaveNativeFilesAdapter.this.writeIfAbsent(scope, write); }
        @Override public boolean supportsConditionalWrite() { return true; }
        @Override public FileObject createCollection(FilePath path) { return WeaveNativeFilesAdapter.this.createCollection(scope, path); }
        @Override public CreatedObject createCollectionIfAbsent(FilePath path) { return WeaveNativeFilesAdapter.this.createCollectionIfAbsent(scope, path); }
        @Override public boolean supportsAtomicCollectionCreate() { return true; }
        @Override public FileObject copy(FilePath source, FilePath destination, boolean overwrite) { return WeaveNativeFilesAdapter.this.copy(scope, source, destination, overwrite); }
        @Override public FileObject move(FilePath source, FilePath destination, boolean overwrite) { return WeaveNativeFilesAdapter.this.move(scope, source, destination, overwrite); }
        @Override public void delete(FilePath path, FileVersion expectedVersion) { WeaveNativeFilesAdapter.this.delete(scope, path, expectedVersion); }
    }
}

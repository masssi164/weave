package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.FilesRuntimeProperties;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;

/** Boot composition for the provider-independent native Files application. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "weave.files.provider",
        havingValue = FilesRuntimeProperties.WEAVE_NATIVE,
        matchIfMissing = true)
public class CanonicalNativeFilesConfiguration {

    @Bean
    @Primary
    FilesProviderPort canonicalNativeFilesProvider(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            WeaveNativeFilesProperties properties) {
        return new CanonicalNativeFilesComposition(
                authority,
                blobs,
                Clock.systemUTC(),
                properties.reconciliationLimit());
    }
}

/**
 * Direct Server composition over canonical Files queries and commands.
 *
 * <p>Open protocols and support-safe HTTP failures remain outside the core. JPA and OpenDAL remain
 * southbound implementations of the two injected ports.</p>
 */
final class CanonicalNativeFilesComposition implements FilesProviderPort {

    static final String ADAPTER_KEY = "weave-native";

    private final BlobStorePort blobs;
    private final CanonicalFilesQueries queries;
    private final CanonicalFilesCommands commands;
    private final CanonicalFilesTreeCommands treeCommands;

    CanonicalNativeFilesComposition(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            Clock clock,
            int reconciliationLimit) {
        FilesAuthorityRepository requiredAuthority = Objects.requireNonNull(
                authority,
                "authority must not be null");
        this.blobs = Objects.requireNonNull(blobs, "blobs must not be null");
        Clock requiredClock = Objects.requireNonNull(clock, "clock must not be null");
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
                Map.of(
                        "canonicalId", MappingClass.PORTABLE,
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
        throw unscoped();
    }

    @Override
    public Optional<VersionedFile> find(FilePath path) {
        throw unscoped();
    }

    @Override
    public FileContent read(FileId id) {
        throw unscoped();
    }

    @Override
    public FileObject write(FileWrite write) {
        throw unscoped();
    }

    @Override
    public FileObject createCollection(FilePath path) {
        throw unscoped();
    }

    @Override
    public FileObject copy(
            FilePath source,
            FilePath destination,
            boolean overwrite) {
        throw unscoped();
    }

    @Override
    public FileObject move(
            FilePath source,
            FilePath destination,
            boolean overwrite) {
        throw unscoped();
    }

    @Override
    public void delete(
            FilePath path,
            FileVersion expectedVersion) {
        throw unscoped();
    }

    void readTo(
            FilesRequestScope scope,
            FileId id,
            OutputStream target) {
        try {
            queries.readTo(queryScope(scope), id, target);
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "read-stream");
        }
    }

    CanonicalFilesQueries.ReconciliationReport reconcile(FilesRequestScope scope) {
        try {
            return queries.reconcile(queryScope(scope));
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "reconcile");
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
            case NOT_FOUND -> new ApiErrorException(
                    HttpStatus.NOT_FOUND,
                    "file-not-found",
                    exception.getMessage(),
                    Map.of(
                            "module", "files",
                            "operation", operation,
                            "diagnosticsRedacted", true));
            case NOT_A_COLLECTION -> conflict(
                    "files-native-not-a-collection",
                    exception.getMessage());
            case NOT_A_FILE -> conflict(
                    "files-native-not-a-file",
                    exception.getMessage());
            case INVALID_BLOB_REFERENCE -> conflict(
                    "files-native-metadata-blob-mismatch",
                    exception.getMessage());
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
        return switch (exception.code()) {
            case NOT_FOUND -> new ApiErrorException(
                    HttpStatus.NOT_FOUND,
                    "file-not-found",
                    exception.getMessage(),
                    Map.of(
                            "module", "files",
                            "operation", operation,
                            "diagnosticsRedacted", true));
            case PRECONDITION_FAILED -> new ApiErrorException(
                    HttpStatus.PRECONDITION_FAILED,
                    "files-precondition-failed",
                    exception.getMessage(),
                    Map.of(
                            "module", "files",
                            "adapter", ADAPTER_KEY,
                            "diagnosticsRedacted", true));
            case PARENT_MISSING -> conflict(
                    "files-native-parent-missing",
                    exception.getMessage());
            case PARENT_NOT_COLLECTION -> conflict(
                    "files-native-parent-not-collection",
                    exception.getMessage());
            case TREE_CONFLICT -> conflict(
                    "files-native-tree-conflict",
                    exception.getMessage());
            case INVALID_BLOB_REFERENCE, CONTENT_INTEGRITY_FAILED -> conflict(
                    "files-native-metadata-blob-mismatch",
                    exception.getMessage());
            case METADATA_CONFLICT -> conflict(
                    "files-native-metadata-conflict",
                    exception.getMessage());
        };
    }

    private ApiErrorException unscoped() {
        return conflict(
                "files-native-scope-required",
                "Native Files operations require an explicit organization/space scope.");
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

        @Override
        public FilesProviderPort scoped(FilesRequestScope next) {
            return CanonicalNativeFilesComposition.this.scoped(next);
        }

        @Override
        public boolean configured() {
            return CanonicalNativeFilesComposition.this.configured();
        }

        @Override
        public ProviderReadiness readiness() {
            return CanonicalNativeFilesComposition.this.readiness();
        }

        @Override
        public ProviderConformanceProfile conformanceProfile() {
            return CanonicalNativeFilesComposition.this.conformanceProfile();
        }

        @Override
        public VersionedListing list(FilePath path) {
            try {
                return queries.list(queryScope(scope), path);
            } catch (FilesApplicationException exception) {
                throw queryFailure(exception, "list");
            }
        }

        @Override
        public Optional<VersionedFile> find(FilePath path) {
            try {
                return queries.find(queryScope(scope), path);
            } catch (FilesApplicationException exception) {
                throw queryFailure(exception, "find");
            }
        }

        @Override
        public FileContent read(FileId id) {
            try {
                return queries.read(queryScope(scope), id);
            } catch (FilesApplicationException exception) {
                throw queryFailure(exception, "read");
            }
        }

        @Override
        public FileObject write(FileWrite write) {
            try {
                return commands.write(commandScope(scope), write);
            } catch (FilesCommandException exception) {
                throw commandFailure(exception);
            }
        }

        @Override
        public FileObject createCollection(FilePath path) {
            try {
                return commands.createCollection(commandScope(scope), path);
            } catch (FilesCommandException exception) {
                throw commandFailure(exception);
            }
        }

        @Override
        public FileObject copy(
                FilePath source,
                FilePath destination,
                boolean overwrite) {
            try {
                return treeCommands.copy(
                        commandScope(scope),
                        source,
                        destination,
                        overwrite);
            } catch (FilesTreeCommandException exception) {
                throw treeFailure(exception, "copy");
            }
        }

        @Override
        public FileObject move(
                FilePath source,
                FilePath destination,
                boolean overwrite) {
            try {
                return treeCommands.move(
                        commandScope(scope),
                        source,
                        destination,
                        overwrite);
            } catch (FilesTreeCommandException exception) {
                throw treeFailure(exception, "move");
            }
        }

        @Override
        public void delete(
                FilePath path,
                FileVersion expectedVersion) {
            try {
                treeCommands.delete(
                        commandScope(scope),
                        path,
                        expectedVersion);
            } catch (FilesTreeCommandException exception) {
                throw treeFailure(exception, "delete");
            }
        }
    }
}

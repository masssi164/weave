package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.FilesRuntimeProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.application.CanonicalFilesCommands;
import com.massimotter.weave.backend.files.application.FilesCommandException;
import com.massimotter.weave.backend.files.application.FilesCommandScope;
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
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;

/**
 * Transitional boot composition for the canonical native Files application.
 *
 * <p>The composition is the primary {@link FilesProviderPort}: canonical queries already live in
 * the transitional adapter, canonical create/write behavior lives in {@link CanonicalFilesCommands},
 * and COPY/MOVE/DELETE remain delegated until their own command slices are extracted.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "weave.files.provider",
        havingValue = FilesRuntimeProperties.WEAVE_NATIVE,
        matchIfMissing = true)
public class CanonicalNativeFilesConfiguration {

    private static final String TRANSITIONAL_ADAPTER_BEAN = "weaveNativeFilesAdapter";

    /**
     * The historical adapter is still annotated primary. Demote its bean definition before
     * autowiring so this explicit composition becomes the single primary Files port.
     */
    @Bean
    static BeanFactoryPostProcessor canonicalNativeFilesPrimarySelection() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition(TRANSITIONAL_ADAPTER_BEAN)) {
                beanFactory.getBeanDefinition(TRANSITIONAL_ADAPTER_BEAN).setPrimary(false);
            }
        };
    }

    @Bean
    @Primary
    FilesProviderPort canonicalNativeFilesProvider(
            WeaveNativeFilesAdapter transitionalAdapter,
            FilesAuthorityRepository authority,
            BlobStorePort blobs) {
        return new CanonicalNativeFilesComposition(
                transitionalAdapter,
                authority,
                blobs,
                Clock.systemUTC());
    }
}

/** Thin composition over canonical use cases and the remaining transitional tree mutations. */
final class CanonicalNativeFilesComposition implements FilesProviderPort {

    private final WeaveNativeFilesAdapter transitionalAdapter;
    private final CanonicalFilesCommands commands;

    CanonicalNativeFilesComposition(
            WeaveNativeFilesAdapter transitionalAdapter,
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            Clock clock) {
        this.transitionalAdapter = Objects.requireNonNull(
                transitionalAdapter,
                "transitionalAdapter must not be null");
        this.commands = new CanonicalFilesCommands(authority, blobs, clock);
    }

    @Override
    public FilesProviderPort scoped(FilesRequestScope scope) {
        FilesRequestScope required = Objects.requireNonNull(scope, "scope must not be null");
        return new Scoped(required, transitionalAdapter.scoped(required));
    }

    @Override
    public boolean configured() {
        return transitionalAdapter.configured();
    }

    @Override
    public ProviderReadiness readiness() {
        return transitionalAdapter.readiness();
    }

    @Override
    public ProviderConformanceProfile conformanceProfile() {
        return transitionalAdapter.conformanceProfile();
    }

    @Override
    public VersionedListing list(FilePath path) {
        return transitionalAdapter.list(path);
    }

    @Override
    public Optional<VersionedFile> find(FilePath path) {
        return transitionalAdapter.find(path);
    }

    @Override
    public FileContent read(FileId id) {
        return transitionalAdapter.read(id);
    }

    @Override
    public FileObject write(FileWrite write) {
        return transitionalAdapter.write(write);
    }

    @Override
    public FileObject createCollection(FilePath path) {
        return transitionalAdapter.createCollection(path);
    }

    @Override
    public FileObject copy(FilePath source, FilePath destination, boolean overwrite) {
        return transitionalAdapter.copy(source, destination, overwrite);
    }

    @Override
    public FileObject move(FilePath source, FilePath destination, boolean overwrite) {
        return transitionalAdapter.move(source, destination, overwrite);
    }

    @Override
    public void delete(FilePath path, FileVersion expectedVersion) {
        transitionalAdapter.delete(path, expectedVersion);
    }

    private FilesCommandScope commandScope(FilesRequestScope scope) {
        return new FilesCommandScope(
                scope.organizationRef(),
                scope.spaceRef(),
                scope.providerBindingRevision());
    }

    private ApiErrorException commandFailure(FilesCommandException exception) {
        String code = switch (exception.code()) {
            case PATH_CONFLICT -> "files-native-path-conflict";
            case PARENT_MISSING -> "files-native-parent-missing";
            case PARENT_NOT_COLLECTION -> "files-native-parent-not-collection";
            case METADATA_CONFLICT -> "files-native-metadata-conflict";
        };
        return new ApiErrorException(
                HttpStatus.CONFLICT,
                code,
                exception.getMessage(),
                Map.of(
                        "module", "files",
                        "adapter", WeaveNativeFilesAdapter.ADAPTER_KEY,
                        "diagnosticsRedacted", true));
    }

    private final class Scoped implements FilesProviderPort {

        private final FilesRequestScope scope;
        private final FilesProviderPort transitionalScoped;

        private Scoped(
                FilesRequestScope scope,
                FilesProviderPort transitionalScoped) {
            this.scope = scope;
            this.transitionalScoped = transitionalScoped;
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
            return transitionalScoped.list(path);
        }

        @Override
        public Optional<VersionedFile> find(FilePath path) {
            return transitionalScoped.find(path);
        }

        @Override
        public FileContent read(FileId id) {
            return transitionalScoped.read(id);
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
        public FileObject copy(FilePath source, FilePath destination, boolean overwrite) {
            return transitionalScoped.copy(source, destination, overwrite);
        }

        @Override
        public FileObject move(FilePath source, FilePath destination, boolean overwrite) {
            return transitionalScoped.move(source, destination, overwrite);
        }

        @Override
        public void delete(FilePath path, FileVersion expectedVersion) {
            transitionalScoped.delete(path, expectedVersion);
        }
    }
}

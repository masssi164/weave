package com.massimotter.weave.backend.files.port;

import com.massimotter.weave.backend.files.domain.FilesDomain.FileContent;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedListing;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderCapabilityProbeResult;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.util.Optional;
import java.util.Objects;

public interface FilesProviderPort {

    /**
     * Binds one canonical organization/space scope to this port.
     *
     * <p>Legacy southbound adapters already project into a single backend-owned provider account,
     * so their default implementation is unchanged. Weave-owned adapters must override this method
     * and reject all unscoped data operations.
     */
    default FilesProviderPort scoped(FilesRequestScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        return this;
    }

    boolean configured();

    ProviderReadiness readiness();

    default ProviderCapabilityProbeResult healthProbe() {
        ProviderReadiness readiness = readiness();
        return readiness.available()
                ? ProviderCapabilityProbeResult.available(readiness.supportSafeCode())
                : ProviderCapabilityProbeResult.degraded(readiness.supportSafeCode());
    }

    ProviderConformanceProfile conformanceProfile();

    VersionedListing list(FilePath path);

    Optional<VersionedFile> find(FilePath path);

    FileContent read(FileId id);

    FileObject write(FileWrite write);

    FileObject createCollection(FilePath path);

    FileObject copy(FilePath source, FilePath destination, boolean overwrite);

    FileObject move(FilePath source, FilePath destination, boolean overwrite);

    void delete(FilePath path, FileVersion expectedVersion);

    record FilesRequestScope(String organizationRef, String spaceRef, long providerBindingRevision) {
        public FilesRequestScope {
            organizationRef = required(organizationRef, "organizationRef");
            spaceRef = required(spaceRef, "spaceRef");
            if (providerBindingRevision < 1) {
                throw new IllegalArgumentException("providerBindingRevision must be positive");
            }
        }

        private static String required(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value.trim();
        }
    }
}

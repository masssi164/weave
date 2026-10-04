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

    /** Stable, private object identity independent of the display path; absent means no User mapping. */
    default Optional<String> providerObjectRef(FilePath path) {
        return Optional.empty();
    }

    default boolean supportsStableObjectRefs() {
        return false;
    }

    FileContent read(FileId id);

    /** The User HTTP plane must never allocate an unbounded provider response. */
    default FileContent readBounded(FileId id, int maxBytes) {
        throw new UnsupportedOperationException("bounded Files read is unsupported by this provider");
    }

    /** Bounded GET whose provider enforces and returns the observed strong version. */
    default FileContent readBoundedIfVersion(FileId id, int maxBytes, FileVersion expectedVersion) {
        throw new UnsupportedOperationException("conditional bounded Files read is unsupported by this provider");
    }

    default boolean supportsBoundedRead() {
        return false;
    }

    FileObject write(FileWrite write);

    /** Atomic absent-name creation; implementations must honor the provider precondition. */
    default CreatedObject writeIfAbsent(FileWrite write) {
        throw new UnsupportedOperationException("conditional Files create is unsupported by this provider");
    }

    /** Atomic replacement against the currently observed strong provider version. */
    default FileObject writeIfVersion(FileWrite write, FileVersion expectedVersion) {
        throw new UnsupportedOperationException("conditional Files update is unsupported by this provider");
    }

    default boolean supportsConditionalWrite() {
        return false;
    }

    FileObject createCollection(FilePath path);

    /** MKCOL-style atomic absent-name creation; a preflight lookup alone is insufficient. */
    default CreatedObject createCollectionIfAbsent(FilePath path) {
        throw new UnsupportedOperationException("atomic Files folder creation is unsupported by this provider");
    }

    default boolean supportsAtomicCollectionCreate() {
        return false;
    }

    /** The provider's object identity captured in the atomic creation response. */
    record CreatedObject(FileObject item, String providerObjectRef) {
        public CreatedObject {
            java.util.Objects.requireNonNull(item, "item");
            if (providerObjectRef == null || providerObjectRef.isBlank()) {
                throw new IllegalArgumentException("providerObjectRef is required");
            }
        }
    }

    FileObject copy(FilePath source, FilePath destination, boolean overwrite);

    FileObject move(FilePath source, FilePath destination, boolean overwrite);

    void delete(FilePath path, FileVersion expectedVersion);

    record FilesRequestScope(
            String organizationRef, String spaceRef, long providerBindingRevision, String configurationRef) {
        public FilesRequestScope(String organizationRef, String spaceRef, long providerBindingRevision) {
            this(organizationRef, spaceRef, providerBindingRevision, null);
        }

        public FilesRequestScope {
            organizationRef = required(organizationRef, "organizationRef");
            spaceRef = required(spaceRef, "spaceRef");
            if (providerBindingRevision < 1) {
                throw new IllegalArgumentException("providerBindingRevision must be positive");
            }
            configurationRef = configurationRef == null ? null : required(configurationRef, "configurationRef");
        }

        private static String required(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value.trim();
        }
    }
}

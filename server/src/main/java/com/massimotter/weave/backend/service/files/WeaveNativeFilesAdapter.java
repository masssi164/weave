package com.massimotter.weave.backend.service.files;

import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.TOMBSTONED;

import com.massimotter.weave.backend.config.FilesRuntimeProperties;
import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.application.CanonicalFilesQueries;
import com.massimotter.weave.backend.files.application.FilesApplicationException;
import com.massimotter.weave.backend.files.application.FilesScope;
import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileContent;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedListing;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile.MappingClass;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Transitional native Files composition.
 *
 * <p>Canonical query behavior lives in {@link CanonicalFilesQueries}. Mutation behavior remains
 * here until it is extracted into the canonical Files application layer.</p>
 */
@Component
@Primary
@ConditionalOnProperty(
        name = "weave.files.provider",
        havingValue = FilesRuntimeProperties.WEAVE_NATIVE,
        matchIfMissing = true)
public final class WeaveNativeFilesAdapter implements FilesProviderPort {

    public static final String ADAPTER_KEY = "weave-native";

    private final FilesAuthorityRepository authority;
    private final BlobStorePort blobs;
    private final Clock clock;
    private final CanonicalFilesQueries queries;

    @Autowired
    public WeaveNativeFilesAdapter(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            WeaveNativeFilesProperties properties) {
        this(authority, blobs, Clock.systemUTC(), properties.reconciliationLimit());
    }

    WeaveNativeFilesAdapter(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            Clock clock,
            int reconciliationLimit) {
        this.authority = Objects.requireNonNull(authority, "authority must not be null");
        this.blobs = Objects.requireNonNull(blobs, "blobs must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.queries = new CanonicalFilesQueries(authority, blobs, reconciliationLimit);
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
                Set.of("list", "read", "write", "create_collection", "delete", "copy", "move", "versions", "locks"),
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

    @Override public VersionedListing list(FilePath path) { throw unscoped(); }
    @Override public Optional<VersionedFile> find(FilePath path) { throw unscoped(); }
    @Override public FileContent read(FileId id) { throw unscoped(); }
    @Override public FileObject write(FileWrite write) { throw unscoped(); }
    @Override public FileObject createCollection(FilePath path) { throw unscoped(); }
    @Override public FileObject copy(FilePath source, FilePath destination, boolean overwrite) { throw unscoped(); }
    @Override public FileObject move(FilePath source, FilePath destination, boolean overwrite) { throw unscoped(); }
    @Override public void delete(FilePath path, FileVersion expectedVersion) { throw unscoped(); }

    public ReconciliationReport reconcile(FilesRequestScope scope) {
        CanonicalFilesQueries.ReconciliationReport report = queries.reconcile(queryScope(scope));
        return new ReconciliationReport(
                report.activeMetadataRecords(),
                report.inventoriedBlobs(),
                report.orphanBlobsDeleted(),
                report.inconsistentMetadataRecords());
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

    private FileContent read(FilesRequestScope scope, FileId id) {
        try {
            return queries.read(queryScope(scope), id);
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "read");
        }
    }

    public void readTo(FilesRequestScope scope, FileId id, OutputStream target) {
        try {
            queries.readTo(queryScope(scope), id, target);
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "read-stream");
        }
    }

    private FileObject write(FilesRequestScope scope, FileWrite write) {
        ensureParent(scope, write.path());
        Instant now = Instant.now(clock);
        byte[] content = write.bytes();
        String digest = FilesystemBlobStore.digest(content);
        CanonicalFileRecord existing = authority
                .findByPath(scope.organizationRef(), scope.spaceRef(), write.path())
                .orElse(null);
        if (existing != null && existing.object().kind() != Kind.FILE) {
            throw conflict("files-native-path-conflict", "A collection already exists at the requested Files path.");
        }
        FileId id = existing == null ? canonicalId(scope, write.path()) : existing.object().id();
        BlobReference reference = blobReference(id, digest);
        blobs.putStream(blobScope(scope), reference, new ByteArrayInputStream(content), content.length, digest);
        FileObject object = new FileObject(id, write.path(), Kind.FILE, content.length, write.mediaType(), now, false);
        CanonicalFileRecord activation = active(scope, object, new FileVersion(digest), digest, reference.value(), now);
        try {
            return authority.save(activation).object();
        } catch (DataIntegrityViolationException exception) {
            CanonicalFileRecord concurrent = authority
                    .findByPath(scope.organizationRef(), scope.spaceRef(), write.path())
                    .orElseThrow(() -> conflict(
                            "files-native-metadata-conflict",
                            "The native Files metadata changed concurrently."));
            if (Objects.equals(concurrent.contentDigest(), digest)
                    && Objects.equals(concurrent.storageReference(), reference.value())) {
                return concurrent.object();
            }
            throw conflict(
                    "files-native-metadata-conflict",
                    "The native Files metadata changed concurrently.");
        }
    }

    private FileObject createCollection(FilesRequestScope scope, FilePath path) {
        ensureParent(scope, path);
        if (authority.findByPath(scope.organizationRef(), scope.spaceRef(), path).isPresent()) {
            throw conflict("files-native-path-conflict", "A Files object already exists at the requested path.");
        }
        Instant now = Instant.now(clock);
        FileObject object = new FileObject(canonicalId(scope, path), path, Kind.COLLECTION, 0, null, now, false);
        String version = FilesystemBlobStore.digest(
                ("collection\u0000" + path.value()).getBytes(StandardCharsets.UTF_8));
        try {
            return authority.save(active(scope, object, new FileVersion(version), null, null, now)).object();
        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "files-native-metadata-conflict",
                    "The native Files metadata changed concurrently.");
        }
    }

    private FileObject copy(
            FilesRequestScope scope,
            FilePath source,
            FilePath destination,
            boolean overwrite) {
        requireDistinctTreePaths(source, destination);
        List<CanonicalFileRecord> records =
                authority.activeFiles(scope.organizationRef(), scope.spaceRef());
        List<CanonicalFileRecord> sourceTree = tree(records, source);
        if (sourceTree.isEmpty()) {
            throw notFound("copy");
        }
        ensureParent(records, destination);
        List<CanonicalFileRecord> destinationTree = tree(records, destination);
        if (!destinationTree.isEmpty() && !overwrite) {
            throw precondition("The destination already exists.");
        }
        Instant now = Instant.now(clock);
        List<CanonicalFileRecord> activations = new ArrayList<>();
        for (CanonicalFileRecord sourceRecord : sourceTree) {
            FilePath copiedPath = substitute(sourceRecord.object().path(), source, destination);
            FileId copiedId = canonicalId(scope, copiedPath);
            String storageReference = null;
            String digest = sourceRecord.contentDigest();
            if (sourceRecord.object().kind() == Kind.FILE) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(
                        Math.toIntExact(Math.min(sourceRecord.object().size(), Integer.MAX_VALUE)));
                blobs.readStream(blobScope(scope), reference(sourceRecord), bytes);
                byte[] content = bytes.toByteArray();
                verify(sourceRecord, content);
                BlobReference copiedReference = blobReference(copiedId, digest);
                blobs.putStream(
                        blobScope(scope),
                        copiedReference,
                        new ByteArrayInputStream(content),
                        content.length,
                        digest);
                storageReference = copiedReference.value();
            }
            FileObject copied = new FileObject(
                    copiedId,
                    copiedPath,
                    sourceRecord.object().kind(),
                    sourceRecord.object().size(),
                    sourceRecord.object().mediaType(),
                    now,
                    sourceRecord.object().hidden());
            activations.add(active(
                    scope,
                    copied,
                    sourceRecord.version(),
                    digest,
                    storageReference,
                    now));
        }
        if (sourceTree.size() == 1 && destinationTree.isEmpty()) {
            CanonicalFileRecord activation = activations.getFirst();
            try {
                return authority.save(activation).object();
            } catch (DataIntegrityViolationException exception) {
                cleanupBlobs(scope, List.of(activation));
                throw conflict(
                        "files-native-metadata-conflict",
                        "The native Files metadata changed concurrently.");
            }
        }
        authority.replace(tombstones(destinationTree, now), activations);
        cleanupBlobs(scope, destinationTree);
        return activations.stream()
                .filter(record -> record.object().path().equals(destination))
                .findFirst()
                .orElseThrow()
                .object();
    }

    private FileObject move(
            FilesRequestScope scope,
            FilePath source,
            FilePath destination,
            boolean overwrite) {
        requireDistinctTreePaths(source, destination);
        List<CanonicalFileRecord> records =
                authority.activeFiles(scope.organizationRef(), scope.spaceRef());
        List<CanonicalFileRecord> sourceTree = tree(records, source);
        if (sourceTree.isEmpty()) {
            throw notFound("move");
        }
        ensureParent(records, destination);
        List<CanonicalFileRecord> destinationTree = tree(records, destination);
        if (!destinationTree.isEmpty() && !overwrite) {
            throw precondition("The destination already exists.");
        }
        Instant now = Instant.now(clock);
        if (sourceTree.size() == 1 && destinationTree.isEmpty()) {
            CanonicalFileRecord sourceRecord = sourceTree.getFirst();
            return authority.move(
                    scope.organizationRef(),
                    scope.spaceRef(),
                    sourceRecord.object().id(),
                    source,
                    destination,
                    now).object();
        }
        List<CanonicalFileRecord> moved = sourceTree.stream().map(record -> {
            FilePath movedPath = substitute(record.object().path(), source, destination);
            FileObject object = new FileObject(
                    record.object().id(),
                    movedPath,
                    record.object().kind(),
                    record.object().size(),
                    record.object().mediaType(),
                    now,
                    record.object().hidden());
            String version = FilesystemBlobStore.digest(
                    (record.version().value() + "\u0000" + movedPath.value())
                            .getBytes(StandardCharsets.UTF_8));
            return active(
                    scope,
                    object,
                    new FileVersion(version),
                    record.contentDigest(),
                    record.storageReference(),
                    now);
        }).toList();
        authority.replace(tombstones(destinationTree, now), moved);
        cleanupBlobs(scope, destinationTree);
        return moved.stream()
                .filter(record -> record.object().path().equals(destination))
                .findFirst()
                .orElseThrow()
                .object();
    }

    private void delete(
            FilesRequestScope scope,
            FilePath path,
            FileVersion expectedVersion) {
        List<CanonicalFileRecord> records =
                authority.activeFiles(scope.organizationRef(), scope.spaceRef());
        List<CanonicalFileRecord> target = tree(records, path);
        if (target.isEmpty()) {
            throw notFound("delete");
        }
        CanonicalFileRecord root = byPath(target, path).orElseThrow();
        if (expectedVersion != null
                && expectedVersion.known()
                && !Objects.equals(expectedVersion.value(), root.version().value())) {
            throw precondition("The expected Files version is stale.");
        }
        authority.replace(tombstones(target, Instant.now(clock)), List.of());
        cleanupBlobs(scope, target);
    }

    private void cleanupBlobs(
            FilesRequestScope scope,
            List<CanonicalFileRecord> records) {
        Set<String> retained = authority
                .activeFiles(scope.organizationRef(), scope.spaceRef())
                .stream()
                .map(CanonicalFileRecord::storageReference)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (CanonicalFileRecord record : records) {
            if (record.object().kind() == Kind.FILE
                    && record.storageReference() != null
                    && !retained.contains(record.storageReference())) {
                try {
                    blobs.delete(blobScope(scope), reference(record));
                } catch (ApiErrorException ignored) {
                    // Metadata is already fail-closed; bounded reconciliation owns orphan cleanup.
                }
            }
        }
    }

    private List<CanonicalFileRecord> tombstones(
            List<CanonicalFileRecord> records,
            Instant now) {
        return records.stream().map(record -> new CanonicalFileRecord(
                record.organizationRef(),
                record.spaceRef(),
                record.object(),
                record.version(),
                record.contentDigest(),
                record.storageReference(),
                record.providerBindingRevision(),
                TOMBSTONED,
                later(now, record.observedAt()))).toList();
    }

    private CanonicalFileRecord active(
            FilesRequestScope scope,
            FileObject object,
            FileVersion version,
            String digest,
            String storageReference,
            Instant now) {
        return new CanonicalFileRecord(
                scope.organizationRef(),
                scope.spaceRef(),
                object,
                version,
                digest,
                storageReference,
                scope.providerBindingRevision(),
                ACTIVE,
                now);
    }

    private void verify(CanonicalFileRecord record, byte[] content) {
        String actual = FilesystemBlobStore.digest(content);
        if (record.object().size() != content.length
                || record.contentDigest() == null
                || !MessageDigest.isEqual(
                        record.contentDigest().getBytes(StandardCharsets.US_ASCII),
                        actual.getBytes(StandardCharsets.US_ASCII))) {
            throw conflict(
                    "files-native-metadata-blob-mismatch",
                    "The native Files metadata and blob do not match.");
        }
    }

    private BlobReference reference(CanonicalFileRecord record) {
        if (record.storageReference() == null) {
            throw conflict(
                    "files-native-metadata-blob-mismatch",
                    "The native Files metadata does not reference a blob.");
        }
        try {
            return new BlobReference(record.storageReference());
        } catch (IllegalArgumentException exception) {
            throw conflict(
                    "files-native-metadata-blob-mismatch",
                    "The native Files metadata references an invalid blob.");
        }
    }

    private BlobReference blobReference(FileId id, String digest) {
        return new BlobReference(
                "v1/" + hash(id.value()) + "/" + digest.substring("sha256:".length()));
    }

    private FileId canonicalId(FilesRequestScope scope, FilePath initialPath) {
        return new FileId(
                "file:"
                        + hash(scope.organizationRef()
                        + "\u0000"
                        + scope.spaceRef()
                        + "\u0000"
                        + initialPath.value()));
    }

    private String hash(String value) {
        return FilesystemBlobStore.digest(value.getBytes(StandardCharsets.UTF_8))
                .substring("sha256:".length());
    }

    private void ensureParent(FilesRequestScope scope, FilePath path) {
        ensureParent(
                authority.activeFiles(scope.organizationRef(), scope.spaceRef()),
                path);
    }

    private void ensureParent(List<CanonicalFileRecord> records, FilePath path) {
        FilePath parent = parent(path);
        if (parent.root()) {
            return;
        }
        CanonicalFileRecord parentRecord = byPath(records, parent)
                .orElseThrow(() -> conflict(
                        "files-native-parent-missing",
                        "The parent Files collection does not exist."));
        if (parentRecord.object().kind() != Kind.COLLECTION) {
            throw conflict(
                    "files-native-parent-not-collection",
                    "The parent Files path is not a collection.");
        }
    }

    private FilePath parent(FilePath path) {
        if (path.root() || path.value().lastIndexOf('/') == 0) {
            return new FilePath("/");
        }
        return new FilePath(path.value().substring(0, path.value().lastIndexOf('/')));
    }

    private List<CanonicalFileRecord> tree(
            List<CanonicalFileRecord> records,
            FilePath root) {
        String prefix = root.value() + "/";
        return records.stream()
                .filter(record -> record.object().path().equals(root)
                        || record.object().path().value().startsWith(prefix))
                .sorted(Comparator.comparingInt(record -> record.object().path().value().length()))
                .toList();
    }

    private Optional<CanonicalFileRecord> byPath(
            List<CanonicalFileRecord> records,
            FilePath path) {
        return records.stream()
                .filter(record -> record.object().path().equals(path))
                .findFirst();
    }

    private FilePath substitute(
            FilePath path,
            FilePath source,
            FilePath destination) {
        return new FilePath(
                destination.value() + path.value().substring(source.value().length()));
    }

    private void requireDistinctTreePaths(
            FilePath source,
            FilePath destination) {
        if (source.root()
                || destination.root()
                || source.equals(destination)
                || destination.value().startsWith(source.value() + "/")) {
            throw conflict(
                    "files-native-tree-conflict",
                    "The Files tree operation has an invalid destination.");
        }
    }

    private BlobScope blobScope(FilesRequestScope scope) {
        return new BlobScope(scope.organizationRef(), scope.spaceRef());
    }

    private FilesScope queryScope(FilesRequestScope scope) {
        return new FilesScope(scope.organizationRef(), scope.spaceRef());
    }

    private Instant later(Instant candidate, Instant floor) {
        return candidate.isBefore(floor) ? floor : candidate;
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

    private ApiErrorException unscoped() {
        return conflict(
                "files-native-scope-required",
                "Native Files operations require an explicit organization/space scope.");
    }

    private ApiErrorException notFound(String operation) {
        return new ApiErrorException(
                HttpStatus.NOT_FOUND,
                "file-not-found",
                "The requested file or folder was not found.",
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

        @Override
        public FilesProviderPort scoped(FilesRequestScope next) {
            return WeaveNativeFilesAdapter.this.scoped(next);
        }

        @Override
        public boolean configured() {
            return WeaveNativeFilesAdapter.this.configured();
        }

        @Override
        public ProviderReadiness readiness() {
            return WeaveNativeFilesAdapter.this.readiness();
        }

        @Override
        public ProviderConformanceProfile conformanceProfile() {
            return WeaveNativeFilesAdapter.this.conformanceProfile();
        }

        @Override
        public VersionedListing list(FilePath path) {
            return WeaveNativeFilesAdapter.this.list(scope, path);
        }

        @Override
        public Optional<VersionedFile> find(FilePath path) {
            return WeaveNativeFilesAdapter.this.find(scope, path);
        }

        @Override
        public FileContent read(FileId id) {
            return WeaveNativeFilesAdapter.this.read(scope, id);
        }

        @Override
        public FileObject write(FileWrite write) {
            return WeaveNativeFilesAdapter.this.write(scope, write);
        }

        @Override
        public FileObject createCollection(FilePath path) {
            return WeaveNativeFilesAdapter.this.createCollection(scope, path);
        }

        @Override
        public FileObject copy(
                FilePath source,
                FilePath destination,
                boolean overwrite) {
            return WeaveNativeFilesAdapter.this.copy(
                    scope, source, destination, overwrite);
        }

        @Override
        public FileObject move(
                FilePath source,
                FilePath destination,
                boolean overwrite) {
            return WeaveNativeFilesAdapter.this.move(
                    scope, source, destination, overwrite);
        }

        @Override
        public void delete(FilePath path, FileVersion expectedVersion) {
            WeaveNativeFilesAdapter.this.delete(scope, path, expectedVersion);
        }
    }
}

package com.massimotter.weave.backend.service.files;

import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.TOMBSTONED;

import com.massimotter.weave.backend.config.FilesRuntimeProperties;
import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
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
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.portability.ProviderCapabilityProbeResult;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile.MappingClass;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

/** Weave-owned canonical Files provider adapter backed by JPA metadata and a streaming blob Infrastructure Port. */
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
    private final int reconciliationLimit;

    @Autowired
    public WeaveNativeFilesAdapter(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            WeaveNativeFilesProperties properties) {
        this(authority, blobs, Clock.systemUTC(), properties.reconciliationLimit());
    }

    WeaveNativeFilesAdapter(FilesAuthorityRepository authority, BlobStorePort blobs, Clock clock, int reconciliationLimit) {
        this.authority = Objects.requireNonNull(authority, "authority must not be null");
        this.blobs = Objects.requireNonNull(blobs, "blobs must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.reconciliationLimit = Math.max(1, reconciliationLimit);
    }

    @Override public FilesProviderPort scoped(FilesRequestScope scope) { return new Scoped(Objects.requireNonNull(scope)); }
    @Override public boolean configured() { return blobs.configured(); }
    @Override public ProviderReadiness readiness() { return configured() ? ProviderReadiness.ready("files-native-ready") : ProviderReadiness.degraded("files-native-blob-store-not-configured"); }
    @Override public ProviderCapabilityProbeResult healthProbe() { return configured() ? ProviderCapabilityProbeResult.available("files-native-ready") : ProviderCapabilityProbeResult.unavailable("files-native-blob-store-not-configured"); }

    @Override
    public ProviderConformanceProfile conformanceProfile() {
        return new ProviderConformanceProfile(
                "files", ADAPTER_KEY,
                Set.of("list", "read", "write", "create_collection", "delete", "copy", "move", "versions", "streaming"),
                Map.of(
                        "canonicalId", MappingClass.PORTABLE,
                        "path", MappingClass.PORTABLE,
                        "content", MappingClass.PORTABLE,
                        "mediaType", MappingClass.PORTABLE,
                        "version", MappingClass.PORTABLE,
                        "lock", MappingClass.PORTABLE,
                        "share", MappingClass.UNSUPPORTED),
                true, true, true);
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
        BlobScope blobScope = blobScope(scope);
        List<CanonicalFileRecord> active = authority.activeFiles(scope.organizationRef(), scope.spaceRef());
        Set<BlobReference> referenced = new LinkedHashSet<>();
        int inconsistent = 0;
        for (CanonicalFileRecord record : active) {
            if (record.object().kind() != Kind.FILE) continue;
            try {
                referenced.add(reference(record));
                verifyStream(record, blobScope, reference(record));
            } catch (RuntimeException exception) {
                inconsistent++;
            }
        }
        List<BlobReference> inventory = blobs.inventory(blobScope, reconciliationLimit);
        int deleted = 0;
        for (BlobReference candidate : inventory) {
            if (!referenced.contains(candidate)) {
                blobs.delete(blobScope, candidate);
                deleted++;
            }
        }
        return new ReconciliationReport(active.size(), inventory.size(), deleted, inconsistent);
    }

    public record ReconciliationReport(int activeMetadataRecords, int inventoriedBlobs, int orphanBlobsDeleted, int inconsistentMetadataRecords) {}

    private VersionedListing list(FilesRequestScope scope, FilePath path) {
        List<CanonicalFileRecord> records = authority.activeFiles(scope.organizationRef(), scope.spaceRef());
        if (!path.root()) {
            CanonicalFileRecord requested = byPath(records, path).orElseThrow(() -> notFound("list"));
            if (requested.object().kind() != Kind.COLLECTION) throw conflict("files-native-not-a-collection", "The requested Files path is not a collection.");
        }
        List<CanonicalFileRecord> children = records.stream()
                .filter(record -> parent(record.object().path()).equals(path))
                .sorted(Comparator.comparing(record -> record.object().path().value()))
                .toList();
        Map<FilePath, FileVersion> versions = new LinkedHashMap<>();
        children.forEach(child -> versions.put(child.object().path(), child.version()));
        long used = records.stream().filter(record -> record.object().kind() == Kind.FILE).mapToLong(record -> record.object().size()).sum();
        return new VersionedListing(new FileListing(path, children.stream().map(CanonicalFileRecord::object).toList(), new FileQuota(null, used)), listingVersion(path, children), versions);
    }

    private Optional<VersionedFile> find(FilesRequestScope scope, FilePath path) {
        if (path.root()) {
            return Optional.of(new VersionedFile(rootObject(), listingVersion(path,
                    authority.activeFiles(scope.organizationRef(), scope.spaceRef()).stream()
                            .filter(record -> parent(record.object().path()).equals(path)).toList())));
        }
        return authority.findByPath(scope.organizationRef(), scope.spaceRef(), path).map(record -> new VersionedFile(record.object(), record.version()));
    }

    private FileContent read(FilesRequestScope scope, FileId id) {
        CanonicalFileRecord record = authority.findById(scope.organizationRef(), scope.spaceRef(), id).orElseThrow(() -> notFound("read"));
        if (record.object().kind() != Kind.FILE) throw conflict("files-native-not-a-file", "The requested Files object has no file content.");
        ByteArrayOutputStream target = new ByteArrayOutputStream(Math.toIntExact(Math.min(record.object().size(), Integer.MAX_VALUE)));
        blobs.readStream(blobScope(scope), reference(record), target);
        byte[] content = target.toByteArray();
        verify(record, content);
        return new FileContent(record.object(), content);
    }

    private FileObject write(FilesRequestScope scope, FileWrite write) {
        ensureParent(scope, write.path());
        Instant now = Instant.now(clock);
        byte[] content = write.bytes();
        String digest = FilesystemBlobStore.digest(content);
        CanonicalFileRecord existing = authority.findByPath(scope.organizationRef(), scope.spaceRef(), write.path()).orElse(null);
        if (existing != null && existing.object().kind() != Kind.FILE) throw conflict("files-native-path-conflict", "A collection already exists at the requested Files path.");
        FileId id = existing == null ? canonicalId(scope, write.path()) : existing.object().id();
        BlobReference reference = blobReference(id, digest);
        blobs.putStream(blobScope(scope), reference, new ByteArrayInputStream(content), content.length, digest);
        FileObject object = new FileObject(id, write.path(), Kind.FILE, content.length, write.mediaType(), now, false);
        CanonicalFileRecord activation = active(scope, object, new FileVersion(digest), digest, reference.value(), now);
        try {
            return authority.save(activation).object();
        } catch (DataIntegrityViolationException exception) {
            CanonicalFileRecord concurrent = authority.findByPath(scope.organizationRef(), scope.spaceRef(), write.path())
                    .orElseThrow(() -> conflict("files-native-metadata-conflict", "The native Files metadata changed concurrently."));
            if (Objects.equals(concurrent.contentDigest(), digest) && Objects.equals(concurrent.storageReference(), reference.value())) return concurrent.object();
            throw conflict("files-native-metadata-conflict", "The native Files metadata changed concurrently.");
        }
    }

    private FileObject createCollection(FilesRequestScope scope, FilePath path) {
        ensureParent(scope, path);
        if (authority.findByPath(scope.organizationRef(), scope.spaceRef(), path).isPresent()) throw conflict("files-native-path-conflict", "A Files object already exists at the requested path.");
        Instant now = Instant.now(clock);
        FileObject object = new FileObject(canonicalId(scope, path), path, Kind.COLLECTION, 0, null, now, false);
        String version = FilesystemBlobStore.digest(("collection\u0000" + path.value()).getBytes(StandardCharsets.UTF_8));
        try { return authority.save(active(scope, object, new FileVersion(version), null, null, now)).object(); }
        catch (DataIntegrityViolationException exception) { throw conflict("files-native-metadata-conflict", "The native Files metadata changed concurrently."); }
    }

    private FileObject copy(FilesRequestScope scope, FilePath source, FilePath destination, boolean overwrite) {
        requireDistinctTreePaths(source, destination);
        List<CanonicalFileRecord> records = authority.activeFiles(scope.organizationRef(), scope.spaceRef());
        List<CanonicalFileRecord> sourceTree = tree(records, source);
        if (sourceTree.isEmpty()) throw notFound("copy");
        ensureParent(records, destination);
        List<CanonicalFileRecord> destinationTree = tree(records, destination);
        if (!destinationTree.isEmpty() && !overwrite) throw precondition("The destination already exists.");
        Instant now = Instant.now(clock);
        List<CanonicalFileRecord> activations = new ArrayList<>();
        for (CanonicalFileRecord sourceRecord : sourceTree) {
            FilePath copiedPath = substitute(sourceRecord.object().path(), source, destination);
            FileId copiedId = canonicalId(scope, copiedPath);
            String storageReference = null;
            String digest = sourceRecord.contentDigest();
            if (sourceRecord.object().kind() == Kind.FILE) {
                BlobReference copiedReference = blobReference(copiedId, digest);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.toIntExact(Math.min(sourceRecord.object().size(), Integer.MAX_VALUE)));
                blobs.readStream(blobScope(scope), reference(sourceRecord), bytes);
                byte[] content = bytes.toByteArray();
                verify(sourceRecord, content);
                blobs.putStream(blobScope(scope), copiedReference, new ByteArrayInputStream(content), content.length, digest);
                storageReference = copiedReference.value();
            }
            FileObject copied = new FileObject(copiedId, copiedPath, sourceRecord.object().kind(), sourceRecord.object().size(), sourceRecord.object().mediaType(), now, sourceRecord.object().hidden());
            activations.add(active(scope, copied, sourceRecord.version(), digest, storageReference, now));
        }
        authority.replace(tombstones(destinationTree, now), activations);
        cleanupBlobs(scope, destinationTree);
        return activations.stream().filter(record -> record.object().path().equals(destination)).findFirst().orElseThrow().object();
    }

    private FileObject move(FilesRequestScope scope, FilePath source, FilePath destination, boolean overwrite) {
        requireDistinctTreePaths(source, destination);
        List<CanonicalFileRecord> records = authority.activeFiles(scope.organizationRef(), scope.spaceRef());
        List<CanonicalFileRecord> sourceTree = tree(records, source);
        if (sourceTree.isEmpty()) throw notFound("move");
        ensureParent(records, destination);
        List<CanonicalFileRecord> destinationTree = tree(records, destination);
        if (!destinationTree.isEmpty() && !overwrite) throw precondition("The destination already exists.");
        Instant now = Instant.now(clock);
        if (sourceTree.size() == 1 && destinationTree.isEmpty()) {
            CanonicalFileRecord sourceRecord = sourceTree.getFirst();
            return authority.move(scope.organizationRef(), scope.spaceRef(), sourceRecord.object().id(), source, destination, now).object();
        }
        List<CanonicalFileRecord> moved = sourceTree.stream().map(record -> {
            FilePath movedPath = substitute(record.object().path(), source, destination);
            FileObject object = new FileObject(record.object().id(), movedPath, record.object().kind(), record.object().size(), record.object().mediaType(), now, record.object().hidden());
            String version = FilesystemBlobStore.digest((record.version().value() + "\u0000" + movedPath.value()).getBytes(StandardCharsets.UTF_8));
            return active(scope, object, new FileVersion(version), record.contentDigest(), record.storageReference(), now);
        }).toList();
        authority.replace(tombstones(destinationTree, now), moved);
        cleanupBlobs(scope, destinationTree);
        return moved.stream().filter(record -> record.object().path().equals(destination)).findFirst().orElseThrow().object();
    }

    private void delete(FilesRequestScope scope, FilePath path, FileVersion expectedVersion) {
        List<CanonicalFileRecord> records = authority.activeFiles(scope.organizationRef(), scope.spaceRef());
        List<CanonicalFileRecord> target = tree(records, path);
        if (target.isEmpty()) throw notFound("delete");
        CanonicalFileRecord root = byPath(target, path).orElseThrow();
        if (expectedVersion != null && expectedVersion.known() && !Objects.equals(expectedVersion.value(), root.version().value())) throw precondition("The expected Files version is stale.");
        authority.replace(tombstones(target, Instant.now(clock)), List.of());
        cleanupBlobs(scope, target);
    }

    private void cleanupBlobs(FilesRequestScope scope, List<CanonicalFileRecord> records) {
        Set<String> retained = authority.activeFiles(scope.organizationRef(), scope.spaceRef()).stream().map(CanonicalFileRecord::storageReference).filter(Objects::nonNull).collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (CanonicalFileRecord record : records) {
            if (record.object().kind() == Kind.FILE && record.storageReference() != null && !retained.contains(record.storageReference())) {
                try { blobs.delete(blobScope(scope), reference(record)); } catch (ApiErrorException ignored) { }
            }
        }
    }

    private void verifyStream(CanonicalFileRecord record, BlobScope scope, BlobReference reference) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            CountingOutputStream count = new CountingOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
            blobs.readStream(scope, reference, count);
            String actual = "sha256:" + java.util.HexFormat.of().formatHex(digest.digest());
            if (count.count != record.object().size() || !Objects.equals(actual, record.contentDigest())) throw conflict("files-native-integrity-mismatch", "The native Files content does not match its canonical metadata.");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void verify(CanonicalFileRecord record, byte[] bytes) {
        if (bytes.length != record.object().size() || !Objects.equals(FilesystemBlobStore.digest(bytes), record.contentDigest())) throw conflict("files-native-integrity-mismatch", "The native Files content does not match its canonical metadata.");
    }

    private CanonicalFileRecord active(FilesRequestScope scope, FileObject object, FileVersion version, String digest, String storageReference, Instant now) {
        return new CanonicalFileRecord(scope.organizationRef(), scope.spaceRef(), object, version, digest, storageReference, scope.providerBindingRevision(), ACTIVE, now);
    }

    private List<CanonicalFileRecord> tombstones(List<CanonicalFileRecord> records, Instant now) {
        return records.stream().map(record -> new CanonicalFileRecord(record.organizationRef(), record.spaceRef(), record.object(), record.version(), record.contentDigest(), record.storageReference(), record.providerBindingRevision(), TOMBSTONED, now)).toList();
    }

    private void ensureParent(FilesRequestScope scope, FilePath path) { ensureParent(authority.activeFiles(scope.organizationRef(), scope.spaceRef()), path); }
    private void ensureParent(List<CanonicalFileRecord> records, FilePath path) {
        FilePath parent = parent(path);
        if (parent.root()) return;
        CanonicalFileRecord parentRecord = byPath(records, parent).orElseThrow(() -> conflict("files-native-parent-missing", "The parent collection does not exist."));
        if (parentRecord.object().kind() != Kind.COLLECTION) throw conflict("files-native-parent-invalid", "The parent Files object is not a collection.");
    }

    private List<CanonicalFileRecord> tree(List<CanonicalFileRecord> records, FilePath root) {
        String prefix = root.value().endsWith("/") ? root.value() : root.value() + "/";
        return records.stream().filter(record -> record.object().path().equals(root) || record.object().path().value().startsWith(prefix)).sorted(Comparator.comparing(record -> record.object().path().value())).toList();
    }

    private Optional<CanonicalFileRecord> byPath(List<CanonicalFileRecord> records, FilePath path) { return records.stream().filter(record -> record.object().path().equals(path)).findFirst(); }
    private FilePath substitute(FilePath path, FilePath source, FilePath destination) { return path.equals(source) ? destination : new FilePath(destination.value() + path.value().substring(source.value().length())); }

    private void requireDistinctTreePaths(FilePath source, FilePath destination) {
        if (source == null || destination == null || source.root() || destination.root()) throw conflict("files-native-path-invalid", "The requested Files source or destination path is invalid.");
        if (source.equals(destination)) throw precondition("The source and destination are identical.");
        if (destination.value().startsWith(source.value() + "/")) throw conflict("files-native-tree-cycle", "A collection cannot be copied or moved into its own subtree.");
    }

    private BlobReference reference(CanonicalFileRecord record) {
        if (record.storageReference() == null || record.storageReference().isBlank()) throw conflict("files-native-storage-reference-missing", "The native Files metadata has no private storage reference.");
        return new BlobReference(record.storageReference());
    }

    private BlobReference blobReference(FileId id, String digest) {
        return new BlobReference(storageToken(id.value()) + "/" + storageToken(digest));
    }

    private BlobScope blobScope(FilesRequestScope scope) { return new BlobScope(scope.organizationRef(), scope.spaceRef()); }

    private FileId canonicalId(FilesRequestScope scope, FilePath path) {
        String material = scope.organizationRef() + "\u0000" + scope.spaceRef() + "\u0000" + path.value();
        return new FileId("weave-" + storageToken(FilesystemBlobStore.digest(material.getBytes(StandardCharsets.UTF_8))).substring(0, 32));
    }

    private String storageToken(String value) {
        String token = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (token.startsWith("sha256:")) token = token.substring("sha256:".length());
        return token.replace(':', '-');
    }

    private FileObject rootObject() { return new FileObject(new FileId("weave-root"), new FilePath("/"), Kind.COLLECTION, 0, null, Instant.EPOCH, false); }
    private FilePath parent(FilePath path) { if (path == null || path.root()) return new FilePath("/"); int slash = path.value().lastIndexOf('/'); return slash <= 0 ? new FilePath("/") : new FilePath(path.value().substring(0, slash)); }
    private FileVersion listingVersion(FilePath path, List<CanonicalFileRecord> children) {
        StringBuilder material = new StringBuilder(path.value());
        children.forEach(child -> material.append('\u0000').append(child.object().id().value()).append('\u0000').append(child.version().value()));
        return new FileVersion(FilesystemBlobStore.digest(material.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private ApiErrorException unscoped() { return conflict("files-native-scope-required", "Weave-native Files operations require an organization/space scope."); }
    private ApiErrorException notFound(String operation) { return new ApiErrorException(HttpStatus.NOT_FOUND, "FILES_NOT_FOUND", "The requested Files object was not found.", Map.of("operation", operation, "supportSafe", true)); }
    private ApiErrorException precondition(String message) { return new ApiErrorException(HttpStatus.PRECONDITION_FAILED, "FILES_PRECONDITION_FAILED", message, Map.of("supportSafe", true)); }
    private ApiErrorException conflict(String code, String message) { return new ApiErrorException(HttpStatus.CONFLICT, code, message, Map.of("supportSafe", true)); }

    private final class Scoped implements FilesProviderPort {
        private final FilesRequestScope scope;
        private Scoped(FilesRequestScope scope) { this.scope = scope; }
        @Override public FilesProviderPort scoped(FilesRequestScope ignored) { return this; }
        @Override public boolean configured() { return WeaveNativeFilesAdapter.this.configured(); }
        @Override public ProviderReadiness readiness() { return WeaveNativeFilesAdapter.this.readiness(); }
        @Override public ProviderCapabilityProbeResult healthProbe() { return WeaveNativeFilesAdapter.this.healthProbe(); }
        @Override public ProviderConformanceProfile conformanceProfile() { return WeaveNativeFilesAdapter.this.conformanceProfile(); }
        @Override public VersionedListing list(FilePath path) { return WeaveNativeFilesAdapter.this.list(scope, path); }
        @Override public Optional<VersionedFile> find(FilePath path) { return WeaveNativeFilesAdapter.this.find(scope, path); }
        @Override public FileContent read(FileId id) { return WeaveNativeFilesAdapter.this.read(scope, id); }
        @Override public FileObject write(FileWrite write) { return WeaveNativeFilesAdapter.this.write(scope, write); }
        @Override public FileObject createCollection(FilePath path) { return WeaveNativeFilesAdapter.this.createCollection(scope, path); }
        @Override public FileObject copy(FilePath source, FilePath destination, boolean overwrite) { return WeaveNativeFilesAdapter.this.copy(scope, source, destination, overwrite); }
        @Override public FileObject move(FilePath source, FilePath destination, boolean overwrite) { return WeaveNativeFilesAdapter.this.move(scope, source, destination, overwrite); }
        @Override public void delete(FilePath path, FileVersion expectedVersion) { WeaveNativeFilesAdapter.this.delete(scope, path, expectedVersion); }
    }

    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private long count;
        private CountingOutputStream(OutputStream delegate) { this.delegate = delegate; }
        @Override public void write(int b) throws IOException { delegate.write(b); count++; }
        @Override public void write(byte[] b, int off, int len) throws IOException { delegate.write(b, off, len); count += len; }
    }
}

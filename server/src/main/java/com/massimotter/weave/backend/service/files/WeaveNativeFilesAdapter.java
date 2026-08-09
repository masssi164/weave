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
        this.authority = Objects.requireNonNull(authority, "authority");
        this.blobs = Objects.requireNonNull(blobs, "blobs");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.reconciliationLimit = reconciliationLimit;
    }

    @Override public boolean configured() { return true; }
    @Override public ProviderReadiness readiness() { return ProviderReadiness.ready("files-native-ready"); }
    @Override public ProviderConformanceProfile conformanceProfile() { return new ProviderConformanceProfile("files", ADAPTER_KEY, Set.of("list", "read", "write", "create_collection", "delete", "copy", "move", "versions", "locks"), Map.of("path", MappingClass.PORTABLE, "content", MappingClass.PORTABLE, "mediaType", MappingClass.PORTABLE, "version", MappingClass.PORTABLE, "lock", MappingClass.PORTABLE, "share", MappingClass.UNSUPPORTED), true, true, true); }
    @Override public FilesProviderPort scoped(FilesRequestScope scope) { return new Scoped(Objects.requireNonNull(scope, "scope")); }
    @Override public VersionedListing list(FilePath path) { throw unscoped(); }
    @Override public Optional<VersionedFile> find(FilePath path) { throw unscoped(); }
    @Override public FileContent read(FileId id) { throw unscoped(); }
    @Override public FileObject write(FileWrite write) { throw unscoped(); }
    @Override public FileObject createCollection(FilePath path) { throw unscoped(); }
    @Override public FileObject copy(FilePath source, FilePath destination, boolean overwrite) { throw unscoped(); }
    @Override public FileObject move(FilePath source, FilePath destination, boolean overwrite) { throw unscoped(); }
    @Override public void delete(FilePath path, FileVersion expectedVersion) { throw unscoped(); }

    private VersionedListing list(FilesRequestScope scope, FilePath path) {
        List<CanonicalFileRecord> records = authority.activeFiles(scope.organizationRef(), scope.spaceRef());
        if (!path.root()) {
            CanonicalFileRecord parentRecord = byPath(records, path).orElseThrow(() -> notFound("list"));
            if (parentRecord.object().kind() != Kind.COLLECTION) throw conflict("files-native-not-a-collection", "The requested Files path is not a collection.");
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
        List<CanonicalFileRecord> records = authority.activeFiles(scope.organizationRef(), scope.spaceRef());
        ensureParent(records, write.path());
        CanonicalFileRecord current = byPath(records, write.path()).orElse(null);
        if (current != null && current.object().kind() != Kind.FILE) throw conflict("files-native-not-a-file", "The requested Files path is not a file.");
        if (write.expectedVersion() != null && write.expectedVersion().known()) {
            if (current == null || !Objects.equals(write.expectedVersion().value(), current.version().value())) throw precondition("The expected Files version is stale.");
        }
        byte[] content = write.content();
        String digest = FilesystemBlobStore.digest(content);
        FileId id = current == null ? canonicalId(scope, write.path()) : current.object().id();
        BlobReference blob = blobReference(id, digest);
        blobs.putStream(blobScope(scope), blob, new ByteArrayInputStream(content), content.length, digest);
        Instant now = Instant.now(clock);
        FileObject object = new FileObject(id, write.path(), Kind.FILE, content.length, write.mediaType(), now, write.hidden());
        CanonicalFileRecord saved = active(scope, object, new FileVersion(digest), digest, blob.value(), now);
        try {
            return authority.save(saved).object();
        } catch (RuntimeException exception) {
            if (current == null) blobs.delete(blobScope(scope), blob);
            if (exception instanceof DataIntegrityViolationException) throw conflict("files-native-metadata-conflict", "The native Files metadata changed concurrently.");
            throw exception;
        }
    }

    private FileObject createCollection(FilesRequestScope scope, FilePath path) {
        List<CanonicalFileRecord> records = authority.activeFiles(scope.organizationRef(), scope.spaceRef());
        if (byPath(records, path).isPresent()) throw precondition("The collection already exists.");
        ensureParent(records, path);
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
        if (sourceTree.size() == 1 && destinationTree.isEmpty()) {
            CanonicalFileRecord activation = activations.getFirst();
            try { return authority.save(activation).object(); }
            catch (DataIntegrityViolationException exception) {
                cleanupBlobs(scope, List.of(activation));
                throw conflict("files-native-metadata-conflict", "The native Files metadata changed concurrently.");
            }
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
                try { blobs.delete(blobScope(scope), new BlobReference(record.storageReference())); } catch (RuntimeException ignored) { }
            }
        }
    }

    public ReconciliationReport reconcile(FilesRequestScope scope) {
        List<CanonicalFileRecord> records = authority.activeFiles(scope.organizationRef(), scope.spaceRef());
        Set<String> referenced = records.stream().map(CanonicalFileRecord::storageReference).filter(Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> inventory = blobs.inventory(blobScope(scope), reconciliationLimit);
        List<String> missing = referenced.stream().filter(reference -> !inventory.contains(reference)).sorted().limit(reconciliationLimit).toList();
        List<String> orphans = inventory.stream().filter(reference -> !referenced.contains(reference)).sorted().limit(reconciliationLimit).toList();
        orphans.forEach(reference -> blobs.delete(blobScope(scope), new BlobReference(reference)));
        return new ReconciliationReport(missing, orphans);
    }

    public void readTo(FilesRequestScope scope, FileId id, OutputStream target) {
        CanonicalFileRecord record = authority.findById(scope.organizationRef(), scope.spaceRef(), id).orElseThrow(() -> notFound("read"));
        if (record.object().kind() != Kind.FILE) throw conflict("files-native-not-a-file", "The requested Files object has no file content.");
        MessageDigest digest = sha256();
        long[] bytes = {0};
        OutputStream bounded = new DigestOutputStream(new OutputStream() {
            @Override public void write(int value) throws IOException { target.write(value); bytes[0]++; }
            @Override public void write(byte[] value, int offset, int length) throws IOException { target.write(value, offset, length); bytes[0] += length; }
        }, digest);
        blobs.readStream(blobScope(scope), reference(record), bounded);
        if (bytes[0] != record.object().size() || !hex(digest.digest()).equals(record.contentDigest())) throw conflict("files-native-content-digest-mismatch", "Native Files content integrity verification failed.");
    }

    private CanonicalFileRecord active(FilesRequestScope scope, FileObject object, FileVersion version, String digest, String storageReference, Instant now) {
        return new CanonicalFileRecord(scope.organizationRef(), scope.spaceRef(), object, version, digest, storageReference, ACTIVE, now, now, 0);
    }

    private List<CanonicalFileRecord> tombstones(List<CanonicalFileRecord> records, Instant now) {
        return records.stream().map(record -> new CanonicalFileRecord(record.organizationRef(), record.spaceRef(), record.object(), record.version(), record.contentDigest(), record.storageReference(), TOMBSTONED, record.createdAt(), now, record.rowVersion())).toList();
    }

    private BlobScope blobScope(FilesRequestScope scope) { return new BlobScope(scope.organizationRef(), scope.spaceRef()); }
    private BlobReference reference(CanonicalFileRecord record) { return new BlobReference(record.storageReference()); }
    private BlobReference blobReference(FileId id, String digest) { return new BlobReference("objects/" + id.value() + "/" + digest); }

    private FileId canonicalId(FilesRequestScope scope, FilePath path) {
        return new FileId(java.util.UUID.nameUUIDFromBytes((scope.organizationRef() + "\u0000" + scope.spaceRef() + "\u0000" + path.value()).getBytes(StandardCharsets.UTF_8)).toString());
    }

    private List<CanonicalFileRecord> tree(List<CanonicalFileRecord> records, FilePath root) {
        String prefix = root.root() ? "/" : root.value() + "/";
        return records.stream().filter(record -> record.object().path().equals(root) || record.object().path().value().startsWith(prefix)).sorted(Comparator.comparing(record -> record.object().path().value())).toList();
    }

    private Optional<CanonicalFileRecord> byPath(List<CanonicalFileRecord> records, FilePath path) { return records.stream().filter(record -> record.object().path().equals(path)).findFirst(); }
    private void ensureParent(List<CanonicalFileRecord> records, FilePath path) { FilePath parent = parent(path); if (parent.root()) return; CanonicalFileRecord record = byPath(records, parent).orElseThrow(() -> conflict("files-native-parent-missing", "The parent collection does not exist.")); if (record.object().kind() != Kind.COLLECTION) throw conflict("files-native-parent-not-collection", "The parent Files object is not a collection."); }
    private FilePath parent(FilePath path) { if (path.root()) return path; int split = path.value().lastIndexOf('/'); return split <= 0 ? FilePath.rootPath() : new FilePath(path.value().substring(0, split)); }
    private FilePath substitute(FilePath path, FilePath source, FilePath destination) { String suffix = path.value().substring(source.value().length()); return new FilePath(destination.value() + suffix); }
    private void requireDistinctTreePaths(FilePath source, FilePath destination) { if (source.equals(destination) || destination.value().startsWith(source.value() + "/")) throw conflict("files-native-invalid-tree-operation", "The destination must be outside the source tree."); }
    private FileObject rootObject() { return new FileObject(new FileId("root"), FilePath.rootPath(), Kind.COLLECTION, 0, null, Instant.EPOCH, false); }
    private FileVersion listingVersion(FilePath path, List<CanonicalFileRecord> records) { StringBuilder value = new StringBuilder(path.value()); records.forEach(record -> value.append('\n').append(record.object().id().value()).append(':').append(record.version().value()).append(':').append(record.rowVersion())); return new FileVersion(FilesystemBlobStore.digest(value.toString().getBytes(StandardCharsets.UTF_8))); }
    private void verify(CanonicalFileRecord record, byte[] content) { if (content.length != record.object().size() || !FilesystemBlobStore.digest(content).equals(record.contentDigest())) throw conflict("files-native-content-digest-mismatch", "Native Files content integrity verification failed."); }
    private static MessageDigest sha256() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
    private static String hex(byte[] bytes) { return java.util.HexFormat.of().formatHex(bytes); }

    private ApiErrorException unscoped() { return conflict("files-native-scope-required", "Native Files operations require an explicit organization/space scope."); }
    private ApiErrorException notFound(String operation) { return new ApiErrorException(HttpStatus.NOT_FOUND, "files-native-" + operation + "-not-found", "The requested Files object was not found.", Map.of()); }
    private ApiErrorException conflict(String code, String message) { return new ApiErrorException(HttpStatus.CONFLICT, code, message, Map.of()); }
    private ApiErrorException precondition(String message) { return new ApiErrorException(HttpStatus.PRECONDITION_FAILED, "files-native-precondition-failed", message, Map.of()); }

    public record ReconciliationReport(List<String> missingBlobReferences, List<String> deletedOrphanReferences) { public ReconciliationReport { missingBlobReferences = List.copyOf(missingBlobReferences); deletedOrphanReferences = List.copyOf(deletedOrphanReferences); } }

    private final class Scoped implements FilesProviderPort {
        private final FilesRequestScope scope;
        private Scoped(FilesRequestScope scope) { this.scope = scope; }
        @Override public boolean configured() { return WeaveNativeFilesAdapter.this.configured(); }
        @Override public ProviderReadiness readiness() { return WeaveNativeFilesAdapter.this.readiness(); }
        @Override public ProviderConformanceProfile conformanceProfile() { return WeaveNativeFilesAdapter.this.conformanceProfile(); }
        @Override public FilesProviderPort scoped(FilesRequestScope ignored) { return new Scoped(Objects.requireNonNull(ignored, "scope")); }
        @Override public VersionedListing list(FilePath path) { return WeaveNativeFilesAdapter.this.list(scope, path); }
        @Override public Optional<VersionedFile> find(FilePath path) { return WeaveNativeFilesAdapter.this.find(scope, path); }
        @Override public FileContent read(FileId id) { return WeaveNativeFilesAdapter.this.read(scope, id); }
        @Override public FileObject write(FileWrite write) { return WeaveNativeFilesAdapter.this.write(scope, write); }
        @Override public FileObject createCollection(FilePath path) { return WeaveNativeFilesAdapter.this.createCollection(scope, path); }
        @Override public FileObject copy(FilePath source, FilePath destination, boolean overwrite) { return WeaveNativeFilesAdapter.this.copy(scope, source, destination, overwrite); }
        @Override public FileObject move(FilePath source, FilePath destination, boolean overwrite) { return WeaveNativeFilesAdapter.this.move(scope, source, destination, overwrite); }
        @Override public void delete(FilePath path, FileVersion expectedVersion) { WeaveNativeFilesAdapter.this.delete(scope, path, expectedVersion); }
    }
}
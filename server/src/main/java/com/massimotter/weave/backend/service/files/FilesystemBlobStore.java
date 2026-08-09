package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.opendal.AsyncOperator;
import org.apache.opendal.Entry;
import org.apache.opendal.ListOptions;
import org.apache.opendal.OpenDALException;
import org.apache.opendal.Operator;
import org.apache.opendal.layer.ConcurrentLimitLayer;
import org.apache.opendal.layer.RetryLayer;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Apache OpenDAL-backed private filesystem blob store for the weave-native Files provider.
 *
 * <p>NIO is restricted to establishing and validating the private filesystem sandbox plus durability permissions.
 * Blob read/write/list/delete/rename are performed exclusively through OpenDAL's filesystem operator.</p>
 */
@Component
@Primary
public final class FilesystemBlobStore implements BlobStorePort {

    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-------");

    private final Path root;
    private final long maximumBlobBytes;
    private final AsyncOperator asyncOperator;
    private final Operator operator;

    public FilesystemBlobStore(WeaveNativeFilesProperties properties) {
        try {
            root = properties.filesystemRoot().toAbsolutePath().normalize();
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw unsafePath();
            enforcePermissions(root, DIRECTORY_PERMISSIONS);
            maximumBlobBytes = properties.maximumBlobBytes();
            asyncOperator = AsyncOperator.of("fs", Map.of("root", root.toString()))
                    .layer(RetryLayer.builder().maxTimes(4).jitter(true).build())
                    .layer(new ConcurrentLimitLayer(32));
            operator = asyncOperator.blocking();
            requireCapabilities();
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (IOException | OpenDALException exception) {
            throw unavailable("files-native-opendal-init-failed");
        }
    }

    @Override public boolean configured() { return true; }

    @Override
    public BlobReceipt put(BlobScope scope, BlobReference reference, byte[] bytes, String expectedDigest) {
        byte[] content = bytes == null ? new byte[0] : bytes.clone();
        requireWithinLimit(content.length, "files-native-blob-too-large");
        String actualDigest = digest(content);
        if (!MessageDigest.isEqual(actualDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII), requiredDigest(expectedDigest).getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw error(HttpStatus.CONFLICT, "files-native-blob-digest-mismatch", "The native Files blob digest did not match the requested digest.");
        }
        String key = key(scope, reference);
        try {
            validateSandbox(scope, reference, true);
            if (exists(key)) return verifyExisting(key, reference, actualDigest, content.length);
            String temporary = parent(key) + ".pending-" + UUID.randomUUID();
            operator.write(temporary, content);
            try {
                operator.rename(temporary, key);
            } catch (OpenDALException concurrentPublish) {
                operator.delete(temporary);
                if (exists(key)) return verifyExisting(key, reference, actualDigest, content.length);
                throw concurrentPublish;
            }
            Path target = resolvedPathForTest(scope, reference);
            enforcePermissions(target, FILE_PERMISSIONS);
            forceFile(target);
            forceDirectory(target.getParent());
            return new BlobReceipt(reference, actualDigest, content.length);
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (IOException | OpenDALException exception) {
            throw map(exception, "files-native-blob-write-failed");
        }
    }

    @Override
    public byte[] read(BlobScope scope, BlobReference reference) {
        String key = key(scope, reference);
        try {
            validateSandbox(scope, reference, false);
            if (!exists(key)) throw conflict("files-native-blob-missing");
            long size = operator.stat(key).getContentLength();
            requireWithinLimit(size, "files-native-blob-size-invalid");
            byte[] value = operator.read(key);
            requireWithinLimit(value.length, "files-native-blob-size-invalid");
            return value;
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (IOException | OpenDALException exception) {
            throw map(exception, "files-native-blob-read-failed");
        }
    }

    @Override
    public void delete(BlobScope scope, BlobReference reference) {
        try {
            validateSandbox(scope, reference, false);
            operator.delete(key(scope, reference));
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (IOException | OpenDALException exception) {
            throw map(exception, "files-native-blob-delete-failed");
        }
    }

    @Override
    public List<BlobReference> inventory(BlobScope scope, int limit) {
        if (limit < 1) throw new IllegalArgumentException("inventory limit must be positive");
        String prefix = scopePrefix(scope);
        try {
            Path scopePath = root.resolve(prefix).normalize();
            if (Files.exists(scopePath, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(scopePath)) throw unsafePath();
            ListOptions options = ListOptions.builder().recursive(true).limit((long) limit + 1).build();
            List<BlobReference> values = operator.list(prefix, options).stream()
                    .filter(entry -> entry.getMetadata().isFile())
                    .map(Entry::getPath)
                    .map(path -> path.substring(prefix.length()))
                    .map(BlobReference::new)
                    .toList();
            if (values.size() > limit) throw conflict("files-native-reconciliation-bound-exceeded");
            return values;
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (OpenDALException exception) {
            if (exception.getCode() == OpenDALException.Code.NotFound) return List.of();
            throw map(exception, "files-native-blob-inventory-failed");
        }
    }

    Path resolvedPathForTest(BlobScope scope, BlobReference reference) {
        Path value = root.resolve(key(scope, reference)).normalize();
        if (!value.startsWith(root)) throw unsafePath();
        return value;
    }

    private void validateSandbox(BlobScope scope, BlobReference reference, boolean createParents) throws IOException {
        Path target = resolvedPathForTest(scope, reference);
        Path parent = target.getParent();
        if (createParents) {
            Files.createDirectories(parent);
            Path current = root;
            for (Path segment : root.relativize(parent)) {
                current = current.resolve(segment);
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) throw unsafePath();
                enforcePermissions(current, DIRECTORY_PERMISSIONS);
            }
            return;
        }
        Path current = root;
        for (Path segment : root.relativize(parent)) {
            current = current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) return;
            if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) throw unsafePath();
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(target)) throw unsafePath();
    }

    private BlobReceipt verifyExisting(String key, BlobReference reference, String expectedDigest, long expectedSize) {
        byte[] existing = operator.read(key);
        if (existing.length != expectedSize || !MessageDigest.isEqual(digest(existing).getBytes(java.nio.charset.StandardCharsets.US_ASCII), expectedDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw conflict("files-native-blob-key-collision");
        }
        return new BlobReceipt(reference, expectedDigest, expectedSize);
    }

    private boolean exists(String key) {
        try { return operator.stat(key).isFile(); }
        catch (OpenDALException exception) {
            if (exception.getCode() == OpenDALException.Code.NotFound) return false;
            throw exception;
        }
    }

    private void requireCapabilities() {
        var capability = operator.info.capability;
        if (!capability.read || !capability.write || !capability.delete || !capability.list || !capability.rename) {
            throw unavailable("files-native-opendal-capability-missing");
        }
    }

    private String key(BlobScope scope, BlobReference reference) { return scopePrefix(scope) + reference.value(); }
    private String scopePrefix(BlobScope scope) { return "weave-native/v1/" + hash(scope.organizationRef()) + "/" + hash(scope.spaceRef()) + "/"; }
    private String parent(String key) { int slash = key.lastIndexOf('/'); return slash < 0 ? "" : key.substring(0, slash + 1); }

    private void requireWithinLimit(long size, String code) {
        if (size < 0 || size > maximumBlobBytes) {
            throw error("files-native-blob-too-large".equals(code) ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.CONFLICT, code, "The native Files blob exceeds its configured bound.");
        }
    }

    private String requiredDigest(String value) {
        if (value == null || !value.matches("sha256:[a-f0-9]{64}")) throw new IllegalArgumentException("expected digest must be sha256");
        return value;
    }

    static String digest(byte[] bytes) {
        try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private String hash(String value) { return digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring("sha256:".length()); }

    private void enforcePermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try { Files.setPosixFilePermissions(path, permissions); } catch (UnsupportedOperationException ignored) { }
    }

    private void forceFile(Path file) { try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) { channel.force(true); } catch (IOException ignored) { } }
    private void forceDirectory(Path directory) { try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); } catch (IOException ignored) { } }

    private ApiErrorException map(Throwable error, String fallback) {
        if (error instanceof OpenDALException opendal) {
            return switch (opendal.getCode()) {
                case NotFound -> conflict("files-native-blob-missing");
                case PermissionDenied -> error(HttpStatus.FORBIDDEN, "files-native-blob-permission-denied", "The native Files blob store denied the operation.");
                case AlreadyExists, ConditionNotMatch -> conflict("files-native-blob-key-collision");
                case RateLimited -> error(HttpStatus.SERVICE_UNAVAILABLE, "files-native-blob-rate-limited", "The native Files blob store is temporarily rate limited.");
                default -> unavailable(fallback);
            };
        }
        return unavailable(fallback);
    }

    private ApiErrorException unsafePath() { return error(HttpStatus.CONFLICT, "files-native-path-containment-failed", "The native Files storage path failed containment validation."); }
    private ApiErrorException conflict(String code) { return error(HttpStatus.CONFLICT, code, "The native Files blob state is inconsistent."); }
    private ApiErrorException unavailable(String code) { return error(HttpStatus.SERVICE_UNAVAILABLE, code, "The native Files blob store is temporarily unavailable."); }
    private ApiErrorException error(HttpStatus status, String code, String message) { return new ApiErrorException(status, code, message, Map.of("module", "files", "adapter", "weave-native", "diagnosticsRedacted", true)); }

    @PreDestroy
    void closeOperator() {
        try { operator.close(); } finally { asyncOperator.close(); }
    }
}

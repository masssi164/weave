package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Private, atomic filesystem storage whose paths are derived only from digests and opaque refs. */
@Component
@Primary
@ConditionalOnProperty(
        name = "weave.files.native.blob-store",
        havingValue = WeaveNativeFilesProperties.FILESYSTEM,
        matchIfMissing = true)
public final class FilesystemBlobStore implements BlobStorePort {

    private final Path configuredRoot;
    private final long maximumBlobBytes;

    public FilesystemBlobStore(WeaveNativeFilesProperties properties) {
        this.configuredRoot = properties.filesystemRoot().toAbsolutePath().normalize();
        this.maximumBlobBytes = properties.maximumBlobBytes();
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public BlobReceipt put(BlobScope scope, BlobReference reference, byte[] bytes, String expectedDigest) {
        byte[] content = bytes == null ? new byte[0] : bytes.clone();
        if (content.length > maximumBlobBytes) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "files-native-blob-too-large",
                    "The file exceeds the configured native Files limit.");
        }
        String actualDigest = digest(content);
        if (!MessageDigest.isEqual(actualDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                requiredDigest(expectedDigest).getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw error(HttpStatus.CONFLICT, "files-native-blob-digest-mismatch",
                    "The native Files blob digest did not match the requested digest.");
        }
        try {
            Path target = target(scope, reference, true);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return verifyExisting(target, reference, actualDigest, content.length);
            }
            Path temporary = target.resolveSibling(".pending-" + UUID.randomUUID());
            try {
                OpenOption[] options = {
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS
                };
                try (FileChannel channel = FileChannel.open(temporary, options)) {
                    ByteBuffer buffer = ByteBuffer.wrap(content);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    channel.force(true);
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, target);
                } catch (FileAlreadyExistsException concurrentPublish) {
                    Files.deleteIfExists(temporary);
                    return verifyExisting(target, reference, actualDigest, content.length);
                }
                forceDirectory(target.getParent());
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new BlobReceipt(reference, actualDigest, content.length);
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (IOException exception) {
            throw unavailable("files-native-blob-write-failed");
        }
    }

    @Override
    public byte[] read(BlobScope scope, BlobReference reference) {
        try {
            Path target = target(scope, reference, false);
            requireRegularFile(target);
            long size = Files.size(target);
            if (size > maximumBlobBytes) {
                throw error(HttpStatus.CONFLICT, "files-native-blob-size-invalid",
                        "The native Files blob exceeded its configured bound.");
            }
            return Files.readAllBytes(target);
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (IOException exception) {
            throw unavailable("files-native-blob-read-failed");
        }
    }

    @Override
    public void delete(BlobScope scope, BlobReference reference) {
        try {
            Path target = target(scope, reference, false);
            if (Files.isSymbolicLink(target)) {
                throw unsafePath();
            }
            Files.deleteIfExists(target);
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (IOException exception) {
            throw unavailable("files-native-blob-delete-failed");
        }
    }

    @Override
    public List<BlobReference> inventory(BlobScope scope, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("inventory limit must be positive");
        }
        try {
            Path scopeRoot = scopeRoot(scope, false);
            if (!Files.exists(scopeRoot, LinkOption.NOFOLLOW_LINKS)) {
                return List.of();
            }
            List<BlobReference> references = new ArrayList<>();
            try (var paths = Files.walk(scopeRoot)) {
                var iterator = paths.iterator();
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    if (Files.isSymbolicLink(path)) {
                        throw unsafePath();
                    }
                    if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        if (references.size() >= limit) {
                            throw error(HttpStatus.CONFLICT, "files-native-reconciliation-bound-exceeded",
                                    "Native Files reconciliation exceeded its configured bound.");
                        }
                        references.add(new BlobReference(scopeRoot.relativize(path).toString().replace('\\', '/')));
                    }
                }
            }
            return List.copyOf(references);
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (IOException exception) {
            throw unavailable("files-native-blob-inventory-failed");
        }
    }

    Path resolvedPathForTest(BlobScope scope, BlobReference reference) {
        try {
            return target(scope, reference, true);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private BlobReceipt verifyExisting(
            Path target, BlobReference reference, String expectedDigest, long expectedSize) throws IOException {
        requireRegularFile(target);
        byte[] existing = Files.readAllBytes(target);
        if (existing.length != expectedSize || !MessageDigest.isEqual(
                digest(existing).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                expectedDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw error(HttpStatus.CONFLICT, "files-native-blob-key-collision",
                    "An immutable native Files blob key already contains different content.");
        }
        return new BlobReceipt(reference, expectedDigest, expectedSize);
    }

    private Path target(BlobScope scope, BlobReference reference, boolean createParents) throws IOException {
        Path root = realRoot();
        Path scopeRoot = scopeRoot(root, scope, createParents);
        Path target = scopeRoot.resolve(reference.value()).normalize();
        if (!target.startsWith(scopeRoot)) {
            throw unsafePath();
        }
        if (createParents) {
            createPrivateDirectories(scopeRoot, target.getParent());
        } else {
            requireContainedParents(scopeRoot, target.getParent());
        }
        return target;
    }

    private Path scopeRoot(BlobScope scope, boolean create) throws IOException {
        return scopeRoot(realRoot(), scope, create);
    }

    private Path scopeRoot(Path root, BlobScope scope, boolean create) throws IOException {
        Path result = root.resolve(hash(scope.organizationRef())).resolve(hash(scope.spaceRef())).normalize();
        if (!result.startsWith(root)) {
            throw unsafePath();
        }
        if (create) {
            createPrivateDirectories(root, result);
        } else {
            requireContainedParents(root, result);
        }
        return result;
    }

    private Path realRoot() throws IOException {
        Files.createDirectories(configuredRoot);
        if (Files.isSymbolicLink(configuredRoot)) {
            throw unsafePath();
        }
        return configuredRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private void createPrivateDirectories(Path base, Path destination) throws IOException {
        Path current = base;
        for (Path segment : base.relativize(destination)) {
            current = current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectory(current);
                } catch (FileAlreadyExistsException ignored) {
                    // A concurrent creator won; the no-follow validation below is still authoritative.
                }
            }
            requireDirectory(current);
        }
    }

    private void requireContainedParents(Path base, Path destination) throws IOException {
        if (destination == null || !destination.normalize().startsWith(base)) {
            throw unsafePath();
        }
        Path current = base;
        for (Path segment : base.relativize(destination)) {
            current = current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            requireDirectory(current);
        }
    }

    private void requireDirectory(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw unsafePath();
        }
    }

    private void requireRegularFile(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw unsafePath();
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw error(HttpStatus.CONFLICT, "files-native-blob-missing",
                    "The native Files metadata does not have a readable blob.");
        }
    }

    private void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
            // The blob is already atomically visible. Some platforms cannot fsync directories.
        }
    }

    private String requiredDigest(String digest) {
        if (digest == null || !digest.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("expected digest must be sha256");
        }
        return digest;
    }

    static String digest(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String hash(String value) {
        return digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring("sha256:".length());
    }

    private ApiErrorException unsafePath() {
        return error(HttpStatus.CONFLICT, "files-native-path-containment-failed",
                "The native Files storage path failed containment validation.");
    }

    private ApiErrorException unavailable(String code) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, code,
                "The native Files blob store is temporarily unavailable.");
    }

    private ApiErrorException error(HttpStatus status, String code, String message) {
        return new ApiErrorException(status, code, message,
                Map.of("module", "files", "adapter", "weave-native", "diagnosticsRedacted", true));
    }
}

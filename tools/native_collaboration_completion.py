#!/usr/bin/env python3
from pathlib import Path


def read(path: str) -> str:
    return Path(path).read_text()


def write(path: str, value: str) -> None:
    Path(path).write_text(value)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    return text.replace(old, new, 1)

# ---- Build pins: OpenDAL Java binding + platform-native runtime -----------------
path = 'gradle/libs.versions.toml'
s = read(path)
if 'opendal = "0.50.1"' not in s:
    s = replace_once(s, 'flyway = "13.0.0"\n', 'flyway = "13.0.0"\nopendal = "0.50.1"\n', 'opendal version')
if 'opendal = { module = "org.apache.opendal:opendal"' not in s:
    s = replace_once(s,
        'flyway-postgresql = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }\n',
        'flyway-postgresql = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }\n'
        'opendal = { module = "org.apache.opendal:opendal", version.ref = "opendal" }\n',
        'opendal library')
write(path, s)

path = 'server/gradle/scripts/java-and-dependencies.gradle'
s = read(path)
header = '''def weaveOpenDalClassifier = System.getenv('WEAVE_OPENDAL_CLASSIFIER') ?: {\n    def os = System.getProperty('os.name').toLowerCase(java.util.Locale.ROOT)\n    def arch = System.getProperty('os.arch').toLowerCase(java.util.Locale.ROOT)\n    def arm = arch in ['aarch64', 'arm64']\n    if (os.contains('mac') || os.contains('darwin')) return arm ? 'osx-aarch_64' : 'osx-x86_64'\n    if (os.contains('win')) return 'windows-x86_64'\n    if (os.contains('linux')) return arm ? 'linux-aarch_64' : 'linux-x86_64'\n    throw new GradleException("Unsupported OpenDAL native platform: ${os}/${arch}; set WEAVE_OPENDAL_CLASSIFIER explicitly")\n}.call()\n\n'''
if not s.startswith('def weaveOpenDalClassifier'):
    s = header + s
if 'implementation libs.opendal' not in s:
    s = replace_once(s, '    implementation libs.ical4j\n',
        '    implementation libs.ical4j\n'
        '    implementation libs.opendal\n'
        '    runtimeOnly "org.apache.opendal:opendal:${libs.versions.opendal.get()}:${weaveOpenDalClassifier}"\n',
        'opendal dependency')
write(path, s)

# ---- Spring Boot: no auto-Flyway module in the product runtime -----------------
path = 'server/src/main/java/com/massimotter/weave/backend/WeaveBackendApplication.java'
s = read(path)
s = s.replace('import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;\n', '')
s = s.replace('import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;\n', '')
s = s.replace('@SpringBootApplication(exclude = FlywayAutoConfiguration.class)', '@SpringBootApplication')
write(path, s)

# ---- Flutter client uses the isolated Matrix client/crypto crate ----------------
path = 'client/hook/build.dart'
s = read(path)
s = s.replace("cratePath: '../rust/matrix-core',", "cratePath: '../rust/matrix-client',")
write(path, s)

# ---- Native Files: all blob data operations run through Apache OpenDAL ----------
filesystem = r'''package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.port.BlobStorePort;
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
import org.apache.opendal.Entry;
import org.apache.opendal.ListOptions;
import org.apache.opendal.OpenDALException;
import org.apache.opendal.Operator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Apache OpenDAL-backed local blob store. NIO is used only to establish and
 * verify the private filesystem sandbox; blob reads/writes/list/delete/rename
 * are exclusively performed through OpenDAL's fs operator.
 */
@Component
@Primary
@ConditionalOnProperty(
        name = "weave.files.native.blob-store",
        havingValue = WeaveNativeFilesProperties.FILESYSTEM,
        matchIfMissing = true)
public final class FilesystemBlobStore implements BlobStorePort, AutoCloseable {

    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");

    private final Path root;
    private final long maximumBlobBytes;
    private final Operator operator;

    public FilesystemBlobStore(WeaveNativeFilesProperties properties) {
        try {
            root = properties.filesystemRoot().toAbsolutePath().normalize();
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) {
                throw unsafePath();
            }
            enforcePermissions(root, DIRECTORY_PERMISSIONS);
            maximumBlobBytes = properties.maximumBlobBytes();
            operator = Operator.of("fs", Map.of("root", root.toString()));
            requireCapabilities();
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (IOException | OpenDALException exception) {
            throw unavailable("files-native-opendal-init-failed");
        }
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public BlobReceipt put(BlobScope scope, BlobReference reference, byte[] bytes, String expectedDigest) {
        byte[] content = bytes == null ? new byte[0] : bytes.clone();
        requireWithinLimit(content.length, "files-native-blob-too-large");
        String digest = digest(content);
        if (!MessageDigest.isEqual(
                digest.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                requiredDigest(expectedDigest).getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw error(HttpStatus.CONFLICT, "files-native-blob-digest-mismatch",
                    "The native Files blob digest did not match the requested digest.");
        }
        String key = key(scope, reference);
        try {
            validateSandbox(scope, reference, true);
            if (exists(key)) {
                return verifyExisting(key, reference, digest, content.length);
            }
            String temporary = parent(key) + ".pending-" + UUID.randomUUID();
            operator.write(temporary, content);
            try {
                // Same-backend rename gives the fs backend its crash-safe publish boundary.
                operator.rename(temporary, key);
            } catch (OpenDALException race) {
                operator.delete(temporary);
                if (exists(key)) {
                    return verifyExisting(key, reference, digest, content.length);
                }
                throw race;
            }
            Path target = resolvedPathForTest(scope, reference);
            enforcePermissions(target, FILE_PERMISSIONS);
            forceFile(target);
            forceDirectory(target.getParent());
            return new BlobReceipt(reference, digest, content.length);
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
            if (!exists(key)) {
                throw conflict("files-native-blob-missing");
            }
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
            if (Files.exists(scopePath, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(scopePath)) {
                throw unsafePath();
            }
            ListOptions options = ListOptions.builder().recursive(true).limit((long) limit + 1).build();
            List<BlobReference> values = operator.list(prefix, options).stream()
                    .filter(entry -> entry.getMetadata().isFile())
                    .map(Entry::getPath)
                    .map(path -> path.substring(prefix.length()))
                    .map(BlobReference::new)
                    .toList();
            if (values.size() > limit) {
                throw conflict("files-native-reconciliation-bound-exceeded");
            }
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
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw unsafePath();
                }
                enforcePermissions(current, DIRECTORY_PERMISSIONS);
            }
        } else {
            Path current = root;
            for (Path segment : root.relativize(parent)) {
                current = current.resolve(segment);
                if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) return;
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw unsafePath();
                }
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(target)) {
                throw unsafePath();
            }
        }
    }

    private BlobReceipt verifyExisting(String key, BlobReference reference, String digest, long size) {
        byte[] existing = operator.read(key);
        if (existing.length != size || !MessageDigest.isEqual(
                digest(existing).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                digest.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw conflict("files-native-blob-key-collision");
        }
        return new BlobReceipt(reference, digest, size);
    }

    private boolean exists(String key) {
        try {
            return operator.stat(key).isFile();
        } catch (OpenDALException exception) {
            if (exception.getCode() == OpenDALException.Code.NotFound) return false;
            throw exception;
        }
    }

    private void requireCapabilities() {
        var c = operator.info.capability;
        if (!c.read || !c.write || !c.delete || !c.list || !c.rename) {
            throw unavailable("files-native-opendal-capability-missing");
        }
    }

    private String key(BlobScope scope, BlobReference reference) {
        return scopePrefix(scope) + reference.value();
    }

    private String scopePrefix(BlobScope scope) {
        return "weave-native/v1/" + hash(scope.organizationRef()) + "/" + hash(scope.spaceRef()) + "/";
    }

    private String parent(String key) {
        int slash = key.lastIndexOf('/');
        return slash < 0 ? "" : key.substring(0, slash + 1);
    }

    private void requireWithinLimit(long size, String code) {
        if (size < 0 || size > maximumBlobBytes) {
            throw error("files-native-blob-too-large".equals(code) ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.CONFLICT,
                    code, "The native Files blob exceeds its configured bound.");
        }
    }

    private String requiredDigest(String value) {
        if (value == null || !value.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("expected digest must be sha256");
        }
        return value;
    }

    static String digest(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String hash(String value) {
        return digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(7);
    }

    private void enforcePermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Platform ACLs apply on non-POSIX filesystems.
        }
    }

    private void forceFile(Path file) {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
        }
    }

    private void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
        }
    }

    private ApiErrorException map(RuntimeException error, String fallback) {
        if (error instanceof OpenDALException opendal) {
            return switch (opendal.getCode()) {
                case NotFound -> conflict("files-native-blob-missing");
                case PermissionDenied -> error(HttpStatus.FORBIDDEN, "files-native-blob-permission-denied",
                        "The native Files blob store denied the operation.");
                case AlreadyExists, ConditionNotMatch -> conflict("files-native-blob-key-collision");
                case RateLimited -> error(HttpStatus.SERVICE_UNAVAILABLE, "files-native-blob-rate-limited",
                        "The native Files blob store is temporarily rate limited.");
                default -> unavailable(fallback);
            };
        }
        return unavailable(fallback);
    }

    private ApiErrorException unsafePath() {
        return error(HttpStatus.CONFLICT, "files-native-path-containment-failed",
                "The native Files storage path failed containment validation.");
    }

    private ApiErrorException conflict(String code) {
        return error(HttpStatus.CONFLICT, code, "The native Files blob state is inconsistent.");
    }

    private ApiErrorException unavailable(String code) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, code, "The native Files blob store is temporarily unavailable.");
    }

    private ApiErrorException error(HttpStatus status, String code, String message) {
        return new ApiErrorException(status, code, message,
                Map.of("module", "files", "adapter", "weave-native", "storageCore", "opendal",
                        "diagnosticsRedacted", true));
    }

    @Override
    public void close() {
        operator.close();
    }
}
'''
write('server/src/main/java/com/massimotter/weave/backend/service/files/FilesystemBlobStore.java', filesystem)

s3 = r'''package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.config.WeaveS3FilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.opendal.Entry;
import org.apache.opendal.ListOptions;
import org.apache.opendal.OpenDALException;
import org.apache.opendal.Operator;
import org.apache.opendal.WriteOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** S3-compatible native blob storage through the same Apache OpenDAL core as local fs. */
@Component
@Primary
@ConditionalOnProperty(
        name = "weave.files.native.blob-store",
        havingValue = WeaveNativeFilesProperties.S3_COMPATIBLE)
public final class S3BlobStore implements BlobStorePort, AutoCloseable {

    private final WeaveS3FilesProperties properties;
    private final long maximumBlobBytes;
    private final Operator operator;

    public S3BlobStore(WeaveS3FilesProperties properties, WeaveNativeFilesProperties nativeProperties) {
        this.properties = java.util.Objects.requireNonNull(properties, "properties");
        this.maximumBlobBytes = nativeProperties.maximumBlobBytes();
        if (!properties.configured()) {
            this.operator = null;
            return;
        }
        try {
            Map<String, String> config = new HashMap<>();
            config.put("bucket", properties.getBucket());
            config.put("region", properties.getRegion());
            config.put("endpoint", properties.getEndpoint().toString());
            config.put("access_key_id", properties.getAccessKey());
            config.put("secret_access_key", properties.getSecretKey());
            if (properties.isPathStyle()) config.put("enable_virtual_host_style", "false");
            operator = Operator.of("s3", config);
            requireCapabilities();
        } catch (OpenDALException exception) {
            throw unavailable("files-native-opendal-init-failed");
        }
    }

    @Override
    public boolean configured() {
        return properties.configured() && operator != null;
    }

    @Override
    public BlobReceipt put(BlobScope scope, BlobReference reference, byte[] bytes, String expectedDigest) {
        ensureConfigured();
        byte[] content = bytes == null ? new byte[0] : bytes.clone();
        requireWithinLimit(content.length, "files-native-blob-too-large");
        String digest = FilesystemBlobStore.digest(content);
        if (!MessageDigest.isEqual(digest.getBytes(StandardCharsets.US_ASCII),
                requiredDigest(expectedDigest).getBytes(StandardCharsets.US_ASCII))) {
            throw conflict("files-native-blob-digest-mismatch");
        }
        String key = key(scope, reference);
        try {
            if (exists(key)) return verifyExisting(key, reference, digest, content.length);
            if (!operator.info.capability.writeWithIfNotExists) {
                throw unavailable("files-native-opendal-conditional-write-missing");
            }
            operator.write(key, content, WriteOptions.builder().ifNotExists(true).build());
            return new BlobReceipt(reference, digest, content.length);
        } catch (OpenDALException exception) {
            if (exception.getCode() == OpenDALException.Code.AlreadyExists
                    || exception.getCode() == OpenDALException.Code.ConditionNotMatch) {
                return verifyExisting(key, reference, digest, content.length);
            }
            throw map(exception, "files-native-blob-write-failed");
        }
    }

    @Override
    public byte[] read(BlobScope scope, BlobReference reference) {
        ensureConfigured();
        String key = key(scope, reference);
        try {
            long size = operator.stat(key).getContentLength();
            requireWithinLimit(size, "files-native-blob-size-invalid");
            byte[] value = operator.read(key);
            requireWithinLimit(value.length, "files-native-blob-size-invalid");
            return value;
        } catch (OpenDALException exception) {
            throw map(exception, "files-native-blob-read-failed");
        }
    }

    @Override
    public void delete(BlobScope scope, BlobReference reference) {
        ensureConfigured();
        try {
            operator.delete(key(scope, reference));
        } catch (OpenDALException exception) {
            throw map(exception, "files-native-blob-delete-failed");
        }
    }

    @Override
    public List<BlobReference> inventory(BlobScope scope, int limit) {
        ensureConfigured();
        if (limit < 1) throw new IllegalArgumentException("inventory limit must be positive");
        String prefix = scopePrefix(scope);
        try {
            List<BlobReference> values = operator.list(prefix,
                            ListOptions.builder().recursive(true).limit((long) limit + 1).build()).stream()
                    .filter(entry -> entry.getMetadata().isFile())
                    .map(Entry::getPath)
                    .map(path -> path.substring(prefix.length()))
                    .map(BlobReference::new)
                    .toList();
            if (values.size() > limit) throw conflict("files-native-reconciliation-bound-exceeded");
            return values;
        } catch (OpenDALException exception) {
            if (exception.getCode() == OpenDALException.Code.NotFound) return List.of();
            throw map(exception, "files-native-blob-inventory-failed");
        }
    }

    private BlobReceipt verifyExisting(String key, BlobReference reference, String digest, long size) {
        byte[] existing = operator.read(key);
        if (existing.length != size || !MessageDigest.isEqual(
                FilesystemBlobStore.digest(existing).getBytes(StandardCharsets.US_ASCII),
                digest.getBytes(StandardCharsets.US_ASCII))) {
            throw conflict("files-native-blob-key-collision");
        }
        return new BlobReceipt(reference, digest, size);
    }

    private boolean exists(String key) {
        try {
            return operator.stat(key).isFile();
        } catch (OpenDALException exception) {
            if (exception.getCode() == OpenDALException.Code.NotFound) return false;
            throw exception;
        }
    }

    private void requireCapabilities() {
        var c = operator.info.capability;
        if (!c.read || !c.write || !c.delete || !c.list) {
            throw unavailable("files-native-opendal-capability-missing");
        }
    }

    private String key(BlobScope scope, BlobReference reference) {
        return scopePrefix(scope) + reference.value();
    }

    private String scopePrefix(BlobScope scope) {
        return "weave-native/v1/" + hash(scope.organizationRef()) + "/" + hash(scope.spaceRef()) + "/";
    }

    private String hash(String value) {
        return FilesystemBlobStore.digest(value.getBytes(StandardCharsets.UTF_8)).substring(7);
    }

    private String requiredDigest(String value) {
        if (value == null || !value.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("expected digest must be sha256");
        }
        return value;
    }

    private void ensureConfigured() {
        if (!configured()) throw unavailable("files-native-s3-not-configured");
    }

    private void requireWithinLimit(long size, String code) {
        if (size < 0 || size > maximumBlobBytes) {
            throw new ApiErrorException("files-native-blob-too-large".equals(code)
                            ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.CONFLICT,
                    code, "The native Files blob exceeds its configured bound.", diagnostics());
        }
    }

    private ApiErrorException map(OpenDALException exception, String fallback) {
        return switch (exception.getCode()) {
            case NotFound -> conflict("files-native-blob-missing");
            case PermissionDenied -> new ApiErrorException(HttpStatus.FORBIDDEN,
                    "files-native-blob-permission-denied", "The native Files blob store denied the operation.", diagnostics());
            case AlreadyExists, ConditionNotMatch -> conflict("files-native-blob-key-collision");
            case RateLimited -> new ApiErrorException(HttpStatus.SERVICE_UNAVAILABLE,
                    "files-native-blob-rate-limited", "The native Files blob store is temporarily rate limited.", diagnostics());
            default -> unavailable(fallback);
        };
    }

    private ApiErrorException conflict(String code) {
        return new ApiErrorException(HttpStatus.CONFLICT, code,
                "The native Files blob state is inconsistent.", diagnostics());
    }

    private ApiErrorException unavailable(String code) {
        return new ApiErrorException(HttpStatus.SERVICE_UNAVAILABLE, code,
                "The native Files blob store is temporarily unavailable.", diagnostics());
    }

    private Map<String, Object> diagnostics() {
        return Map.of("module", "files", "adapter", "weave-native", "storageCore", "opendal",
                "diagnosticsRedacted", true);
    }

    @Override
    public void close() {
        if (operator != null) operator.close();
    }
}
'''
write('server/src/main/java/com/massimotter/weave/backend/service/files/S3BlobStore.java', s3)

# Update S3 unit test so it tests the OpenDAL-backed production constructor and
# still proves the size guard runs before any remote I/O.
path = 'server/src/test/java/com/massimotter/weave/backend/service/files/S3BlobStoreTest.java'
s = read(path)
s = s.replace('import static org.mockito.Mockito.mock;\n', '').replace('import static org.mockito.Mockito.verifyNoInteractions;\n', '')
s = s.replace('import software.amazon.awssdk.services.s3.S3Client;\n', '')
s = s.replace('''        S3Client client = mock(S3Client.class);\n        S3BlobStore store = new S3BlobStore(configuredProperties(), client, 2);\n''', '''        S3BlobStore store = new S3BlobStore(\n                configuredProperties(),\n                new com.massimotter.weave.backend.config.WeaveNativeFilesProperties(\n                        "s3-compatible", java.nio.file.Path.of(".weave/files/blobs"), 2, 100));\n''')
s = s.replace('        verifyNoInteractions(client);\n', '')
write(path, s)

# ---- Calendar: normalized relational state is the production read/write plane ---
path = 'weave-persistence-jpa/src/main/java/com/massimotter/weave/backend/calendar/adapter/CalendarNativePersistence.java'
s = read(path)
s = s.replace('        timezoneId = event.timezone().getId();',
              '        timezoneId = event.timezone() == null ? "UTC" : event.timezone().getId();')
write(path, s)

path = 'server/src/main/java/com/massimotter/weave/backend/calendar/adapter/NativeCalendarProviderAdapter.java'
s = read(path)
if 'private final NativeCalendarRelationalStore relationalStore;' not in s:
    s = replace_once(s, '    private final CalendarSnapshotChangeRepository snapshotChanges;\n',
        '    private final CalendarSnapshotChangeRepository snapshotChanges;\n'
        '    private final NativeCalendarRelationalStore relationalStore;\n', 'calendar relational field')

old = '''    @Autowired\n    NativeCalendarProviderAdapter(\n            CalendarCollectionJpaRepository collections,\n            CalendarEventJpaRepository events,\n            CalendarChangeJpaRepository changes,\n            CalendarSnapshotChangeRepository snapshotChanges) {\n        this(collections, events, changes, snapshotChanges, Clock.systemUTC());\n    }\n'''
new = '''    @Autowired\n    NativeCalendarProviderAdapter(\n            CalendarCollectionJpaRepository collections,\n            CalendarEventJpaRepository events,\n            CalendarChangeJpaRepository changes,\n            CalendarSnapshotChangeRepository snapshotChanges,\n            NativeCalendarRelationalStore relationalStore) {\n        this(collections, events, changes, snapshotChanges, relationalStore, Clock.systemUTC());\n    }\n'''
if old in s:
    s = s.replace(old, new, 1)

s = s.replace('''        this(collections, events, changes, null, clock);\n''',
              '''        this(collections, events, changes, null, null, clock);\n''')
old_ctor = '''    NativeCalendarProviderAdapter(\n            CalendarCollectionJpaRepository collections,\n            CalendarEventJpaRepository events,\n            CalendarChangeJpaRepository changes,\n            CalendarSnapshotChangeRepository snapshotChanges,\n            Clock clock) {\n        this.collections = Objects.requireNonNull(collections, "collections");\n        this.events = Objects.requireNonNull(events, "events");\n        this.changes = Objects.requireNonNull(changes, "changes");\n        this.snapshotChanges = snapshotChanges;\n        this.clock = Objects.requireNonNull(clock, "clock");\n    }\n'''
new_ctor = '''    NativeCalendarProviderAdapter(\n            CalendarCollectionJpaRepository collections,\n            CalendarEventJpaRepository events,\n            CalendarChangeJpaRepository changes,\n            CalendarSnapshotChangeRepository snapshotChanges,\n            NativeCalendarRelationalStore relationalStore,\n            Clock clock) {\n        this.collections = Objects.requireNonNull(collections, "collections");\n        this.events = Objects.requireNonNull(events, "events");\n        this.changes = Objects.requireNonNull(changes, "changes");\n        this.snapshotChanges = snapshotChanges;\n        this.relationalStore = relationalStore;\n        this.clock = Objects.requireNonNull(clock, "clock");\n    }\n'''
if old_ctor in s:
    s = s.replace(old_ctor, new_ctor, 1)

old_query = '''        List<CalendarEvent> active = events.findActive(calendarId.value(), scopeKey(scope)).stream()\n                .map(CalendarEventJpaEntity::toDomain)\n                .toList();\n        if (from == null || to == null) {\n            return active;\n        }\n        requireRange(from, to);\n        return active.stream()\n                .filter(event -> !event.occurrences(from, to).isEmpty())\n                .toList();\n'''
new_query = '''        String key = scopeKey(scope);\n        List<CalendarEvent> active = events.findActive(calendarId.value(), key).stream()\n                .map(CalendarEventJpaEntity::toDomain)\n                .map(event -> enrich(event, key))\n                .toList();\n        if (from == null || to == null) {\n            return active;\n        }\n        requireRange(from, to);\n        java.util.Set<String> candidates = relationalStore == null\n                ? active.stream().map(event -> event.id().value()).collect(java.util.stream.Collectors.toSet())\n                : new java.util.HashSet<>(relationalStore.candidateEventIds(calendarId.value(), key, from, to));\n        return active.stream()\n                .filter(event -> candidates.contains(event.id().value()))\n                .filter(event -> !occurrences(event, from, to).isEmpty())\n                .toList();\n'''
if old_query in s:
    s = s.replace(old_query, new_query, 1)

old_read = '''        CalendarEventJpaEntity entity = events.findById(eventKey(calendarId, scope, id))\n                .filter(candidate -> !candidate.deleted())\n                .orElseThrow(() -> notFound("read-event"));\n        return entity.toDomain();\n'''
new_read = '''        CalendarEventJpaEntity entity = events.findById(eventKey(calendarId, scope, id))\n                .filter(candidate -> !candidate.deleted())\n                .orElseThrow(() -> notFound("read-event"));\n        return enrich(entity.toDomain(), scopeKey(scope));\n'''
if old_read in s:
    s = s.replace(old_read, new_read, 1)

# Make write comparison use the canonical normalized view, then persist normalized details atomically.
s = s.replace('''        CalendarEventJpaEntity current = events.findById(key).orElse(null);\n\n        if (write.intent() == WriteIntent.CREATE && current != null && !current.deleted()) {\n            if (sameEvent(current.toDomain(), incoming)) {\n                return current.toDomain();\n''', '''        CalendarEventJpaEntity current = events.findById(key).orElse(null);\n        CalendarEvent currentDomain = current == null || current.deleted() ? null : enrich(current.toDomain(), scopeKey(incoming.scope()));\n\n        if (write.intent() == WriteIntent.CREATE && currentDomain != null) {\n            if (sameEvent(currentDomain, incoming)) {\n                return currentDomain;\n''')
s = s.replace('''        if (current != null && !current.deleted() && sameEvent(current.toDomain(), incoming)) {\n            return current.toDomain();\n        }\n''', '''        if (currentDomain != null && sameEvent(currentDomain, incoming)) {\n            return currentDomain;\n        }\n''')
s = s.replace('''        changes.save(CalendarChangeJpaEntity.create(\n                new CalendarChangeId(\n                        incoming.calendarId().value(),\n                        scopeKey(incoming.scope()),\n                        sequence),\n                incoming.id().value(),\n                false,\n                version,\n                timestamp));\n        return entity.toDomain();\n''', '''        changes.save(CalendarChangeJpaEntity.create(\n                new CalendarChangeId(\n                        incoming.calendarId().value(),\n                        scopeKey(incoming.scope()),\n                        sequence),\n                incoming.id().value(),\n                false,\n                version,\n                timestamp));\n        CalendarEvent persisted = canonicalVersion(incoming, version, timestamp);\n        if (relationalStore != null) {\n            relationalStore.save(persisted, scopeKey(incoming.scope()));\n        }\n        return persisted;\n''')

s = s.replace('''        changes.save(CalendarChangeJpaEntity.create(\n                new CalendarChangeId(calendarId.value(), scopeKey(scope), sequence),\n                id.value(),\n                true,\n                version,\n                timestamp));\n''', '''        changes.save(CalendarChangeJpaEntity.create(\n                new CalendarChangeId(calendarId.value(), scopeKey(scope), sequence),\n                id.value(),\n                true,\n                version,\n                timestamp));\n        if (relationalStore != null) {\n            relationalStore.delete(calendarId.value(), scopeKey(scope), id.value());\n        }\n''')

old_free = '''        return query(calendarId, scope, from, to).stream()\n                .flatMap(event -> event.occurrences(from, to).stream())\n                .map(occurrence -> new FreeBusyWindow(\n                        occurrence.start().toInstant(),\n                        occurrence.end().toInstant()))\n                .sorted(Comparator.comparing(FreeBusyWindow::start))\n                .toList();\n'''
new_free = '''        return query(calendarId, scope, from, to).stream()\n                .flatMap(event -> occurrences(event, from, to).stream())\n                .map(occurrence -> new FreeBusyWindow(occurrence.start(), occurrence.end()))\n                .sorted(Comparator.comparing(FreeBusyWindow::start))\n                .toList();\n'''
if old_free in s:
    s = s.replace(old_free, new_free, 1)

# Stronger token tamper check: scope-bound sequence plus checksum.
s = s.replace('''        String prefix = SYNC_TOKEN_PREFIX + scopeDigest(calendarId, scope) + "-";\n        String normalized = token.trim();\n        if (!normalized.startsWith(prefix)) {\n            throw conflict("sync-events");\n        }\n        try {\n            long sequence = Long.parseLong(normalized.substring(prefix.length()));\n            if (sequence < 0 || sequence > latestSequence) {\n                throw conflict("sync-events");\n            }\n            return sequence;\n        } catch (NumberFormatException exception) {\n            throw conflict("sync-events");\n        }\n''', '''        String prefix = SYNC_TOKEN_PREFIX + scopeDigest(calendarId, scope) + "-";\n        String normalized = token.trim();\n        if (!normalized.startsWith(prefix)) {\n            throw conflict("sync-events");\n        }\n        try {\n            String tail = normalized.substring(prefix.length());\n            int separator = tail.lastIndexOf('-');\n            if (separator < 1) throw conflict("sync-events");\n            long sequence = Long.parseLong(tail.substring(0, separator));\n            String checksum = tail.substring(separator + 1);\n            if (!checksum.equals(syncChecksum(calendarId, scope, sequence))\n                    || sequence < 0 || sequence > latestSequence) {\n                throw conflict("sync-events");\n            }\n            return sequence;\n        } catch (NumberFormatException exception) {\n            throw conflict("sync-events");\n        }\n''')
s = s.replace('''        return SYNC_TOKEN_PREFIX + scopeDigest(calendarId, scope) + "-" + sequence;\n''',
              '''        return SYNC_TOKEN_PREFIX + scopeDigest(calendarId, scope) + "-" + sequence + "-"\n                + syncChecksum(calendarId, scope, sequence);\n''')

# Canonical fingerprints include exact temporal kind/value and full RFC recurrence profile.
s = s.replace('''        append(canonical, event.localStart().toString());\n        append(canonical, event.localEnd().toString());\n        append(canonical, event.timezone().getId());\n        append(canonical, Boolean.toString(event.allDay()));\n''', '''        append(canonical, event.startValue().kind().name());\n        append(canonical, event.startValue().date() == null ? null : event.startValue().date().toString());\n        append(canonical, event.startValue().localDateTime() == null ? null : event.startValue().localDateTime().toString());\n        append(canonical, event.startValue().instant() == null ? null : event.startValue().instant().toString());\n        append(canonical, event.startValue().zoneId() == null ? null : event.startValue().zoneId().getId());\n        append(canonical, event.endValue().date() == null ? null : event.endValue().date().toString());\n        append(canonical, event.endValue().localDateTime() == null ? null : event.endValue().localDateTime().toString());\n        append(canonical, event.endValue().instant() == null ? null : event.endValue().instant().toString());\n        append(canonical, Boolean.toString(event.allDay()));\n''')
s = s.replace('''            append(canonical, recurrence.frequency().name());\n            append(canonical, Integer.toString(recurrence.interval()));\n            append(canonical, recurrence.count() == null ? null : recurrence.count().toString());\n            append(canonical, recurrence.until() == null ? null : recurrence.until().toString());\n''', '''            append(canonical, recurrence.rrule());\n''')

insert_before = '    private static void append(StringBuilder canonical, String value) {'
helpers = '''    private CalendarEvent enrich(CalendarEvent event, String scopeKey) {\n        return relationalStore == null ? event : relationalStore.enrich(event, scopeKey);\n    }\n\n    private List<NativeCalendarRelationalStore.Occurrence> occurrences(\n            CalendarEvent event, Instant from, Instant to) {\n        if (relationalStore != null) {\n            return relationalStore.occurrences(event, from, to);\n        }\n        return event.occurrences(from, to).stream()\n                .map(value -> new NativeCalendarRelationalStore.Occurrence(\n                        value.start().toInstant(), value.end().toInstant()))\n                .toList();\n    }\n\n    private CalendarEvent canonicalVersion(CalendarEvent event, String version, Instant updatedAt) {\n        return new CalendarEvent(\n                event.calendarId(), event.id(), event.scope(), event.title(), event.description(),\n                event.startValue(), event.endValue(), event.location(), event.attendees(),\n                event.recurrence(), event.overrides(), new EventVersion(version), updatedAt);\n    }\n\n    private String syncChecksum(CalendarId calendarId, CalendarScope scope, long sequence) {\n        return digest("weave-native-calendar-sync-v2\\u0000" + calendarId.value() + "\\u0000"\n                + scopeKey(scope) + "\\u0000" + sequence).substring(0, 24);\n    }\n\n'''
if helpers not in s:
    s = s.replace(insert_before, helpers + insert_before, 1)
write(path, s)

# ---- Matrix relational cutover: run existing deterministic transform later ------
# Remove stale old crate directory if an empty/deleted entry survived a merge.
core = Path('rust/matrix-core')
if core.exists():
    import shutil
    shutil.rmtree(core)

# Evidence/docs state the actual native cores rather than legacy implementation names.
evidence = Path('docs/evidence/native-collaboration-core.md')
evidence.parent.mkdir(parents=True, exist_ok=True)
evidence.write_text('''# Native collaboration core\n\n- Files blob data plane: Apache OpenDAL Java binding 0.50.1. Local filesystem and S3-compatible storage use the same OpenDAL operator boundary; PostgreSQL remains canonical metadata authority.\n- Chat Matrix server projection: `weave-matrix-protocol` uses Ruma 0.16.0 plus jni-rs and contains no Matrix SDK client dependency. Client E2EE remains isolated in `weave-matrix-client` with matrix-sdk.\n- Calendar RFC 5545 syntax and recurrence: iCal4j 4.2.5 behind `IcalendarCodec`/`RecurrenceEngine`; PostgreSQL stores normalized exact DATE/FLOATING/UTC/ZONED and recurrence metadata.\n- Runtime application does not auto-mutate relational schema. Flyway is invoked only by the explicit `schema-init` operator command.\n''')

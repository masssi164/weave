package com.massimotter.weave.backend.agentruntime.adapter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateKeyWrapper;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStoreException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateWrappingKeyLifecycle;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Guarded self-hosted wrapping-key adapter for encrypted runtime state.
 *
 * <p>The mounted root contains the AES wrapping keys. PostgreSQL receives only opaque key
 * references and AES-KWP wrapped generation keys. Initialization and rotation are explicit offline
 * operations; normal server startup never creates a replacement key after an incomplete restore.
 */
public final class FileRuntimeStateKeyWrapper
    implements RuntimeStateKeyWrapper, RuntimeStateWrappingKeyLifecycle {
  private static final String SCHEMA = "weave.runtime-state-wrapping-keys/v1";
  private static final String MANIFEST_NAME = "runtime-state-wrapping-keys.json";
  private static final String LOCK_NAME = ".runtime-state-wrapping-keys.lock";
  private static final String KEY_DIRECTORY = "keys";
  private static final int WRAPPING_KEY_BYTES = 32;
  private static final int DATA_KEY_BYTES = 32;
  private static final int CONTEXT_HASH_BYTES = 32;
  private static final int MAX_MANIFEST_BYTES = 262_144;
  private static final int MAX_WRAPPING_KEY_BYTES = 64;
  private static final Pattern KEY_REF = Pattern.compile("rsk_[A-Za-z0-9_-]{32}");
  private static final Pattern HASH = Pattern.compile("sha256:[a-f0-9]{64}");
  private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
  private static final Set<PosixFilePermission> SECRET_PERMISSIONS =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  private final Path root;
  private final Path keyDirectory;
  private final Path manifestPath;
  private final Path lockPath;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final SecureRandom secureRandom;
  private final ReentrantLock localLock = new ReentrantLock();

  public FileRuntimeStateKeyWrapper(
      Path root, ObjectMapper objectMapper, Clock clock, SecureRandom secureRandom) {
    if (root == null || objectMapper == null || clock == null || secureRandom == null) {
      throw new IllegalArgumentException(
          "wrapping-key root, mapper, clock, and randomness are required");
    }
    if (!root.isAbsolute()) {
      throw new IllegalArgumentException("wrapping-key root must be an explicit absolute path");
    }
    this.root = root.normalize();
    this.keyDirectory = this.root.resolve(KEY_DIRECTORY);
    this.manifestPath = this.root.resolve(MANIFEST_NAME);
    this.lockPath = this.root.resolve(LOCK_NAME);
    this.mapper =
        objectMapper
            .rebuild()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    this.clock = clock;
    this.secureRandom = secureRandom;
    ensureSecretDirectories();
  }

  @Override
  public WrappedDataKey wrap(byte[] dataKey, byte[] authenticatedContext) {
    requireKeyAndContext(dataKey, authenticatedContext);
    return readLocked(
        () -> {
          StoredManifest manifest = requireManifest();
          StoredKey active =
              manifest.keys().stream()
                  .filter(key -> key.keyRef().equals(manifest.activeKeyRef()))
                  .filter(key -> key.status() == Status.ACTIVE)
                  .findFirst()
                  .orElseThrow(
                      () ->
                          unavailable(
                              "The runtime-state active wrapping key is unavailable", null));
          byte[] wrappingKey = readWrappingKey(active);
          byte[] material = new byte[DATA_KEY_BYTES + CONTEXT_HASH_BYTES];
          try {
            System.arraycopy(dataKey, 0, material, 0, DATA_KEY_BYTES);
            byte[] contextHash = digest(authenticatedContext);
            System.arraycopy(contextHash, 0, material, DATA_KEY_BYTES, CONTEXT_HASH_BYTES);
            Arrays.fill(contextHash, (byte) 0);
            Cipher cipher = Cipher.getInstance("AES/KWP/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(wrappingKey, "AES"));
            return new WrappedDataKey(active.keyRef(), cipher.doFinal(material));
          } catch (GeneralSecurityException failure) {
            throw unavailable("The runtime-state data key could not be wrapped", failure);
          } finally {
            Arrays.fill(wrappingKey, (byte) 0);
            Arrays.fill(material, (byte) 0);
          }
        });
  }

  @Override
  public byte[] unwrap(WrappedDataKey wrappedDataKey, byte[] authenticatedContext) {
    Objects.requireNonNull(wrappedDataKey, "wrappedDataKey");
    requireContext(authenticatedContext);
    if (!KEY_REF.matcher(wrappedDataKey.keyRef()).matches()) {
      throw unavailable("The runtime-state wrapping-key reference is invalid", null);
    }
    return readLocked(
        () -> {
          StoredManifest manifest = requireManifest();
          StoredKey selected =
              manifest.keys().stream()
                  .filter(key -> key.keyRef().equals(wrappedDataKey.keyRef()))
                  .findFirst()
                  .orElseThrow(
                      () -> unavailable("The runtime-state wrapping key is unavailable", null));
          byte[] wrappingKey = readWrappingKey(selected);
          byte[] unwrapped = null;
          try {
            Cipher cipher = Cipher.getInstance("AES/KWP/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(wrappingKey, "AES"));
            unwrapped = cipher.doFinal(wrappedDataKey.wrappedKey());
            if (unwrapped.length != DATA_KEY_BYTES + CONTEXT_HASH_BYTES) {
              throw unavailable("The wrapped runtime-state data key is invalid", null);
            }
            byte[] expectedContext = digest(authenticatedContext);
            byte[] actualContext =
                Arrays.copyOfRange(unwrapped, DATA_KEY_BYTES, DATA_KEY_BYTES + CONTEXT_HASH_BYTES);
            boolean contextMatches = MessageDigest.isEqual(expectedContext, actualContext);
            Arrays.fill(expectedContext, (byte) 0);
            Arrays.fill(actualContext, (byte) 0);
            if (!contextMatches) {
              throw unavailable("The wrapped runtime-state data key binding is invalid", null);
            }
            return Arrays.copyOf(unwrapped, DATA_KEY_BYTES);
          } catch (RuntimeStateStoreException failure) {
            throw failure;
          } catch (GeneralSecurityException failure) {
            throw unavailable("The runtime-state data key could not be unwrapped", failure);
          } finally {
            Arrays.fill(wrappingKey, (byte) 0);
            if (unwrapped != null) {
              Arrays.fill(unwrapped, (byte) 0);
            }
          }
        });
  }

  @Override
  public KeyReadiness readiness() {
    try {
      StoredManifest manifest = readLocked(this::requireManifest);
      StoredKey active =
          manifest.keys().stream()
              .filter(key -> key.keyRef().equals(manifest.activeKeyRef()))
              .filter(key -> key.status() == Status.ACTIVE)
              .findFirst()
              .orElseThrow();
      byte[] key = readLocked(() -> readWrappingKey(active));
      Arrays.fill(key, (byte) 0);
      return new KeyReadiness(true, referenceHash(active.keyRef(), "active key reference"));
    } catch (RuntimeException unavailable) {
      return new KeyReadiness(false, null);
    }
  }

  @Override
  public KeyRingState initialize(String operationRef) {
    String operationHash = referenceHash(operationRef, "initialization operation reference");
    return writeLocked(
        () -> {
          Optional<StoredManifest> existing = readManifest();
          if (existing.isPresent()) {
            StoredManifest current = existing.orElseThrow();
            if (!current.initializationRefHash().equals(operationHash)) {
              throw unavailable("The runtime-state wrapping-key root is already initialized", null);
            }
            return projection(current);
          }
          Instant now = clock.instant();
          GeneratedKey generated = generate(now, Status.ACTIVE, null);
          try {
            writeWrappingKey(generated.key(), generated.material());
          } finally {
            Arrays.fill(generated.material(), (byte) 0);
          }
          StoredManifest created =
              new StoredManifest(
                  SCHEMA,
                  operationHash,
                  generated.key().keyRef(),
                  operationHash,
                  List.of(generated.key()));
          validate(created);
          writeManifest(created);
          return projection(created);
        });
  }

  @Override
  public KeyRingState rotate(String operationRef) {
    String operationHash = referenceHash(operationRef, "rotation operation reference");
    return writeLocked(
        () -> {
          StoredManifest current = requireManifest();
          if (current.lastOperationRefHash().equals(operationHash)) {
            return projection(current);
          }
          Instant now = clock.instant();
          GeneratedKey generated = generate(now, Status.ACTIVE, null);
          if (current.keys().stream()
              .anyMatch(key -> key.keyRef().equals(generated.key().keyRef()))) {
            throw unavailable("A runtime-state wrapping-key identifier collision occurred", null);
          }
          try {
            writeWrappingKey(generated.key(), generated.material());
          } finally {
            Arrays.fill(generated.material(), (byte) 0);
          }
          List<StoredKey> rotatedKeys = new ArrayList<>();
          for (StoredKey key : current.keys()) {
            rotatedKeys.add(
                key.keyRef().equals(current.activeKeyRef())
                    ? new StoredKey(
                        key.keyRef(), Status.OVERLAP, key.activatedAt(), now, key.keyFile())
                    : key);
          }
          rotatedKeys.add(generated.key());
          StoredManifest rotated =
              new StoredManifest(
                  current.schemaVersion(),
                  current.initializationRefHash(),
                  generated.key().keyRef(),
                  operationHash,
                  List.copyOf(rotatedKeys));
          validate(rotated);
          writeManifest(rotated);
          return projection(rotated);
        });
  }

  @Override
  public KeyRingState current() {
    return readLocked(() -> projection(requireManifest()));
  }

  private GeneratedKey generate(Instant now, Status status, Instant overlapStartedAt) {
    byte[] material = new byte[WRAPPING_KEY_BYTES];
    secureRandom.nextBytes(material);
    String keyRef = keyRef(material);
    return new GeneratedKey(
        new StoredKey(keyRef, status, now, overlapStartedAt, KEY_DIRECTORY + "/" + keyRef + ".key"),
        material);
  }

  private byte[] readWrappingKey(StoredKey key) {
    Path path = resolveKeyPath(key);
    try {
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(path)
          || Files.size(path) > MAX_WRAPPING_KEY_BYTES) {
        throw unavailable("A runtime-state wrapping-key file is unavailable", null);
      }
      requirePrivatePermissions(path);
      byte[] value = Files.readAllBytes(path);
      if (value.length != WRAPPING_KEY_BYTES || !key.keyRef().equals(keyRef(value))) {
        Arrays.fill(value, (byte) 0);
        throw unavailable("A runtime-state wrapping-key file is invalid", null);
      }
      return value;
    } catch (RuntimeStateStoreException failure) {
      throw failure;
    } catch (IOException failure) {
      throw unavailable("A runtime-state wrapping-key file is unavailable", failure);
    }
  }

  private void writeWrappingKey(StoredKey key, byte[] material) {
    if (material.length != WRAPPING_KEY_BYTES || !key.keyRef().equals(keyRef(material))) {
      throw unavailable("Generated runtime-state wrapping-key material is invalid", null);
    }
    writeAtomic(resolveKeyPath(key), material, SECRET_PERMISSIONS);
  }

  private Path resolveKeyPath(StoredKey key) {
    Path relative;
    try {
      relative = Path.of(key.keyFile());
    } catch (RuntimeException invalid) {
      throw unavailable("A runtime-state wrapping-key file reference is invalid", invalid);
    }
    Path resolved = root.resolve(relative).normalize();
    if (relative.isAbsolute()
        || !resolved.startsWith(keyDirectory)
        || !resolved.getFileName().toString().equals(key.keyRef() + ".key")) {
      throw unavailable("A runtime-state wrapping-key file reference is invalid", null);
    }
    return resolved;
  }

  private Optional<StoredManifest> readManifest() {
    if (!Files.exists(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
      return Optional.empty();
    }
    try {
      if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(manifestPath)
          || Files.size(manifestPath) > MAX_MANIFEST_BYTES) {
        throw unavailable("The runtime-state wrapping-key manifest is invalid", null);
      }
      byte[] encoded = Files.readAllBytes(manifestPath);
      try {
        StoredManifest manifest = mapper.readValue(encoded, StoredManifest.class);
        validate(manifest);
        return Optional.of(manifest);
      } finally {
        Arrays.fill(encoded, (byte) 0);
      }
    } catch (RuntimeStateStoreException failure) {
      throw failure;
    } catch (IOException failure) {
      throw unavailable("The runtime-state wrapping-key manifest is unavailable", failure);
    }
  }

  private StoredManifest requireManifest() {
    return readManifest()
        .orElseThrow(
            () -> unavailable("The runtime-state wrapping-key root is not initialized", null));
  }

  private void writeManifest(StoredManifest manifest) {
    try {
      byte[] encoded = mapper.writeValueAsBytes(manifest);
      if (encoded.length > MAX_MANIFEST_BYTES) {
        throw unavailable("The runtime-state wrapping-key manifest is too large", null);
      }
      try {
        writeAtomic(manifestPath, encoded, SECRET_PERMISSIONS);
      } finally {
        Arrays.fill(encoded, (byte) 0);
      }
    } catch (RuntimeStateStoreException failure) {
      throw failure;
    } catch (JacksonException failure) {
      throw unavailable("The runtime-state wrapping-key manifest could not be published", failure);
    }
  }

  private void validate(StoredManifest manifest) {
    if (manifest == null
        || !SCHEMA.equals(manifest.schemaVersion())
        || !HASH.matcher(manifest.initializationRefHash()).matches()
        || !KEY_REF.matcher(manifest.activeKeyRef()).matches()
        || !HASH.matcher(manifest.lastOperationRefHash()).matches()
        || manifest.keys() == null
        || manifest.keys().isEmpty()) {
      throw unavailable("The runtime-state wrapping-key manifest is invalid", null);
    }
    Set<String> references = new HashSet<>();
    int active = 0;
    for (StoredKey key : manifest.keys()) {
      if (key == null
          || !KEY_REF.matcher(key.keyRef()).matches()
          || key.status() == null
          || key.activatedAt() == null
          || key.keyFile() == null
          || key.keyFile().isBlank()
          || !references.add(key.keyRef())) {
        throw unavailable("The runtime-state wrapping-key manifest is invalid", null);
      }
      if (key.status() == Status.ACTIVE) {
        active++;
        if (!key.keyRef().equals(manifest.activeKeyRef()) || key.overlapStartedAt() != null) {
          throw unavailable("The runtime-state wrapping-key manifest is invalid", null);
        }
      } else if (key.overlapStartedAt() == null
          || key.overlapStartedAt().isBefore(key.activatedAt())) {
        throw unavailable("The runtime-state wrapping-key manifest is invalid", null);
      }
      resolveKeyPath(key);
    }
    if (active != 1 || !references.contains(manifest.activeKeyRef())) {
      throw unavailable("The runtime-state wrapping-key manifest is invalid", null);
    }
  }

  private KeyRingState projection(StoredManifest manifest) {
    List<KeyState> keys =
        manifest.keys().stream()
            .map(
                key ->
                    new KeyState(
                        key.keyRef(), key.status(), key.activatedAt(), key.overlapStartedAt()))
            .sorted(Comparator.comparing(KeyState::keyRef))
            .toList();
    return new KeyRingState(manifest.activeKeyRef(), manifest.lastOperationRefHash(), keys);
  }

  private void ensureSecretDirectories() {
    try {
      if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(root)) {
        throw unavailable("The runtime-state wrapping-key root cannot be a symbolic link", null);
      }
      Files.createDirectories(root);
      Files.createDirectories(keyDirectory);
      if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
          || !Files.isDirectory(keyDirectory, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(keyDirectory)) {
        throw unavailable("The runtime-state wrapping-key root is invalid", null);
      }
      setPermissions(root, DIRECTORY_PERMISSIONS);
      setPermissions(keyDirectory, DIRECTORY_PERMISSIONS);
    } catch (RuntimeStateStoreException failure) {
      throw failure;
    } catch (IOException failure) {
      throw unavailable("The runtime-state wrapping-key root is unavailable", failure);
    }
  }

  private void writeAtomic(Path target, byte[] value, Set<PosixFilePermission> permissions) {
    Path temporary = null;
    try {
      temporary = Files.createTempFile(target.getParent(), ".runtime-state-", ".tmp");
      setPermissions(temporary, permissions);
      try (FileChannel channel =
          FileChannel.open(
              temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
        channel.write(java.nio.ByteBuffer.wrap(value));
        channel.force(true);
      }
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
      setPermissions(target, permissions);
      fsyncDirectory(target.getParent());
    } catch (IOException failure) {
      throw unavailable("The runtime-state wrapping-key store could not be updated", failure);
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // A later operator run can remove a support-safe orphan temporary file.
        }
      }
    }
  }

  private void requirePrivatePermissions(Path path) throws IOException {
    try {
      Set<PosixFilePermission> permissions =
          Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
      if (permissions.contains(PosixFilePermission.GROUP_READ)
          || permissions.contains(PosixFilePermission.GROUP_WRITE)
          || permissions.contains(PosixFilePermission.GROUP_EXECUTE)
          || permissions.contains(PosixFilePermission.OTHERS_READ)
          || permissions.contains(PosixFilePermission.OTHERS_WRITE)
          || permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
        throw unavailable("A runtime-state wrapping-key file has unsafe permissions", null);
      }
    } catch (UnsupportedOperationException ignored) {
      // Non-POSIX platforms rely on the owning secret-mount ACL.
    }
  }

  private static void setPermissions(Path path, Set<PosixFilePermission> permissions)
      throws IOException {
    try {
      Files.setPosixFilePermissions(path, permissions);
    } catch (UnsupportedOperationException ignored) {
      // Non-POSIX platforms rely on the owning secret-mount ACL.
    }
  }

  private static void fsyncDirectory(Path directory) {
    try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
      channel.force(true);
    } catch (IOException | UnsupportedOperationException ignored) {
      // Atomic publication and file fsync remain enforced when directory fsync is unsupported.
    }
  }

  private <T> T readLocked(Operation<T> operation) {
    return locked(true, operation);
  }

  private <T> T writeLocked(Operation<T> operation) {
    return locked(false, operation);
  }

  private <T> T locked(boolean shared, Operation<T> operation) {
    localLock.lock();
    try {
      ensureSecretDirectories();
      try (FileChannel channel =
          FileChannel.open(
              lockPath,
              StandardOpenOption.CREATE,
              StandardOpenOption.READ,
              StandardOpenOption.WRITE)) {
        setPermissions(lockPath, SECRET_PERMISSIONS);
        try (FileLock ignored = channel.lock(0L, Long.MAX_VALUE, shared)) {
          return operation.run();
        }
      } catch (IOException failure) {
        throw unavailable("The runtime-state wrapping-key lock is unavailable", failure);
      }
    } finally {
      localLock.unlock();
    }
  }

  private static void requireKeyAndContext(byte[] key, byte[] context) {
    if (key == null || key.length != DATA_KEY_BYTES) {
      throw new IllegalArgumentException("runtime-state data key must contain 256 bits");
    }
    requireContext(context);
  }

  private static void requireContext(byte[] context) {
    if (context == null || context.length < 16 || context.length > 16_384) {
      throw new IllegalArgumentException("runtime-state authenticated context is invalid");
    }
  }

  private static String keyRef(byte[] material) {
    byte[] digest = digest(material);
    try {
      return "rsk_"
          + Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 32);
    } finally {
      Arrays.fill(digest, (byte) 0);
    }
  }

  private static String referenceHash(String reference, String label) {
    if (reference == null || reference.isBlank() || reference.length() > 1024) {
      throw new IllegalArgumentException(
          label + " is required and must be at most 1024 characters");
    }
    byte[] digest = digest(reference.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    try {
      return "sha256:" + java.util.HexFormat.of().formatHex(digest);
    } finally {
      Arrays.fill(digest, (byte) 0);
    }
  }

  private static byte[] digest(byte[] value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value);
    } catch (GeneralSecurityException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static RuntimeStateStoreException unavailable(String message, Throwable cause) {
    return cause == null
        ? new RuntimeStateStoreException(message)
        : new RuntimeStateStoreException(message, cause);
  }

  @FunctionalInterface
  private interface Operation<T> {
    T run();
  }

  private record StoredManifest(
      String schemaVersion,
      String initializationRefHash,
      String activeKeyRef,
      String lastOperationRefHash,
      List<StoredKey> keys) {
    private StoredManifest {
      keys = keys == null ? null : List.copyOf(keys);
    }
  }

  private record StoredKey(
      String keyRef,
      Status status,
      Instant activatedAt,
      Instant overlapStartedAt,
      String keyFile) {}

  private record GeneratedKey(StoredKey key, byte[] material) {}
}

package com.massimotter.weave.backend.schema;

import com.massimotter.weave.backend.files.adapter.FilesVolumeAuthorityJpaEntity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.erdtman.jcs.JsonCanonicalizer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Canonical adapter-private contract for one native Files volume generation. */
public final class NativeFilesVolumeAuthority {

  public static final String AUTHORITY_KEY = "native-files";
  public static final String MARKER_FILE_NAME = ".weave-files-volume-authority-v1.json";
  public static final String MARKER_FORMAT = "weave.files-volume-authority-marker/v1";
  public static final String ROW_FORMAT = "weave.files-volume-authority-row/v1";
  public static final String TRANSITION_CONTEXT_FILE_NAME =
      "files-volume-transition-context-v1.json";
  public static final String TRANSITION_CONTEXT_FORMAT =
      "weave.files-volume-transition-context/v1";
  public static final String TRANSITION_RECEIPT_FORMAT =
      "weave.files-volume-transition-receipt/v1";
  static final long MAX_TRANSITION_CONTEXT_BYTES = 4 * 1024;
  static final long MAX_ROOT_MARKER_BYTES = 4 * 1024;

  private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
  private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
  private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");
  private static final Pattern SCOPE = Pattern.compile("[a-z0-9][a-z0-9._-]{2,127}");
  private static final Set<String> TRANSITION_KINDS =
      Set.of("INITIAL_PROVISION", "AUTHORIZED_RESET");
  private static final Set<String> CONTEXT_KEYS =
      Set.of(
          "schemaVersion",
          "transitionKind",
          "composeProject",
          "runScope",
          "candidateCommit");
  private static final Set<String> MARKER_KEYS =
      Set.of(
          "schemaVersion",
          "authorityKey",
          "volumeRef",
          "generationRef",
          "transitionRef",
          "transitionReceiptDigest",
          "schemaHistoryFingerprint");
  private static final Set<String> RECEIPT_PROJECTION_KEYS =
      Set.of(
          "authorityKey",
          "volumeRef",
          "generationRef",
          "transitionKind",
          "transitionRef",
          "transitionReceiptDigest",
          "schemaHistoryFingerprint",
          "rootMarkerDigest",
          "createdAtUtc",
          "authorityRowDigest");
  private static final ObjectMapper JSON = new ObjectMapper();

  private NativeFilesVolumeAuthority() {}

  public record TransitionContext(
      String transitionKind,
      String composeProject,
      String runScope,
      String candidateCommit) {}

  public record Authority(
      String authorityKey,
      String volumeRef,
      String generationRef,
      String transitionKind,
      String transitionRef,
      String transitionReceiptDigest,
      String schemaHistoryFingerprint,
      String rootMarkerDigest,
      Instant createdAt) {

    public FilesVolumeAuthorityJpaEntity toEntity() {
      return new FilesVolumeAuthorityJpaEntity(
          authorityKey,
          volumeRef,
          generationRef,
          transitionKind,
          transitionRef,
          transitionReceiptDigest,
          schemaHistoryFingerprint,
          rootMarkerDigest,
          createdAt);
    }

    public static Authority fromEntity(FilesVolumeAuthorityJpaEntity entity) {
      return new Authority(
          entity.authorityKey(),
          entity.volumeRef(),
          entity.generationRef(),
          entity.transitionKind(),
          entity.transitionRef(),
          entity.transitionReceiptDigest(),
          entity.schemaHistoryFingerprint(),
          entity.rootMarkerDigest(),
          entity.createdAt());
    }
  }

  public static Optional<TransitionContext> readTransitionContext(
      Path receiptParent, String candidateCommit) throws Exception {
    if (receiptParent == null
        || Files.isSymbolicLink(receiptParent)
        || !Files.isDirectory(receiptParent, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException(
          "native Files transition context root is unavailable or unsafe");
    }
    Path contextPath = receiptParent.resolve(TRANSITION_CONTEXT_FILE_NAME).normalize();
    if (!contextPath.getParent().equals(receiptParent.normalize())) {
      throw new IllegalStateException("native Files transition context path escaped its root");
    }
    if (!Files.exists(contextPath, LinkOption.NOFOLLOW_LINKS)) {
      return Optional.empty();
    }
    if (Files.isSymbolicLink(contextPath)) {
      throw new IllegalStateException("native Files transition context is unsafe");
    }
    JsonNode value = JSON.readTree(
        readBoundedRegularFile(
            contextPath,
            MAX_TRANSITION_CONTEXT_BYTES,
            "native Files transition context"));
    requireExactKeys(value, CONTEXT_KEYS, "native Files transition context");
    TransitionContext context =
        new TransitionContext(
            value.path("transitionKind").asText(),
            value.path("composeProject").asText(),
            value.path("runScope").asText(),
            value.path("candidateCommit").asText());
    if (!TRANSITION_CONTEXT_FORMAT.equals(value.path("schemaVersion").asText())
        || !TRANSITION_KINDS.contains(context.transitionKind())
        || !SCOPE.matcher(context.composeProject()).matches()
        || !SCOPE.matcher(context.runScope()).matches()
        || !COMMIT.matcher(context.candidateCommit()).matches()
        || !context.candidateCommit().equals(candidateCommit)) {
      throw new IllegalStateException("native Files transition context is stale or invalid");
    }
    return Optional.of(context);
  }

  public static Authority mint(TransitionContext context, String schemaHistoryFingerprint)
      throws Exception {
    Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    String volumeRef = UUID.randomUUID().toString();
    String generationRef = UUID.randomUUID().toString();
    String transitionRef = UUID.randomUUID().toString();
    String transitionReceiptDigest =
        digest(
            canonical(
                transitionReceiptProjection(
                    context,
                    volumeRef,
                    generationRef,
                    transitionRef,
                    schemaHistoryFingerprint,
                    createdAt)));
    Authority withoutMarkerDigest =
        new Authority(
            AUTHORITY_KEY,
            volumeRef,
            generationRef,
            context.transitionKind(),
            transitionRef,
            transitionReceiptDigest,
            schemaHistoryFingerprint,
            "",
            createdAt);
    String rootMarkerDigest = digest(markerBytes(withoutMarkerDigest));
    Authority authority =
        new Authority(
            withoutMarkerDigest.authorityKey(),
            withoutMarkerDigest.volumeRef(),
            withoutMarkerDigest.generationRef(),
            withoutMarkerDigest.transitionKind(),
            withoutMarkerDigest.transitionRef(),
            withoutMarkerDigest.transitionReceiptDigest(),
            withoutMarkerDigest.schemaHistoryFingerprint(),
            rootMarkerDigest,
            withoutMarkerDigest.createdAt());
    validate(authority);
    return authority;
  }

  public static void validateTransitionReceipt(
      Authority authority, TransitionContext context) throws Exception {
    String observed =
        digest(
            canonical(
                transitionReceiptProjection(
                    context,
                    authority.volumeRef(),
                    authority.generationRef(),
                    authority.transitionRef(),
                    authority.schemaHistoryFingerprint(),
                    authority.createdAt())));
    if (!authority.transitionKind().equals(context.transitionKind())
        || !MessageDigest.isEqual(
            observed.getBytes(StandardCharsets.US_ASCII),
            authority.transitionReceiptDigest().getBytes(StandardCharsets.US_ASCII))) {
      throw new IllegalStateException(
          "native Files transition receipt does not match its accepted call path");
    }
  }

  public static void createOrValidateMarker(Path blobRoot, Authority authority)
      throws Exception {
    requireSafeRoot(blobRoot);
    Path marker = markerPath(blobRoot);
    byte[] expected = markerBytes(authority);
    if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
      validateMarker(blobRoot, authority);
      return;
    }
    try (var entries = Files.list(blobRoot)) {
      if (entries.findAny().isPresent()) {
        throw new IllegalStateException(
            "native Files volume authority requires an empty physical blob root");
      }
    }
    Path temporary = Files.createTempFile(blobRoot, ".weave-files-volume-authority-", ".tmp");
    try {
      Files.write(
          temporary,
          expected,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
      try {
        Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
      } catch (UnsupportedOperationException ignored) {
        // POSIX permissions are enforced by the deployment filesystem where supported.
      }
      try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        file.force(true);
      }
      Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE);
      forceDirectory(blobRoot);
    } finally {
      Files.deleteIfExists(temporary);
      Arrays.fill(expected, (byte) 0);
    }
    validateMarker(blobRoot, authority);
  }

  public static void validateMarker(Path blobRoot, Authority authority) throws Exception {
    validate(authority);
    requireSafeRoot(blobRoot);
    Path marker = markerPath(blobRoot);
    byte[] observed = readBoundedRegularFile(
        marker,
        MAX_ROOT_MARKER_BYTES,
        "native Files volume authority marker");
    byte[] canonicalObserved;
    JsonNode markerValue;
    try {
      markerValue = JSON.readTree(observed);
      requireExactKeys(markerValue, MARKER_KEYS, "native Files volume authority marker");
      canonicalObserved = canonical(markerValue);
    } catch (RuntimeException invalidJson) {
      throw new IllegalStateException("native Files volume authority marker is invalid", invalidJson);
    }
    byte[] expected = markerBytes(authority);
    try {
      if (!MessageDigest.isEqual(observed, canonicalObserved)
          || !MessageDigest.isEqual(observed, expected)
          || !MessageDigest.isEqual(
              digest(observed).getBytes(StandardCharsets.US_ASCII),
              authority.rootMarkerDigest().getBytes(StandardCharsets.US_ASCII))) {
        throw new IllegalStateException("native Files volume authority marker was altered");
      }
    } finally {
      Arrays.fill(observed, (byte) 0);
      Arrays.fill(canonicalObserved, (byte) 0);
      Arrays.fill(expected, (byte) 0);
    }
  }

  public static Map<String, Object> receiptProjection(Authority authority) throws Exception {
    validate(authority);
    Map<String, Object> value = authorityFields(authority);
    value.put("authorityRowDigest", rowDigest(authority));
    return value;
  }

  public static Authority authorityFromReceipt(JsonNode value) throws Exception {
    requireExactKeys(value, RECEIPT_PROJECTION_KEYS, "native Files schema-receipt binding");
    Authority authority =
        new Authority(
            value.path("authorityKey").asText(),
            value.path("volumeRef").asText(),
            value.path("generationRef").asText(),
            value.path("transitionKind").asText(),
            value.path("transitionRef").asText(),
            value.path("transitionReceiptDigest").asText(),
            value.path("schemaHistoryFingerprint").asText(),
            value.path("rootMarkerDigest").asText(),
            Instant.parse(value.path("createdAtUtc").asText()));
    validate(authority);
    String expectedRowDigest = rowDigest(authority);
    if (!MessageDigest.isEqual(
        expectedRowDigest.getBytes(StandardCharsets.US_ASCII),
        value.path("authorityRowDigest").asText().getBytes(StandardCharsets.US_ASCII))) {
      throw new IllegalStateException("native Files authority-row receipt digest is invalid");
    }
    return authority;
  }

  public static String rowDigest(Authority authority) throws Exception {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("schemaVersion", ROW_FORMAT);
    row.putAll(authorityFields(authority));
    return digest(canonical(row));
  }

  public static void deleteTransitionContext(Path receiptParent) throws IOException {
    Path context = receiptParent.resolve(TRANSITION_CONTEXT_FILE_NAME).normalize();
    if (!context.getParent().equals(receiptParent.normalize()) || Files.isSymbolicLink(context)) {
      throw new IllegalStateException("native Files transition context is unsafe");
    }
    if (Files.deleteIfExists(context)) {
      forceDirectory(receiptParent);
    }
  }

  private static Map<String, Object> authorityFields(Authority authority) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("authorityKey", authority.authorityKey());
    value.put("volumeRef", authority.volumeRef());
    value.put("generationRef", authority.generationRef());
    value.put("transitionKind", authority.transitionKind());
    value.put("transitionRef", authority.transitionRef());
    value.put("transitionReceiptDigest", authority.transitionReceiptDigest());
    value.put("schemaHistoryFingerprint", authority.schemaHistoryFingerprint());
    value.put("rootMarkerDigest", authority.rootMarkerDigest());
    value.put("createdAtUtc", authority.createdAt().toString());
    return value;
  }

  private static Map<String, Object> markerProjection(Authority authority) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("schemaVersion", MARKER_FORMAT);
    value.put("authorityKey", authority.authorityKey());
    value.put("volumeRef", authority.volumeRef());
    value.put("generationRef", authority.generationRef());
    value.put("transitionRef", authority.transitionRef());
    value.put("transitionReceiptDigest", authority.transitionReceiptDigest());
    value.put("schemaHistoryFingerprint", authority.schemaHistoryFingerprint());
    return value;
  }

  private static Map<String, Object> transitionReceiptProjection(
      TransitionContext context,
      String volumeRef,
      String generationRef,
      String transitionRef,
      String schemaHistoryFingerprint,
      Instant createdAt) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("schemaVersion", TRANSITION_RECEIPT_FORMAT);
    value.put("transitionKind", context.transitionKind());
    value.put("volumeRef", volumeRef);
    value.put("generationRef", generationRef);
    value.put("transitionRef", transitionRef);
    value.put("composeProject", context.composeProject());
    value.put("runScope", context.runScope());
    value.put("schemaHistoryFingerprint", schemaHistoryFingerprint);
    value.put("createdAtUtc", createdAt.toString());
    return value;
  }

  private static byte[] markerBytes(Authority authority) throws Exception {
    return canonical(markerProjection(authority));
  }

  private static byte[] canonical(Object value) throws Exception {
    String json = value instanceof JsonNode ? value.toString() : JSON.writeValueAsString(value);
    return new JsonCanonicalizer(json).getEncodedUTF8();
  }

  private static String digest(byte[] value) throws Exception {
    return "sha256:"
        + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }

  private static Path markerPath(Path root) {
    Path marker = root.resolve(MARKER_FILE_NAME).normalize();
    if (!marker.getParent().equals(root.normalize())) {
      throw new IllegalStateException("native Files marker path escaped its root");
    }
    return marker;
  }

  private static void requireSafeRoot(Path root) {
    if (Files.isSymbolicLink(root)
        || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException("native Files blob root is unavailable or unsafe");
    }
  }

  static byte[] readBoundedRegularFile(Path path, long maximumBytes, String description) {
    if (path == null || maximumBytes < 1 || maximumBytes > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("bounded authority artifact read is invalid");
    }
    try {
      BasicFileAttributes attributes =
          Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      if (!attributes.isRegularFile() || attributes.isSymbolicLink()
          || attributes.size() < 0 || attributes.size() > maximumBytes) {
        throw new IllegalStateException(description + " is missing, oversized, or unsafe");
      }
      int initialSize = Math.max(32, Math.toIntExact(attributes.size()));
      try (SeekableByteChannel channel = Files.newByteChannel(
              path,
              Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
          ByteArrayOutputStream output = new ByteArrayOutputStream(initialSize)) {
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        long total = 0;
        while (channel.read(buffer) >= 0) {
          buffer.flip();
          int read = buffer.remaining();
          total += read;
          if (total > maximumBytes) {
            throw new IllegalStateException(description + " is oversized");
          }
          output.write(buffer.array(), buffer.position(), read);
          buffer.clear();
        }
        return output.toByteArray();
      }
    } catch (IOException failure) {
      throw new IllegalStateException(description + " is unavailable or unsafe", failure);
    }
  }

  static void forceDirectory(Path directory) throws IOException {
    try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
      channel.force(true);
    }
  }

  private static void validate(Authority authority) {
    if (!AUTHORITY_KEY.equals(authority.authorityKey())
        || !isUuid(authority.volumeRef())
        || !isUuid(authority.generationRef())
        || !TRANSITION_KINDS.contains(authority.transitionKind())
        || !isUuid(authority.transitionRef())
        || !DIGEST.matcher(authority.transitionReceiptDigest()).matches()
        || !FINGERPRINT.matcher(authority.schemaHistoryFingerprint()).matches()
        || !DIGEST.matcher(authority.rootMarkerDigest()).matches()
        || authority.createdAt() == null
        || !authority.createdAt().toString().endsWith("Z")) {
      throw new IllegalStateException("native Files volume authority is invalid");
    }
  }

  private static boolean isUuid(String value) {
    try {
      return value != null && UUID.fromString(value).toString().equals(value);
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  private static void requireExactKeys(JsonNode value, Set<String> keys, String description) {
    if (!value.isObject()
        || !value.properties().stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet())
            .equals(keys)) {
      throw new IllegalStateException(description + " has an invalid shape");
    }
  }
}

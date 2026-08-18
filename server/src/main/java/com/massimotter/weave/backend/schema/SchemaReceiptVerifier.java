package com.massimotter.weave.backend.schema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.erdtman.jcs.JsonCanonicalizer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Support-safe Compose gate for the exact Flyway schema initializer receipt. */
public final class SchemaReceiptVerifier {

  private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
  private static final Pattern TABLE = Pattern.compile("weave_[a-z0-9_]+");

  private SchemaReceiptVerifier() {}

  public static void main(String[] args) {
    try {
      verify(System.getenv());
      System.out.println("schema-receipt: verified");
    } catch (Exception failure) {
      System.err.println("schema-receipt: blocked");
      System.exit(2);
    }
  }

  static void verify(Map<String, String> environment) throws Exception {
    String candidate = environment.getOrDefault("WEAVE_CANDIDATE_COMMIT", "");
    if (!COMMIT.matcher(candidate).matches()) {
      throw new IllegalStateException("candidate commit is invalid");
    }
    Path receipt =
        Path.of(environment.getOrDefault("WEAVE_SCHEMA_INIT_RECEIPT_FILE", ""))
            .toAbsolutePath()
            .normalize();
    if (Files.isSymbolicLink(receipt) || !Files.isRegularFile(receipt)) {
      throw new IllegalStateException("schema receipt is unavailable");
    }
    JsonNode value = new ObjectMapper().readTree(Files.readString(receipt));
    List<String> tables = new ArrayList<>();
    JsonNode tableValues = value.path("tables");
    if (!tableValues.isArray()) {
      throw new IllegalStateException("schema receipt has no catalog shape");
    }
    for (JsonNode table : tableValues) {
      tables.add(table.asText());
    }
    List<String> sortedTables = tables.stream().sorted().toList();
    JsonNode catalogProjection = value.path("catalogProjection");
    String observedFingerprint = "";
    List<String> projectionTables = new ArrayList<>();
    if (catalogProjection.isObject()) {
      byte[] canonical =
          new JsonCanonicalizer(catalogProjection.toString()).getEncodedUTF8();
      observedFingerprint =
          java.util.HexFormat.of()
              .formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
      java.util.Arrays.fill(canonical, (byte) 0);
      catalogProjection
          .path("tables")
          .properties()
          .forEach(property -> projectionTables.add(property.getKey()));
    }
    boolean validTables =
        !tables.isEmpty()
            && tables.equals(sortedTables)
            && tables.size() == new HashSet<>(tables).size()
            && tables.stream().allMatch(name -> TABLE.matcher(name).matches())
            && tables.contains("weave_schema_authority")
            && tables.equals(projectionTables);
    if (!"weave.schema-init-receipt/v3".equals(value.path("schemaVersion").asText())
        || !value.path("supportSafe").asBoolean(false)
        || !"flyway".equals(value.path("authority").asText())
        || !SchemaAuthorityInitializer.EPOCH.equals(value.path("epoch").asText())
        || !SchemaAuthorityInitializer.MODEL_ID.equals(value.path("relationalModelId").asText())
        || !candidate.equals(value.path("candidateCommit").asText())
        || value.path("migrationsExecuted").asInt(-1) < 0
        || value.path("targetSchemaVersion").asText().isBlank()
        || !value.path("catalogFingerprint").asText().matches("[0-9a-f]{64}")
        || !value.path("catalogFingerprint").asText().equals(observedFingerprint)
        || value.path("tableCount").asInt() != tables.size()
        || !validTables
        || value.path("secretValuesIncluded").asBoolean(true)) {
      throw new IllegalStateException("schema receipt does not match the exact candidate");
    }
  }
}

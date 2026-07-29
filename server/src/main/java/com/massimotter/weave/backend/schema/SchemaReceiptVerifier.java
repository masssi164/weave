package com.massimotter.weave.backend.schema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Support-safe Compose gate for the exact schema initializer receipt. */
public final class SchemaReceiptVerifier {

  private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

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
    if (!"weave.schema-init-receipt/v1".equals(value.path("schemaVersion").asText())
        || !value.path("supportSafe").asBoolean(false)
        || !SchemaAuthorityInitializer.EPOCH.equals(value.path("epoch").asText())
        || !SchemaAuthorityInitializer.MODEL_ID.equals(value.path("relationalModelId").asText())
        || !candidate.equals(value.path("candidateCommit").asText())
        || !value.path("catalogFingerprint").asText().matches("[0-9a-f]{64}")
        || value.path("tableCount").asInt() != 47
        || value.path("secretValuesIncluded").asBoolean(true)) {
      throw new IllegalStateException("schema receipt does not match the exact candidate");
    }
  }
}

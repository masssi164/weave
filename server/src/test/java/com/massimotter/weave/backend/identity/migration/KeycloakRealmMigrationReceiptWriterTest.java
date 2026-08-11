package com.massimotter.weave.backend.identity.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeycloakRealmMigrationReceiptWriterTest {
  private static final String SHA =
      "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

  @TempDir Path temporary;

  private final ObjectMapper mapper =
      tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();

  @Test
  void atomicallyPublishesOnlyTheSupportSafeCompletionReceipt() throws Exception {
    Files.createDirectories(temporary.resolve("keycloak/migrations"));
    KeycloakFgapMigrationExecutor.MigrationResult receipt = receipt();

    Path result = new KeycloakRealmMigrationReceiptWriter(mapper).write(temporary, receipt);

    assertThat(result)
        .isEqualTo(temporary.resolve(KeycloakFgapMigrationContract.RECEIPT_PATH));
    JsonNode value = mapper.readTree(Files.readAllBytes(result));
    assertThat(value.path("schemaVersion").asString())
        .isEqualTo("weave.keycloak-fgap-migration-receipt/v1");
    assertThat(value.path("status").asString()).isEqualTo("complete");
    assertThat(value.path("bootstrapAuthorityDeleted").asBoolean()).isTrue();
    assertThat(value.path("bootstrapAuthorityNegativeReadbackVerified").asBoolean()).isTrue();
    assertThat(value.path("containsSecretValues").asBoolean(true)).isFalse();
    assertThat(Files.list(result.getParent()).map(path -> path.getFileName().toString()))
        .containsExactly(result.getFileName().toString());
  }

  @Test
  void refusesToOverwriteExistingMigrationEvidence() throws Exception {
    Files.createDirectories(temporary.resolve("keycloak/migrations"));
    KeycloakRealmMigrationReceiptWriter writer = new KeycloakRealmMigrationReceiptWriter(mapper);
    writer.write(temporary, receipt());

    assertThatThrownBy(() -> writer.write(temporary, receipt()))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("migration-receipt-target-invalid");
  }

  private static KeycloakFgapMigrationExecutor.MigrationResult receipt() {
    return new KeycloakFgapMigrationExecutor.MigrationResult(
        KeycloakFgapMigrationContract.RESULT_SCHEMA,
        "complete",
        KeycloakFgapMigrationContract.OPERATION_ID,
        KeycloakFgapMigrationContract.KEYCLOAK_VERSION,
        SHA,
        SHA,
        SHA,
        SHA,
        SHA,
        List.of("create-identity-admin-subject-policy"),
        1,
        true,
        true,
        KeycloakFgapMigrationContract.BOOTSTRAP_REALM,
        KeycloakFgapMigrationContract.MIGRATION_CLIENT_ID,
        true,
        true,
        true,
        false);
  }
}

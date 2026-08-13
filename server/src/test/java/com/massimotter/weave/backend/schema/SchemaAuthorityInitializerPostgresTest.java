package com.massimotter.weave.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@Tag("postgres")
class SchemaAuthorityInitializerPostgresTest {

  private static final String CANDIDATE = "a".repeat(40);
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.9-alpine"))
          .withDatabaseName("weave_schema_authority")
          .withUsername("weave")
          .withPassword("weave-test-only");

  static {
    POSTGRES.start();
  }

  @Test
  void convergesEmptySchemaProducesStableReceiptAndRejectsDrift(@TempDir Path directory)
      throws Exception {
    Environment environment = environment("converged", directory);

    SchemaAuthorityInitializer.run(environment.values());
    var first = new ObjectMapper().readTree(Files.readString(environment.receipt()));
    assertThat(first.path("schemaVersion").asText()).isEqualTo("weave.schema-init-receipt/v3");
    assertThat(first.path("candidateCommit").asText()).isEqualTo(CANDIDATE);
    assertThat(first.path("catalogFingerprint").asText()).matches("[0-9a-f]{64}");
    assertThat(first.path("tableCount").asInt()).isEqualTo(first.path("tables").size());
    assertThat(first.path("tables"))
        .anyMatch(table -> "weave_schema_authority".equals(table.asText()));
    assertThat(first.path("catalogProjection").path("tables").size())
        .isEqualTo(first.path("tableCount").asInt());
    assertThat(first.path("secretValuesIncluded").asBoolean()).isFalse();
    SchemaReceiptVerifier.verify(environment.values());

    SchemaAuthorityInitializer.run(environment.values());
    var second = new ObjectMapper().readTree(Files.readString(environment.receipt()));
    assertThat(second.path("catalogFingerprint").asText())
        .isEqualTo(first.path("catalogFingerprint").asText());

    try (var connection =
            DriverManager.getConnection(
                environment.url(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute(
          "alter table weave_schema_authority "
              + "add constraint unexpected_unique unique (candidate_commit)");
    }
    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(environment.values()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fingerprint")
        .hasMessageNotContaining(POSTGRES.getPassword());

    try (var connection =
            DriverManager.getConnection(
                environment.url(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("alter table weave_schema_authority drop constraint unexpected_unique");
      statement.execute("alter table weave_schema_authority add column unexpected_drift integer");
    }
    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(environment.values()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fingerprint")
        .hasMessageNotContaining(POSTGRES.getPassword());
  }

  @Test
  void rejectsNonEmptyForeignSchemaBeforeHibernate(@TempDir Path directory) throws Exception {
    Environment environment = environment("foreign", directory);
    try (var connection =
            DriverManager.getConnection(
                environment.url(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("create table foreign_history(id bigint primary key)");
    }

    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(environment.values()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("non-empty schema(s)");

    try (var connection =
            DriverManager.getConnection(
                environment.url(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var rows =
            connection
                .prepareStatement(
                    "select count(*) from information_schema.tables "
                        + "where table_schema = current_schema()")
                .executeQuery()) {
      rows.next();
      assertThat(rows.getInt(1)).isEqualTo(1);
    }
  }

  private Environment environment(String semanticName, Path directory) throws Exception {
    String schema =
        semanticName + "_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("create schema \"" + schema + "\"");
    }
    String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
    String url = POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema;
    Path receipt = directory.resolve("schema-init-receipt.json");
    Map<String, String> values = new LinkedHashMap<>();
    values.put("WEAVE_PERSISTENCE_URL", url);
    values.put("WEAVE_PERSISTENCE_USERNAME", POSTGRES.getUsername());
    values.put("WEAVE_PERSISTENCE_PASSWORD", POSTGRES.getPassword());
    values.put("WEAVE_CANDIDATE_COMMIT", CANDIDATE);
    values.put("WEAVE_SCHEMA_INIT_RECEIPT_FILE", receipt.toString());
    return new Environment(url, receipt, values);
  }

  private record Environment(String url, Path receipt, Map<String, String> values) {}
}

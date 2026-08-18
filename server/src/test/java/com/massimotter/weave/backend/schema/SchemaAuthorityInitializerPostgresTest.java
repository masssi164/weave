package com.massimotter.weave.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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
    var first = receipt(environment);
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
    var second = receipt(environment);
    assertThat(second.path("catalogFingerprint").asText())
        .isEqualTo(first.path("catalogFingerprint").asText());
    assertThat(second.path("migrationsExecuted").asInt()).isZero();
    assertThat(second.path("targetSchemaVersion").asText()).isNotBlank();
    SchemaReceiptVerifier.verify(environment.values());

    try (var connection = connection(environment);
        var statement = connection.createStatement()) {
      statement.execute(
          "alter table weave_schema_authority "
              + "add constraint unexpected_unique unique (candidate_commit)");
    }
    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(environment.values()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fingerprint")
        .hasMessageNotContaining(POSTGRES.getPassword());

    try (var connection = connection(environment);
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
  void serializesConcurrentInitializersAcrossMigrationValidationAndReceipt(
      @TempDir Path directory) throws Exception {
    Environment first = environment("concurrent", directory, "schema-init-first.json");
    Environment second = first.withReceipt(directory.resolve("schema-init-second.json"));
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var firstRun = executor.submit(() -> runAfterGate(first, ready, start));
      var secondRun = executor.submit(() -> runAfterGate(second, ready, start));
      assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      firstRun.get(3, TimeUnit.MINUTES);
      secondRun.get(3, TimeUnit.MINUTES);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    }

    var firstReceipt = receipt(first);
    var secondReceipt = receipt(second);
    assertThat(List.of(
            firstReceipt.path("migrationsExecuted").asInt(),
            secondReceipt.path("migrationsExecuted").asInt()))
        .contains(0)
        .anyMatch(executed -> executed > 0);
    assertThat(firstReceipt.path("catalogFingerprint").asText())
        .isEqualTo(secondReceipt.path("catalogFingerprint").asText());
    SchemaReceiptVerifier.verify(first.values());
    SchemaReceiptVerifier.verify(second.values());

    try (var connection = connection(first);
        var statement = connection.createStatement();
        var markerRows = statement.executeQuery("select count(*) from weave_schema_authority")) {
      markerRows.next();
      assertThat(markerRows.getInt(1)).isEqualTo(1);
    }
    try (var connection = connection(first);
        var statement = connection.createStatement();
        var duplicateVersions = statement.executeQuery(
            "select count(*) from ("
                + "select version from flyway_schema_history "
                + "where success and version is not null "
                + "group by version having count(*) > 1"
                + ") duplicated")) {
      duplicateVersions.next();
      assertThat(duplicateVersions.getInt(1)).isZero();
    }
  }

  @Test
  void rejectsModifiedAppliedMigrationChecksumBeforeHibernate(@TempDir Path directory)
      throws Exception {
    Environment environment = environment("checksum", directory);
    SchemaAuthorityInitializer.run(environment.values());

    try (var connection = connection(environment);
        var statement = connection.createStatement()) {
      int changed = statement.executeUpdate(
          "update flyway_schema_history set checksum = checksum + 1 "
              + "where installed_rank = ("
              + "select max(installed_rank) from flyway_schema_history "
              + "where success and version is not null and checksum is not null"
              + ")");
      assertThat(changed).isEqualTo(1);
    }

    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(environment.values()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("checksum")
        .hasMessageNotContaining(POSTGRES.getPassword());
  }

  @Test
  void upgradesPreviousResolvedFlywayVersionAndThenRestartsAsNoOp(@TempDir Path directory)
      throws Exception {
    Environment environment = environment("upgrade", directory);
    List<MigrationVersion> versions = Arrays.stream(flyway(environment, null).info().all())
        .map(info -> info.getVersion())
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .toList();
    assertThat(versions).hasSizeGreaterThanOrEqualTo(2);
    MigrationVersion previous = versions.get(versions.size() - 2);
    MigrationVersion latest = versions.getLast();

    Flyway previousFlyway = flyway(environment, previous);
    previousFlyway.migrate();
    assertThat(previousFlyway.info().current().getVersion()).isEqualTo(previous);

    SchemaAuthorityInitializer.run(environment.values());
    var upgraded = receipt(environment);
    assertThat(upgraded.path("migrationsExecuted").asInt()).isPositive();
    assertThat(upgraded.path("targetSchemaVersion").asText()).isEqualTo(latest.getVersion());
    assertThat(flyway(environment, null).info().current().getVersion()).isEqualTo(latest);
    SchemaReceiptVerifier.verify(environment.values());

    SchemaAuthorityInitializer.run(environment.values());
    assertThat(receipt(environment).path("migrationsExecuted").asInt()).isZero();
  }

  @Test
  void rejectsNonEmptyForeignSchemaBeforeHibernate(@TempDir Path directory) throws Exception {
    Environment environment = environment("foreign", directory);
    try (var connection = connection(environment);
        var statement = connection.createStatement()) {
      statement.execute("create table foreign_history(id bigint primary key)");
    }

    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(environment.values()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("non-empty schema(s)");

    try (var connection = connection(environment);
        var rows = connection
            .prepareStatement(
                "select count(*) from information_schema.tables "
                    + "where table_schema = current_schema()")
            .executeQuery()) {
      rows.next();
      assertThat(rows.getInt(1)).isEqualTo(1);
    }
  }

  private Void runAfterGate(
      Environment environment,
      CountDownLatch ready,
      CountDownLatch start) throws Exception {
    ready.countDown();
    if (!start.await(30, TimeUnit.SECONDS)) {
      throw new IllegalStateException("concurrent schema initializer start gate timed out");
    }
    SchemaAuthorityInitializer.run(environment.values());
    return null;
  }

  private Flyway flyway(Environment environment, MigrationVersion target) {
    var configuration = Flyway.configure()
        .dataSource(environment.url(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .baselineOnMigrate(false)
        .cleanDisabled(true);
    if (target != null) {
      configuration.target(target);
    }
    return configuration.load();
  }

  private tools.jackson.databind.JsonNode receipt(Environment environment) throws Exception {
    return new ObjectMapper().readTree(Files.readString(environment.receipt()));
  }

  private java.sql.Connection connection(Environment environment) throws Exception {
    return DriverManager.getConnection(
        environment.url(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private Environment environment(String semanticName, Path directory) throws Exception {
    return environment(semanticName, directory, "schema-init-receipt.json");
  }

  private Environment environment(
      String semanticName,
      Path directory,
      String receiptFileName) throws Exception {
    String schema = semanticName + "_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("create schema \"" + schema + "\"");
    }
    String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
    String url = POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema;
    Path receipt = directory.resolve(receiptFileName);
    Map<String, String> values = new LinkedHashMap<>();
    values.put("WEAVE_PERSISTENCE_URL", url);
    values.put("WEAVE_PERSISTENCE_USERNAME", POSTGRES.getUsername());
    values.put("WEAVE_PERSISTENCE_PASSWORD", POSTGRES.getPassword());
    values.put("WEAVE_CANDIDATE_COMMIT", CANDIDATE);
    values.put("WEAVE_SCHEMA_INIT_RECEIPT_FILE", receipt.toString());
    return new Environment(url, receipt, values);
  }

  private record Environment(String url, Path receipt, Map<String, String> values) {
    private Environment {
      values = Map.copyOf(values);
    }

    private Environment withReceipt(Path nextReceipt) {
      Map<String, String> nextValues = new LinkedHashMap<>(values);
      nextValues.put("WEAVE_SCHEMA_INIT_RECEIPT_FILE", nextReceipt.toString());
      return new Environment(url, nextReceipt, nextValues);
    }
  }
}

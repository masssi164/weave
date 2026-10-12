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
    assertThat(first.path("schemaVersion").asText())
        .isEqualTo(SchemaAuthorityInitializer.RECEIPT_FORMAT);
    assertThat(first.path("catalogFingerprintFormat").asText())
        .isEqualTo(SchemaCatalogFingerprint.FORMAT);
    assertThat(first.path("catalogProjection").path("format").asText())
        .isEqualTo(SchemaCatalogFingerprint.FORMAT);
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
  void missingOrSharedRuntimeRoleCannotPublishReceipt(@TempDir Path directory) throws Exception {
    Environment environment = environment("missing_role", directory);
    var values = new LinkedHashMap<>(environment.values());
    values.remove("WEAVE_SCHEMA_RUNTIME_ROLE");
    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(values))
        .hasMessageContaining("WEAVE_SCHEMA_RUNTIME_ROLE");
    values.put("WEAVE_SCHEMA_RUNTIME_ROLE", POSTGRES.getUsername());
    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(values))
        .hasMessageContaining("distinct");
    assertThat(Files.exists(environment.receipt())).isFalse();
  }

  @Test
  void rejectsMalformedStaleAndHistoricalReceipt(@TempDir Path directory) throws Exception {
    Environment environment = environment("receipt_types", directory);
    SchemaAuthorityInitializer.run(environment.values());
    String original = Files.readString(environment.receipt());
    for (String field : List.of("supportSafe", "secretValuesIncluded", "migrationsExecuted", "tableCount")) {
      var malformed = (tools.jackson.databind.node.ObjectNode) new ObjectMapper().readTree(original);
      malformed.put(field, "true");
      Files.writeString(environment.receipt(), malformed.toString());
      assertThatThrownBy(() -> SchemaReceiptVerifier.verify(environment.values()))
          .hasMessageContaining("malformed");
    }
    for (String field : List.of("candidateCommit", "schemaVersion", "completedAtUtc")) {
      var malformed = (tools.jackson.databind.node.ObjectNode) new ObjectMapper().readTree(original);
      malformed.put(field, switch (field) {
        case "candidateCommit" -> "b".repeat(40);
        case "schemaVersion" -> "weave.schema-init-receipt/v4";
        default -> "2099-01-01T00:00:00Z";
      });
      Files.writeString(environment.receipt(), malformed.toString());
      assertThatThrownBy(() -> SchemaReceiptVerifier.verify(environment.values()))
          .isInstanceOf(IllegalStateException.class);
    }
    Files.writeString(environment.receipt(), original);
    SchemaReceiptVerifier.verify(environment.values());
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
    try (var connection = connection(environment);
        var marker = connection.prepareStatement("insert into weave_schema_authority "
            + "(epoch,relational_model_id,candidate_commit,catalog_fingerprint,completed_at_utc) "
            + "values (?,?,?,?,CURRENT_TIMESTAMP)");
        var statement = connection.createStatement()) {
      marker.setString(1, SchemaAuthorityInitializer.EPOCH);
      marker.setString(2, SchemaAuthorityInitializer.MODEL_ID);
      marker.setString(3, "b".repeat(40));
      marker.setString(4, SchemaCatalogFingerprint.inspectLegacyV2(connection).sha256());
      marker.executeUpdate();
      statement.execute("insert into weave_files_user_resources "
          + "(organization_ref,file_id,binding_revision,space_ref,parent_file_id,canonical_path,active_path_key,"
          + "object_kind,owner_principal_ref,lifecycle_state,created_at_utc,modified_at_utc,version) "
          + "values ('org:legacy','file:legacy',1,'workspace-default','file:root','/legacy','/legacy',"
          + "'FILE','user:alice','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)");
    }

    SchemaAuthorityInitializer.run(environment.values());
    var upgraded = receipt(environment);
    assertThat(upgraded.path("migrationsExecuted").asInt()).isPositive();
    assertThat(upgraded.path("targetSchemaVersion").asText()).isEqualTo(latest.getVersion());
    assertThat(flyway(environment, null).info().current().getVersion()).isEqualTo(latest);
    SchemaReceiptVerifier.verify(environment.values());
    try (var connection = connection(environment); var statement = connection.createStatement();
        var rows = statement.executeQuery("select visibility_scope,owner_principal_ref "
            + "from weave_files_user_resources where file_id='file:legacy'")) {
      assertThat(rows.next()).isTrue();
      assertThat(rows.getString(1)).isEqualTo("PRIVATE");
      assertThat(rows.getString(2)).isEqualTo("user:alice");
    }

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

  @Test
  void rejectsManualDdlWithoutMarkerAndCleansOwnedReference(@TempDir Path directory) throws Exception {
    Environment environment = environment("unmarked_drift", directory);
    flyway(environment, MigrationVersion.fromVersion("12")).migrate();
    try (var connection = connection(environment); var statement = connection.createStatement()) {
      statement.execute("alter table weave_files_user_resources add column manual_drift integer");
    }
    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(environment.values()))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("fingerprint");
    assertThat(flyway(environment, null).info().current().getVersion().getVersion()).isEqualTo("12");
    assertThat(Files.exists(environment.receipt())).isFalse();
    try (var connection = connection(environment); var statement = connection.createStatement()) {
      statement.execute("alter table weave_files_user_resources drop column manual_drift");
      statement.execute("create table sql_unexpected_owned_object(id integer)");
    }
    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(environment.values()))
        .hasMessageContaining("fingerprint");
    try (var connection = connection(environment); var statement = connection.createStatement();
        var rows = statement.executeQuery("select count(*) from pg_namespace "
            + "where nspname like 'weave_catalog_reference_%'")) {
      rows.next();
      assertThat(rows.getInt(1)).isZero();
    }
  }

  @Test
  void rejectsUnexpectedViewFunctionAndCustomTypes(@TempDir Path directory) throws Exception {
    Environment environment = environment("other_objects", directory);
    SchemaAuthorityInitializer.run(environment.values());
    String[] additions = {
        "create view unexpected_view as select 1 as value",
        "create materialized view unexpected_materialized as select 1 as value",
        "create function unexpected_function() returns integer language sql as 'select 1'",
        "create type unexpected_enum as enum ('first','second')",
        "create domain unexpected_domain as integer check (value > 0)",
        "create type unexpected_composite as (value integer)",
        "create sequence unexpected_sequence"
    };
    String[] removals = {
        "drop view unexpected_view", "drop materialized view unexpected_materialized",
        "drop function unexpected_function()", "drop type unexpected_enum",
        "drop domain unexpected_domain", "drop type unexpected_composite", "drop sequence unexpected_sequence"
    };
    for (int index = 0; index < additions.length; index++) {
      try (var connection = connection(environment); var statement = connection.createStatement()) {
        statement.execute(additions[index]);
      }
      assertThatThrownBy(() -> SchemaAuthorityInitializer.run(environment.values()))
          .hasMessageContaining("fingerprint");
      try (var connection = connection(environment); var statement = connection.createStatement()) {
        statement.execute(removals[index]);
      }
    }
    SchemaAuthorityInitializer.run(environment.values());
    assertThat(receipt(environment).path("migrationsExecuted").asInt()).isZero();
  }

  @Test
  void terminatedProbeOwnedLockConnectionReleasesInitializer(@TempDir Path directory) throws Exception {
    Environment environment = environment("terminated_lock", directory);
    var executor = Executors.newSingleThreadExecutor();
    try (var lock = connection(environment); var statement = lock.createStatement()) {
      int pid;
      try (var rows = statement.executeQuery("select pg_backend_pid(),pg_advisory_lock("
          + "hashtext(current_database()),hashtext(coalesce(current_schema(),'public')))")) {
        rows.next(); pid = rows.getInt(1);
      }
      var blocked = executor.submit(() -> { SchemaAuthorityInitializer.run(environment.values()); return null; });
      try (var control = connection(environment); var query = control.prepareStatement(
          "select count(*) from pg_stat_activity where datname=current_database() "
              + "and ?=any(pg_blocking_pids(pid))")) {
        query.setInt(1, pid);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean waiting = false;
        while (System.nanoTime() < deadline) {
          try (var rows = query.executeQuery()) { rows.next(); waiting = rows.getInt(1) > 0; }
          if (waiting) break;
          TimeUnit.MILLISECONDS.sleep(50);
        }
        assertThat(waiting).isTrue();
        assertThat(blocked.isDone()).isFalse();
        try (var terminate = control.prepareStatement("select pg_terminate_backend(?)")) {
          terminate.setInt(1, pid);
          try (var rows = terminate.executeQuery()) { rows.next(); assertThat(rows.getBoolean(1)).isTrue(); }
        }
      }
      blocked.get(60, TimeUnit.SECONDS);
      SchemaReceiptVerifier.verify(environment.values());
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void rejectsColumnWidthAndForeignKeyActionDriftBeforeUpgrade(@TempDir Path directory) throws Exception {
    Environment environment = environment("typed_drift", directory);
    flyway(environment, MigrationVersion.fromVersion("12")).migrate();
    try (var connection = connection(environment); var statement = connection.createStatement()) {
      statement.execute("alter table weave_files_user_resources alter column owner_principal_ref type varchar(254)");
    }
    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(environment.values()))
        .hasMessageContaining("fingerprint");
    try (var connection = connection(environment); var statement = connection.createStatement()) {
      statement.execute("alter table weave_files_user_resources alter column owner_principal_ref type varchar(255)");
      try (var rows = statement.executeQuery("select rel.relname,pc.conname from pg_constraint pc "
          + "join pg_class rel on rel.oid=pc.conrelid join pg_namespace ns on ns.oid=rel.relnamespace "
          + "where pc.contype='f' and ns.nspname=current_schema() limit 1")) {
        assertThat(rows.next()).isTrue();
        String table = rows.getString(1), constraint = rows.getString(2);
        try (var change = connection.createStatement()) {
          change.execute("alter table \"" + table + "\" drop constraint \"" + constraint + "\"");
          change.execute("alter table \"" + table + "\" add constraint \"" + constraint
              + "\" foreign key(category) references weave_provider_selections(category) on delete cascade");
        }
      }
    }
    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(environment.values()))
        .hasMessageContaining("fingerprint");
    assertThat(flyway(environment, null).info().current().getVersion().getVersion()).isEqualTo("12");
  }

  @Test
  void servingRoleMustLackDdlIncludingInheritedOwnership(@TempDir Path directory) throws Exception {
    Environment environment = environment("runtime_role", directory);
    SchemaAuthorityInitializer.run(environment.values());
    String role = "weave_runtime_" + UUID.randomUUID().toString().replace("-", "");
    String schema;
    try (var connection = connection(environment); var statement = connection.createStatement()) {
      schema = connection.getSchema();
      assertThatThrownBy(() -> ServingSchemaAuthorityGuard.requireDmlOnly(connection))
          .hasMessageContaining("DDL capability");
      statement.execute("create role " + role + " login password 'test-only-runtime'");
      statement.execute("revoke create on database " + POSTGRES.getDatabaseName() + " from public");
      statement.execute("grant usage on schema " + schema + " to " + role);
      statement.execute("grant select,insert,update,delete on all tables in schema " + schema + " to " + role);
    }
    try {
      var runtimeEnvironment = new LinkedHashMap<>(environment.values());
      runtimeEnvironment.put("WEAVE_SCHEMA_RUNTIME_ROLE", role);
      SchemaAuthorityInitializer.run(runtimeEnvironment);
      var markerRepository = org.mockito.Mockito.mock(
          com.massimotter.weave.backend.persistence.jpa.schema.SchemaAuthorityJpaRepository.class);
      var acceptedMarker = new com.massimotter.weave.backend.persistence.jpa.schema.SchemaAuthorityJpaEntity(
          SchemaAuthorityInitializer.EPOCH, SchemaAuthorityInitializer.MODEL_ID, CANDIDATE,
          receipt(environment).path("catalogFingerprint").asText(), java.time.Instant.now());
      org.mockito.Mockito.when(markerRepository.findAll()).thenReturn(List.of(acceptedMarker));
      var settings = new org.springframework.core.env.StandardEnvironment();
      var values = new LinkedHashMap<String, Object>();
      values.put("weave.deployment.profile", "dogfood");
      values.put("weave.candidate.commit", CANDIDATE);
      settings.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test", values));
      var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
          environment.url(), role, "test-only-runtime");
      var guard = new ServingSchemaAuthorityGuard(source, markerRepository, settings);
      guard.afterSingletonsInstantiated();
      values.put("weave.candidate.commit", "b".repeat(40));
      assertThatThrownBy(guard::afterSingletonsInstantiated).hasMessageContaining("exact candidate");
      values.put("weave.candidate.commit", CANDIDATE);
      try (var connection = connection(environment); var statement = connection.createStatement()) {
        statement.execute("create index unexpected_runtime_drift on weave_schema_authority(candidate_commit)");
      }
      assertThatThrownBy(guard::afterSingletonsInstantiated)
          .hasRootCauseMessage("serving schema catalog fingerprint differs from accepted authority");
      try (var connection = connection(environment); var statement = connection.createStatement()) {
        statement.execute("drop index unexpected_runtime_drift");
      }
      try (var connection = DriverManager.getConnection(environment.url(), role, "test-only-runtime")) {
        ServingSchemaAuthorityGuard.requireDmlOnly(connection);
        assertThatThrownBy(() -> connection.createStatement().execute("delete from weave_schema_authority"))
            .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> connection.createStatement().execute("delete from flyway_schema_history"))
            .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> connection.createStatement().execute("create table forbidden(id int)"))
            .hasMessageContaining("permission denied");
      }
      try (var connection = connection(environment); var statement = connection.createStatement()) {
        statement.execute("grant " + POSTGRES.getUsername() + " to " + role);
      }
      try (var connection = DriverManager.getConnection(environment.url(), role, "test-only-runtime")) {
        assertThatThrownBy(() -> ServingSchemaAuthorityGuard.requireDmlOnly(connection))
            .hasMessageContaining("DDL capability");
      }
    } finally {
      try (var connection = connection(environment); var statement = connection.createStatement()) {
        statement.execute("drop owned by " + role);
        statement.execute("drop role " + role);
      }
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
    return new Environment(url, receipt, SchemaAuthorityTestSupport.withRuntimeRole(values));
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

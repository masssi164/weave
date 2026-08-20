package com.massimotter.weave.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.port.FilesMutationPlan;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@Tag("postgres")
class SchemaAuthorityInitializerPostgresTest {

  private static final String CANDIDATE = "a".repeat(40);
  private static final String DIGEST_A = "sha256:" + "a".repeat(64);
  private static final String DIGEST_B = "sha256:" + "b".repeat(64);
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.9-alpine"))
          .withDatabaseName("weave_schema_authority")
          .withUsername("weave")
          .withPassword("weave-test-only");

  static {
    POSTGRES.start();
    try {
      SchemaAuthorityTestSupport.ensureServingRole(
          POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    } catch (Exception failure) {
      throw new ExceptionInInitializerError(failure);
    }
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
    assertThat(first.path("nativeFilesVolumeAuthority").path("authorityKey").asText())
        .isEqualTo(NativeFilesVolumeAuthority.AUTHORITY_KEY);
    assertThat(first.path("secretValuesIncluded").asBoolean()).isFalse();
    assertThat(PosixFilePermissions.toString(
            Files.getPosixFilePermissions(environment.receipt())))
        .isEqualTo("rw-------");
    assertThat(directory.resolve(
            NativeFilesVolumeAuthority.TRANSITION_CONTEXT_FILE_NAME))
        .doesNotExist();
    SchemaReceiptVerifier.verify(environment.values());

    SchemaAuthorityInitializer.run(environment.values());
    var second = receipt(environment);
    assertThat(second.path("catalogFingerprint").asText())
        .isEqualTo(first.path("catalogFingerprint").asText());
    assertThat(second.path("migrationsExecuted").asInt()).isZero();
    assertThat(second.path("targetSchemaVersion").asText()).isNotBlank();
    assertThat(second.path("nativeFilesVolumeAuthority").path("volumeRef").asText())
        .isEqualTo(first.path("nativeFilesVolumeAuthority").path("volumeRef").asText());
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
  void rejectsPopulatedBlobRootBeforeV7AndTrustsItOnlyAfterV7History(
      @TempDir Path directory) throws Exception {
    Environment blocked = environment("v7_blob_guard", directory);
    flyway(blocked, MigrationVersion.fromVersion("6")).migrate();
    Files.writeString(blocked.blobRoot().resolve("orphaned-private-blob"), "payload");

    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(blocked.values()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("blob root is populated")
        .hasMessageNotContaining(POSTGRES.getPassword());
    assertThat(flyway(blocked, null).info().current().getVersion().getVersion())
        .isEqualTo("6");

    Environment initialized = environment("v7_blob_history", directory);
    SchemaAuthorityInitializer.run(initialized.values());
    Files.writeString(initialized.blobRoot().resolve("committed-private-blob"), "payload");
    SchemaAuthorityInitializer.run(initialized.values());

    assertThat(receipt(initialized).path("nativeFilesVolumeAuthority").path("authorityKey").asText())
        .isEqualTo(NativeFilesVolumeAuthority.AUTHORITY_KEY);
    SchemaReceiptVerifier.verify(initialized.values());
  }

  @Test
  void ordinaryInitializationCannotCreateOrRepairImmutableVolumeAuthority(
      @TempDir Path directory) throws Exception {
    Environment ordinary = environment("volume_authority_ordinary", directory);
    Files.delete(
        directory.resolve(NativeFilesVolumeAuthority.TRANSITION_CONTEXT_FILE_NAME));

    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(ordinary.values()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("accepted empty-root transition");
    try (var connection = connection(ordinary);
        var statement = connection.createStatement();
        var rows = statement.executeQuery(
            "select count(*) from weave_files_volume_authorities")) {
      assertThat(rows.next()).isTrue();
      assertThat(rows.getInt(1)).isZero();
    }
    assertThat(
            ordinary.blobRoot().resolve(
                NativeFilesVolumeAuthority.MARKER_FILE_NAME))
        .doesNotExist();

    Environment authorized = environment("volume_authority_immutable", directory);
    SchemaAuthorityInitializer.run(authorized.values());
    try (var connection = connection(authorized);
        var statement = connection.createStatement()) {
      assertThatThrownBy(
              () -> statement.executeUpdate(
                  "update weave_files_volume_authorities "
                      + "set transition_kind = 'AUTHORIZED_RESET'"))
          .hasMessageContaining("immutable");
      assertThatThrownBy(
              () -> statement.executeUpdate(
                  "delete from weave_files_volume_authorities"))
          .hasMessageContaining("immutable");
    }
    Files.writeString(
        authorized.blobRoot().resolve(
            NativeFilesVolumeAuthority.MARKER_FILE_NAME),
        "{}");
    assertThatThrownBy(() -> SchemaAuthorityInitializer.run(authorized.values()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("marker");
  }

  @Test
  void dedicatedMigratorOwnsSchemaWhileServingHasOnlyBoundedDml(
      @TempDir Path directory) throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String migrator = "weave_migrator_" + suffix;
    String serving = "weave_serving_" + suffix;
    String database = "weave_roles_" + suffix;
    String migratorPassword = "migrator-test-" + suffix;
    String servingPassword = "serving-test-" + suffix;
    try (var connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute(
          "create role \"" + migrator + "\" login password '" + migratorPassword
              + "' nosuperuser nocreatedb nocreaterole noinherit noreplication");
      statement.execute(
          "create role \"" + serving + "\" login password '" + servingPassword
              + "' nosuperuser nocreatedb nocreaterole noinherit noreplication");
      statement.execute("create database \"" + database + "\" owner \"" + migrator + "\"");
    }
    String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":"
        + POSTGRES.getMappedPort(5432) + "/" + database;
    try (var connection = DriverManager.getConnection(
            url, POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("alter schema public owner to \"" + migrator + "\"");
    }
    Path receipt = directory.resolve("role-separated-schema-receipt.json");
    Path blobRoot = Files.createDirectory(directory.resolve("role-separated-blobs"));
    writeTransitionContext(directory, CANDIDATE);
    Map<String, String> values = new LinkedHashMap<>();
    values.put("WEAVE_PERSISTENCE_URL", url);
    values.put("WEAVE_PERSISTENCE_USERNAME", migrator);
    values.put("WEAVE_PERSISTENCE_PASSWORD", migratorPassword);
    values.put("WEAVE_SERVING_DB_USERNAME", serving);
    values.put("WEAVE_CANDIDATE_COMMIT", CANDIDATE);
    values.put("WEAVE_SCHEMA_INIT_RECEIPT_FILE", receipt.toString());
    values.put("WEAVE_NATIVE_FILES_BLOB_ROOT", blobRoot.toString());

    SchemaAuthorityInitializer.run(Map.copyOf(values));

    try (var servingConnection = DriverManager.getConnection(url, serving, servingPassword);
        var statement = servingConnection.createStatement()) {
      try (var rows = statement.executeQuery(
          "select count(*) from weave_files_volume_authorities")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isEqualTo(1);
      }
      try (var rows = statement.executeQuery("select count(*) from flyway_schema_history")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isPositive();
      }
      assertThat(statement.executeUpdate("""
          insert into weave_files_stream_heads (
              organization_ref,
              space_ref,
              latest_revision,
              reset_required_floor,
              lock_version,
              updated_at_utc)
          values ('role-test-org', 'role-test-space', 0, 0, 0, now())
          """)).isEqualTo(1);
      assertThat(statement.executeUpdate("""
          delete from weave_files_stream_heads
           where organization_ref = 'role-test-org'
             and space_ref = 'role-test-space'
          """)).isEqualTo(1);
      assertPrivilegeDenied(() -> statement.executeUpdate(
          "insert into weave_files_volume_authorities (authority_key) values ('other')"));
      assertPrivilegeDenied(() -> statement.executeUpdate(
          "delete from weave_schema_authority"));
      assertPrivilegeDenied(() -> statement.executeUpdate(
          "update flyway_schema_history set success = false"));
      assertPrivilegeDenied(() -> statement.execute("create table serving_owned(id bigint)"));
      assertPrivilegeDenied(() -> statement.execute("create temporary table serving_temp(id bigint)"));
    }

    try (var migratorConnection = DriverManager.getConnection(url, migrator, migratorPassword);
        var statement = migratorConnection.createStatement();
        var rows = statement.executeQuery("""
            select pg_get_userbyid(class_value.relowner)
              from pg_class class_value
              join pg_namespace namespace_value
                on namespace_value.oid = class_value.relnamespace
             where namespace_value.nspname = 'public'
               and class_value.relname = 'weave_files_volume_authorities'
            """)) {
      assertThat(rows.next()).isTrue();
      assertThat(rows.getString(1)).isEqualTo(migrator);
    }
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
    assertThat(previous.getVersion()).isEqualTo("6");
    assertThat(latest.getVersion()).isEqualTo("7");
    insertOtherDomainState(environment);

    SchemaAuthorityInitializer.run(environment.values());
    var upgraded = receipt(environment);
    assertThat(upgraded.path("migrationsExecuted").asInt()).isPositive();
    assertThat(upgraded.path("targetSchemaVersion").asText()).isEqualTo(latest.getVersion());
    assertThat(flyway(environment, null).info().current().getVersion()).isEqualTo(latest);
    SchemaReceiptVerifier.verify(environment.values());
    assertV7CatalogAndOtherDomainState(environment);

    SchemaAuthorityInitializer.run(environment.values());
    assertThat(receipt(environment).path("migrationsExecuted").asInt()).isZero();
  }

  @Test
  void v7RejectsPopulatedFilesStateBeforeAnySchemaChange(@TempDir Path directory)
      throws Exception {
    for (PreV7FilesState state : PreV7FilesState.values()) {
      Environment environment = environment("v7_guard_" + state.name().toLowerCase(), directory,
          "schema-init-" + state.name().toLowerCase() + ".json");
      Flyway v6 = flyway(environment, MigrationVersion.fromVersion("6"));
      v6.migrate();
      insertOtherDomainState(environment);
      insertPreV7FilesState(environment, state);

      assertThatThrownBy(() -> SchemaAuthorityInitializer.run(environment.values()))
          .isInstanceOf(RuntimeException.class)
          .hasStackTraceContaining("native Files V7 requires empty pre-V7 Files state")
          .hasMessageNotContaining(POSTGRES.getPassword());

      assertThat(flyway(environment, null).info().current().getVersion().getVersion())
          .isEqualTo("6");
      try (Connection connection = connection(environment)) {
        assertThat(tableExists(connection, "weave_files_stream_heads")).isFalse();
        assertThat(tableExists(connection, "weave_files_mutation_plans")).isFalse();
        assertThat(tableExists(connection, "weave_files_mutation_targets")).isFalse();
        assertThat(tableExists(connection, "weave_files_changes")).isFalse();
        assertThat(queryCount(connection,
            "select count(*) from weave_transfer_runs where run_id = 'transfer:v7-preserved'"))
            .isEqualTo(1);
        assertThat(queryCount(connection, """
            select count(*)
              from pg_constraint
             where conname = 'uq_weave_operation_intents_scope_idempotency'
               and connamespace = (
                   select oid from pg_namespace where nspname = current_schema())
            """))
            .isZero();
      }
    }
  }

  @Test
  void v7EnforcesSealedImmutableAndDeferredCompletePlans(@TempDir Path directory)
      throws Exception {
    Environment environment = environment("v7_plan_guards", directory);
    SchemaAuthorityInitializer.run(environment.values());

    try (Connection connection = connection(environment)) {
      insertFilesIntent(connection, "operation:v7-valid", "outbox:v7-valid");
      connection.setAutoCommit(false);
      insertPlan(connection, "operation:v7-valid", 1);
      insertCollectionTarget(connection, "operation:v7-valid", 0);
      sealPlan(connection, "operation:v7-valid");
      connection.commit();
      connection.setAutoCommit(true);

      assertThat(queryCount(connection, """
          select count(*)
            from weave_files_mutation_plans
           where operation_ref = 'operation:v7-valid'
             and plan_state = 'SEALED'
          """))
          .isEqualTo(1);
      assertThatThrownBy(() -> execute(connection, """
          update weave_files_mutation_targets
             set target_file_ref = 'file:altered'
           where operation_ref = 'operation:v7-valid'
          """))
          .hasStackTraceContaining("Files mutation targets are insert-only");
      assertThatThrownBy(() -> insertCollectionTarget(connection, "operation:v7-valid", 1))
          .hasStackTraceContaining("Files mutation targets require one OPEN plan");
      assertThatThrownBy(() -> execute(connection, """
          delete from weave_files_mutation_plans
           where operation_ref = 'operation:v7-valid'
          """))
          .hasStackTraceContaining("Files mutation plans are immutable");
      assertThatThrownBy(() -> execute(connection, """
          update weave_files_mutation_plans
             set targets_digest = 'sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc'
           where operation_ref = 'operation:v7-valid'
          """))
          .hasStackTraceContaining("Files mutation plan sealing may only change OPEN to SEALED");
    }

    assertRejectedPlanCommit(environment, "operation:v7-unsealed", 1, List.of(0), false);
    assertRejectedPlanCommit(environment, "operation:v7-incomplete", 2, List.of(0), true);
    assertRejectedPlanCommit(environment, "operation:v7-noncontiguous", 2, List.of(0, 2), true);

    try (Connection connection = connection(environment)) {
      insertFilesIntent(connection, "operation:v7-altered-seal", "outbox:v7-altered-seal");
      connection.setAutoCommit(false);
      insertPlan(connection, "operation:v7-altered-seal", 1);
      insertCollectionTarget(connection, "operation:v7-altered-seal", 0);
      assertThatThrownBy(() -> execute(connection, """
          update weave_files_mutation_plans
             set plan_state = 'SEALED',
                 sealed_at_utc = '2026-08-20T08:00:00Z',
                 target_count = 2
           where operation_ref = 'operation:v7-altered-seal'
          """))
          .hasStackTraceContaining("Files mutation plan sealing may only change OPEN to SEALED");
      connection.rollback();
    }
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

  private void assertV7CatalogAndOtherDomainState(Environment environment) throws Exception {
    try (Connection connection = connection(environment)) {
      assertThat(tableNames(connection, "weave_files_%"))
          .contains(
              "weave_files_changes",
              "weave_files_mutation_plans",
              "weave_files_mutation_targets",
              "weave_files_objects",
              "weave_files_stream_heads");
      assertThat(constraintNames(connection))
          .contains(
              "uq_weave_operation_intents_scope_idempotency",
              "uq_weave_operation_intents_initial_outbox_ref",
              "uq_weave_operation_intents_plan_link",
              "uq_weave_operation_outbox_outbox_ref",
              "fk_weave_operation_outbox_intent",
              "pk_weave_files_stream_heads",
              "ck_weave_files_stream_heads_revisions",
              "ck_weave_files_stream_heads_lock_version",
              "pk_weave_files_mutation_plans",
              "uq_weave_files_mutation_plans_scope_link",
              "fk_weave_files_mutation_plans_intent",
              "ck_weave_files_mutation_plans_version",
              "ck_weave_files_mutation_plans_operation_kind",
              "ck_weave_files_mutation_plans_binding_revision",
              "ck_weave_files_mutation_plans_state",
              "ck_weave_files_mutation_plans_target_count",
              "ck_weave_files_mutation_plans_arguments_digest",
              "ck_weave_files_mutation_plans_targets_digest",
              "ck_weave_files_mutation_plans_sealed_at",
              "pk_weave_files_mutation_targets",
              "fk_weave_files_mutation_targets_plan",
              "ck_weave_files_mutation_targets_version",
              "ck_weave_files_mutation_targets_ordinal",
              "ck_weave_files_mutation_targets_change_kind",
              "ck_weave_files_mutation_targets_object_kind",
              "ck_weave_files_mutation_targets_lifecycle",
              "ck_weave_files_mutation_targets_source_lifecycle",
              "ck_weave_files_mutation_targets_source_size",
              "ck_weave_files_mutation_targets_result_size",
              "ck_weave_files_mutation_targets_source_digest",
              "ck_weave_files_mutation_targets_result_digest",
              "ck_weave_files_mutation_targets_source_content",
              "ck_weave_files_mutation_targets_result_content",
              "ck_weave_files_mutation_targets_paths",
              "ck_weave_files_mutation_targets_result_lifecycle",
              "ck_weave_files_mutation_targets_source_path_shape",
              "ck_weave_files_mutation_targets_target_path_shape",
              "pk_weave_files_changes",
              "uq_weave_files_changes_operation_file",
              "fk_weave_files_changes_head",
              "fk_weave_files_changes_plan",
              "ck_weave_files_changes_revision",
              "ck_weave_files_changes_range",
              "ck_weave_files_changes_change_kind",
              "ck_weave_files_changes_object_kind",
              "ck_weave_files_changes_lifecycle",
              "ck_weave_files_changes_binding_revision",
              "ck_weave_files_changes_result_size",
              "ck_weave_files_changes_result_digest",
              "ck_weave_files_changes_result_snapshot",
              "ck_weave_files_changes_paths",
              "ck_weave_files_changes_result_lifecycle",
              "ck_weave_files_changes_source_path_shape",
              "ck_weave_files_changes_target_path_shape");
      assertThat(indexNames(connection))
          .contains(
              "idx_weave_files_mutation_targets_source_binding",
              "idx_weave_files_mutation_targets_result_binding",
              "idx_weave_files_changes_operation");
      assertThat(triggerNames(connection))
          .contains(
              "trg_weave_files_v7_plan_immutability",
              "trg_weave_files_v7_plan_intent",
              "trg_weave_files_v7_target_immutability",
              "trg_weave_files_v7_plan_complete",
              "trg_weave_files_v7_target_complete",
              "trg_weave_files_v7_outbox_link");
      assertThat(queryCount(connection,
          "select count(*) from weave_transfer_runs where run_id = 'transfer:v7-preserved'"))
          .isEqualTo(1);
      assertThat(queryCount(connection, "select count(*) from weave_files_objects"))
          .isZero();
      assertThat(queryCount(connection, "select count(*) from weave_file_locks"))
          .isZero();
    }
  }

  private void insertOtherDomainState(Environment environment) throws Exception {
    try (Connection connection = connection(environment)) {
      execute(connection, """
          insert into weave_transfer_runs (
              run_id,
              organization_ref,
              canonical_model_version,
              transfer_format_version,
              state_revision,
              run_status,
              batches_applied,
              items_applied,
              last_aggregate_digest,
              updated_at_utc,
              persistence_version)
          values (
              'transfer:v7-preserved',
              'org:v7-preserved',
              'weave.canonical/v1',
              1,
              1,
              'ACTIVE',
              0,
              0,
              'sha256:v7-preserved',
              '2026-08-20T08:00:00Z',
              0)
          """);
    }
  }

  private void insertPreV7FilesState(
      Environment environment,
      PreV7FilesState state) throws Exception {
    try (Connection connection = connection(environment)) {
      switch (state) {
        case OBJECT -> execute(connection, """
            insert into weave_files_objects (
                file_id,
                organization_ref,
                space_ref,
                active_path_key,
                byte_size,
                canonical_path,
                hidden,
                object_kind,
                lifecycle_state,
                observed_at_utc,
                provider_binding_revision,
                version)
            values (
                'file:v7-object',
                'org:v7-guard',
                'space:v7-guard',
                '/guard',
                0,
                '/guard',
                false,
                'COLLECTION',
                'ACTIVE',
                '2026-08-20T08:00:00Z',
                1,
                0)
            """);
        case PRIVATE_BLOB_BINDING -> execute(connection, """
            insert into weave_files_objects (
                file_id,
                organization_ref,
                space_ref,
                active_path_key,
                byte_size,
                canonical_path,
                content_digest,
                hidden,
                object_kind,
                lifecycle_state,
                media_type,
                modified_at_utc,
                observed_at_utc,
                provider_binding_revision,
                storage_reference,
                version,
                version_token)
            values (
                'file:v7-binding',
                'org:v7-guard',
                'space:v7-guard',
                '/guard.txt',
                1,
                '/guard.txt',
                'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                false,
                'FILE',
                'ACTIVE',
                'text/plain',
                '2026-08-20T08:00:00Z',
                '2026-08-20T08:00:00Z',
                1,
                'v1/v7/guard',
                0,
                'version:v7')
            """);
        case LOCK -> execute(connection, """
            insert into weave_file_locks (
                canonical_path,
                organization_ref,
                space_ref,
                created_at_utc,
                expires_at_utc,
                fence,
                owner_ref,
                token_digest,
                version)
            values (
                '/guard.txt',
                'org:v7-guard',
                'space:v7-guard',
                '2026-08-20T08:00:00Z',
                '2026-08-20T09:00:00Z',
                1,
                'person:v7-guard',
                'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                0)
            """);
        case INTENT -> insertFilesIntent(
            connection,
            "operation:v7-guard-intent",
            "outbox:v7-guard-intent");
        case INTENT_AND_OUTBOX -> {
          insertFilesIntent(
              connection,
              "operation:v7-guard-outbox",
              "outbox:v7-guard-outbox");
          try (var statement = connection.prepareStatement("""
              insert into weave_operation_outbox (
                  attempt_count,
                  created_at_utc,
                  delivery_state,
                  event_type,
                  operation_ref,
                  outbox_ref,
                  payload_json,
                  version)
              values (0, '2026-08-20T08:00:00Z', 'PENDING', 'operation.created', ?, ?, '{}', 0)
              """)) {
            statement.setString(1, "operation:v7-guard-outbox");
            statement.setString(2, "outbox:v7-guard-outbox");
            statement.executeUpdate();
          }
        }
      }
    }
  }

  private void assertRejectedPlanCommit(
      Environment environment,
      String operationRef,
      int targetCount,
      List<Integer> ordinals,
      boolean seal) throws Exception {
    try (Connection connection = connection(environment)) {
      insertFilesIntent(connection, operationRef, "outbox:" + operationRef.substring("operation:".length()));
      connection.setAutoCommit(false);
      insertPlan(connection, operationRef, targetCount);
      for (int ordinal : ordinals) {
        insertCollectionTarget(connection, operationRef, ordinal);
      }
      if (seal) {
        sealPlan(connection, operationRef);
      }
      assertThatThrownBy(connection::commit)
          .hasStackTraceContaining("Files mutation plan is unsealed, incomplete, or noncontiguous");
      connection.rollback();
      connection.setAutoCommit(true);
      assertThat(queryCount(connection,
          "select count(*) from weave_files_mutation_plans where operation_ref = '"
              + operationRef + "'"))
          .isZero();
    }
  }

  private void insertFilesIntent(
      Connection connection,
      String operationRef,
      String outboxRef) throws Exception {
    try (var statement = connection.prepareStatement("""
        insert into weave_operation_intents (
            operation_ref,
            action_digest,
            actor_kind,
            canonical_arguments_digest,
            created_at_utc,
            domain_key,
            entitlement_revision,
            idempotency_key,
            initial_outbox_ref,
            intent_version,
            object_refs_json,
            organization_ref,
            person_ref,
            policy_revision,
            projection_kind,
            projection_value_1,
            projection_value_2,
            projection_value_3,
            provider_binding_revision,
            reconciliation_attempts,
            reconciliation_max_attempts,
            intent_state,
            subject_ref,
            updated_at_utc,
            version)
        values (
            ?, ?, 'human', ?, '2026-08-20T08:00:00Z', 'files', 'entitlement:v7', ?, ?,
            'weave.operation-intent/v2', '["file:v7-target"]', 'org:v7', 'person:v7',
            'policy:v7', 'protocol', 'webdav', 'webdav-put', 'weave.webdav.files/v1', 1,
            0, 5, 'CREATED', 'subject:v7', '2026-08-20T08:00:00Z', 0)
        """)) {
      statement.setString(1, operationRef);
      statement.setString(2, DIGEST_A);
      statement.setString(3, DIGEST_B);
      statement.setString(4, "idempotency:" + operationRef);
      statement.setString(5, outboxRef);
      statement.executeUpdate();
    }
  }

  private void insertPlan(
      Connection connection,
      String operationRef,
      int targetCount) throws Exception {
    try (var statement = connection.prepareStatement("""
        insert into weave_files_mutation_plans (
            operation_ref,
            organization_ref,
            space_ref,
            plan_version,
            canonical_arguments_digest,
            operation_kind,
            provider_binding_revision,
            if_match_condition,
            if_none_match_condition,
            destination_must_remain_absent,
            plan_state,
            target_count,
            targets_digest,
            fence_count,
            fences_digest)
        values (?, 'org:v7', 'space:v7', 'weave.files-mutation-plan/v1', ?, 'PUT', 1,
            'NOT_SUPPLIED', 'NOT_SUPPLIED', false, 'OPEN', ?, ?, 1, ?)
        """)) {
      statement.setString(1, operationRef);
      statement.setString(2, DIGEST_B);
      statement.setInt(3, targetCount);
      statement.setString(4, DIGEST_A);
      statement.setString(5, DIGEST_A);
      statement.executeUpdate();
    }
    var fence = FilesMutationPlan.Fence.absent(
        0, FilesMutationPlan.FenceRole.REQUEST_TARGET, "/target-0");
    try (var statement = connection.prepareStatement("""
        insert into weave_files_mutation_fences (
            operation_ref,
            fence_ordinal,
            fence_version,
            fence_role,
            canonical_path,
            expected_presence,
            snapshot_digest)
        values (?, 0, 'weave.files-mutation-fence/v1', 'REQUEST_TARGET',
            '/target-0', 'ABSENT', ?)
        """)) {
      statement.setString(1, operationRef);
      statement.setString(2, fence.snapshotDigest());
      statement.executeUpdate();
    }
  }

  private void insertCollectionTarget(
      Connection connection,
      String operationRef,
      int ordinal) throws Exception {
    try (var statement = connection.prepareStatement("""
        insert into weave_files_mutation_targets (
            operation_ref,
            target_ordinal,
            target_version,
            change_kind,
            target_file_ref,
            target_path,
            object_kind,
            result_lifecycle_state,
            result_size,
            result_modified_at_utc,
            result_hidden,
            result_observed_at_utc)
        values (?, ?, 'weave.files-mutation-target/v1', 'CREATED', ?, ?, 'COLLECTION',
            'ACTIVE', 0, '2026-08-20T08:00:00Z', false, '2026-08-20T08:00:00Z')
        """)) {
      statement.setString(1, operationRef);
      statement.setInt(2, ordinal);
      statement.setString(3, "file:v7-target:" + ordinal);
      statement.setString(4, "/target-" + ordinal);
      statement.executeUpdate();
    }
  }

  private void sealPlan(Connection connection, String operationRef) throws Exception {
    try (var statement = connection.prepareStatement("""
        update weave_files_mutation_plans
           set plan_state = 'SEALED',
               sealed_at_utc = '2026-08-20T08:00:00Z'
         where operation_ref = ?
        """)) {
      statement.setString(1, operationRef);
      assertThat(statement.executeUpdate()).isEqualTo(1);
    }
  }

  private List<String> tableNames(Connection connection, String pattern) throws Exception {
    try (var statement = connection.prepareStatement("""
        select table_name
          from information_schema.tables
         where table_schema = current_schema()
           and table_name like ?
         order by table_name
        """)) {
      statement.setString(1, pattern);
      try (ResultSet rows = statement.executeQuery()) {
        return strings(rows);
      }
    }
  }

  private List<String> constraintNames(Connection connection) throws Exception {
    try (var statement = connection.prepareStatement("""
        select conname
          from pg_constraint
         where connamespace = (select oid from pg_namespace where nspname = current_schema())
         order by conname
        """);
        ResultSet rows = statement.executeQuery()) {
      return strings(rows);
    }
  }

  private List<String> triggerNames(Connection connection) throws Exception {
    try (var statement = connection.prepareStatement("""
        select trigger_name
          from information_schema.triggers
         where trigger_schema = current_schema()
         order by trigger_name
        """);
        ResultSet rows = statement.executeQuery()) {
      return strings(rows);
    }
  }

  private List<String> indexNames(Connection connection) throws Exception {
    try (var statement = connection.prepareStatement("""
        select indexname
          from pg_indexes
         where schemaname = current_schema()
         order by indexname
        """);
        ResultSet rows = statement.executeQuery()) {
      return strings(rows);
    }
  }

  private List<String> strings(ResultSet rows) throws Exception {
    List<String> values = new ArrayList<>();
    while (rows.next()) {
      values.add(rows.getString(1));
    }
    return List.copyOf(values);
  }

  private boolean tableExists(Connection connection, String table) throws Exception {
    try (var statement = connection.prepareStatement("select to_regclass(?)")) {
      statement.setString(1, table);
      try (var rows = statement.executeQuery()) {
        rows.next();
        return rows.getString(1) != null;
      }
    }
  }

  private int queryCount(Connection connection, String sql) throws Exception {
    try (var statement = connection.prepareStatement(sql);
        var rows = statement.executeQuery()) {
      rows.next();
      return rows.getInt(1);
    }
  }

  private void execute(Connection connection, String sql) throws Exception {
    try (var statement = connection.prepareStatement(sql)) {
      statement.executeUpdate();
    }
  }

  private static void assertPrivilegeDenied(ThrowingCallable action) {
    assertThatThrownBy(action)
        .isInstanceOf(SQLException.class)
        .satisfies(failure ->
            assertThat(((SQLException) failure).getSQLState()).isEqualTo("42501"));
  }

  private static void writeTransitionContext(Path directory, String candidate) throws Exception {
    Map<String, Object> transitionContext = new LinkedHashMap<>();
    transitionContext.put(
        "schemaVersion", NativeFilesVolumeAuthority.TRANSITION_CONTEXT_FORMAT);
    transitionContext.put("transitionKind", "INITIAL_PROVISION");
    transitionContext.put("composeProject", "weave-test");
    transitionContext.put("runScope", "test-run");
    transitionContext.put("candidateCommit", candidate);
    Files.writeString(
        directory.resolve(NativeFilesVolumeAuthority.TRANSITION_CONTEXT_FILE_NAME),
        new ObjectMapper().writeValueAsString(transitionContext));
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
    Path blobRoot = directory.resolve(semanticName + "-blobs");
    Files.createDirectories(blobRoot);
    writeTransitionContext(directory, CANDIDATE);
    Map<String, String> values = new LinkedHashMap<>();
    values.put("WEAVE_PERSISTENCE_URL", url);
    values.put("WEAVE_PERSISTENCE_USERNAME", POSTGRES.getUsername());
    values.put("WEAVE_PERSISTENCE_PASSWORD", POSTGRES.getPassword());
    values.put("WEAVE_SERVING_DB_USERNAME", SchemaAuthorityTestSupport.SERVING_USERNAME);
    values.put("WEAVE_CANDIDATE_COMMIT", CANDIDATE);
    values.put("WEAVE_SCHEMA_INIT_RECEIPT_FILE", receipt.toString());
    values.put("WEAVE_NATIVE_FILES_BLOB_ROOT", blobRoot.toString());
    return new Environment(url, receipt, blobRoot, values);
  }

  private record Environment(String url, Path receipt, Path blobRoot, Map<String, String> values) {
    private Environment {
      values = Map.copyOf(values);
    }

    private Environment withReceipt(Path nextReceipt) {
      Map<String, String> nextValues = new LinkedHashMap<>(values);
      nextValues.put("WEAVE_SCHEMA_INIT_RECEIPT_FILE", nextReceipt.toString());
      return new Environment(url, nextReceipt, blobRoot, nextValues);
    }
  }

  private enum PreV7FilesState {
    OBJECT,
    PRIVATE_BLOB_BINDING,
    LOCK,
    INTENT,
    INTENT_AND_OUTBOX
  }
}

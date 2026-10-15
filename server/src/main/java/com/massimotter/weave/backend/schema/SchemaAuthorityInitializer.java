package com.massimotter.weave.backend.schema;

import com.massimotter.weave.backend.persistence.jpa.schema.SchemaAuthorityJpaRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** Fail-closed one-shot PostgreSQL schema migration and validation entrypoint. */
public final class SchemaAuthorityInitializer {

  public static final String EPOCH = "weave-flyway-v1";
  public static final String MODEL_ID = "WEAVE-ARCH-RELATIONAL-CORE-MODEL";
  public static final String RECEIPT_FORMAT = "weave.schema-init-receipt/v5";
  private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
  private static final String MIGRATION_LOCATION = "classpath:db/migration";
  private static final int INITIALIZATION_LOCK_TIMEOUT_SECONDS = 60;

  private SchemaAuthorityInitializer() {}

  public static void main(String[] args) {
    try {
      run(System.getenv());
    } catch (Exception failure) {
      System.err.println("schema-init: blocked: " + supportSafeMessage(failure));
      System.exit(2);
    }
  }

  static void run(Map<String, String> environment) throws Exception {
    run(environment, () -> {});
  }

  // Package-private deterministic publication probe; production supplies no callback.
  static void run(Map<String, String> environment, Runnable beforePublication) throws Exception {
    String url = requiredAny(environment, "WEAVE_PERSISTENCE_URL", "SPRING_DATASOURCE_URL");
    if (!url.startsWith("jdbc:postgresql://")) {
      throw new IllegalStateException("schema-init requires PostgreSQL");
    }
    String username =
        requiredAny(environment, "WEAVE_PERSISTENCE_USERNAME", "SPRING_DATASOURCE_USERNAME");
    String password = persistencePassword(environment);
    String candidate = required(environment, "WEAVE_CANDIDATE_COMMIT");
    if (!COMMIT.matcher(candidate).matches()) {
      throw new IllegalStateException("candidate commit must be one lowercase immutable SHA-1");
    }
    Path receipt = Path.of(required(environment, "WEAVE_SCHEMA_INIT_RECEIPT_FILE"))
        .toAbsolutePath()
        .normalize();

    // Flyway serializes migration DDL, but the authority marker, Hibernate validation,
    // fingerprint and receipt happen afterwards. Hold one schema-scoped session lock over
    // the complete one-shot operation so two initializers cannot race outside Flyway.
    // File output exclusion precedes the database lock, including cleanup. A
    // replacement can never hold the database lock while waiting for this output.
    try (var output = ReceiptOutputLock.acquire(receipt);
         Connection lockConnection = DriverManager.getConnection(url, username, password)) {
      lockConnection.setClientInfo("ApplicationName", "weave-schema-authority-init");
      acquireSchemaInitializationLock(lockConnection);
      runLocked(lockConnection, url, username, password, candidate, receipt, required(environment, "WEAVE_SCHEMA_RUNTIME_ROLE"), beforePublication);
    }
  }

  private static void runLocked(
      Connection lockConnection,
      String url,
      String username,
      String password,
      String candidate,
      Path receipt,
      String runtimeRole,
      Runnable beforePublication) throws Exception {
    if (!runtimeRole.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")
        || runtimeRole.equals(username)) {
      throw new IllegalStateException("migration and runtime roles must be distinct bounded identifiers");
    }
    Flyway flyway = Flyway.configure()
        .dataSource(url, username, password)
        .locations(MIGRATION_LOCATION)
        .baselineOnMigrate(false)
        .cleanDisabled(true)
        .outOfOrder(false)
        .validateOnMigrate(true)
        .validateMigrationNaming(true)
        .ignoreMigrationPatterns(new String[0])
        .load();

    // Flyway owns DDL and its PostgreSQL history/checksums. A non-empty schema without
    // Flyway history intentionally fails here instead of being silently baselined.
    // Pending reviewed migrations are legitimate before an upgrade; missing or changed
    // applied history is still rejected. The post-migration validation ignores nothing.
    Flyway.configure().dataSource(url, username, password).locations(MIGRATION_LOCATION)
        .baselineOnMigrate(false).cleanDisabled(true).outOfOrder(false)
        .validateMigrationNaming(true).ignoreMigrationPatterns("*:pending").load().validate();
    var previous = flyway.info().current();
    SchemaCatalogFingerprint.Snapshot previousCatalog;
    SchemaCatalogFingerprint.Snapshot previousLegacyV2;
    try (Connection connection = DriverManager.getConnection(url, username, password)) {
      previousCatalog = SchemaCatalogFingerprint.inspect(connection);
      previousLegacyV2 = SchemaCatalogFingerprint.inspectLegacyV2(connection);
    }
    try (MigrationCatalogReference reference = new MigrationCatalogReference(url, username, password)) {
      if (previous != null) {
        var expectedPrevious = reference.atVersion(previous.getVersion());
        requireCatalog(previousCatalog, expectedPrevious);
        try (var connection = DriverManager.getConnection(url, username, password)) {
          requirePreviousMarker(connection, previousCatalog.sha256(), previousLegacyV2.sha256());
        }
      }
      var migration = flyway.migrate();
      flyway.validate();
      String targetSchemaVersion = migration.targetSchemaVersion;
      if (targetSchemaVersion == null) {
        var currentMigration = flyway.info().current();
        targetSchemaVersion =
            currentMigration == null || currentMigration.getVersion() == null
                ? null
                : currentMigration.getVersion().getVersion();
      }

      Map<String, Object> properties = new LinkedHashMap<>();
      properties.put("spring.config.name", "schema-init");
      properties.put("spring.main.web-application-type", "none");
      properties.put("spring.datasource.url", url);
      properties.put("spring.datasource.username", username);
      properties.put("spring.datasource.password", password);
      properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
      properties.put("spring.jpa.open-in-view", "false");
      properties.put("spring.jpa.hibernate.ddl-auto", "validate");
      properties.put("spring.flyway.enabled", "false");

      try (ConfigurableApplicationContext context = application(properties).run();
           Connection connection = DriverManager.getConnection(url, username, password)) {
        SchemaCatalogFingerprint.Snapshot validated = SchemaCatalogFingerprint.inspect(connection);
        requireCatalog(validated, reference.atVersion(null));
        if (!validated.tables().contains("weave_schema_authority")) {
          throw new IllegalStateException("authority marker table is absent after Flyway migration");
        }
        SchemaAuthorityJpaRepository markers = context.getBean(SchemaAuthorityJpaRepository.class);
        var existingMarkers = markers.findAll();
        if (!existingMarkers.isEmpty()) {
          if (existingMarkers.size() != 1
              || !EPOCH.equals(existingMarkers.getFirst().epoch())
              || !MODEL_ID.equals(existingMarkers.getFirst().relationalModelId())
              || !COMMIT.matcher(existingMarkers.getFirst().candidateCommit()).matches()
              || existingMarkers.getFirst().completedAt() == null
              // A v2 marker may be upgraded only after full v3 physical comparison with
              // the clean prior migration chain. This never accepts arbitrary stale values.
              || (!previousCatalog.sha256().equals(existingMarkers.getFirst().catalogFingerprint())
                  && !previousLegacyV2.sha256().equals(existingMarkers.getFirst().catalogFingerprint()))) {
            throw new IllegalStateException(
                "schema catalog fingerprint does not match the completed authority marker");
          }
        }
        protectAuthorityTables(connection, runtimeRole);
        // The acceptance lock's own session commits the marker. Terminating it
        // cannot leave a different connection publishing authority without a lock.
        lockConnection.setAutoCommit(false);
        Instant completed;
        try (var delete = lockConnection.createStatement();
             var insert = lockConnection.prepareStatement(
                 "insert into weave_schema_authority "
                     + "(epoch,relational_model_id,candidate_commit,catalog_fingerprint,completed_at_utc) "
                     + "values (?,?,?,?,clock_timestamp()) returning completed_at_utc")) {
          delete.executeUpdate("delete from weave_schema_authority");
          insert.setString(1, EPOCH);
          insert.setString(2, MODEL_ID);
          insert.setString(3, candidate);
          insert.setString(4, validated.sha256());
          try (var rows = insert.executeQuery()) {
            if (!rows.next()) throw new IllegalStateException("schema authority completion was not written");
            completed = rows.getTimestamp(1).toInstant();
          }
          writeReceipt(lockConnection, url, username, password, receipt, candidate, migration.migrationsExecuted,
              targetSchemaVersion, validated, completed, beforePublication);
        } catch (Exception failure) {
          try { lockConnection.rollback(); } catch (Exception rollback) { failure.addSuppressed(rollback); }
          throw failure;
        }
      }
    }
  }

  private static void requirePreviousMarker(Connection connection, String current, String legacy) throws Exception {
    try (var statement = connection.createStatement(); var rows = statement.executeQuery(
        "select epoch,relational_model_id,candidate_commit,catalog_fingerprint,completed_at_utc "
            + "from weave_schema_authority")) {
      if (!rows.next()) return; // Trusted immutable Flyway history is independently checked.
      if (!EPOCH.equals(rows.getString(1)) || !MODEL_ID.equals(rows.getString(2))
          || rows.getString(3) == null || !COMMIT.matcher(rows.getString(3)).matches()
          || (!current.equals(rows.getString(4)) && !legacy.equals(rows.getString(4)))
          || rows.getTimestamp(5) == null || rows.next()) {
        throw new IllegalStateException("schema catalog fingerprint does not match the completed authority marker");
      }
    }
  }

  private static void protectAuthorityTables(Connection connection, String runtimeRole) throws Exception {
    try (var statement = connection.createStatement()) {
      String quotedRole = "\"" + runtimeRole + "\"";
      statement.execute("revoke all privileges on flyway_schema_history,weave_schema_authority from public");
      statement.execute("revoke all privileges on flyway_schema_history,weave_schema_authority from " + quotedRole);
      statement.execute("grant select on flyway_schema_history,weave_schema_authority to " + quotedRole);
    }
  }

  private static void requireCatalog(SchemaCatalogFingerprint.Snapshot observed,
      SchemaCatalogFingerprint.Snapshot expected) {
    if (!observed.sha256().equals(expected.sha256())) {
      throw new IllegalStateException("schema catalog fingerprint differs from reviewed migrations");
    }
  }

  private static void acquireSchemaInitializationLock(Connection connection) throws Exception {
    try (var statement = connection.createStatement()) {
      statement.setQueryTimeout(INITIALIZATION_LOCK_TIMEOUT_SECONDS);
      try (var rows = statement.executeQuery(
          "select pg_advisory_lock(" +
              "hashtext(current_database()), " +
              "hashtext(coalesce(current_schema(), 'public')))")) {
        if (!rows.next()) {
          throw new IllegalStateException("schema initialization lock was not acquired");
        }
      }
    }
    // PostgreSQL releases this session advisory lock when lockConnection closes.
  }

  private static SpringApplication application(Map<String, Object> properties) {
    SpringApplication application = new SpringApplication(SchemaInitConfiguration.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setAdditionalProfiles("schema-init");
    application.setDefaultProperties(properties);
    return application;
  }

  private static void writeReceipt(
      Connection lockConnection,
      String url,
      String username,
      String password,
      Path receipt,
      String candidate,
      int migrationsExecuted,
      String targetSchemaVersion,
      SchemaCatalogFingerprint.Snapshot snapshot,
      Instant completed,
      Runnable beforePublication) throws Exception {
    Path parent = receipt.getParent();
    if (parent == null || !Files.isDirectory(parent)) {
      throw new IllegalStateException("schema receipt parent directory is unavailable");
    }
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("schemaVersion", RECEIPT_FORMAT);
    value.put("supportSafe", true);
    value.put("authority", "flyway");
    value.put("epoch", EPOCH);
    value.put("relationalModelId", MODEL_ID);
    value.put("candidateCommit", candidate);
    value.put("migrationsExecuted", migrationsExecuted);
    value.put("targetSchemaVersion", targetSchemaVersion);
    value.put("catalogFingerprintFormat", SchemaCatalogFingerprint.FORMAT);
    value.put("catalogFingerprint", snapshot.sha256());
    value.put("tableCount", snapshot.tables().size());
    value.put("tables", snapshot.tables());
    value.put(
        "catalogProjection",
        new ObjectMapper().readTree(snapshot.canonicalJson()));
    value.put("completedAtUtc", completed.toString());
    value.put("secretValuesIncluded", false);
    byte[] serialized = new ObjectMapper().writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
    Path temporary = Files.createTempFile(parent, ".schema-init-", ".json");
    boolean published = false;
    boolean completedPublication = false;
    try {
      Files.write(
          temporary,
          serialized,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
      requireHeldAcceptanceLock(lockConnection);
      lockConnection.commit();
      requireCommittedMarker(lockConnection, candidate, snapshot.sha256(), completed);
      beforePublication.run();
      requireHeldAcceptanceLock(lockConnection);
      Files.move(
          temporary,
          receipt,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      published = true;
      requireHeldAcceptanceLock(lockConnection);
      requireCommittedMarker(lockConnection, candidate, snapshot.sha256(), completed);
      completedPublication = true;
    } finally {
      if (published && !completedPublication) {
        cleanupOwnReceipt(lockConnection, url, username, password, receipt, serialized);
      }
      Files.deleteIfExists(temporary);
    }
  }

  private record ReceiptOutputLock(java.nio.channels.FileChannel channel,
      java.nio.channels.FileLock lock) implements AutoCloseable {
    static ReceiptOutputLock acquire(Path receipt) throws Exception {
      Path parent = receipt.getParent();
      if (parent == null || !Files.isDirectory(parent)) {
        throw new IllegalStateException("schema receipt parent directory is unavailable");
      }
      // Keep one stable inode: unlinking this non-authoritative coordination file
      // could let replacement writers lock different inodes for the same receipt.
      Path lockPath = receipt.resolveSibling("." + receipt.getFileName() + ".publication.lock");
      var channel = java.nio.channels.FileChannel.open(lockPath, StandardOpenOption.CREATE,
          StandardOpenOption.WRITE, java.nio.file.LinkOption.NOFOLLOW_LINKS);
      try {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(INITIALIZATION_LOCK_TIMEOUT_SECONDS);
        do {
          java.nio.channels.FileLock acquired = null;
          try { acquired = channel.tryLock(); }
          catch (java.nio.channels.OverlappingFileLockException waitingInThisJvm) { /* Bounded same-JVM waiter. */ }
          if (acquired != null) return new ReceiptOutputLock(channel, acquired);
          Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        throw new IllegalStateException("schema receipt output exclusion timed out");
      } catch (Exception failure) {
        channel.close();
        throw failure;
      }
    }

    @Override
    public void close() throws Exception {
      try (channel) { lock.release(); }
    }
  }

  static void cleanupOwnReceipt(Connection failedSession, String url, String username,
      String password, Path receipt, byte[] ownBytes) throws Exception {
    // A lost session no longer serializes filesystem publication. Reuse the
    // existing acceptance lock for bounded cleanup before comparing/unlinking.
    failedSession.close();
    try (var cleanup = DriverManager.getConnection(url, username, password)) {
      acquireSchemaInitializationLock(cleanup);
      if (Files.isRegularFile(receipt) && !Files.isSymbolicLink(receipt)
          && java.util.Arrays.equals(Files.readAllBytes(receipt), ownBytes)) {
        Files.delete(receipt);
      }
    }
  }

  private static void requireHeldAcceptanceLock(Connection connection) throws Exception {
    try (var statement = connection.createStatement(); var rows = statement.executeQuery(
        "select exists(select 1 from pg_locks where locktype='advisory' and granted "
            + "and pid=pg_backend_pid() and classid=(hashtext(current_database())::bigint & 4294967295)::oid "
            + "and objid=(hashtext(coalesce(current_schema(),'public'))::bigint & 4294967295)::oid and objsubid=2)")) {
      if (!rows.next() || !rows.getBoolean(1)) {
        throw new IllegalStateException("schema acceptance lock was lost before receipt publication");
      }
    }
  }

  private static void requireCommittedMarker(Connection connection, String candidate,
      String fingerprint, Instant completed) throws Exception {
    try (var query = connection.prepareStatement("select epoch,relational_model_id,candidate_commit,"
        + "catalog_fingerprint,completed_at_utc from weave_schema_authority")) {
      try (var rows = query.executeQuery()) {
        if (!rows.next() || !EPOCH.equals(rows.getString(1)) || !MODEL_ID.equals(rows.getString(2))
            || !candidate.equals(rows.getString(3)) || !fingerprint.equals(rows.getString(4))
            || !completed.equals(rows.getTimestamp(5).toInstant()) || rows.next()) {
          throw new IllegalStateException("schema receipt does not match its committed authority completion");
        }
      }
    }
  }

  private static String required(Map<String, String> environment, String name) {
    String value = environment.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " is required");
    }
    return value;
  }

  private static String requiredAny(
      Map<String, String> environment, String primary, String secondary) {
    String value = environment.get(primary);
    if (value == null || value.isBlank()) {
      value = environment.get(secondary);
    }
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(primary + " or " + secondary + " is required");
    }
    return value;
  }

  private static String persistencePassword(Map<String, String> environment) throws Exception {
    String file = environment.get("WEAVE_PERSISTENCE_PASSWORD_FILE");
    if (file != null && !file.isBlank()) {
      Path path = Path.of(file).toAbsolutePath().normalize();
      if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) {
        throw new IllegalStateException("persistence password SecretRef is not a regular file");
      }
      String value = Files.readString(path, StandardCharsets.UTF_8).strip();
      if (value.isEmpty()) {
        throw new IllegalStateException("persistence password SecretRef is empty");
      }
      return value;
    }
    return required(environment, "WEAVE_PERSISTENCE_PASSWORD");
  }

  private static String supportSafeMessage(Exception failure) {
    String message = failure.getMessage();
    if (message == null || message.isBlank()) {
      return failure.getClass().getSimpleName();
    }
    return message.replaceAll("(?i)(password|token|secret)=?[^\\s,;]*", "$1=<redacted>");
  }
}

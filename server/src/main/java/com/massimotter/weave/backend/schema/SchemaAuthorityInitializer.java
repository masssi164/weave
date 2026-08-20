package com.massimotter.weave.backend.schema;

import com.massimotter.weave.backend.files.adapter.FilesVolumeAuthorityJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.schema.SchemaAuthorityJpaEntity;
import com.massimotter.weave.backend.persistence.jpa.schema.SchemaAuthorityJpaRepository;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
  public static final String RECEIPT_FORMAT = "weave.schema-init-receipt/v6";
  private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
  private static final Pattern DATABASE_IDENTIFIER =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_-]{0,62}");
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
    String url = requiredAny(environment, "WEAVE_PERSISTENCE_URL", "SPRING_DATASOURCE_URL");
    if (!url.startsWith("jdbc:postgresql://")) {
      throw new IllegalStateException("schema-init requires PostgreSQL");
    }
    String username =
        requiredAny(environment, "WEAVE_PERSISTENCE_USERNAME", "SPRING_DATASOURCE_USERNAME");
    String servingUsername = required(environment, "WEAVE_SERVING_DB_USERNAME");
    if (!DATABASE_IDENTIFIER.matcher(username).matches()
        || !DATABASE_IDENTIFIER.matcher(servingUsername).matches()
        || username.equals(servingUsername)) {
      throw new IllegalStateException(
          "schema-init requires distinct valid migrator and serving database roles");
    }
    String password = persistencePassword(environment);
    String candidate = required(environment, "WEAVE_CANDIDATE_COMMIT");
    if (!COMMIT.matcher(candidate).matches()) {
      throw new IllegalStateException("candidate commit must be one lowercase immutable SHA-1");
    }
    Path receipt = Path.of(required(environment, "WEAVE_SCHEMA_INIT_RECEIPT_FILE"))
        .toAbsolutePath()
        .normalize();
    Path nativeFilesBlobRoot = Path.of(required(environment, "WEAVE_NATIVE_FILES_BLOB_ROOT"))
        .toAbsolutePath()
        .normalize();

    // Flyway serializes migration DDL, but the authority marker, Hibernate validation,
    // fingerprint and receipt happen afterwards. Hold one schema-scoped session lock over
    // the complete one-shot operation so two initializers cannot race outside Flyway.
    try (Connection lockConnection = DriverManager.getConnection(url, username, password)) {
      acquireSchemaInitializationLock(lockConnection);
      runLocked(
          url,
          username,
          password,
          servingUsername,
          candidate,
          receipt,
          nativeFilesBlobRoot);
    }
  }

  private static void runLocked(
      String url,
      String username,
      String password,
      String servingUsername,
      String candidate,
      Path receipt,
      Path nativeFilesBlobRoot) throws Exception {
    Flyway flyway = Flyway.configure()
        .dataSource(url, username, password)
        .locations(MIGRATION_LOCATION)
        .baselineOnMigrate(false)
        .cleanDisabled(true)
        .load();

    inspectNativeFilesV7Precondition(flyway, nativeFilesBlobRoot);

    // Flyway owns DDL and its PostgreSQL history/checksums. A non-empty schema without
    // Flyway history intentionally fails here instead of being silently baselined.
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
      reconcileServingPrivileges(connection, servingUsername);
      SchemaCatalogFingerprint.Snapshot validated = SchemaCatalogFingerprint.inspect(connection);
      if (!validated.tables().contains("weave_schema_authority")) {
        throw new IllegalStateException("authority marker table is absent after Flyway migration");
      }
      if (!validated.tables().contains("weave_files_volume_authorities")) {
        throw new IllegalStateException(
            "native Files volume-authority table is absent after Flyway migration");
      }
      Optional<NativeFilesVolumeAuthority.TransitionContext> transitionContext =
          NativeFilesVolumeAuthority.readTransitionContext(receipt.getParent(), candidate);
      FilesVolumeAuthorityJpaRepository volumeAuthorities =
          context.getBean(FilesVolumeAuthorityJpaRepository.class);
      NativeFilesVolumeAuthority.Authority nativeFilesVolumeAuthority =
          establishOrValidateNativeFilesVolumeAuthority(
              connection,
              volumeAuthorities,
              transitionContext,
              nativeFilesBlobRoot,
              validated.sha256());
      SchemaAuthorityJpaRepository markers = context.getBean(SchemaAuthorityJpaRepository.class);
      var existingMarkers = markers.findAll();
      if (!existingMarkers.isEmpty()) {
        if (existingMarkers.size() != 1
            || !EPOCH.equals(existingMarkers.getFirst().epoch())
            || !MODEL_ID.equals(existingMarkers.getFirst().relationalModelId())
            || !validated.sha256().equals(existingMarkers.getFirst().catalogFingerprint())) {
          throw new IllegalStateException(
              "schema catalog fingerprint does not match the completed authority marker");
        }
      }
      markers.deleteAllInBatch();
      markers.saveAndFlush(new SchemaAuthorityJpaEntity(
          EPOCH, MODEL_ID, candidate, validated.sha256(), Instant.now()));
      writeReceipt(
          receipt,
          candidate,
          migration.migrationsExecuted,
          targetSchemaVersion,
          nativeFilesVolumeAuthority,
          validated);
      SchemaReceiptVerifier.verify(
          Map.of(
              "WEAVE_CANDIDATE_COMMIT", candidate,
              "WEAVE_SCHEMA_INIT_RECEIPT_FILE", receipt.toString(),
              "WEAVE_NATIVE_FILES_BLOB_ROOT", nativeFilesBlobRoot.toString()));
      if (transitionContext.isPresent()) {
        NativeFilesVolumeAuthority.deleteTransitionContext(receipt.getParent());
      }
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

  private static void reconcileServingPrivileges(
      Connection connection, String servingUsername) throws Exception {
    String migratorUsername;
    String database;
    String schema;
    String databaseOwner;
    String schemaOwner;
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery(
            "select current_user, current_database(), current_schema(), "
                + "pg_get_userbyid(database_value.datdba), "
                + "pg_get_userbyid(schema_value.nspowner) "
                + "from pg_database database_value "
                + "join pg_namespace schema_value on schema_value.nspname = current_schema() "
                + "where database_value.datname = current_database()")) {
      if (!rows.next()) {
        throw new IllegalStateException("schema-init database ownership is unavailable");
      }
      migratorUsername = rows.getString(1);
      database = rows.getString(2);
      schema = rows.getString(3);
      databaseOwner = rows.getString(4);
      schemaOwner = rows.getString(5);
    }
    if (!DATABASE_IDENTIFIER.matcher(migratorUsername).matches()
        || !DATABASE_IDENTIFIER.matcher(database).matches()
        || !DATABASE_IDENTIFIER.matcher(schema).matches()
        || !migratorUsername.equals(databaseOwner)
        || !migratorUsername.equals(schemaOwner)
        || migratorUsername.equals(servingUsername)) {
      throw new IllegalStateException(
          "schema-init migrator must own the database and schema independently of serving");
    }
    try (var role = connection.prepareStatement(
        "select rolsuper, rolcreatedb, rolcreaterole, rolreplication, "
            + "pg_has_role(?, current_user, 'MEMBER') "
            + "from pg_roles where rolname = ?")) {
      role.setString(1, servingUsername);
      role.setString(2, servingUsername);
      try (var rows = role.executeQuery()) {
        if (!rows.next()
            || rows.getBoolean(1)
            || rows.getBoolean(2)
            || rows.getBoolean(3)
            || rows.getBoolean(4)
            || rows.getBoolean(5)) {
          throw new IllegalStateException(
              "schema-init serving database role is absent or over-privileged");
        }
      }
    }
    try (var ownership = connection.prepareStatement(
        "select count(*) from ("
            + "select class_value.oid from pg_class class_value "
            + "join pg_namespace namespace_value "
            + "on namespace_value.oid = class_value.relnamespace "
            + "where namespace_value.nspname = ? "
            + "and class_value.relkind in ('r', 'p', 'S', 'v', 'm', 'f', 'i', 'I', 'c') "
            + "and pg_get_userbyid(class_value.relowner) <> current_user "
            + "union all "
            + "select procedure_value.oid from pg_proc procedure_value "
            + "join pg_namespace namespace_value "
            + "on namespace_value.oid = procedure_value.pronamespace "
            + "where namespace_value.nspname = ? "
            + "and pg_get_userbyid(procedure_value.proowner) <> current_user"
            + " union all "
            + "select type_value.oid from pg_type type_value "
            + "join pg_namespace namespace_value "
            + "on namespace_value.oid = type_value.typnamespace "
            + "where namespace_value.nspname = ? "
            + "and pg_get_userbyid(type_value.typowner) <> current_user"
            + ") foreign_owned")) {
      ownership.setString(1, schema);
      ownership.setString(2, schema);
      ownership.setString(3, schema);
      try (var rows = ownership.executeQuery()) {
        if (!rows.next() || rows.getLong(1) != 0) {
          throw new IllegalStateException(
              "schema-init migrator does not own every migration object");
        }
      }
    }

    String quotedMigrator = quotedIdentifier(migratorUsername);
    String quotedServing = quotedIdentifier(servingUsername);
    String quotedDatabase = quotedIdentifier(database);
    String quotedSchema = quotedIdentifier(schema);
    String authorityTables =
        quotedSchema + ".weave_schema_authority, "
            + quotedSchema + ".weave_files_volume_authorities, "
            + quotedSchema + ".flyway_schema_history";
    try (var statement = connection.createStatement()) {
      statement.execute("REVOKE ALL PRIVILEGES ON DATABASE " + quotedDatabase + " FROM PUBLIC");
      statement.execute(
          "REVOKE ALL PRIVILEGES ON DATABASE " + quotedDatabase + " FROM " + quotedServing);
      statement.execute("GRANT CONNECT ON DATABASE " + quotedDatabase + " TO " + quotedServing);
      statement.execute("REVOKE ALL PRIVILEGES ON SCHEMA " + quotedSchema + " FROM PUBLIC");
      statement.execute(
          "REVOKE ALL PRIVILEGES ON SCHEMA " + quotedSchema + " FROM " + quotedServing);
      statement.execute("GRANT USAGE ON SCHEMA " + quotedSchema + " TO " + quotedServing);
      statement.execute(
          "REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA "
              + quotedSchema
              + " FROM "
              + quotedServing);
      statement.execute(
          "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA "
              + quotedSchema
              + " TO "
              + quotedServing);
      statement.execute(
          "REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA "
              + quotedSchema
              + " FROM "
              + quotedServing);
      statement.execute(
          "GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA "
              + quotedSchema
              + " TO "
              + quotedServing);
      statement.execute(
          "REVOKE INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER ON "
              + authorityTables
              + " FROM "
              + quotedServing);
      statement.execute("GRANT SELECT ON " + authorityTables + " TO " + quotedServing);
      statement.execute(
          "ALTER DEFAULT PRIVILEGES FOR ROLE "
              + quotedMigrator
              + " IN SCHEMA "
              + quotedSchema
              + " REVOKE ALL ON TABLES FROM PUBLIC");
      statement.execute(
          "ALTER DEFAULT PRIVILEGES FOR ROLE "
              + quotedMigrator
              + " IN SCHEMA "
              + quotedSchema
              + " REVOKE ALL ON TABLES FROM "
              + quotedServing);
      statement.execute(
          "ALTER DEFAULT PRIVILEGES FOR ROLE "
              + quotedMigrator
              + " IN SCHEMA "
              + quotedSchema
              + " GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "
              + quotedServing);
      statement.execute(
          "ALTER DEFAULT PRIVILEGES FOR ROLE "
              + quotedMigrator
              + " IN SCHEMA "
              + quotedSchema
              + " REVOKE ALL ON SEQUENCES FROM PUBLIC");
      statement.execute(
          "ALTER DEFAULT PRIVILEGES FOR ROLE "
              + quotedMigrator
              + " IN SCHEMA "
              + quotedSchema
              + " REVOKE ALL ON SEQUENCES FROM "
              + quotedServing);
      statement.execute(
          "ALTER DEFAULT PRIVILEGES FOR ROLE "
              + quotedMigrator
              + " IN SCHEMA "
              + quotedSchema
              + " GRANT USAGE, SELECT ON SEQUENCES TO "
              + quotedServing);
    }
  }

  private static String quotedIdentifier(String value) {
    if (value == null || !DATABASE_IDENTIFIER.matcher(value).matches()) {
      throw new IllegalStateException("database identifier is invalid");
    }
    return '"' + value + '"';
  }

  private static SpringApplication application(Map<String, Object> properties) {
    SpringApplication application = new SpringApplication(SchemaInitConfiguration.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setAdditionalProfiles("schema-init");
    application.setDefaultProperties(properties);
    return application;
  }

  private static void inspectNativeFilesV7Precondition(Flyway flyway, Path blobRoot)
      throws Exception {
    boolean v7Applied = Arrays.stream(flyway.info().applied())
        .anyMatch(migration -> migration.getVersion() != null
            && "7".equals(migration.getVersion().getVersion()));
    if (!v7Applied) {
      requireEmptyNativeFilesBlobRoot(blobRoot);
      return;
    }
    requireNativeFilesBlobRoot(blobRoot);
  }

  private static NativeFilesVolumeAuthority.Authority establishOrValidateNativeFilesVolumeAuthority(
      Connection connection,
      FilesVolumeAuthorityJpaRepository repository,
      Optional<NativeFilesVolumeAuthority.TransitionContext> transitionContext,
      Path blobRoot,
      String schemaHistoryFingerprint)
      throws Exception {
    var existing = repository.findAll();
    if (existing.size() > 1) {
      throw new IllegalStateException("native Files volume authority is duplicated");
    }
    if (existing.isEmpty()) {
      NativeFilesVolumeAuthority.TransitionContext accepted =
          transitionContext.orElseThrow(
              () ->
                  new IllegalStateException(
                      "native Files volume authority requires an accepted empty-root transition"));
      requireEmptyNativeFilesRelationalState(connection);
      requireEmptyNativeFilesBlobRoot(blobRoot);
      NativeFilesVolumeAuthority.Authority authority =
          NativeFilesVolumeAuthority.mint(accepted, schemaHistoryFingerprint);
      repository.saveAndFlush(authority.toEntity());
      NativeFilesVolumeAuthority.createOrValidateMarker(blobRoot, authority);
      return authority;
    }

    NativeFilesVolumeAuthority.Authority authority =
        NativeFilesVolumeAuthority.Authority.fromEntity(existing.getFirst());
    if (!schemaHistoryFingerprint.equals(authority.schemaHistoryFingerprint())) {
      throw new IllegalStateException(
          "native Files volume authority has a different schema-history fingerprint");
    }
    if (transitionContext.isPresent()) {
      NativeFilesVolumeAuthority.validateTransitionReceipt(authority, transitionContext.get());
      NativeFilesVolumeAuthority.createOrValidateMarker(blobRoot, authority);
    } else {
      NativeFilesVolumeAuthority.validateMarker(blobRoot, authority);
    }
    return authority;
  }

  private static void requireEmptyNativeFilesRelationalState(Connection connection)
      throws Exception {
    String statement =
        """
        SELECT EXISTS (SELECT 1 FROM weave_files_objects)
            OR EXISTS (SELECT 1 FROM weave_file_locks)
            OR EXISTS (SELECT 1 FROM weave_files_stream_heads)
            OR EXISTS (SELECT 1 FROM weave_files_mutation_plans)
            OR EXISTS (SELECT 1 FROM weave_files_mutation_targets)
            OR EXISTS (SELECT 1 FROM weave_files_mutation_fences)
            OR EXISTS (SELECT 1 FROM weave_files_blob_cleanup_dispositions)
            OR EXISTS (SELECT 1 FROM weave_files_changes)
            OR EXISTS (
                SELECT 1 FROM weave_operation_intents WHERE domain_key = 'files')
            OR EXISTS (
                SELECT 1
                  FROM weave_operation_outbox outbox
                  JOIN weave_operation_intents intent
                    ON intent.operation_ref = outbox.operation_ref
                 WHERE intent.domain_key = 'files')
        """;
    try (var query = connection.createStatement(); var rows = query.executeQuery(statement)) {
      if (!rows.next() || rows.getBoolean(1)) {
        throw new IllegalStateException(
            "native Files volume authority requires empty relational Files state");
      }
    }
  }

  private static void requireEmptyNativeFilesBlobRoot(Path blobRoot) throws Exception {
    requireNativeFilesBlobRoot(blobRoot);
    try (var entries = Files.list(blobRoot)) {
      if (entries.findAny().isPresent()) {
        throw new IllegalStateException(
            "native Files blob root is populated without trusted V7 initialization history");
      }
    }
  }

  private static void requireNativeFilesBlobRoot(Path blobRoot) {
    if (Files.isSymbolicLink(blobRoot)
        || !Files.isDirectory(blobRoot, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException("native Files blob root is unavailable or unsafe");
    }
  }

  private static void writeReceipt(
      Path receipt,
      String candidate,
      int migrationsExecuted,
      String targetSchemaVersion,
      NativeFilesVolumeAuthority.Authority nativeFilesVolumeAuthority,
      SchemaCatalogFingerprint.Snapshot snapshot) throws Exception {
    Path parent = receipt.getParent();
    if (parent == null
        || Files.isSymbolicLink(parent)
        || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
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
    value.put(
        "nativeFilesVolumeAuthority",
        NativeFilesVolumeAuthority.receiptProjection(nativeFilesVolumeAuthority));
    value.put("catalogFingerprintFormat", SchemaCatalogFingerprint.FORMAT);
    value.put("catalogFingerprint", snapshot.sha256());
    value.put("tableCount", snapshot.tables().size());
    value.put("tables", snapshot.tables());
    value.put(
        "catalogProjection",
        new ObjectMapper().readTree(snapshot.canonicalJson()));
    value.put("completedAtUtc", Instant.now().toString());
    value.put("secretValuesIncluded", false);
    byte[] serialized = new ObjectMapper().writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
    if (serialized.length > SchemaReceiptVerifier.MAX_SCHEMA_RECEIPT_BYTES) {
      throw new IllegalStateException("schema receipt exceeds its bounded artifact size");
    }
    Path temporary = Files.createTempFile(parent, ".schema-init-", ".json");
    try {
      try {
        Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
      } catch (UnsupportedOperationException ignored) {
        // The deployment filesystem is POSIX; other test filesystems retain exclusive creation.
      }
      try (FileChannel channel = FileChannel.open(
          temporary, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
        ByteBuffer buffer = ByteBuffer.wrap(serialized);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }
      Files.move(
          temporary,
          receipt,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      NativeFilesVolumeAuthority.forceDirectory(parent);
    } finally {
      Files.deleteIfExists(temporary);
      Arrays.fill(serialized, (byte) 0);
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

package com.massimotter.weave.backend.schema;

import com.massimotter.weave.backend.persistence.jpa.schema.SchemaAuthorityJpaEntity;
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
  private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
  private static final String MIGRATION_LOCATION = "classpath:db/migration";

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
    String password = persistencePassword(environment);
    String candidate = required(environment, "WEAVE_CANDIDATE_COMMIT");
    if (!COMMIT.matcher(candidate).matches()) {
      throw new IllegalStateException("candidate commit must be one lowercase immutable SHA-1");
    }
    Path receipt = Path.of(required(environment, "WEAVE_SCHEMA_INIT_RECEIPT_FILE"))
        .toAbsolutePath()
        .normalize();

    Flyway flyway = Flyway.configure()
        .dataSource(url, username, password)
        .locations(MIGRATION_LOCATION)
        .baselineOnMigrate(false)
        .cleanDisabled(true)
        .load();

    // Flyway owns DDL and its PostgreSQL lock/history. A non-empty schema without
    // Flyway history intentionally fails here instead of being silently baselined.
    flyway.validate();
    var migration = flyway.migrate();
    flyway.validate();

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
      if (!validated.tables().contains("weave_schema_authority")) {
        throw new IllegalStateException("authority marker table is absent after Flyway migration");
      }
      SchemaAuthorityJpaRepository markers = context.getBean(SchemaAuthorityJpaRepository.class);
      markers.deleteAllInBatch();
      markers.saveAndFlush(new SchemaAuthorityJpaEntity(
          EPOCH, MODEL_ID, candidate, validated.sha256(), Instant.now()));
      writeReceipt(
          receipt,
          candidate,
          migration.migrationsExecuted,
          migration.targetSchemaVersion == null ? null : migration.targetSchemaVersion,
          validated);
    }
  }

  private static SpringApplication application(Map<String, Object> properties) {
    SpringApplication application = new SpringApplication(SchemaInitConfiguration.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setAdditionalProfiles("schema-init");
    application.setDefaultProperties(properties);
    return application;
  }

  private static void writeReceipt(
      Path receipt,
      String candidate,
      int migrationsExecuted,
      String targetSchemaVersion,
      SchemaCatalogFingerprint.Snapshot snapshot) throws Exception {
    Path parent = receipt.getParent();
    if (parent == null || !Files.isDirectory(parent)) {
      throw new IllegalStateException("schema receipt parent directory is unavailable");
    }
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("schemaVersion", "weave.schema-init-receipt/v3");
    value.put("supportSafe", true);
    value.put("authority", "flyway");
    value.put("epoch", EPOCH);
    value.put("relationalModelId", MODEL_ID);
    value.put("candidateCommit", candidate);
    value.put("migrationsExecuted", migrationsExecuted);
    value.put("targetSchemaVersion", targetSchemaVersion);
    value.put("catalogFingerprint", snapshot.sha256());
    value.put("tableCount", snapshot.tables().size());
    value.put("tables", snapshot.tables());
    value.put(
        "catalogProjection",
        new ObjectMapper().readTree(snapshot.canonicalJson()));
    value.put("completedAtUtc", Instant.now().toString());
    value.put("secretValuesIncluded", false);
    byte[] serialized = new ObjectMapper().writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
    Path temporary = Files.createTempFile(parent, ".schema-init-", ".json");
    try {
      Files.write(
          temporary,
          serialized,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
      Files.move(
          temporary,
          receipt,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
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

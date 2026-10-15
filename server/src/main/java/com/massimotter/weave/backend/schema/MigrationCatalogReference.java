package com.massimotter.weave.backend.schema;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

/** Independently executes reviewed migrations in an exact run-owned disposable schema. */
final class MigrationCatalogReference implements AutoCloseable {
  private final Connection connection;
  private final String schema = "weave_catalog_reference_" + UUID.randomUUID().toString().replace("-", "");
  private final String targetSchema;
  private final String url;
  private final String username;
  private final String password;

  MigrationCatalogReference(String url, String username, String password) throws Exception {
    this.url = url;
    this.username = username;
    this.password = password;
    connection = DriverManager.getConnection(url, username, password);
    targetSchema = connection.getSchema();
    try (var statement = connection.createStatement()) {
      statement.execute("create schema \"" + schema + "\"");
    } catch (Exception failure) {
      connection.close();
      throw failure;
    }
    try {
      connection.setSchema(schema);
    } catch (Exception failure) {
      try { close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  SchemaCatalogFingerprint.Snapshot atVersion(MigrationVersion version) throws Exception {
    var configuration = Flyway.configure().dataSource(url, username, password)
        .schemas(schema).defaultSchema(schema).createSchemas(false)
        .locations("classpath:db/migration").baselineOnMigrate(false).cleanDisabled(true)
        .outOfOrder(false).validateOnMigrate(true).validateMigrationNaming(true).ignoreMigrationPatterns(new String[0]);
    if (version != null) configuration.target(version);
    configuration.load().migrate();
    return SchemaCatalogFingerprint.inspect(connection, targetSchema);
  }

  @Override
  public void close() throws Exception {
    try (connection) {
      // Never drop a caller-selected schema. UUID identity and owner are both checked.
      try (var query = connection.prepareStatement(
          "select nspowner = (select oid from pg_roles where rolname = current_user) "
              + "from pg_namespace where nspname = ?")) {
        query.setString(1, schema);
        try (var rows = query.executeQuery()) {
          if (!rows.next() || !rows.getBoolean(1)) {
            throw new IllegalStateException("catalog reference cleanup ownership could not be verified");
          }
        }
      }
      try (var statement = connection.createStatement()) {
        statement.setQueryTimeout(60);
        statement.execute("drop schema \"" + schema + "\" cascade");
      }
    }
  }
}

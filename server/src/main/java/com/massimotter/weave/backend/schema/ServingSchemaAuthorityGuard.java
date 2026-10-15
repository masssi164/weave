package com.massimotter.weave.backend.schema;

import com.massimotter.weave.backend.persistence.jpa.schema.SchemaAuthorityJpaRepository;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Non-mutating readiness boundary for exact catalog authority and DML-only serving roles. */
@Component
final class ServingSchemaAuthorityGuard implements SmartInitializingSingleton {
  private static final Set<String> PROFILES = Set.of("test", "dogfood", "prod", "e2e");
  private final DataSource dataSource;
  private final SchemaAuthorityJpaRepository markers;
  private final Environment environment;

  ServingSchemaAuthorityGuard(DataSource dataSource, SchemaAuthorityJpaRepository markers,
      Environment environment) {
    this.dataSource = dataSource;
    this.markers = markers;
    this.environment = environment;
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (!PROFILES.contains(environment.getProperty("weave.deployment.profile", ""))
        && Arrays.stream(environment.getActiveProfiles()).noneMatch(PROFILES::contains)) return;
    String candidate = environment.getProperty("weave.candidate.commit", "");
    var accepted = markers.findAll();
    if (!candidate.matches("[0-9a-f]{40}") || accepted.size() != 1
        || !SchemaAuthorityInitializer.EPOCH.equals(accepted.getFirst().epoch())
        || !SchemaAuthorityInitializer.MODEL_ID.equals(accepted.getFirst().relationalModelId())
        || !candidate.equals(accepted.getFirst().candidateCommit())) {
      throw new IllegalStateException("serving schema authority does not match the exact candidate");
    }
    try (Connection connection = dataSource.getConnection()) {
      requireDmlOnly(connection);
      if (!SchemaCatalogFingerprint.inspect(connection).sha256()
          .equals(accepted.getFirst().catalogFingerprint())) {
        throw new IllegalStateException("serving schema catalog fingerprint differs from accepted authority");
      }
    } catch (Exception failure) {
      throw new IllegalStateException("serving schema authority validation failed", failure);
    }
  }

  static void requireDmlOnly(Connection connection) throws Exception {
    String query = """
        select r.rolsuper or r.rolcreatedb or r.rolcreaterole
          or has_database_privilege(current_user, current_database(), 'CREATE')
          or has_schema_privilege(current_user, current_schema(), 'CREATE')
          or exists (select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
            where n.nspname = current_schema() and pg_has_role(current_user, c.relowner, 'MEMBER'))
          or exists (select 1 from pg_roles elevated where
            (elevated.rolsuper or elevated.rolcreatedb or elevated.rolcreaterole)
            and pg_has_role(current_user, elevated.oid, 'MEMBER'))
          or has_table_privilege(current_user, 'flyway_schema_history', 'INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
          or has_table_privilege(current_user, 'weave_schema_authority', 'INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
        from pg_roles r where r.rolname = current_user
        """;
    try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
      if (!rows.next() || rows.getBoolean(1)) {
        throw new IllegalStateException("serving database role has schema DDL capability");
      }
    }
  }
}

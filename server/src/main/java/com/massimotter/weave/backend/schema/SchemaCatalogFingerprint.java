package com.massimotter.weave.backend.schema;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.erdtman.jcs.JsonCanonicalizer;
import tools.jackson.databind.ObjectMapper;

/** Fixed-shape PostgreSQL catalog projection used only by the schema authority boundary. */
final class SchemaCatalogFingerprint {

  static final String FORMAT = "weave.schema-catalog/v3";

  private static final String TEXT_TYPE = "(?:character varying|varchar|text)";
  private static final String STRING_LITERAL = "(?:[eE])?'(?:''|[^'])*'";
  private static final Pattern PARENTHESIZED_STRING_LITERAL_TEXT_CAST =
      Pattern.compile(
          "\\((" + STRING_LITERAL + ")::" + TEXT_TYPE + "\\)::" + TEXT_TYPE,
          Pattern.CASE_INSENSITIVE);
  private static final Pattern STRING_LITERAL_TEXT_CAST =
      Pattern.compile(
          "(" + STRING_LITERAL + ")::" + TEXT_TYPE,
          Pattern.CASE_INSENSITIVE);
  private static final Pattern PARENTHESIZED_ARRAY_TEXT_CAST =
      Pattern.compile(
          "\\((ARRAY\\[[^\\]]*])\\)::" + TEXT_TYPE + "\\[\\]",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern ARRAY_TEXT_CAST =
      Pattern.compile(
          "(ARRAY\\[[^\\]]*])::" + TEXT_TYPE + "\\[\\]",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private SchemaCatalogFingerprint() {}

  static Snapshot inspect(Connection connection) throws Exception {
    String schema = connection.getSchema();
    if (schema == null || schema.isBlank()) {
      schema = "public";
    }
    return inspect(connection, schema);
  }

  static Snapshot inspectLegacyV2(Connection connection) throws Exception {
    return inspect(connection, connection.getSchema(), true);
  }

  static Snapshot inspect(Connection connection, String projectionSchema) throws Exception {
    return inspect(connection, projectionSchema, false);
  }

  private static Snapshot inspect(Connection connection, String projectionSchema, boolean legacyV2) throws Exception {
    String schema = connection.getSchema();
    DatabaseMetaData metadata = connection.getMetaData();
    Map<String, Map<String, Object>> tables = new TreeMap<>();
    try (ResultSet rows = metadata.getTables(connection.getCatalog(), schema, "%", new String[] {"TABLE"})) {
      while (rows.next()) {
        String table = rows.getString("TABLE_NAME");
        if (table != null
            && (!legacyV2 || (!table.startsWith("pg_") && !table.startsWith("sql_")))
            && !"flyway_schema_history".equals(table)) {
          tables.put(table, tableProjection(metadata, connection, schema, projectionSchema, table, legacyV2));
        }
      }
    }
    Map<String, Object> projection = new LinkedHashMap<>();
    projection.put("format", legacyV2 ? "weave.schema-catalog/v2" : FORMAT);
    projection.put("schema", projectionSchema);
    projection.put("tables", tables);
    if (!legacyV2) projection.put("otherObjects", otherObjects(connection, schema));
    byte[] canonical =
        new JsonCanonicalizer(new ObjectMapper().writeValueAsString(projection)
            .replace(schema + ".", projectionSchema + ".")
            .replace("\\\"" + schema + "\\\".", "\\\"" + projectionSchema + "\\\"."))
            .getEncodedUTF8();
    String fingerprint = hex(MessageDigest.getInstance("SHA-256").digest(canonical));
    return new Snapshot(fingerprint, new String(canonical, StandardCharsets.UTF_8), List.copyOf(tables.keySet()));
  }

  /** Owned schema objects outside JDBC's TABLE inventory must not escape catalog validation. */
  private static Map<String, Object> otherObjects(Connection connection, String schema) throws SQLException {
    Map<String, Object> objects = new LinkedHashMap<>();
    objects.put("views", catalogRows(connection, schema, """
        select c.relname as name,c.relkind::text as kind,pg_get_viewdef(c.oid,true) as definition
        from pg_class c join pg_namespace n on n.oid=c.relnamespace
        where n.nspname=? and c.relkind in ('v','m') order by c.relname
        """));
    objects.put("functions", catalogRows(connection, schema, """
        select p.proname as name,p.prokind::text as kind,
          pg_get_function_identity_arguments(p.oid) as arguments,
          pg_get_function_result(p.oid) as result,l.lanname as language
        from pg_proc p join pg_namespace n on n.oid=p.pronamespace
          join pg_language l on l.oid=p.prolang
        where n.nspname=? order by p.proname,pg_get_function_identity_arguments(p.oid)
        """));
    objects.put("types", catalogRows(connection, schema, """
        select t.typname as name,t.typtype::text as kind,
          case when t.typtype='d' then format_type(t.typbasetype,t.typtypmod) else '' end as base_type,
          t.typnotnull as not_null,coalesce(t.typdefault,'') as default_value,
          coalesce((select string_agg(e.enumlabel,',' order by e.enumsortorder)
            from pg_enum e where e.enumtypid=t.oid),'') as enum_labels,
          coalesce((select string_agg(pg_get_constraintdef(c.oid,true),'|' order by c.conname)
            from pg_constraint c where c.contypid=t.oid),'') as constraints
        from pg_type t join pg_namespace n on n.oid=t.typnamespace
        where n.nspname=? and t.typtype in ('e','d') order by t.typname
        """));
    objects.put("otherRelations", catalogRows(connection, schema, """
        select c.relname as name,c.relkind::text as kind
        from pg_class c join pg_namespace n on n.oid=c.relnamespace
        where n.nspname=? and c.relkind not in ('r','p','i','I','t','S','v','m') order by c.relname
        """));
    objects.put("sequences", catalogRows(connection, schema, """
        select c.relname as name,s.seqstart as start,s.seqincrement as increment,
          s.seqmax as maximum,s.seqmin as minimum,s.seqcache as cache,s.seqcycle as cycle
        from pg_sequence s join pg_class c on c.oid=s.seqrelid
          join pg_namespace n on n.oid=c.relnamespace
        where n.nspname=? order by c.relname
        """));
    return objects;
  }

  private static List<Map<String, Object>> catalogRows(Connection connection, String schema,
      String query) throws SQLException {
    List<Map<String, Object>> result = new ArrayList<>();
    try (var statement = connection.prepareStatement(query)) {
      statement.setString(1, schema);
      try (var rows = statement.executeQuery()) {
        var metadata = rows.getMetaData();
        while (rows.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          for (int column = 1; column <= metadata.getColumnCount(); column++) {
            Object value = rows.getObject(column);
            row.put(metadata.getColumnLabel(column), value == null ? "" : value);
          }
          result.add(row);
        }
      }
    }
    return result;
  }

  private static Map<String, Object> tableProjection(
      DatabaseMetaData metadata, Connection connection, String schema, String projectionSchema, String table,
      boolean legacyV2)
      throws SQLException {
    Map<String, Object> projection = new LinkedHashMap<>();
    List<Map<String, Object>> columns = new ArrayList<>();
    try (ResultSet rows = metadata.getColumns(connection.getCatalog(), schema, table, "%")) {
      while (rows.next()) {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("name", rows.getString("COLUMN_NAME"));
        column.put("type", normalizeType(rows.getString("TYPE_NAME")));
        if (!legacyV2) {
          column.put("size", rows.getInt("COLUMN_SIZE"));
          column.put("scale", rows.getInt("DECIMAL_DIGITS"));
        }
        column.put("nullable", rows.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
        column.put("default", normalizeDefault(rows.getString("COLUMN_DEF")));
        column.put("position", rows.getInt("ORDINAL_POSITION"));
        columns.add(column);
      }
    }
    columns.sort(Comparator.comparingInt(column -> (Integer) column.get("position")));
    projection.put("columns", columns);

    List<Map<String, Object>> primaryKey = new ArrayList<>();
    try (ResultSet rows = metadata.getPrimaryKeys(connection.getCatalog(), schema, table)) {
      while (rows.next()) {
        primaryKey.add(Map.of(
            "column", rows.getString("COLUMN_NAME"),
            "position", rows.getShort("KEY_SEQ")));
      }
    }
    primaryKey.sort(Comparator.comparingInt(key -> ((Number) key.get("position")).intValue()));
    projection.put("primaryKey", primaryKey);

    List<Map<String, Object>> foreignKeys = new ArrayList<>();
    try (ResultSet rows = metadata.getImportedKeys(connection.getCatalog(), schema, table)) {
      while (rows.next()) {
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("name", safe(rows.getString("FK_NAME")));
        key.put("column", rows.getString("FKCOLUMN_NAME"));
        if (!legacyV2) {
          String target = rows.getString("PKTABLE_SCHEM");
          key.put("targetSchema", schema.equals(target) ? projectionSchema : safe(target));
          key.put("updateRule", rows.getShort("UPDATE_RULE"));
          key.put("deleteRule", rows.getShort("DELETE_RULE"));
          key.put("deferrability", rows.getShort("DEFERRABILITY"));
        }
        key.put("targetTable", rows.getString("PKTABLE_NAME"));
        key.put("targetColumn", rows.getString("PKCOLUMN_NAME"));
        key.put("position", rows.getShort("KEY_SEQ"));
        foreignKeys.add(key);
      }
    }
    foreignKeys.sort(Comparator.comparing(key -> key.toString()));
    projection.put("foreignKeys", foreignKeys);
    projection.put("checks", checks(connection, schema, table));
    projection.put("uniqueConstraints", uniqueConstraints(connection, schema, table));
    projection.put("indexes", explicitIndexes(connection, schema, table));
    return projection;
  }

  private static List<Map<String, Object>> checks(
      Connection connection, String schema, String table) throws SQLException {
    String query =
        """
        select pc.conname as constraint_name, pg_get_constraintdef(pc.oid, true) as definition
        from pg_constraint pc
        join pg_class rel on rel.oid = pc.conrelid
        join pg_namespace ns on ns.oid = rel.relnamespace
        where pc.contype = 'c' and ns.nspname = ? and rel.relname = ?
        order by constraint_name
        """;
    List<Map<String, Object>> checks = new ArrayList<>();
    try (var statement = connection.prepareStatement(query)) {
      statement.setString(1, schema);
      statement.setString(2, table);
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          checks.add(Map.of(
              "name", rows.getString("constraint_name"),
              "definition", normalizeRestoreStableSql(rows.getString("definition"))));
        }
      }
    }
    return checks;
  }

  private static List<Map<String, Object>> uniqueConstraints(
      Connection connection, String schema, String table) throws SQLException {
    String query =
        """
        select pc.conname as constraint_name, pg_get_constraintdef(pc.oid, true) as definition
        from pg_constraint pc
        join pg_class rel on rel.oid = pc.conrelid
        join pg_namespace ns on ns.oid = rel.relnamespace
        where pc.contype = 'u' and ns.nspname = ? and rel.relname = ?
        order by constraint_name
        """;
    List<Map<String, Object>> constraints = new ArrayList<>();
    try (var statement = connection.prepareStatement(query)) {
      statement.setString(1, schema);
      statement.setString(2, table);
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          constraints.add(
              Map.of(
                  "name", rows.getString("constraint_name"),
                  "definition", normalizeRestoreStableSql(rows.getString("definition"))));
        }
      }
    }
    return constraints;
  }

  private static List<Map<String, Object>> explicitIndexes(
      Connection connection, String schema, String table) throws SQLException {
    String query =
        """
        select idx.relname as index_name, pg_get_indexdef(i.indexrelid) as definition
        from pg_index i
        join pg_class rel on rel.oid = i.indrelid
        join pg_namespace ns on ns.oid = rel.relnamespace
        join pg_class idx on idx.oid = i.indexrelid
        left join pg_constraint con on con.conindid = i.indexrelid
        where ns.nspname = ? and rel.relname = ? and con.oid is null
        order by idx.relname
        """;
    List<Map<String, Object>> indexes = new ArrayList<>();
    try (var statement = connection.prepareStatement(query)) {
      statement.setString(1, schema);
      statement.setString(2, table);
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          indexes.add(Map.of(
              "name", rows.getString("index_name"),
              "definition", normalizeRestoreStableSql(rows.getString("definition"))));
        }
      }
    }
    return indexes;
  }

  /**
   * PostgreSQL may redistribute redundant text casts while pg_dump/pg_restore reparses
   * check expressions, partial-index predicates, and defaults. Remove casts only from
   * string literals and literal text arrays, including the parenthesized form emitted by
   * PostgreSQL after restore. Casts on columns and computed expressions remain part of the
   * fingerprint.
   */
  private static String normalizeRestoreStableSql(String value) {
    String normalized = normalizeSql(value);
    String previous;
    do {
      previous = normalized;
      normalized =
          PARENTHESIZED_STRING_LITERAL_TEXT_CAST.matcher(normalized).replaceAll("$1");
      normalized = STRING_LITERAL_TEXT_CAST.matcher(normalized).replaceAll("$1");
      normalized = PARENTHESIZED_ARRAY_TEXT_CAST.matcher(normalized).replaceAll("$1");
      normalized = ARRAY_TEXT_CAST.matcher(normalized).replaceAll("$1");
    } while (!previous.equals(normalized));
    return normalized;
  }

  private static String normalizeSql(String value) {
    return WHITESPACE.matcher(safe(value).strip()).replaceAll(" ");
  }

  private static String normalizeType(String value) {
    return safe(value).toLowerCase(Locale.ROOT).replace("character varying", "varchar");
  }

  private static String normalizeDefault(String value) {
    return value == null ? "" : normalizeRestoreStableSql(value);
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private static String hex(byte[] value) {
    StringBuilder result = new StringBuilder(value.length * 2);
    for (byte element : value) {
      result.append(String.format(Locale.ROOT, "%02x", element));
    }
    return result.toString();
  }

  record Snapshot(String sha256, String canonicalJson, List<String> tables) {}
}

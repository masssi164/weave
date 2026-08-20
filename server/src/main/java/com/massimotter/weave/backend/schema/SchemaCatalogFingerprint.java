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

  static final String FORMAT = "weave.schema-catalog/v2";

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
    DatabaseMetaData metadata = connection.getMetaData();
    Map<String, Map<String, Object>> tables = new TreeMap<>();
    try (ResultSet rows = metadata.getTables(connection.getCatalog(), schema, "%", new String[] {"TABLE"})) {
      while (rows.next()) {
        String table = rows.getString("TABLE_NAME");
        if (table != null
            && !table.startsWith("pg_")
            && !table.startsWith("sql_")
            && !"flyway_schema_history".equals(table)) {
          tables.put(table, tableProjection(metadata, connection, schema, table));
        }
      }
    }
    Map<String, Object> projection = new LinkedHashMap<>();
    projection.put("format", FORMAT);
    projection.put("schema", schema);
    projection.put("tables", tables);
    byte[] canonical =
        new JsonCanonicalizer(new ObjectMapper().writeValueAsString(projection)).getEncodedUTF8();
    String fingerprint = hex(MessageDigest.getInstance("SHA-256").digest(canonical));
    return new Snapshot(fingerprint, new String(canonical, StandardCharsets.UTF_8), List.copyOf(tables.keySet()));
  }

  private static Map<String, Object> tableProjection(
      DatabaseMetaData metadata, Connection connection, String schema, String table)
      throws SQLException {
    Map<String, Object> projection = new LinkedHashMap<>();
    List<Map<String, Object>> columns = new ArrayList<>();
    try (ResultSet rows = metadata.getColumns(connection.getCatalog(), schema, table, "%")) {
      while (rows.next()) {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("name", rows.getString("COLUMN_NAME"));
        column.put("type", normalizeType(rows.getString("TYPE_NAME")));
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

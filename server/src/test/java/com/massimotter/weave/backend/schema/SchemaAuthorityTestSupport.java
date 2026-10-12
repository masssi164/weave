package com.massimotter.weave.backend.schema;

import java.util.Map;

/** Test-only entry point for the real fail-closed schema initializer and receipt verifier. */
public final class SchemaAuthorityTestSupport {

    private SchemaAuthorityTestSupport() {
    }

    /** Disposable PostgreSQL fixtures use a real separate DML-only runtime role. */
    public static Map<String, String> withRuntimeRole(Map<String, String> values) throws Exception {
        var result = new java.util.LinkedHashMap<>(values);
        String role = "weave_runtime_" + java.util.UUID.randomUUID().toString().replace("-", "");
        try (var connection = java.sql.DriverManager.getConnection(
                values.get("WEAVE_PERSISTENCE_URL"), values.get("WEAVE_PERSISTENCE_USERNAME"),
                values.get("WEAVE_PERSISTENCE_PASSWORD")); var statement = connection.createStatement()) {
            String schema = "\"" + connection.getSchema().replace("\"", "\"\"") + "\"";
            statement.execute("create role " + role + " login password 'test-only-runtime'");
            statement.execute("revoke create on database \""
                    + connection.getCatalog().replace("\"", "\"\"") + "\" from public");
            statement.execute("revoke create on schema " + schema + " from public");
            statement.execute("grant usage on schema " + schema + " to " + role);
            statement.execute("alter default privileges in schema " + schema
                    + " grant select,insert,update,delete on tables to " + role);
        }
        result.put("WEAVE_SCHEMA_RUNTIME_ROLE", role);
        return Map.copyOf(result);
    }

    public static void initializeAndVerify(Map<String, String> environment) throws Exception {
        SchemaAuthorityInitializer.run(Map.copyOf(environment));
        SchemaReceiptVerifier.verify(Map.copyOf(environment));
    }
}

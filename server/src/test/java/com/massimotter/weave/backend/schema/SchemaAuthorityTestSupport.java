package com.massimotter.weave.backend.schema;

import java.sql.DriverManager;
import java.util.Map;
import java.util.regex.Pattern;

/** Test-only entry point for the real fail-closed schema initializer and receipt verifier. */
public final class SchemaAuthorityTestSupport {

    public static final String SERVING_USERNAME = "weave_serving_test";
    public static final String SERVING_PASSWORD = "weave-serving-test-only";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");

    private SchemaAuthorityTestSupport() {
    }

    public static void initializeAndVerify(Map<String, String> environment) throws Exception {
        SchemaAuthorityInitializer.run(Map.copyOf(environment));
        SchemaReceiptVerifier.verify(Map.copyOf(environment));
    }

    public static void verify(Map<String, String> environment) throws Exception {
        SchemaReceiptVerifier.verify(Map.copyOf(environment));
    }

    public static void ensureServingRole(
            String jdbcUrl,
            String administrator,
            String administratorPassword) throws Exception {
        if (!IDENTIFIER.matcher(SERVING_USERNAME).matches()) {
            throw new IllegalStateException("test serving database role is invalid");
        }
        if (!IDENTIFIER.matcher(administrator).matches()) {
            throw new IllegalStateException("test migrator database role is invalid");
        }
        try (var connection = DriverManager.getConnection(
                        jdbcUrl, administrator, administratorPassword);
                var statement = connection.createStatement()) {
            statement.execute(
                    "DO $weave_test_role$ BEGIN "
                            + "IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '"
                            + SERVING_USERNAME
                            + "') THEN CREATE ROLE \""
                            + SERVING_USERNAME
                            + "\" LOGIN PASSWORD '"
                            + SERVING_PASSWORD
                            + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION; "
                            + "END IF; END $weave_test_role$");
            statement.execute("ALTER SCHEMA public OWNER TO \"" + administrator + "\"");
        }
    }
}

package com.massimotter.weave.backend.schema;

import java.util.Map;

/** Test-only entry point for the real fail-closed schema initializer and receipt verifier. */
public final class SchemaAuthorityTestSupport {

    private SchemaAuthorityTestSupport() {
    }

    public static void initializeAndVerify(Map<String, String> environment) throws Exception {
        SchemaAuthorityInitializer.run(Map.copyOf(environment));
        SchemaReceiptVerifier.verify(Map.copyOf(environment));
    }
}

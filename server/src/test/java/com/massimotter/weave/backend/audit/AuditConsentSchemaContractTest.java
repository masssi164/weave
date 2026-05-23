package com.massimotter.weave.backend.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditConsentSchemaContractTest {

    @Test
    void schemaDocumentsAuditEnvelopeConsentEventsAndWriteDisabledConnectorEnvelope() throws Exception {
        var schema = new ObjectMapper().readTree(Files.readString(Path.of("src/main/resources/contracts/audit-consent.schema.json")));

        assertThat(schema.path("title").asText()).contains("Audit and Consent");
        assertThat(schema.path("required").toString())
                .contains("tenant_id")
                .contains("actor_ref")
                .contains("idempotency_key")
                .contains("redaction_level")
                .contains("payload");
        assertThat(schema.path("properties").path("action").path("enum").toString())
                .contains("connector.write.attempted")
                .contains("assistant.write.attempted")
                .contains("consent.granted")
                .contains("consent.revoked");
        assertThat(schema.path("$defs").path("connector_write_envelope")
                        .path("properties").path("provider_writes_enabled").path("const").asBoolean())
                .isFalse();
        assertThat(schema.path("properties").path("payload").path("description").asText())
                .contains("Raw tokens")
                .contains("Authorization headers")
                .contains("raw provider errors");
    }
}

package com.massimotter.weave.backend.context;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSpaceContractTest {

    @Test
    void contextSchemaTreatsTeamAndChannelAsTemplatesNotHardHierarchy() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/contracts/context-space.schema.json"));

        assertThat(schema).contains("Weave Context Graph Contract");
        assertThat(schema).contains("Team and channel are templates/kinds, not the hard backend hierarchy");
        assertThat(schema).contains("\"tenant_id\"");
        assertThat(schema).contains("\"context_id\"");
        assertThat(schema).contains("\"kind\"");
        assertThat(schema).contains("\"template\"");
        assertThat(schema).contains("\"team\"");
        assertThat(schema).contains("\"channel\"");
        assertThat(schema).contains("\"meeting\"");
        assertThat(schema).contains("\"custom\"");
    }

    @Test
    void contextSchemaIncludesGraphMembershipsProviderBindingsAndRebacTuples() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/contracts/context-space.schema.json"));

        assertThat(schema).contains("\"edges\"");
        assertThat(schema).contains("\"memberships\"");
        assertThat(schema).contains("\"provider_bindings\"");
        assertThat(schema).contains("\"rebac_tuples\"");
        assertThat(schema).contains("\"from_context_id\"");
        assertThat(schema).contains("\"to_context_id\"");
        assertThat(schema).contains("\"principal_ref\"");
        assertThat(schema).contains("\"object_ref\"");
        assertThat(schema).contains("\"subject_ref\"");
        assertThat(schema).contains("\"context_viewer\"");
        assertThat(schema).contains("\"context_editor\"");
        assertThat(schema).contains("\"context_admin\"");
    }

    @Test
    void contextSchemaKeepsProviderSyncConsentAndAuditFailClosed() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/contracts/context-space.schema.json"));

        assertThat(schema).contains("\"provider_bindings\"");
        assertThat(schema).contains("\"matrix\"");
        assertThat(schema).contains("\"nextcloud\"");
        assertThat(schema).contains("\"vikunja\"");
        assertThat(schema).contains("\"cursor_ref\"");
        assertThat(schema).contains("\"webhook_ref\"");
        assertThat(schema).contains("\"connector_consent_required\"");
        assertThat(schema).contains("\"const\": true");
        assertThat(schema).contains("\"agentic_writes_allowed\"");
        assertThat(schema).contains("\"const\": false");
        assertThat(schema).contains("\"audit_required\"");
    }

    @Test
    void contextAdrDocumentsOrderedPlanAndNoRuntimePromotion() throws Exception {
        String adr = Files.readString(Path.of("docs/context-space-adr.md"));

        assertThat(adr).contains("Context Graph Schema");
        assertThat(adr).contains("ReBAC Adapter MVP");
        assertThat(adr).contains("Connector SDK Skeleton");
        assertThat(adr).contains("OpenProject Board workspace-sync MVP");
        assertThat(adr).contains("Vikunja and Nextcloud Deck remain comparison/fallback candidates only");
        assertThat(adr).contains("provider writes disabled until audit/consent promotion; no agentic/team writes");
        assertThat(adr).contains("Audit Event Pipeline");
        assertThat(adr).contains("Consent Center MVP");
        assertThat(adr).contains("Meeting Thread Schema");
        assertThat(adr).contains("Client-side Personal Index MVP");
        assertThat(adr).contains("No public `/api/contexts` route is introduced by this PR");
        assertThat(adr).contains("No agentic writes in team rooms or shared contexts are enabled");
        assertThat(adr).contains("private calendars require explicit connector consent, revocation, audit, and data-limit gates");
        assertThat(adr).contains("Weave is the product boundary");
    }
}

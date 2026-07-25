package com.massimotter.weave.backend.service.connector;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.config.ConnectorRuntimeProperties;
import com.massimotter.weave.backend.model.connector.ConnectorManifestValidationRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorManifestSkeletonTest {

    private final ConnectorRuntimeService service = new ConnectorRuntimeService(new ConnectorRuntimeProperties(false));

    @Test
    void manifestContractDocumentsReadOnlyFailClosedConnectorSkeleton() throws Exception {
        var schema = new ObjectMapper().readTree(Files.readString(Path.of("src/main/resources/contracts/connector-manifest.schema.json")));

        assertThat(schema.path("title").asText()).contains("Internal Connector Manifest Skeleton");
        assertThat(schema.path("properties").path("provider_writes_enabled").path("const").asBoolean()).isFalse();
        assertThat(schema.path("properties").path("release_status").path("enum").toString())
                .contains("disabled")
                .contains("read-sync-preview");
        assertThat(schema.path("properties").path("support_safe_errors").path("items").path("enum").toString())
                .contains("rate_limited")
                .contains("provider_unavailable");
        assertThat(schema.path("properties").path("webhook_refs").path("additionalProperties").path("pattern").asText())
                .isEqualTo("^webhook://.+");
    }

    @Test
    void validatesOpenProjectReadSyncSkeletonWithoutAcceptingProviderWritesOrSecrets() {
        var valid = service.validate(new ConnectorManifestValidationRequest(
                "openproject-boards-read-sync",
                "openproject",
                "read-sync-preview",
                List.of("boards.project.read", "boards.work_package.read"),
                List.of("boards.sync.read"),
                Map.of("workPackages", "cursor://openproject/work-packages"),
                Map.of(),
                Map.of("apiToken", "secret://connectors/openproject/api-token"),
                "support-safe-redacted",
                false));

        assertThat(valid.valid()).isTrue();
        assertThat(valid.publicSdkAccepted()).isFalse();
        assertThat(valid.secretValuesAccepted()).isFalse();

        var invalid = service.validate(new ConnectorManifestValidationRequest(
                "openproject-write-leak",
                "openproject",
                "live",
                List.of("boards.work_package.write"),
                List.of("boards.task.create"),
                Map.of(),
                Map.of(),
                Map.of("apiToken", "sk-op-token-value-that-looks-like-a-secret-and-must-not-pass-validation"),
                "plain",
                true));

        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.errors()).contains(
                "releaseStatus must be disabled, skeleton, or read-sync-preview until live provider promotion is specified",
                "providerWritesEnabled must remain false until a promotion spec enables writes",
                "redactionPolicy must be support-safe and redacted",
                "cursorRefs must declare at least one cursor or sync reference");
        assertThat(invalid.errors()).anySatisfy(error -> assertThat(error).contains("secret material"));
    }

    @Test
    void validatesWebhookRefsAsSignedBackendOwnedIngressOnly() {
        var valid = service.validate(new ConnectorManifestValidationRequest(
                "openproject-webhook-preview",
                "openproject",
                "read-sync-preview",
                List.of("boards.work_package.read", "webhook_events"),
                List.of("boards.sync.read"),
                Map.of("workPackages", "cursor://openproject/work-packages"),
                Map.of("workPackages", "webhook://openproject/work-packages"),
                Map.of(
                        "apiToken", "secret://connectors/openproject/api-token",
                        "webhookSignatureSecret", "secret://connectors/openproject/webhook-signature-secret"),
                "support-safe-redacted",
                false));

        assertThat(valid.valid()).isTrue();

        var invalid = service.validate(new ConnectorManifestValidationRequest(
                "openproject-raw-webhook",
                "openproject",
                "read-sync-preview",
                List.of("boards.work_package.read", "webhook_events"),
                List.of("boards.sync.read"),
                Map.of("workPackages", "cursor://openproject/work-packages"),
                Map.of("workPackages", "https://openproject.example.test/webhooks?token=secret"),
                Map.of("apiToken", "secret://connectors/openproject/api-token"),
                "support-safe-redacted",
                false));

        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.errors()).contains(
                "webhookRefs.workPackages must be a backend-owned webhook:// reference, not a raw provider URL",
                "webhookRefs require secretRefs.webhookSignatureSecret for signed provider ingress");
    }
}

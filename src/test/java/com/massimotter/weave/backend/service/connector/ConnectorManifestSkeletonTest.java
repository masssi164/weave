package com.massimotter.weave.backend.service.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
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
}

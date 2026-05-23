package com.massimotter.weave.backend.model.connector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record ConnectorManifestValidationRequest(
        @NotBlank @Size(max = 128) String id,
        @NotBlank @Size(max = 128) String provider,
        @Size(max = 64) String releaseStatus,
        List<@Size(max = 128) String> capabilities,
        List<@Size(max = 128) String> commands,
        Map<String, String> cursorRefs,
        Map<String, String> webhookRefs,
        Map<String, String> secretRefs,
        @Size(max = 128) String redactionPolicy,
        Boolean providerWritesEnabled) {
}

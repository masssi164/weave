package com.massimotter.weave.backend.integrations.api;

import java.util.List;

public record ConnectorManifestValidationResponse(
        boolean valid,
        boolean publicSdkAccepted,
        boolean secretValuesAccepted,
        List<String> errors,
        List<String> warnings) {
}

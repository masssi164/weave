package com.massimotter.weave.backend.model.connector;

import java.util.List;

public record ConnectorBoundaryResponse(
        boolean publicSdkEnabled,
        String status,
        List<String> internalRuntimeBoundaries,
        List<String> deferredUntil) {
}

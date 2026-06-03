package com.massimotter.weave.backend.cicd;

import java.util.List;

public record PipelineProviderManifest(
        String providerKey,
        String providerFamily,
        String displayName,
        String workflowRef,
        boolean manualDispatch,
        boolean polling,
        boolean webhook,
        boolean cancellationSupported,
        boolean retrySupported,
        List<String> requiredSecretRefs,
        List<String> requiredVariables,
        List<String> failClosedCases
) {}

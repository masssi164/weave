package com.massimotter.weave.backend.cicd;

import java.util.List;

public record PipelinePreflightRequest(
        boolean runnerRegistered,
        boolean runnerRunning,
        List<String> presentSecretRefs,
        List<String> presentVariables,
        boolean rawValueSubmitted,
        boolean adminApprovalCaptured,
        String supportSafePlanRef
) {}

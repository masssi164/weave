package com.massimotter.weave.backend.cicd;

import java.util.List;

public record PipelinePreflightResult(
        PipelineSetupState state,
        String reasonCode,
        boolean dispatchAllowed,
        List<String> missingNames,
        String auditRef,
        String evidenceRef,
        String supportSafeSummary
) {}

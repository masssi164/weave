package com.massimotter.weave.backend.cicd;

public record PipelineRunRef(
        String providerKey,
        String workflowRef,
        String runRef,
        PipelineRunStatus status,
        String correlationRef,
        String auditRef,
        String evidenceRef,
        String nextActionCode,
        String supportSafeSummary
) {}

package com.massimotter.weave.backend.cicd;

public record PipelineDispatchRequest(
        PipelinePreflightRequest preflight,
        String correlationRef,
        String idempotencyKey
) {}

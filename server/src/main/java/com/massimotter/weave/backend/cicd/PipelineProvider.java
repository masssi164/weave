package com.massimotter.weave.backend.cicd;

public interface PipelineProvider {
    PipelineProviderManifest manifest();

    PipelinePreflightResult preflight(PipelinePreflightRequest request);

    PipelineRunRef requestDispatch(PipelineDispatchRequest request);

    PipelineRunRef observe(PipelineRunRef runRef, PipelineObservedStatus observedStatus);
}

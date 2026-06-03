package com.massimotter.weave.backend.cicd;

public enum PipelineObservedStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILURE,
    CANCELLED,
    TIMED_OUT,
    RATE_LIMITED,
    UNKNOWN
}

package com.massimotter.weave.backend.cicd;

public enum PipelineRunStatus {
    BLOCKED,
    APPROVAL_REQUIRED,
    QUEUED,
    RUNNING,
    EVIDENCE_COMPLETE,
    FAILED,
    CANCELLED,
    TIMED_OUT,
    RATE_LIMITED,
    UNKNOWN
}

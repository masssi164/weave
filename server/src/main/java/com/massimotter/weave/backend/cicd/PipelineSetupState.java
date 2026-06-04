package com.massimotter.weave.backend.cicd;

public enum PipelineSetupState {
    PROVIDER_DISCOVERY,
    CI_CD_REGISTRATION,
    DOMAIN_SELECTION,
    ADAPTER_QUESTION,
    PREFLIGHT,
    ADMIN_APPROVAL,
    TRIGGER_REQUESTED,
    RUN_OBSERVING,
    EVIDENCE_COMPLETE,
    BLOCKED,
    FAILURE
}

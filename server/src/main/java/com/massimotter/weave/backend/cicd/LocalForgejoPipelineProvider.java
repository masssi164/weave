package com.massimotter.weave.backend.cicd;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class LocalForgejoPipelineProvider implements PipelineProvider {
    public static final String PROVIDER_KEY = "local-forgejo-actions";
    public static final String WORKFLOW_REF = "weave-admin-setup-e2e";
    private static final List<String> REQUIRED_SECRETS = List.of(
            "WEAVE_FORGEJO_TOKEN",
            "WEAVE_SERVER_SIGNING_KEY",
            "WEAVE_INFRA_STATE_SECRET"
    );
    private static final List<String> REQUIRED_VARIABLES = List.of(
            "WEAVE_FORGEJO_BASE_URL",
            "WEAVE_FORGEJO_API_URL",
            "WEAVE_FORGEJO_USERNAME",
            "WEAVE_FORGEJO_SSH_HOST",
            "WEAVE_FORGEJO_SSH_PORT"
    );

    @Override
    public PipelineProviderManifest manifest() {
        return new PipelineProviderManifest(
                PROVIDER_KEY,
                "forgejo-actions",
                "Forgejo Actions / act_runner",
                WORKFLOW_REF,
                true,
                true,
                true,
                false,
                false,
                REQUIRED_SECRETS,
                REQUIRED_VARIABLES,
                List.of("runner_missing", "runner_offline", "runner_secret_missing", "approval_missing", "unknown_status_timeout", "rate_limit_exhausted", "raw_value_supplied")
        );
    }

    @Override
    public PipelinePreflightResult preflight(PipelinePreflightRequest request) {
        if (request.rawValueSubmitted() || SupportSafePipelineRedactor.containsUnsafeValue(request.supportSafePlanRef())) {
            return blocked("raw_value_supplied", List.of(), "Raw secret, URL, log, or provider payload was supplied; dispatch blocked before provider mutation.");
        }
        if (!request.runnerRegistered()) {
            return blocked("runner_missing", List.of("FORGEJO_ACTIONS_RUNNER_REGISTRATION"), "Forgejo runner registration is missing.");
        }
        if (!request.runnerRunning()) {
            return blocked("runner_offline", List.of(), "Forgejo runner is registered but not running.");
        }
        List<String> missing = missingNames(request.presentSecretRefs(), REQUIRED_SECRETS);
        missing.addAll(missingNames(request.presentVariables(), REQUIRED_VARIABLES));
        missing.sort(String::compareTo);
        if (!missing.isEmpty()) {
            return blocked("runner_secret_missing", missing, "Required SecretRef or variable names are missing; values remain outside Weave.");
        }
        if (!request.adminApprovalCaptured()) {
            return new PipelinePreflightResult(
                    PipelineSetupState.ADMIN_APPROVAL,
                    "approval_missing",
                    false,
                    List.of(),
                    "audit://admin-cicd/local-forgejo/preflight-approval-required",
                    "release/provider-lab/admin-cicd/local-forgejo-pipeline-provider.fixture.json",
                    "Preflight passed, but explicit admin approval is required before dispatch."
            );
        }
        return new PipelinePreflightResult(
                PipelineSetupState.TRIGGER_REQUESTED,
                "dispatch_allowed",
                true,
                List.of(),
                "audit://admin-cicd/local-forgejo/preflight-dispatch-allowed",
                "release/provider-lab/admin-cicd/local-forgejo-pipeline-provider.fixture.json",
                "Preflight and approval passed; provider dispatch may be requested through the backend-owned PipelineProvider."
        );
    }

    @Override
    public PipelineRunRef requestDispatch(PipelineDispatchRequest request) {
        if (SupportSafePipelineRedactor.containsUnsafeValue(request.correlationRef())) {
            return new PipelineRunRef(
                    PROVIDER_KEY,
                    WORKFLOW_REF,
                    "none-trigger-blocked-before-dispatch",
                    PipelineRunStatus.BLOCKED,
                    "support-unsafe-correlation-ref-redacted",
                    "audit://admin-cicd/local-forgejo/preflight-blocked",
                    "release/provider-lab/admin-cicd/local-forgejo-pipeline-provider.fixture.json",
                    "replace_raw_values_with_secretrefs",
                    "Raw secret, URL, log, or provider payload was supplied in correlation metadata; dispatch blocked before provider mutation."
            );
        }
        PipelinePreflightResult result = preflight(request.preflight());
        if (!result.dispatchAllowed()) {
            PipelineRunStatus blockedStatus = "approval_missing".equals(result.reasonCode()) ? PipelineRunStatus.APPROVAL_REQUIRED : PipelineRunStatus.BLOCKED;
            String nextAction = nextActionForBlockedPreflight(result.reasonCode());
            return new PipelineRunRef(PROVIDER_KEY, WORKFLOW_REF, "none-trigger-blocked-before-dispatch", blockedStatus, supportSafeCorrelationRef(request.correlationRef()), result.auditRef(), result.evidenceRef(), nextAction, result.supportSafeSummary());
        }
        String correlationRef = supportSafeCorrelationRef(request.correlationRef());
        String runRef = "forgejo-run-" + safeRef(correlationRef) + "-pending-provider-call";
        return new PipelineRunRef(PROVIDER_KEY, WORKFLOW_REF, runRef, PipelineRunStatus.QUEUED, correlationRef, "audit://admin-cicd/local-forgejo/dispatch-requested", "release/provider-lab/admin-cicd/local-forgejo-pipeline-provider.fixture.json", "observe_run_status", "Dispatch requested through support-safe provider abstraction; provider internals stay hidden.");
    }

    @Override
    public PipelineRunRef observe(PipelineRunRef runRef, PipelineObservedStatus observedStatus) {
        PipelineRunStatus status = switch (observedStatus) {
            case QUEUED -> PipelineRunStatus.QUEUED;
            case RUNNING -> PipelineRunStatus.RUNNING;
            case SUCCESS -> PipelineRunStatus.EVIDENCE_COMPLETE;
            case FAILURE -> PipelineRunStatus.FAILED;
            case CANCELLED -> PipelineRunStatus.CANCELLED;
            case TIMED_OUT -> PipelineRunStatus.TIMED_OUT;
            case RATE_LIMITED -> PipelineRunStatus.RATE_LIMITED;
            case UNKNOWN -> PipelineRunStatus.UNKNOWN;
        };
        String nextAction = switch (status) {
            case EVIDENCE_COMPLETE -> "collect_deployed_stack_e2e_evidence";
            case RATE_LIMITED -> "backoff_and_retry_observation";
            case TIMED_OUT, UNKNOWN -> "fail_closed_unknown_status_timeout";
            case FAILED -> "collect_redacted_failure_summary";
            case CANCELLED -> "record_cancelled_terminal_state";
            default -> "continue_support_safe_observation";
        };
        return new PipelineRunRef(runRef.providerKey(), runRef.workflowRef(), runRef.runRef(), status, runRef.correlationRef(), "audit://admin-cicd/local-forgejo/status-observed", runRef.evidenceRef(), nextAction, "Observed support-safe pipeline status with redacted provider diagnostics.");
    }

    private static PipelinePreflightResult blocked(String reason, List<String> missing, String summary) {
        return new PipelinePreflightResult(PipelineSetupState.BLOCKED, reason, false, missing, "audit://admin-cicd/local-forgejo/preflight-blocked", "release/provider-lab/admin-cicd/local-forgejo-pipeline-provider.fixture.json", summary);
    }

    private static List<String> missingNames(List<String> presentNames, List<String> requiredNames) {
        Set<String> present = new TreeSet<>();
        if (presentNames != null) {
            for (String name : presentNames) {
                if (name != null && !name.isBlank()) {
                    present.add(name);
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (String required : requiredNames) {
            if (!present.contains(required)) {
                missing.add(required);
            }
        }
        return missing;
    }

    private static String nextActionForBlockedPreflight(String reasonCode) {
        return switch (reasonCode) {
            case "approval_missing" -> "capture_admin_approval";
            case "runner_missing" -> "register_runner_before_trigger";
            case "runner_offline" -> "start_runner_before_trigger";
            case "runner_secret_missing" -> "configure_secretrefs_before_trigger";
            case "raw_value_supplied" -> "replace_raw_values_with_secretrefs";
            default -> "resolve_preflight_block_before_trigger";
        };
    }

    private static String safeRef(String raw) {
        if (raw == null || raw.isBlank()) {
            return "uncorrelated";
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    private static String supportSafeCorrelationRef(String raw) {
        if (raw == null || raw.isBlank()) {
            return "uncorrelated";
        }
        return raw;
    }
}

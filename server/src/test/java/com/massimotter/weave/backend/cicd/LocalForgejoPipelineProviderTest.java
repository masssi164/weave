package com.massimotter.weave.backend.cicd;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalForgejoPipelineProviderTest {
    private final LocalForgejoPipelineProvider provider = new LocalForgejoPipelineProvider();

    @Test
    void manifestNamesSupportSafeLocalForgejoContract() {
        PipelineProviderManifest manifest = provider.manifest();

        assertThat(manifest.providerKey()).isEqualTo("local-forgejo-actions");
        assertThat(manifest.workflowRef()).isEqualTo("weave-admin-setup-e2e");
        assertThat(manifest.requiredSecretRefs()).contains("WEAVE_FORGEJO_TOKEN");
        assertThat(manifest.cancellationSupported()).isFalse();
        assertThat(manifest.retrySupported()).isFalse();
        assertThat(manifest.failClosedCases()).contains("runner_missing", "runner_secret_missing", "approval_missing", "unknown_status_timeout");
    }

    @Test
    void preflightFailsClosedForMissingRunnerAndNeverDisplaysValues() {
        PipelinePreflightResult result = provider.preflight(new PipelinePreflightRequest(false, false, List.of(), List.of(), false, false, "support_safe_domain_plan_ref"));

        assertThat(result.dispatchAllowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("runner_missing");
        assertThat(result.missingNames()).containsExactly("FORGEJO_ACTIONS_RUNNER_REGISTRATION");
        assertThat(result.supportSafeSummary()).doesNotContain("token", "http://", "https://");

        PipelineRunRef blockedRun = provider.requestDispatch(new PipelineDispatchRequest(new PipelinePreflightRequest(false, false, List.of(), List.of(), false, false, "support_safe_domain_plan_ref"), "runner-missing", "idem-runner"));
        assertThat(blockedRun.nextActionCode()).isEqualTo("register_runner_before_trigger");
    }

    @Test
    void preflightFailsClosedForRawValueSubmission() {
        PipelinePreflightResult result = provider.preflight(new PipelinePreflightRequest(true, true, allSecrets(), allVariables(), true, true, "support_safe_domain_plan_ref"));

        assertThat(result.dispatchAllowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("raw_value_supplied");
    }

    @Test
    void preflightRequiresSecretRefsVariablesAndApprovalBeforeDispatch() {
        PipelineRunRef approvalBlocked = provider.requestDispatch(new PipelineDispatchRequest(new PipelinePreflightRequest(true, true, allSecrets(), allVariables(), false, false, "support_safe_domain_plan_ref"), "approval-needed", "idem-approval"));
        assertThat(approvalBlocked.status()).isEqualTo(PipelineRunStatus.APPROVAL_REQUIRED);
        assertThat(approvalBlocked.nextActionCode()).isEqualTo("capture_admin_approval");

        PipelinePreflightResult missing = provider.preflight(new PipelinePreflightRequest(true, true, List.of("WEAVE_FORGEJO_TOKEN"), List.of("WEAVE_FORGEJO_BASE_URL"), false, false, "support_safe_domain_plan_ref"));
        assertThat(missing.reasonCode()).isEqualTo("runner_secret_missing");
        assertThat(missing.missingNames()).contains("WEAVE_INFRA_STATE_SECRET", "WEAVE_FORGEJO_API_URL");

        PipelineRunRef missingBlocked = provider.requestDispatch(new PipelineDispatchRequest(new PipelinePreflightRequest(true, true, Arrays.asList("WEAVE_FORGEJO_TOKEN", null, " "), Arrays.asList("WEAVE_FORGEJO_BASE_URL", null), false, false, "support_safe_domain_plan_ref"), "missing-secretrefs", "idem-missing"));
        assertThat(missingBlocked.status()).isEqualTo(PipelineRunStatus.BLOCKED);
        assertThat(missingBlocked.nextActionCode()).isEqualTo("configure_secretrefs_before_trigger");

        PipelinePreflightResult approval = provider.preflight(new PipelinePreflightRequest(true, true, allSecrets(), allVariables(), false, false, "support_safe_domain_plan_ref"));
        assertThat(approval.state()).isEqualTo(PipelineSetupState.ADMIN_APPROVAL);
        assertThat(approval.dispatchAllowed()).isFalse();

        PipelinePreflightResult allowed = provider.preflight(new PipelinePreflightRequest(true, true, allSecrets(), allVariables(), false, true, "support_safe_domain_plan_ref"));
        assertThat(allowed.dispatchAllowed()).isTrue();
        assertThat(allowed.reasonCode()).isEqualTo("dispatch_allowed");
    }

    @Test
    void dispatchAndObservationReturnSupportSafePipelineRunRefs() {
        PipelineDispatchRequest request = new PipelineDispatchRequest(new PipelinePreflightRequest(true, true, allSecrets(), allVariables(), false, true, "support_safe_domain_plan_ref"), "forgejo-local-preflight-2026-06-03", "idem-1");

        PipelineRunRef queued = provider.requestDispatch(request);
        assertThat(queued.status()).isEqualTo(PipelineRunStatus.QUEUED);
        assertThat(queued.runRef()).contains("pending-provider-call");
        assertThat(queued.runRef()).doesNotContain("http", "token");

        PipelineRunRef complete = provider.observe(queued, PipelineObservedStatus.SUCCESS);
        assertThat(complete.status()).isEqualTo(PipelineRunStatus.EVIDENCE_COMPLETE);
        assertThat(complete.nextActionCode()).isEqualTo("collect_deployed_stack_e2e_evidence");
        assertThat(complete.supportSafeSummary()).doesNotContain("payload", "log:", "http://", "https://");

        PipelineRunRef rateLimited = provider.observe(queued, PipelineObservedStatus.RATE_LIMITED);
        assertThat(rateLimited.nextActionCode()).isEqualTo("backoff_and_retry_observation");
        PipelineRunRef unknown = provider.observe(queued, PipelineObservedStatus.UNKNOWN);
        assertThat(unknown.nextActionCode()).isEqualTo("fail_closed_unknown_status_timeout");
    }

    @Test
    void redactorBlocksPemPrivateKeys() {
        assertThat(SupportSafePipelineRedactor.containsUnsafeValue("-----BEGIN PRIVATE KEY-----")).isTrue();
        assertThat(SupportSafePipelineRedactor.containsUnsafeValue("-----BEGIN OPENSSH PRIVATE KEY-----")).isTrue();
    }

    private static List<String> allSecrets() {
        return List.of("WEAVE_FORGEJO_TOKEN", "WEAVE_SERVER_SIGNING_KEY", "WEAVE_INFRA_STATE_SECRET");
    }

    private static List<String> allVariables() {
        return List.of("WEAVE_FORGEJO_BASE_URL", "WEAVE_FORGEJO_API_URL", "WEAVE_FORGEJO_USERNAME", "WEAVE_FORGEJO_SSH_HOST", "WEAVE_FORGEJO_SSH_PORT");
    }
}

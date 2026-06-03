# Local Forgejo deployed-stack E2E handoff

Status: Sprint 27 / #665 blocked handoff contract.

## Scope

#665 may close only when the local Forgejo setup flow reaches a deployed stack and Weave E2E evidence, correlated back to bootstrapper output and the Admin Console pipeline run. Local fixture truth alone is not sufficient.

## Required upstream evidence

- Bootstrapper plan: `release/provider-lab/local-cicd-bootstrapper/support-safe-plan.fixture.json`.
- Real runner readiness: `release/provider-lab/local-forgejo-runner-readiness/runner-readiness.fixture.json` plus the concise `~/server` signal from main/operator local DevOps work.
- Deployable domain plan from #664.
- Pipeline dispatch/status `PipelineRunRef` from #663.

## Required concise local signals

The handoff accepts only yes/no or opaque-ref evidence:

- `service_exists`, `config_path_exists`, `registered`, and `running` for the `~/server` Forgejo runner.
- `secret_refs_present` for required names; values stay hidden.
- `pipeline_terminal_success` for the selected `weave-admin-setup-e2e` workflow.
- `stack_readiness_passed` for the deployed local stack.
- `weave_e2e_passed` for Weave E2E.

No raw logs, registration tokens, secret values, provider payloads, credential-bearing URLs, tenant URLs, or member content may be copied into the repo, issues, PRs, release evidence, or support bundles.

## Current claim boundary

The current repo artifact is `blocked_awaiting_local_runner_and_pipeline_signal`. It may prove that #665 has a support-safe handoff contract. It must not claim live pipeline success, deployed-stack readiness, Weave E2E success, production cutover, or release readiness.

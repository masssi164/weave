# Local Forgejo deployed-stack E2E handoff

Status: Sprint 27 / #665 blocked handoff contract. The real local runner, deployable domain plan, and PipelineProvider evidence are present on `origin/main`; live pipeline/deployed-stack/E2E signals remain pending.

## Scope

#665 may close only when the local Forgejo setup flow reaches a deployed stack and Weave E2E evidence, correlated back to bootstrapper output and the Admin Console pipeline run. Local fixture truth alone is not sufficient.

## Required upstream evidence

- Bootstrapper plan: `release/provider-lab/local-cicd-bootstrapper/support-safe-plan.fixture.json`.
- Real runner readiness: `release/provider-lab/local-forgejo-runner-readiness/runner-readiness.fixture.json` plus the verified concise `~/server` signal from main/operator local DevOps work.
- Deployable domain plan from #664: `release/provider-lab/local-domain-plan/deployable-domain-plan.fixture.json`.
- Pipeline dispatch/status `PipelineRunRef` from #663: `release/provider-lab/admin-cicd/local-forgejo-pipeline-provider.fixture.json`.

## Required concise local signals

The handoff accepts only yes/no or opaque-ref evidence:

Satisfied upstream runner signals:

- `service_exists`, `config_path_exists`, `registered`, and `running` for the `~/server` Forgejo runner.
- `secret_refs_present` for required names; values stay hidden.

Still required before #665 can claim E2E:

- `pipeline_terminal_success` for the selected `weave-admin-setup-e2e` workflow.
- `stack_readiness_passed` for the deployed local stack.
- `weave_e2e_passed` for Weave E2E.

No raw logs, registration tokens, secret values, provider payloads, credential-bearing URLs, tenant URLs, or member content may be copied into the repo, issues, PRs, release evidence, or support bundles.

## Admin Console readiness/evidence state

The Admin Console handoff state is evidence-first and fail-closed:

- before dispatch it may show runner, deployable-plan, SecretRef-name, and approval readiness without secret values;
- after dispatch it may show only a support-safe `PipelineRunRef`, terminal status, and sanitized evidence refs;
- E2E readiness stays blocked unless `pipeline_terminal_success`, `stack_readiness_passed`, and `weave_e2e_passed` are all true.

## Failure cases

The #665 handoff must remain blocked and support-safe for:

- missing test credential or SecretRef name;
- stack not ready;
- Weave E2E failed;
- pipeline/status timeout or unknown terminal state;
- evidence redaction failure.

## Mainline dependency boundary

`origin/main` now contains the #662 runner readiness, #663 PipelineProvider, #664 deployable domain plan, and #666 bootstrapper evidence artifacts required as upstream handoff inputs. These artifacts prove dispatch readiness contracts only; they do not prove a live local Forgejo workflow run, deployed-stack readiness, or Weave E2E success.

## Live evidence boundary

A read-only local probe under `~/server` found Forgejo/runner service names present but no checked-in `.forgejo`/`.gitea` workflow file named for `weave-admin-setup-e2e`; no file contents, logs, env, URLs, tokens, or secrets were read. Creating or dispatching that workflow is a live local Forgejo/stack mutation and needs explicit operator approval.

## Current claim boundary

The current repo artifact is `blocked_awaiting_pipeline_deployed_stack_and_e2e_signal`. It may prove that #665 has a support-safe handoff contract and that upstream bootstrapper, runner, deployable-plan, and PipelineProvider prerequisites are present on main. It must not claim live pipeline success, deployed-stack readiness, Weave E2E success, production cutover, or release readiness.

# Local Forgejo runner readiness proof

Status: Sprint 27 / #662 implementation slice.

## Scope

The local Forgejo runner readiness contract is the support-safe preflight between the Go bootstrapper output from #666 and any later PipelineProvider dispatch from #663. Local DevOps fixture truth is necessary but not sufficient: the proof also requires a real Forgejo `act_runner` service and config path under `~/server`, verified outside this repository as a concise yes/no signal.

## Source of truth

The source of truth for `providerKey=local-forgejo-actions` is a customer-owned Forgejo Actions runner under `~/server` plus customer-owned SecretRef or provider-native secret mechanism. GitHub repository secrets are not the source of truth for this local E2E path unless GitHub Actions is selected as the execution backend. Runner registration tokens and secret values remain environment/SecretRef-only and must never be committed, printed, or copied into evidence.

## Readiness states

- `runner_missing` — no supported runner registration is present; dispatch is blocked and the Admin Console may display only `FORGEJO_ACTIONS_RUNNER_REGISTRATION` as the missing name.
- `runner_registered` — registration exists and is backed by a support-safe `~/server` service/config signal, but dispatch is still blocked until required SecretRef/variable names are present and explicit admin approval is captured.
- `runner_offline` — registration exists but is not online; dispatch is blocked before provider mutation.
- `runner_secret_missing` — one or more required SecretRef/variable names are absent; missing names may be displayed, values never are.
- `dispatch_allowed` — runner and required names are ready; live dispatch still requires explicit admin approval.

## Local `~/server` signal contract

Main/operator local DevOps work owns creation and verification of the real runner. Repository checks consume only this concise signal:

- `service_exists`: Forgejo runner service exists under `~/server`.
- `config_path_exists`: runner config path exists under `~/server`.
- `registered`: runner is registered with Forgejo.
- `running`: runner is running/online.
- `secret_refs_present`: required SecretRef/variable names exist; values are not displayed.

Do not paste raw logs, runner registration tokens, secret values, provider URLs, or credential-bearing links. If the signal is unavailable, repo evidence must stay blocked as `awaiting_main_local_signal`.

## Workflow/ref contract

- Provider: `local-forgejo-actions`.
- Workflow ref: `weave-admin-setup-e2e`.
- Correlation fields: `providerKey`, `workflowRef`, `runRef`, and `correlationRef`.
- Evidence path: `release/provider-lab/local-forgejo-runner-readiness/runner-readiness.fixture.json`.

## Approval boundary and stop conditions

This repo-side proof must not duplicate local infra mutation. Main/operator work may mutate `~/server` under the approved direction, but repository evidence records only the concise support-safe signal. Do not create/rotate/delete secrets, print registration tokens, or dispatch a live workflow from this proof. Evidence must not contain secret values, tokens, raw CI logs, raw provider payloads, credential-bearing URLs, tenant URLs, or member content.

## Gates

- `cd tools/weave-setup && go test ./...`
- `python3 tools/local_forgejo_runner_readiness_check.py`
- `./gradlew localForgejoRunnerReadinessCheck adminCicdOrchestrationCheck acceptanceContract releaseEvidenceCheck --console=plain`

## Claim boundary

This slice may claim that Weave has a support-safe runner readiness contract and that repo-side dispatch is blocked until the concise `~/server` runner signal is present. It must not claim that secrets were created, Forgejo workflow dispatch works, local stack deployment completed, or E2E passed.

# Local Forgejo runner readiness proof

Status: Sprint 27 / #662 implementation slice.

## Scope

The local Forgejo runner readiness contract is the support-safe preflight between the Go bootstrapper output from #666 and any later PipelineProvider dispatch from #663. It is intentionally evidence-only until Massimo explicitly approves live infrastructure or secret operations.

## Source of truth

The source of truth for `providerKey=local-forgejo-actions` is a customer-owned Forgejo Actions runner plus customer-owned SecretRef or provider-native secret mechanism. GitHub repository secrets are not the source of truth for this local E2E path unless GitHub Actions is selected as the execution backend.

## Readiness states

- `runner_missing` — no supported runner registration is present; dispatch is blocked and the Admin Console may display only `FORGEJO_ACTIONS_RUNNER_REGISTRATION` as the missing name.
- `runner_registered` — registration exists, but dispatch is still blocked until required SecretRef/variable names are present and explicit admin approval is captured.
- `runner_offline` — registration exists but is not online; dispatch is blocked before provider mutation.
- `runner_secret_missing` — one or more required SecretRef/variable names are absent; missing names may be displayed, values never are.
- `dispatch_allowed` — runner and required names are ready; live dispatch still requires explicit admin approval.

## Workflow/ref contract

- Provider: `local-forgejo-actions`.
- Workflow ref: `weave-admin-setup-e2e`.
- Correlation fields: `providerKey`, `workflowRef`, `runRef`, and `correlationRef`.
- Evidence path: `release/provider-lab/local-forgejo-runner-readiness/runner-readiness.fixture.json`.

## Approval boundary and stop conditions

Do not mutate `~/server`, register a runner, create/rotate/delete secrets, or dispatch a live workflow from this proof. If a next step requires live mutation, prepare a concise command/config diff and stop for explicit approval. Evidence must not contain secret values, tokens, raw CI logs, raw provider payloads, credential-bearing URLs, tenant URLs, or member content.

## Gates

- `cd tools/weave-setup && go test ./...`
- `python3 tools/local_forgejo_runner_readiness_check.py`
- `./gradlew localForgejoRunnerReadinessCheck adminCicdOrchestrationCheck acceptanceContract releaseEvidenceCheck --console=plain`

## Claim boundary

This slice may claim that Weave has a support-safe runner readiness contract and current local `runner_missing` block. It must not claim that a live runner was registered, secrets were created, Forgejo workflow dispatch works, local stack deployment completed, or E2E passed.

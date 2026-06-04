# Local Forgejo PipelineProvider proof

Status: Sprint 27 / #663 implementation slice.

## Scope

#663 introduces a backend-owned `PipelineProvider` abstraction for `providerKey=local-forgejo-actions` and workflow `weave-admin-setup-e2e`. The implementation models support-safe preflight, explicit admin approval, dispatch request refs, status observation, and fail-closed outcomes without exposing provider internals.

## Backend contract

- Port: `com.massimotter.weave.backend.cicd.PipelineProvider`.
- Implementation: `com.massimotter.weave.backend.cicd.LocalForgejoPipelineProvider`.
- Run ref DTO: `PipelineRunRef` with provider key, workflow ref, opaque run ref, status, correlation ref, audit ref, evidence ref, next action, and support-safe summary only.
- Redaction guard: `SupportSafePipelineRedactor` blocks raw secret, URL, log, or provider payload submission before dispatch.

## Preconditions

Dispatch can be requested only after:

- #666 bootstrapper plan exists.
- #662 runner readiness is registered/running support-safely.
- #664 domain-adapter deployable plan exists.
- Required SecretRef and variable names are present; values remain outside Weave.
- Explicit admin approval is captured.

## Fail-closed cases

The provider blocks `runner_missing`, `runner_offline`, `runner_secret_missing`, `approval_missing`, `unknown_status_timeout`, `rate_limit_exhausted`, and `raw_value_supplied`.

## Live dispatch boundary

This slice does not perform live Forgejo dispatch. When ready, ask main/operator for the smallest action: dispatch `weave-admin-setup-e2e` on local Forgejo and return only queued/running/terminal status plus an opaque run ref. Do not request or paste raw logs, provider URLs, tokens, registration secrets, or payload bodies.

## Gates

- `cd server && ./gradlew test --tests 'com.massimotter.weave.backend.cicd.LocalForgejoPipelineProviderTest'`
- `python3 tools/local_forgejo_pipeline_provider_check.py`
- `./gradlew localForgejoPipelineProviderCheck acceptanceContract releaseEvidenceCheck --console=plain`

## Claim boundary

This slice may claim backend-owned support-safe PipelineProvider dispatch/status contracts. It must not claim live workflow success, deployed-stack readiness, Weave E2E success, production cutover, or release readiness.

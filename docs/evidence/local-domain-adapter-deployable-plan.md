# Local domain-adapter deployable plan

Status: Sprint 27 / #664 implementation slice.

## Scope

The domain-adapter deployable plan maps Admin setup choices into deterministic, support-safe plan fragments consumed by the Go bootstrapper output and later local Forgejo workflow dispatch. It does not run deployment, register runners, create secrets, or claim live E2E.

## Domain-led selection model

Admin setup asks each domain whether to use an existing adapter/provider or the self-hosted recommendation. The fixture covers:

- Server/backend.
- Infrastructure stack.
- Identity.
- Chat.
- Files.
- Calendar.
- Boards/tasks.
- Health/readiness.

Each domain fragment names only required SecretRef names, required variable names, readiness checks, rollback refs, and evidence refs. Values, provider URLs, raw logs, provider payloads, tenant URLs, and member content are forbidden.

## Dispatch boundary

The plan feeds `providerKey=local-forgejo-actions` and workflow `weave-admin-setup-e2e` through a support-safe plan ref. The verified `~/server` runner signal from #662 is necessary but not sufficient: #663 still owns PipelineProvider dispatch/status and explicit admin approval before live workflow mutation.

## Fail-closed cases

The plan fails closed on unsupported adapters, missing SecretRefs, missing variables, raw secret values, missing loss/rollback refs, missing runner signal, and missing admin approval.

## Gates

- `python3 tools/local_domain_adapter_plan_check.py`
- `./gradlew localDomainAdapterPlanCheck acceptanceContract releaseEvidenceCheck --console=plain`

## Claim boundary

This slice may claim that Weave has a deterministic support-safe deployable local domain plan. It must not claim live deployment, pipeline success, stack readiness, Weave E2E, production cutover, or release readiness.

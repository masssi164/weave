# Admin CI/CD setup UI test plan

Status: Sprint 26 / #659 support-safe UI coverage plan.

## Required Admin Console paths

| Path | Expected Admin Console result | Safety rule |
| --- | --- | --- |
| Provider-domain selection | Admin chooses Weave domains and target provider families in product language. | No provider SDK fields or raw endpoints in member-facing copy. |
| Existing adapter question | Admin can state whether a current adapter/provider should be reused. | Existing provider details remain support-safe refs. |
| Self-hosted fallback | When no adapter exists, Admin Console recommends self-hosted options such as Forgejo/Woodpecker-style CI/CD. | Recommendation does not imply runner installation or mutation. |
| Missing-secret display | Admin sees missing names and roles such as `WEAVE_FORGEJO_TOKEN` or `FORGEJO_ACTIONS_RUNNER_REGISTRATION`. | Values, tokens, raw URLs, raw CI logs, payloads, and tenant URLs stay hidden. |
| Trigger blocked | If preflight lacks runner readiness, SecretRefs, approval, provider support, or rate-limit capacity, trigger remains unavailable. | Fail closed before dispatch. |
| Run started | After preflight and approval pass, Admin Console shows a support-safe `PipelineRunRef`. | No raw provider link or log required to continue in Weave. |
| Dry-run complete | Admin sees mapping/loss report and next actions. | Dry-run is not migration apply or go-live. |
| Migration aborted | Admin sees cancellation/abort status and evidence refs. | Provider-specific cancellation diagnostics are redacted. |
| Migration applied | Admin sees apply evidence only when the domain contract permits apply. | Apply still does not equal production go-live. |
| Post-reconcile evidence | Admin sees post-reconcile readiness, evidence freshness, and blockers. | Go-live approval remains separate. |

## Local Forgejo scenario

Current local Forgejo proof is intentionally blocked before dispatch because runner readiness is missing. The UI test should assert:

- required variables/SecretRefs are named, not valued;
- `FORGEJO_ACTIONS_RUNNER_REGISTRATION` is displayed as the missing runner-registration name;
- setup state is `preflight_blocked` with reason `runner_missing`;
- no pipeline dispatch action is available;
- raw CI logs, provider payloads, credential-bearing URLs, tenant URLs, token values, secret values, and member content are absent.

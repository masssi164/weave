# Admin CI/CD orchestration contract

Status: Sprint 26 / issue #659 contract slice.

## Product rule

The Organization/Admin Console is the canonical setup surface. CI/CD is the customer-owned execution and validation backend. Admins choose domains, providers, and setup intent in Weave product language; Weave maps that intent to a supported pipeline provider contract, validates required variable and SecretRef names, triggers only when authorized, and reflects support-safe progress back in the Admin Console.

This contract does not authorize production cutover, provider mutation in customer infrastructure, commercial adapter implementation, raw provider diagnostics, raw CI logs, raw provider payloads, token display, secret-value display, or credential-bearing links. Migration apply and go-live approval are separate gates with separate evidence.

## Setup state model

Admin Console setup states are stable Weave states, not CI-provider folklore:

1. `provider_discovery` — show supported setup backends and their capability limits.
2. `ci_cd_registration` — register a CI/CD provider by manifest and support-safe refs.
3. `domain_selection` — choose Weave domains such as Chat, Files, Boards, Calendar, Identity, Health, and Weaver.
4. `adapter_question` — ask whether an existing adapter/provider should be reused.
5. `self_hosted_recommendation` — recommend a self-hosted default when no adapter exists.
6. `target_provider_selection` — select target providers in product terms.
7. `preflight` — validate manifest, auth scope, approval requirements, variable names, SecretRef names, runner readiness, and rate limits.
8. `dry_run_mapping` — produce a support-safe mapping/loss report without mutation.
9. `admin_approval` — require explicit admin approval before trigger/apply steps.
10. `trigger_requested` — call the pipeline provider abstraction only after preflight and approval pass.
11. `run_observing` — correlate to a `PipelineRunRef` and show Weave progress states.
12. `migration_apply_candidate` — apply is still separate and blocked unless the domain contract permits it.
13. `abort_requested` — cancel or mark abort when supported.
14. `resume_requested` — resume from a prior support-safe run ref when supported.
15. `post_reconcile_readiness` — show reconciliation evidence after the run.
16. `evidence_complete` — capture release/operator evidence without claiming go-live.
17. `go_live_approval_required` — production cutover needs a separate explicit approval.

## PipelineProviderManifest v1

Each provider manifest must declare:

- `providerKey`, `providerFamily`, `displayName`, and `supportTier`.
- Trigger capabilities: manual dispatch, workflow/pipeline refs, branch/ref input, environment approval support, dry-run input support, idempotency key support, and trigger auth ref type.
- Status capabilities: polling, webhook, terminal states, unknown-status handling, run correlation fields, and raw-log redaction requirement.
- Approval capabilities: native environment approval, service connection approval, manual Admin Console approval, or unsupported.
- Cancellation, retry, rollback-support, timeout, and rate-limit behavior.
- Required variables and SecretRefs by role. The manifest names required names and roles only; it never carries values.
- Support-safe diagnostic codes. Provider payloads, raw CI logs, bearer diagnostics, credential-bearing links, tenant URLs, secret values, and member content are forbidden.

## SecretRef and variable validation

Validation states:

- `present` — required name exists; value is not displayed.
- `missing` — required name is absent; Admin Console shows the missing name and where to set it.
- `wrong_role` — a name exists in the wrong scope or role.
- `stale` — rotation or freshness evidence is too old.
- `unverified` — provider cannot prove presence without unsafe access.
- `blocked_value_supplied` — a secret value, token, bearer diagnostic, raw URL, raw log, or raw payload was supplied to Weave; fail closed.

Admin Console copy must say exactly which names are missing, for example `WEAVE_FORGEJO_TOKEN` or `WEAVE_FORGEJO_API_URL`, and whether the customer may set them as repository variables, repository secrets, environment secrets, external secret-manager refs, or provider-native secret refs. It must not display values.

## PipelineRunRef v1

A run reference contains only:

- `providerKey` and `runRef` as opaque refs.
- `workflowRef` or `pipelineRef` as a support-safe logical name.
- `status` from `blocked`, `queued`, `running`, `failed`, `cancelled`, `timed_out`, `rate_limited`, `approval_required`, `dry_run_complete`, `evidence_complete`, or `unknown`.
- `correlationRef`, `auditRef`, `evidenceRef`, and `updatedAt`.
- Optional `nextActionCode` and `supportSafeSummary`.

It must not include provider URLs, credential-bearing links, raw logs, raw payloads, tokens, secret values, tenant URLs, or member content.

## Fail-closed rules

The setup abstraction fails closed for:

- unconfigured pipeline provider;
- missing SecretRef or required variable name;
- unsupported adapter or unsupported trigger/status feature;
- failed or missing admin approval;
- missing runner readiness;
- unknown status beyond timeout;
- webhook timeout;
- rate-limit exhaustion;
- cancellation unsupported by provider;
- raw secret/log/payload/value supplied in any Admin Console or support path.

## Local Forgejo proof seam

Massimo's local Forgejo is the Sprint 26 local E2E target. The proof must represent it through support-safe refs only:

- `providerKey=local-forgejo-actions`;
- `providerFamily=forgejo-actions`;
- base/API/SSH connection details referenced by variable names, not values;
- token referenced by `WEAVE_FORGEJO_TOKEN`, never printed;
- runner readiness as a validation state. If no act_runner/Woodpecker runner is present, trigger is blocked with `runner_missing` before any pipeline dispatch.

The local proof may validate preflight, missing-name display, redaction, trigger-blocked status, run-ref shape, and support-safe evidence. It must not mutate Forgejo data, create runners, rotate unrelated secrets, or claim production setup completion without separate approval and evidence.

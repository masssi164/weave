# Weave Control bootstrap-to-client contract

Status: Sprint 30 contract slice for issue #681. This page turns the customer-simple setup direction into support-safe acceptance language; it does not authorize live provider mutation, production cutover, or a Weave Server rewrite.

## Product boundary

Weave Control is the admin/operator product surface. It consists of `weavectl` plus the embedded Control UI/Admin Console, backed by Weave Server as the Java domain facade, policy, readiness, audit, and evidence brain. Weave Server stays separately deployable or attachable until contract evidence proves a different implementation is safer.

Weave App is the member product surface. A normal member enters through an organization auth URL, invite link, or deep link, completes SSO, and sees Weave product capabilities. Members never configure CI/CD targets, Forgejo/GitHub/GitLab/Azure repositories, OIDC clients, provider URLs, service endpoints, SecretRefs, Matrix/Nextcloud/OpenProject/LiveKit internals, or bootstrap diagnostics.

## Setup modes

| Mode | Admin/operator intent | Allowed mutation boundary | Member result | Evidence boundary |
| --- | --- | --- | --- | --- |
| `deploy_new` | Weave Control provisions a new provider/domain target from an approved plan. | Only resources named in the plan, after dry-run/preflight, consequence copy, rollback/support boundary, and explicit apply approval. | Organization manifest exposes provider-neutral states such as `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`. | Plan ref, pipeline/run ref, readiness refs, audit refs, and redacted support bundle refs. |
| `attach_existing` | Weave Control binds an existing customer/provider domain without redeploying it. | No provider redeploy, destructive migration, or credential rotation unless a separate approved action says so. | Members see product capability states only; attach diagnostics remain admin/operator-only. | Attach preflight ref, SecretRef posture, health/readiness refs, and support-safe next-action codes. |
| `hybrid` | The organization mixes deploy-new and attach-existing by domain. | Each domain keeps its own mutation boundary; unsupported combinations fail closed before apply. | Members receive one coherent Weave manifest, not provider-specific setup prompts. | Per-domain mode refs plus aggregate member preview and release-claim boundary. |

Unsupported combinations must return stable support-safe next-action codes. They must not leak raw provider errors, credential-bearing URLs, tenant URLs, downstream payloads, tokens, or member content.

## Bootstrap-to-client state machine

1. `draft_plan` — owner/admin/operator selects domains and setup modes. Inputs are provider category choices, opaque `SecretRef`/`CredentialRef` handles, repo/pipeline target refs, policy profile refs, and rollback/support expectation. Raw secrets and credential-bearing URLs are rejected.
2. `preflight_ready` — Weave Server validates domain compatibility, policy posture, required SecretRefs, redaction posture, and member impact preview. No mutation has happened.
3. `awaiting_apply_approval` — Weave Control shows consequences, recovery boundary, support-safe evidence that will be emitted, and blocked claims. High-impact actions require an approval receipt.
4. `apply_dispatched` — after approval, `weavectl`/Control UI writes or dispatches only the selected plan target. Dogfood local Forgejo dispatch remains blocked until the operator explicitly approves the local mutation requested by #665.
5. `pipeline_observed` — Admin Console and/or shell surfaces pipeline status through support-safe refs and terminal booleans, not raw logs.
6. `stack_readiness_observed` — Weave Server exposes per-domain readiness and policy state. Admins see next actions; members see only translated capability states.
7. `first_user_activation_ready` — owner/admin creates, activates, or invites the first users through Weave Control.
8. `member_client_ready` — invited members open Weave App, complete SSO, and enter product surfaces without provider/OIDC/endpoint setup.
9. `evidence_complete` — release claims may reference only the exact mode/domain scope whose pipeline, readiness, E2E, support-bundle, accessibility, and claim-control evidence is current.

## Inputs, outputs, and errors

Required plan fields:

- `plan_ref` and candidate commit SHA;
- organization and domain refs;
- setup mode per domain: `deploy_new`, `attach_existing`, or `hybrid` aggregate;
- selected provider category/adapters visible only to owner/admin/operator roles;
- required `SecretRef`/`CredentialRef` handles and missing-secret reason codes;
- mutation boundary and rollback/support expectation;
- member capability preview using provider-neutral states;
- evidence refs to be emitted: plan, pipeline, readiness, E2E, audit, support bundle, and claim gate.

Required terminal booleans for the local Forgejo deployed-stack release blocker are `pipeline_terminal_success`, `stack_readiness_passed`, and `weave_e2e_passed`. They remain false/unknown until an approved local run produces evidence; GitHub-only Live Stack evidence is not a substitute. A dispatched workflow, generated plan, or preflight-only proof is `dispatch_preflight_only` until all three terminal booleans are true for the selected target. The member handoff may claim `member_provider_neutral_join_passed` only when a normal member has joined through an organization URL, invite link, or deep link and seen product surfaces without provider setup leakage.

Error responses must use stable codes such as `unsupported_hybrid_combination`, `missing_secretref`, `preflight_failed`, `approval_required`, `pipeline_not_terminal`, `readiness_degraded`, `e2e_not_proven`, `manual_at_missing`, or `claim_blocked`. Support-safe evidence may include opaque refs and reason codes only.

## Member-provider boundary

The member path may contain:

- organization/invite/deep-link entry;
- Weave SSO sign-in;
- member states `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`;
- short impact/fallback copy such as “Calendar is unavailable; ask an admin.”

The member path must not contain provider setup forms, OIDC/SAML wiring, realms, CI/CD target selection, raw service endpoints, SecretRefs, selected adapter names in core workflows, provider diagnostics, raw downstream errors, tokens, tenant URLs, Matrix room IDs, Nextcloud paths, OpenProject/Vikunja project identifiers, LiveKit room tokens, or bootstrap run logs.

## Optional governed Weaver boundary

Weaver remains an optional governed personal-assistant line, disabled by default. It is unavailable unless all of these are true:

1. organization policy enables the Weaver category;
2. per-user or group policy grants `weaver.enabled`;
3. the governed runtime generator and sandbox profile are enabled;
4. tool and domain allowlists intersect with the member's normal rights;
5. write-like or external actions require approval receipts;
6. audit, revoke, redaction, and support-safe evidence refs are active.

Policy-denied, approval-required, revoked, unavailable, and not-configured states must be observable to admins/operators and translated to safe member copy. Support evidence must exclude prompts, private memory, member content, tokens, raw provider payloads, credential URLs, raw runtime configuration, and raw downstream errors. No PR, release note, issue comment, or product copy may claim Weaver is a default production PA or broadly autonomous assistant from this slice.

## Current repo slices

- `docs/admin-provisioned-first-use.md` owns the member/admin first-use split.
- `docs/admin-suite-readiness-setup-contract.md` owns Admin Console readiness, guided setup, and claim-control summary language.
- `docs/control-plane-infra-bootstrap.md` owns current bootstrap artifacts, SecretRefs, and smoke contract.
- `docs/governed-weaver-runtime-security-contract.md` owns Weaver policy, runtime, sandbox, approval, audit, and revoke gates.
- `client/test/architecture/admin_provisioned_first_use_contract_test.dart` and `client/test/architecture/member_client_provider_boundary_contract_test.dart` guard member setup leakage.

## Release-claim boundary

This contract narrows #681 into a testable flow. It does not close #665 without operator-approved local Forgejo deployed-stack evidence, and it does not close #591 or claim manual assistive-technology pass without human evidence or an explicit scoped release-owner waiver.

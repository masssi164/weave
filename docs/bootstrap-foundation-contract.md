# Bootstrap foundation contract

Status: enterprise foundation contract for reproducible Weave setup. This page is the product installation boundary; it does not authorize production mutation without an approved plan, and it does not turn the Flutter/member client into a deployment target.

## Executive model

Weave setup has four product lines with strict ownership. Canonical shorthand: **Control Plane = server/backend + admin-console web UI**.

- **Bootstrap / Weave Control** is the onboarding and operations entry point. It discovers the target, drafts the plan, validates preflight, requests approval, dispatches the selected deployment lane, and emits support-safe evidence and handoff refs.
- **Control Plane** is deployed as one unit: **Weave Server** plus **Admin Console**. The server owns policy, provider registry, readiness, authorization, audit, SecretRef/CredentialRef handling, support-safe errors, and domain facades. The Admin Console owns organization/provider/policy/readiness/audit management on top of the same server contract.
- **Provider Stack / Infra** is optional and profile-driven. It may provision self-hosted providers such as identity, files/calendar, chat, boards, or pipeline targets only when the selected profile and approved plan require it.
- **Clients** consume the handoff. Flutter/mobile/desktop/web clients are not deployed by bootstrap; they open an organization URL, invite, or deep link, complete SSO, and read the provider-neutral manifest/capability states.

Short rule: **bootstrap deploys the Control Plane and optional Provider Stack; it does not deploy the member client.** Provider Stack / Infra is optional and profile-driven.

## Component taxonomy

| Component | Deployment status in bootstrap | Required for first enterprise setup | Notes |
| --- | --- | --- | --- |
| Bootstrap / Weave Control | entry point | yes | CLI and/or Control UI can initiate the same plan/apply contract. |
| Weave Server | Control Plane | yes | Separately buildable artifact, deployed through the selected lane. |
| Admin Console | Control Plane | yes | Built/deployed like the server; local Vite is development-only, never the reproducible setup target. |
| Provider Stack / Infra | optional module | profile-dependent | Deploy-new only when selected; attach-existing binds external providers without redeploying them. |
| Flutter/member client | consumer only | no | Uses organization URL/invite/deep link plus SSO and manifest; separate app E2E lane owns client proof. |

## Setup profiles

| Profile | Purpose | Control Plane | Provider Stack | Client |
| --- | --- | --- | --- | --- |
| `local-minimal` | fastest local/operator proof | deploy | none or fixture adapters | not deployed |
| `local-dogfood` | full local self-hosted dogfood | deploy | deploy selected local providers, typically identity/files/chat/pipeline | not deployed |
| `local-lan-dogfood` | LAN-reachable dogfood handoff for physical phone/member testing | deploy | deploy selected local providers by profile | not deployed |
| `external-providers` | customer already has providers | deploy | attach existing providers only | not deployed |
| `hybrid` | mixed deploy-new and attach-existing | deploy | per-domain mode | not deployed |
| `full-selfhosted` | self-hosted reference stack | deploy | deploy approved provider domains | not deployed |

Every profile must produce the same categories of outputs: plan ref, selected setup modes, mutation boundary, approval posture, pipeline/evidence refs, readiness refs, support-safe next actions, and member handoff refs.

## Environment profile parity

The profile contract in `release/bootstrap-foundation/environment-profiles.v1.json` keeps development and production structurally uniform: one deployable shape, with profile variables selecting endpoint class, DNS/TLS posture, provider lane, approval gates, and evidence gates. Local dogfood defaults to the reserved `weave.test` domain and may use `local_lan_host` only as a non-canonical break-glass CA/bootstrap route for physical devices. Production uses the same component shape but requires an operator-owned domain, public DNS, trusted TLS, and forbids LAN host shortcuts. Planning and CI checks must not mutate live infrastructure, DNS, providers, production data, or customer environments.

## Bootstrap state machine

1. `init` — operator chooses the GitHub source repository and the GitHub Actions delivery lane, or a local-shell engineering path that cannot satisfy release evidence.
2. `plan` — bootstrap selects profile and domain modes: `deploy_new`, `attach_existing`, or `hybrid`.
3. `preflight` — Weave Server/Control validates policy, SecretRef/CredentialRef presence, redaction, target readiness, profile compatibility, and member impact.
4. `approval_required` — any mutation waits for explicit approval and clear consequence/rollback/support copy.
5. `apply_dispatched` — bootstrap dispatches only the selected lane and resources named in the approved plan.
6. `pipeline_observed` — pipeline status is tracked through terminal booleans and refs, not raw logs or secrets.
7. `control_plane_ready` — Weave Server and Admin Console readiness are true for the selected target.
8. `provider_stack_ready_or_attached` — optional provider domains are either provisioned or attached with readiness and support-safe next actions.
9. `handoff_ready` — organization URL, invite link, or deep link is emitted for clients.
10. `client_e2e_separate` — client/member evidence is collected separately against the handoff target.

## Required bootstrap commands

The target operator UX is intentionally short:

```bash
tools/weavectl bootstrap plan --profile <profile> --target <provider-lane>
tools/weavectl bootstrap apply --plan <plan-ref>
```

`bootstrap plan` validates the selected `--profile` and `--target` against `release/bootstrap-foundation/bootstrap-profiles.v1.json`. `bootstrap apply` without `--execute` — or with explicit `--dry-run` — is a CI-safe validation that emits an apply receipt but does not mutate infrastructure. Live mutation requires `--execute --approval-ref <approval-ref>`; today only the local shell/local Docker executor may dispatch `infra/weave-workspace/install.sh`, while remote provider lanes stay plan-only until their adapters prove support-safe dispatch. For local LAN dogfood, planning also requires `--lan-host <LAN-IP>` and stores only the endpoint class in support-safe artifacts.

For local dogfood, the final command may wrap defaults, but it must remain equivalent to the plan/apply contract and must not require manual server startup, manual Admin Console Vite startup, raw provider setup, or Flutter deployment.

## Inputs

Plan input may include:

- source repo ref and candidate commit SHA;
- target provider lane: GitHub Actions for repository delivery, with local Docker/Compose or Kubernetes remaining deployment substrates behind that workflow;
- setup profile;
- domain modes for identity, chat, files, calendar, boards, documents, calls, and optional Agent Runtime Control;
- opaque `SecretRef`/`CredentialRef` handles;
- approval policy and rollback/support expectations;
- provider category choices visible only to owner/admin/operator roles.

Plan input must not include raw tokens, passwords, private keys, bearer strings, credential-bearing URLs, or raw provider diagnostic payloads.

## Outputs

Bootstrap output must include support-safe refs only:

- `plan_ref`;
- `profile`;
- `target_provider_lane`;
- `control_plane_deploy_ref`;
- `provider_stack_ref` when applicable;
- terminal booleans: `pipeline_terminal_success`, `weave_server_ready`, `admin_console_ready`, `control_plane_ready`, `provider_stack_ready_or_attached`, `client_bootstrap_handoff_ready`;
- `organization_url`, `invite_url`, or `deep_link` when handoff is ready;
- `support_bundle_ref` and redacted next-action codes.

The bootstrap lane must not emit `weave_client_e2e_passed` or `member_provider_neutral_join_passed`; those belong to the separate client/member E2E lane.

## Non-goals and forbidden shortcuts

- Do not use `npm exec vite` as the reproducible Admin Console deployment target. Vite is development-only.
- Do not require manual backend startup after bootstrap apply.
- Do not make the optional provider stack mandatory when `attach_existing` or `external-providers` is selected.
- Do not deploy Flutter/mobile/desktop clients from bootstrap.
- Do not expose raw OIDC endpoints, provider URLs, SecretRefs, CI/CD targets, bootstrap diagnostics, or provider logs to normal members.
- Do not satisfy attach-existing evidence with a deploy-new pipeline run.
- Do not claim production readiness, provider migration safety, or public release readiness from contract-only evidence.

## Enterprise acceptance bar

A bootstrap implementation is acceptable only when:

- one plan/apply path can deploy or attach the selected target from a clean checkout;
- Control Plane means server plus Admin Console, built and deployed through the selected lane;
- infra/provider deployment is profile-driven and optional;
- secrets are represented only by SecretRef/CredentialRef handles;
- evidence is support-safe and file/ref based;
- client/member join proof runs separately against the handoff target;
- the final operator instruction can be expressed as one or two commands.

## Related contracts

- [Weave Control bootstrap-to-client contract](weave-control-bootstrap-to-client-contract.md)
- [Control-plane infra bootstrap](control-plane-infra-bootstrap.md)
- [Admin-provisioned first use](admin-provisioned-first-use.md)
- [Admin-Suite readiness and setup contract](admin-suite-readiness-setup-contract.md)

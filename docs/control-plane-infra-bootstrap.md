# Control-plane infra bootstrap

This page describes the provider-stack implementation slice for self-hosted/default bootstrap. At
the canonical product setup boundary, [Bootstrap foundation](bootstrap-foundation-contract.md)
defines the Control Plane as Weave Server plus Admin Console, while
**Provider Stack / Infra is optional and profile-driven**. This page proves only the
provider-stack implementation slice; the
Admin Console deployment target remains outside this slice and no complete Control Plane runtime
claim follows from it. Keycloak is the mandatory IDM backbone and OAuth authority. LDAP, Active
Directory, and external OIDC/SAML identity systems connect upstream through Keycloak and do not
replace the Weave identity boundary. The Sprint 30 bootstrap-to-client acceptance contract is
[Weave Control bootstrap-to-client contract](weave-control-bootstrap-to-client-contract.md).

- central Keycloak realm as the mandatory identity, organization, session, and OAuth foundation;
- backend-owned provider registry, readiness, policy, audit, and SecretRef seams;
- a separate Organization/Admin Console deploy target (`admin.<tenant-domain>`), not the member client;
- optional external-provider placeholders for Microsoft-heavy or other existing organization stacks.

## Setup modes

Weave Control models `deploy_new`, `attach_existing`, or `hybrid` only for collaboration-provider domains. Identity always terminates at Keycloak. `deploy_new` may mutate only resources named in an approved plan. `attach_existing` binds an existing collaboration provider without redeploying it. `hybrid` combines those modes by domain and fails closed for unsupported combinations. Weave Server remains the Java domain facade, policy, readiness, audit, and evidence brain for this contract.

The repo-local runtime bridge is `tools/weavectl bootstrap plan/apply`. Apply is dry-run/validate-only unless the operator passes `--execute --approval-ref <approval-ref>`. In this slice, executable local shell plans dispatch `infra/weave-workspace/install.sh`; remote CI/GitOps lanes emit support-safe plan refs until their adapters prove dispatch, readiness, and rollback evidence.

## Generated artifacts

- `specs/weave-specs.lock.json` pins the canonical `weave-specs:main` corpus. Its
  `contracts/examples/keycloak-desired-state.valid.json` is rendered with the environment
  overlay into the namespace-private
  `.generated/<namespace>/keycloak/desired-state.json`; no second repo-local realm contract is
  maintained.
- `infra/weave-workspace/provider-profiles/sovereign-default.json` maps recommended self-hosted provider categories into backend adapter keys and SecretRefs.
- `infra/weave-workspace/provider-profiles/microsoft-hybrid-placeholder.json` records an external-provider adoption shape without enabling live credentials.

## SecretRefs

Profiles and support bundles may name `secretref://weave/provider/...` references. They must not contain raw client secrets, tokens, passwords, signing keys, JWT secrets, or provider error dumps.

## Smoke contract

`smoke-test.sh` and `operator-check.sh` verify:

- Keycloak issuer discovery is reachable;
- backend manifest/provider routes are reachable through Weave APIs;
- `/admin/control-plane` rejects member tokens with `403`;
- support-bundle public env output includes only no-secret admin/profile metadata.

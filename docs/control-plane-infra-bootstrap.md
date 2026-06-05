# Control-plane infra bootstrap

This page describes the provider-stack implementation slice for self-hosted/default bootstrap. The canonical product setup boundary is [Bootstrap foundation](bootstrap-foundation-contract.md): Control Plane = Weave Server + Admin Console, while Provider Stack / Infra is optional and profile-driven. Weave's recommended self-hosted/default organization bootstrap is provider-neutral but ships with a sovereign default profile. The Sprint 30 bootstrap-to-client acceptance contract is [Weave Control bootstrap-to-client contract](weave-control-bootstrap-to-client-contract.md).

- central Keycloak realm as the default identity broker and IDM foundation;
- backend-owned provider registry, readiness, policy, audit, and SecretRef seams;
- a separate Organization/Admin Console deploy target (`admin.<tenant-domain>`), not the member client;
- optional external-provider placeholders for Microsoft-heavy or other existing organization stacks.

## Setup modes

Weave Control must model setup as `deploy_new`, `attach_existing`, or `hybrid` per domain. `deploy_new` may mutate only resources named in an approved plan. `attach_existing` binds an existing customer/provider domain without redeploying it. `hybrid` combines those modes by domain and fails closed for unsupported combinations. Provider Stack / Infra is optional for `attach_existing` and external-provider profiles; this infra tree becomes active only when the approved plan selects a deploy-new or self-hosted provider slice. Weave Server remains the Java domain facade, policy, readiness, audit, and evidence brain for this contract; no bootstrap slice mandates a rewrite.

## Generated artifacts

- `infra/weave-workspace/keycloak/weave-dev-realm-contract.json` documents the expected dev/demo realm shape without raw secrets.
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

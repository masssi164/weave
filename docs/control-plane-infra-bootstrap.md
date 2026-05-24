# Control-plane infra bootstrap

Weave's recommended self-hosted/default organization bootstrap is provider-neutral but ships with a sovereign default profile:

- central Keycloak realm as the default identity broker and IDM foundation;
- backend-owned provider registry, readiness, policy, audit, and SecretRef seams;
- a separate Organization/Admin Console deploy target (`admin.<tenant-domain>`), not the member client;
- optional external-provider placeholders for Microsoft-heavy or other existing organization stacks.

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

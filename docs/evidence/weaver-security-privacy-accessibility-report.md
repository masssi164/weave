# Agent Runtime Control security, privacy, and accessibility evidence

Status: active v2 implementation evidence. The earlier fixture-only Weaver
Runtime Factory and synthetic MCP customization claims are retired.

## Security boundary

- Keycloak is the entitlement and workload-identity authority. A runtime cell
  receives one confidential OIDC client using `private_key_jwt`; human tokens
  and shared service accounts are rejected by the MCP boundary.
- RuntimeProfile v2 is a short-lived, signed desired-state projection. It is not
  an authorization grant and there is no v1 reader.
- MCP accepts only RFC 9068 access tokens with the exact MCP audience and
  service-account binding, performs Standard Token Exchange v2, and asks the
  backend to revalidate the current cell/profile/entitlement context.
- The active MCP catalog contains only `files.search` and the canonical file
  resource over the existing Weave WebDAV projection. RuntimeProfile content,
  provider configuration, and historical fixtures cannot manufacture
  additional tools.
- Agent Runtime Control owns cell lifecycle, fenced reconciliation, workload
  credential revocation, and support-safe audit correlation. Collaboration
  domains retain content authority and fine-grained authorization.

## State and privacy boundary

- Cells have zero durable local bytes. Runtime-internal state is stored in
  PostgreSQL as AES-256-GCM encrypted, content-addressed chunks and generations;
  data-encryption keys are wrapped by an operator-mounted AES-KWP key.
- Generation commits use compare-and-swap and a fencing epoch. Restore verifies
  authenticated metadata and chunk hashes before returning plaintext.
- `DELETE_RUNTIME_STATE_ONLY` removes external runtime state, the per-cell
  Keycloak client, and its private credential. It deliberately does not delete
  canonical WebDAV/Files content.
- Support and admin projections use opaque `personRef`, cell/profile/workspace
  references, stable states, and audit refs. They omit raw Keycloak objects,
  tokens, private keys, plaintext state, provider payloads, and `openclaw.json`.

## Accessibility evidence

- The Organization/Admin Console exposes Agent Runtime Control as a labelled
  region with a level-two heading, labelled person reference and reason fields,
  text state chips, keyboard-operable lifecycle buttons, and an explicit
  confirmation checkbox for runtime-state deletion.
- Destructive and revocation actions are not color-only: button text, warning
  copy, desired/observed states, and the audit reference remain available to
  assistive technology.
- Operator mode may inspect support-safe state but cannot invoke mutation
  controls. Normal members receive only provider-neutral capability states.

## Executable evidence

- `./gradlew :server:test`
- `./gradlew :weave-mcp-server:test`
- `cd admin-console && npm run ci`
- `bash infra/weave-workspace/tests/weaver-runtime-lifecycle-contract-test.sh`
- `bash infra/weave-workspace/tests/weave-mcp-tool-contract-test.sh`
- `python3 tools/domain_registry_check.py`

Live local-stack evidence must additionally prove per-cell client creation,
positive token exchange/current-context admission, negative human/shared-client
rejection, runtime-state deletion, client revocation, and convergent Compose plus desired-state
apply. A green contract fixture alone is not a release-ready runtime claim.

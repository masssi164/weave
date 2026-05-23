# Deploy weave-backend and finalize the Keycloak contract

## Problem

`weave-infra` already defines a `weave-backend` Keycloak client, but the backend is not deployed by the stack and the auth contract is incomplete:

- no backend runtime/service exists yet
- no backend base URL is published
- no final decision exists for audience mapping vs token exchange

## Proposal

- Add a backend runtime to the infrastructure stack, exposed at the canonical API base `https://api.<tenant_domain>/api`
- Promote the backend client model from placeholder to a documented contract:
  - issuer URI
  - backend base URL
  - required audience or token-exchange path
  - optional service-account credentials for server-owned workflows
- Wire the backend runtime config from Terraform outputs/environment variables instead of hardcoded URLs

## Acceptance criteria

- `weave-backend` is deployable from `weave-infra`
- the stack exports the backend base URL and issuer URI in a stable way
- the Keycloak setup clearly documents whether mobile tokens call the backend directly, require an audience mapper, or require token exchange
- confidential/service-account credentials exist only for server-owned backend workflows

# Establish the backend API and OpenAPI contract

## Problem

The backend started as a direct-proxy spike for Matrix and Nextcloud, but that boundary is too broad for the current stack and did not compile cleanly.

## Proposal

- Keep the backend as a JWT-protected product API
- Define the first stable endpoints and publish them through OpenAPI
- Keep the surface small and explicit:
  - identity/session introspection
  - workspace capabilities
  - server-owned orchestration endpoints

## Acceptance criteria

- a versioned REST API namespace exists
- OpenAPI documentation is generated or maintained from code
- the API contract is consumable by `weave`
- Matrix and Nextcloud direct-user flows are not reintroduced as blind proxy endpoints

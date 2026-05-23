# Implement server-owned integrations only

## Problem

The backend should help slim the client, but only for workflows that truly benefit from a backend. Reusing frontend user tokens directly against Matrix and Nextcloud would create brittle coupling and unclear trust boundaries.

## Proposal

- Reserve backend integrations for server-owned workflows such as:
  - Keycloak admin/provisioning calls
  - Nextcloud pre-provisioning or admin APIs
  - Matrix automation/bot messages or background tasks
- Use the right auth mechanism per integration:
  - service accounts where the backend is the actor
  - token exchange only when there is a real on-behalf-of requirement and the infrastructure contract supports it
- Add outbound client abstractions only after the credential model is documented

## Acceptance criteria

- each downstream integration has a documented actor model: user, backend service account, or exchanged token
- no backend endpoint assumes that an app bearer token is automatically valid for Matrix or Nextcloud operations
- integration clients are driven by configuration properties, not hardcoded hosts or secrets
- automated Matrix messages use the appropriate event/message semantics for bot-like behavior

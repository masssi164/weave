# Enable local TLS and align public hostnames

## Problem

The Flutter app requires:

- an HTTPS OIDC issuer
- HTTPS Nextcloud access for the live integration flow

Older `weave-infra` revisions defaulted to an HTTP ingress on `:8090` and service-specific hosts that did not match the final product gateway topology.

## Proposal

- Add local TLS termination for the browser-facing stack through Caddy with generated local certificates
- Align public hostnames with the app contract:
  - `<tenant_domain>` for the product shell and files/calendar product routes
  - `api.<tenant_domain>/api` for the backend API base
  - `auth.<tenant_domain>` for the OIDC issuer
  - `matrix.<tenant_domain>`
  - `files.<tenant_domain>` for canonical Nextcloud URL
- Export the final browser-facing URLs as Terraform outputs or generated install metadata for client/backend consumers

## Acceptance criteria

- the stack can be reached over HTTPS with the configured hostnames
- Nextcloud uses `files.<tenant_domain>` as the canonical Nextcloud URL while the product gateway owns the Weave `/files` product route
- installer output shows the HTTPS URLs that the client and backend should consume
- local bootstrap docs include the trusted certificate/dev CA step needed for mobile or desktop testing

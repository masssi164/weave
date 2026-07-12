# Identity environment parity

Dogfood and production run one identity implementation. Both use the same version-pinned Weave Keycloak image, Organizations invitation lifecycle, `/join` completion route, organization discovery, and OIDC Authorization Code with PKCE. There is no dogfood-specific login branch and `main` does not define a second authentication flow.

Only operator inputs differ:

| Input | Dogfood | Production |
| --- | --- | --- |
| SMTP | Docker-internal Mailpit `weave-mailpit:1025` | Transactional SMTP configured through private deployment inputs |
| Mail inspection | `https://mail.<tenant-domain>` restricted to private CIDRs | No Mailpit deployment or inbox route |
| DNS and TLS | Private DNS and locally trusted Weave CA | Public DNS and publicly trusted TLS |
| Organization/sender | Deployment variables | Deployment variables |
| Distribution | Signed dogfood/TestFlight build | Release distribution |

Keycloak owns invitation tokens, expiry, resend, revoke, registration, credentials, email verification, and organization membership. The installed `weave-identity-events` provider does not replace those functions. It emits only a signed, secret-free `organization_membership_added` fact to the Docker-internal Weave endpoint so temporary role/group provisioning intent can be reconciled idempotently. The extension and Keycloak versions are upgraded together.

## iPhone Mailpit gate

Mailpit is deliberately unauthenticated and therefore private-LAN-only. SMTP is never published to the host or LAN. Before using Safari on a dogfood iPhone:

Mailpit stores up to 500 recent messages in the `weave_mailpit_data` Docker volume. Ordinary container replacement and non-destructive teardown preserve this inbox. A confirmed destructive reset deletes it because captured activation links are sensitive runtime data; it is intentionally excluded from identity backup and support-bundle evidence.

1. Configure the iPhone Wi-Fi network to resolve `mail.<tenant-domain>` to the dogfood host.
2. Install the Weave local CA and enable full trust under **Settings → General → About → Certificate Trust Settings**.
3. Run `./weave-workspace/iphone-mailpit-smoke.sh` on the host.
4. Open the printed HTTPS URL in Safari. The default non-standard-port URL is `https://mail.weave.test:44443`.
5. Open the Keycloak invitation from Mailpit. Do not copy the activation URL into logs, QR codes, support bundles, or documentation.

An HTTP `403` means the phone address is outside `TF_VAR_mailpit_allowed_cidrs`. A certificate warning means trust was not enabled or the certificate does not cover the configured hostname. A DNS error must be fixed in the private DNS service; an IP-address URL is not a supported substitute because it breaks the TLS and issuer contract.

This page is an implementation projection of `WEAVE-DOMAIN-IDENTITY-IDM` and `ADR-0005-keycloak-identity-system-of-record` in the pinned specification corpus.

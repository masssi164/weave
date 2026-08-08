# Identity environment parity

`dev`, `dogfood`, `e2e`, and `prod` run the same identity model: the approved downstream Keycloak
26.7 runtime, one generated secret-free realm baseline, the bounded Server-owned post-import
migration, native organization roles, and OIDC Authorization Code with PKCE. There is no
environment-specific login branch and no general identity reconciler. Delivery lanes never select
an application environment.

Only reviewed operator coordinates differ:

| Input | Dogfood | Production |
| --- | --- | --- |
| SMTP | Docker-internal Mailpit with implicit TLS | Transactional SMTP through private deployment inputs and Keycloak File Vault |
| Mail inspection | Private-CIDR `https://mail.<tenant-domain>` | Operator-owned mailbox; no Mailpit route |
| DNS and TLS | Private DNS and locally trusted Weave CA | Public DNS and publicly trusted TLS |
| Organization/sender | Reviewed dogfood environment | Reviewed production environment |
| Images | Exact candidate mapping | Approved published digests |

Dogfood has no SMTP shared secret. Production supplies `WEAVE_SMTP_USERNAME` as a reviewed
non-secret coordinate and the SMTP password as a private mode-`0600` file mounted only into
Keycloak File Vault; rendered production realm JSON contains only the
`${vault.smtp-password}` reference.

Keycloak owns invitation tokens, expiry, resend, revoke, registration, credentials, email
verification, and organization membership. Weave does not install a custom Keycloak provider JAR
to make login correct. After authentication, the server-owned session-reconciliation use case
checks current organization entitlement in the fixed Keycloak authority; when it reports changed
access, the client performs
exactly one standard refresh-token grant before workspace bootstrap.

## Dogfood iPhone Mailpit gate

Mailpit is required persistent dogfood infrastructure for the initial invitation and email
verification flow. It is unauthenticated and therefore private-LAN-only. SMTP is not published to
the host or LAN. Mailpit stores recent messages in the persistent dogfood volume. Captured
activation links are sensitive runtime data and are excluded from backups and support bundles.

Before using Safari on a physical iPhone:

1. Resolve `mail.<tenant-domain>` to the dogfood host through private DNS.
2. Install the Weave local CA and enable full trust under **Settings → General → About →
   Certificate Trust Settings**.
3. Run `./weave-workspace/iphone-mailpit-smoke.sh` on the host.
4. Open the printed HTTPS URL in Safari. The default dogfood URL is
   `https://mail.weave.test:44443`.
5. Open the current Keycloak invitation. Never copy its action URL into logs, QR codes, support
   bundles, screenshots, or documentation.

An HTTP `403` means the phone is outside the reviewed private CIDR. A certificate warning means
the CA trust or SAN coverage is wrong. A DNS error must be fixed in private DNS; an IP-address URL
is not a supported substitute because it breaks TLS and issuer validation.

This page projects `WEAVE-PLATFORM-IDENTITY-SECURITY`, the fixed Keycloak authority contract, and
the current runtime composition ADR from the pinned specification corpus.

# Identity environment parity

`test` and `prod` run the same identity implementation: the approved stock Keycloak OCI image,
the same canonical desired state, rootless one-shot Identity Ops, native organization roles, and
OIDC Authorization Code with PKCE. There is no dogfood-only login branch and no production-only
realm model. `dogfood` and `main` are Git delivery lanes, not runtime/application profiles.

Only reviewed operator coordinates differ:

| Input | Test | Production |
| --- | --- | --- |
| SMTP | Docker-internal Mailpit | Transactional SMTP through private deployment inputs |
| Mail inspection | Private-CIDR `https://mail.<tenant-domain>` | No Mailpit route |
| DNS and TLS | Private DNS and locally trusted Weave CA | Public DNS and publicly trusted TLS |
| Organization/sender | Reviewed test environment | Reviewed production environment |
| Images | Exact candidate mapping | Approved published digests |

Keycloak owns invitation tokens, expiry, resend, revoke, registration, credentials, email
verification, and organization membership. Weave does not install a custom Keycloak provider JAR
to make login correct. After authentication, the server-owned session-reconciliation use case
checks current organization entitlement in the fixed Keycloak authority; when it reports changed
access, the client performs
exactly one standard refresh-token grant before workspace bootstrap.

## iPhone Mailpit gate

Mailpit is unauthenticated and therefore private-LAN-only. SMTP is not published to the host or
LAN. Mailpit stores recent messages in the persistent test volume. Captured activation links are
sensitive runtime data and are excluded from backups and support bundles.

Before using Safari on a physical iPhone:

1. Resolve `mail.<tenant-domain>` to the test host through private DNS.
2. Install the Weave local CA and enable full trust under **Settings → General → About →
   Certificate Trust Settings**.
3. Run `./weave-workspace/iphone-mailpit-smoke.sh` on the host.
4. Open the printed HTTPS URL in Safari. The default test-profile URL is
   `https://mail.weave.test:44443`.
5. Open the current Keycloak invitation. Never copy its action URL into logs, QR codes, support
   bundles, screenshots, or documentation.

An HTTP `403` means the phone is outside the reviewed private CIDR. A certificate warning means
the CA trust or SAN coverage is wrong. A DNS error must be fixed in private DNS; an IP-address URL
is not a supported substitute because it breaks TLS and issuer validation.

This page projects `WEAVE-PLATFORM-IDENTITY-SECURITY`, the fixed Keycloak authority contract, and
the current runtime composition ADR from the pinned specification corpus.

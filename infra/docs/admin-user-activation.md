# Dogfood owner and member invitations

Dogfood keeps invitation delivery inside the private deployment through persistent Mailpit. Keycloak
Organizations owns invitation acceptance and browser activation; Weave Server owns every dynamic
human-lifecycle mutation.

## Initial owner

After a Fresh Start has created the empty realm and the normal Server is healthy, run the protected
**Dogfood Owner Bootstrap** workflow once. It invokes:

```bash
cd infra/weave-workspace
./compose.sh dogfood bootstrap-owner \
  --request-file /absolute/private/owner-request.json \
  --evidence-file /absolute/private/support-safe-evidence.json
```

The request is a mode-0600 JSON file containing exactly `displayName`, `email`, and
`idempotencyKey`. The command temporarily recreates only Weave Server with a fresh file-mounted
credential, creates or exactly replays the first owner Organizations invitation, observes only the
Mailpit recipient summary, and unconditionally restores the canonical Server. Success evidence must
prove the request anchor exists and the credential, mount, environment value, and bootstrap route
are absent.

Open the resulting activation message only through the private Mailpit UI at
`https://mail.weave.test:44443`. Never copy its action URL into logs, artifacts, QR codes, app
storage, support bundles, or GitHub comments.

## Later members

Once the owner is active, create, list, resend, or revoke invitations through the Admin Console or
the authenticated Weave Server `/api/admin/organizations/{organizationId}/invitations` API. Browser
JavaScript never calls the Keycloak Admin API. Infrastructure has no member writer, no persistent
realm-admin credential, and no direct subject replacement path.

Expired or pending invitations use the same Server-owned resend/revoke lifecycle. An active account
uses Keycloak password, passkey, and session recovery. If persistent identity state is lost, restore
the Keycloak database from an integrity-checked backup or approve a whole-generation Fresh Start;
do not recreate an individual subject.

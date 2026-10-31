# Managed LDAP fixtures

These are standard LDIF sources. They contain no password or password hash. The suffix in
the checked-in examples is `dc=weave,dc=test`; provisioning resolves it from the reviewed
`WEAVE_TENANT_DOMAIN` without changing login names. No custom LDAP schema or human IAM DSL.

| Source | Purpose | Applied by |
| --- | --- | --- |
| `00-base.ldif` | Directory containers and stock password policy | Initial managed dependency setup |
| `05-bind.ldif` | Separate `keycloak` service identity | Initial setup with its private bind input |
| `10-automation.ldif` | Ordinary `weave-test-a` and `weave-test-b` users | Automated dev/Dogfood/E2E setup |
| `15-groups.ldif` | Flat application-role groups | Initial setup only |
| `20-personal.ldif` | Personal ordinary user `massimotter` | Protected physical iPhone preparation only |
| `30-personal-membership.ldif` | Initial personal member assignment | Same explicit physical preparation |

`massimotter` is not an automation actor. Neither normal startup nor isolated E2E creates this
entry or reads `DOGFOOD_USER_SECRET`. The existing `Prepare Human Test` workflow supplies that
repository secret only to the bounded personal-provisioning step, after qualification. The
helper removes the value from its environment before child processes and sends it to the
verified running LDAP container through stdin. It performs no LDAP bind, OIDC token request,
browser login or app-session injection as the personal user. The first personal sign-in is
on the iPhone using login **massimotter** and the password chosen in **DOGFOOD_USER_SECRET**.

OpenLDAP's `slappasswd` Argon2id module hashes private initial inputs through stdin. Directory
data and configuration persist in their own Compose volumes. The service binds only inside
the Compose network over LDAPS and holds only its dedicated leaf/key and the existing public
CA. The CA signing key and gateway key never enter the LDAP container or runtime artifact.
The read-only Keycloak bind can read required identity/group attributes, never password hashes
or user/group writes. LDAPI with local SASL/EXTERNAL is the operator boundary; there is no
network root password. Production provisions no demonstration account.

Repeated preparation verifies its private entryUUID bindings and leaves accepted passwords,
directory profile changes and revoked memberships intact. Changing the GitHub secret alone
does not rotate an initialized account. An unknown existing entry requires explicit migration;
matching uid/mail is not ownership. Interrupted initial creation has private durable ownership
records; membership retry uses the original group entryCSN and an LDAP assertion control so it
cannot restore a grant revoked after the remote commit.

The hosted runtime bundle carries the pinned ARM64 LDAP image as a digest-verified image
archive. Mac delivery loads it; it never compiles it or publishes a new public registry
package. The payload includes these public sources, never a directory database, runtime
password/hash, private key, personal secret or operator state.

## Local provider verification

With the pinned OpenLDAP 2.6.10 tools installed:

```sh
python3 infra/weave-workspace/tests/ldap_provision_live_test.py -v
python3 infra/weave-workspace/tests/ldap_tls_test.py -v
```

The provider tests use isolated temporary directories, their own CA and private random
credentials. They never authenticate as the personal user. This execution environment denies
AF_UNIX sockets, so the local provider tests use a separate private test-only operator over
verified LDAPS; this does not qualify the production LDAPI transport. Full Compose/Keycloak
and physical evidence remain separate requirements.

## Coordinated delivery boundary

This foundation is part of #1781/#1782, not a standalone activation. Accepted corpus #175,
native realm/federation #1783, backend/client cleanup #1784/#1785 and personal iPhone actor/grant
handoff #1786 must agree before promotion. The old physical A/B selector is not the personal
handoff and must not be dispatched as one. Existing operator/data identities require explicit
one-to-one migration; these fixtures do not replace them or create an additional administrator.
The current branch must remain draft until that combined boundary is complete and qualified.

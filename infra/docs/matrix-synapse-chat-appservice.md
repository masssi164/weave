# Matrix/Synapse southbound Chat Application Service

This is an optional interoperability/provider profile. `weave-native` is the Server composition
default and requires no Application Service credential, Synapse, or MAS. The wiring below applies
only when an environment explicitly selects `WEAVE_CHAT_PROVIDER=matrix-synapse`.

The Weave client talks only to the Matrix Client-Server facade on the API
origin. Spring authenticates the Keycloak member, enforces canonical
authorization, commits canonical Chat state through the JPA relational port, and then calls Synapse
through the `matrix-synapse` southbound adapter. Synapse is a replaceable
provider representation, not the northbound product boundary.

Keycloak remains the human identity authority. MAS continues delegated human
Matrix authentication for provider/operator use. Neither Keycloak member tokens
nor MAS member sessions are used as the backend service credential. The backend
and Synapse instead share one narrowly registered Matrix Application Service:

- registration ID `weave-chat-synapse`;
- private callback
  `http://weave-backend:8080/api/internal/chat/matrix/appservice`;
- independent `as_token` and `hs_token` values;
- exclusive opaque `_weave_` virtual-user and alias namespaces;
- `rate_limited: true`;
- no room namespace, administrator token, registration secret, signing key, or
  broad wildcard namespace.

The northbound member identity remains the immutable tenant, Keycloak issuer,
and `user:<sub>` tuple. The isolated stack deliberately configures
`user:<preferred_username>` only as its deterministic Context/ReBAC policy
principal; it is resolved from the same validated JWT during facade
registration and is never used as the canonical Chat or provider-mapping key.
The provider proof therefore registers authenticated author and collaborator
sessions before the author invites the collaborator.

The callback and every nested transaction/query path are explicitly denied by
public Caddy with `404`. Synapse reaches the callback only through the private
Docker network.

## Runtime files and backend contract

`secrets-init` preserves the two stable tokens as separate mode-`0600` files below
`WEAVE_SECRET_ROOT`. The deterministic Compose renderer stages them into the private
`weave_matrix_chat_appservice_runtime` volume, and
mounts that volume read-only at `/run/weave-chat-appservice` in only
`weave-backend` and `weave-synapse`.

The backend receives only file references and support-safe adapter settings:

```text
WEAVE_CHAT_PROVIDER=matrix-synapse
WEAVE_CHAT_MATRIX_INTERNAL_BASE_URL=http://weave-synapse:8008
WEAVE_CHAT_MATRIX_SERVER_NAME=<configured Matrix host>
WEAVE_CHAT_MATRIX_APPSERVICE_ID=weave-chat-synapse
WEAVE_CHAT_MATRIX_VIRTUAL_USER_PREFIX=_weave_
WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN_FILE=/run/weave-chat-appservice/as-token
WEAVE_CHAT_MATRIX_APPSERVICE_HS_TOKEN_FILE=/run/weave-chat-appservice/hs-token
```

The token values are never placed in container environment variables, Compose interpolation,
logs, public app configuration, readiness payloads, or support bundles. Rendering and staging
occur inside the private file boundary and never print token content.

The disposable collaboration lane adds a separate proof boundary. Identity
preparation creates a 384-bit run-scoped credential as a mode-`0600` host file;
only the proof caller can read that host file, and the isolated Compose overlay bind-mounts it
read-only into the isolated backend at `/run/weave-chat-e2e-proof/token`. The
credential value never becomes a Compose variable or model value. It is distinct
from both Application Service tokens and is never mounted into Synapse.

Only that isolated backend receives:

```text
WEAVE_E2E_STACK_SCOPE=isolated
WEAVE_CHAT_E2E_PROOF_ENABLED=true
WEAVE_CHAT_E2E_PROOF_TOKEN_FILE=/run/weave-chat-e2e-proof/token
WEAVE_CHAT_E2E_PROOF_RUN_ID=<exact disposable run ID>
```

Persistent dogfood and production receive only the default
`WEAVE_CHAT_E2E_PROOF_ENABLED=false`; the token file, run binding, mount, and
isolated scope are absent. Public Caddy returns `404` for
`/api/internal/e2e/chat/provider-proof` on both product and API hosts.

## Install and rotation

Ordinary installation preserves both files. A non-local operator installs them under the named
`WEAVE_SECRET_ROOT` paths `matrix-appservice-as-token` and `matrix-appservice-hs-token`.
Dev/isolated initialization creates independent high-entropy values only when the files are absent.

Rotation is one protected, coordinated operation:

1. take a private backup;
2. set two new independently random values in the operator secret authority;
3. run `install.sh` once;
4. let installation restart `weave-backend` and `weave-synapse` after staging;
5. run operator and provider collaboration checks;
6. retain the prior private backup until restore smoke succeeds.

Do not rotate one token alone. Do not copy either value into a command argument,
terminal transcript, issue, workflow output, or evidence artifact.

## Backup, restore, and diagnostics

`backup.sh` includes the runtime projection in `matrix-appservice.tgz` and the source SecretRefs in
the private `private-config-secrets.tgz` artifact. Canonical Chat, provider mappings,
outbox/ledger data, and Synapse state remain in the PostgreSQL dump; Synapse
media/local data remains in `synapse-data.tgz`. Backup artifacts contain
secrets and member data and must stay encrypted/operator-readable only.

After a separately approved Compose/control-store restore rehearsal, run:

```sh
bash weave-workspace/restore-smoke.sh /private/path/to/weave-backup
```

The live restore check verifies that both containers have the runtime volume
mounted read-only, Synapse can read the narrow rate-limited registration, and
the backend can read two distinct non-empty token files. It never prints token
content.

`support-bundle.sh` reports only the support-safe facts that the selected adapter
is `matrix-synapse`, canonical storage is relational, and the Application Service is
configured. It excludes the registration, tokens, internal URL, provider
identifiers, callback bodies, and raw service logs.

## Isolated provider proof operations

The following controls are for the disposable isolated E2E runner only. They
must not target persistent dogfood.

Restart continuity uses the stable service names:

```sh
docker restart weave-backend
docker restart weave-synapse
```

A bounded provider-outage test arms recovery first, stops only Synapse, observes
the canonical pending/invisible operation, and always restores Synapse. A
running container is not treated as provider readiness: recovery first waits
for Synapse's listener health endpoint and then proves the authenticated
Application Service path through the Weave facade within a bounded window.
The backend's fail-closed exponential backoff remains active:

```sh
trap 'docker start weave-synapse >/dev/null 2>&1 || true' EXIT
docker stop weave-synapse
# Run the bounded canonical write/visibility assertion here.
docker start weave-synapse
trap - EXIT
```

A runner-private proof caller authenticates only to the isolated proof surface
through the loopback backend port. Before replay it polls the read-only
`GET /api/internal/e2e/chat/provider-proof/callback-replay/readiness` contract
for at most 90 seconds. Readiness exposes only a boolean, stable code, contract
version, and `supportSafe: true`; it never returns the captured transaction ID
or payload. The caller then uses
`POST /api/internal/e2e/chat/provider-proof` for provider evidence and invokes
the replay trigger exactly once after a genuine encrypted callback is captured.
The JSON request includes the exact run ID and pre-existing canonical
conversation and actor references. The outsider remains outside the authorized
workspace and has no provider mapping or membership. The proof verifies that
absence directly; it does not manufacture an outsider actor, room, membership,
event, or credential just to simplify a denial assertion.

Direct provider readback returns only support-safe hashes, counts, bounded
status values, timestamps, ages, and correlation references. It never returns
the proof token, Application Service tokens, provider identifiers, provider
URLs, raw callback bodies, encrypted envelopes, or member content. The
`hs_token` remains directional: it authenticates simulated Synapse callback
replay only and grants no provider-proof access.

Application Service retry identity is the authenticated, homeserver-generated
transaction ID, as required by the Matrix Application Service API. The first
delivery retains a canonical semantic digest as provenance. A retry does not
compare its freshly serialized body with that digest: Synapse reconstructs
queued transactions and can recalculate presentation fields such as
`unsigned.age` and the client-v1 top-level `age` copy, including inside
redaction metadata. Rejecting those changes blocks Synapse's ordered
Application Service queue. Completed transaction IDs are acknowledged without
reprocessing, while interrupted transactions resume through the existing
per-event identifier, mapping, authorization, content, and quarantine checks.
Synapse also emits the expected `m.room.canonical_alias` state event when the
provider-neutral create operation supplies its reserved alias intent. That
state event is ledgered and ignored like the other supported room-creation
state; it must not quarantine or degrade an otherwise acknowledged room
mapping. Unknown state types continue to fail closed.

Correlation evidence is phase-aware: the outage snapshot names only the two
events committed before retry, while post-retry and restart snapshots name all
three. A proof request whose expected correlation count differs from the
canonical committed-event count fails closed instead of manufacturing an
"exact" result.

Isolated cleanup removes the run-scoped proof token and private provider runtime
together with the rest of the isolated namespace. The token is explicitly
excluded from private backup archives and support bundles; cleanup evidence
contains only support-safe zero-count and destroyed-state assertions.

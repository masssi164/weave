# Native Flutter acceptance execution for #1533

Status: implementation evidence contract for #1474, #1475, #1479, and #1480. The
accepted product behavior remains governed by the pinned Weave Specification
Corpus and its 2026-10 consolidation release contract.

The `Full Compose E2E` workflow owns this lane. It runs the exact candidate
source on a logged-in macOS runner with Xcode, Flutter, Java, Docker, a valid
Apple Development signing identity, Accessibility permission for the runner,
and a preconfigured native acceptance TLS CA. The runner's personal keychain
must not be exposed to unreviewed fork code. GitHub must require maintainer
approval for every external contributor's workflow; the required job routes
fork pull requests to a GitHub-hosted runner and fails before checkout.

For a trusted candidate, `testApp` creates a fresh disposable Compose stack,
waits for Keycloak, Server, PostgreSQL, Files, Calendar, and Matrix readiness,
creates dedicated member/admin/workload identities and product fixtures, and
executes the existing Gherkin-mapped Java/OpenClaw and native Flutter journeys.
The native runner builds a signed macOS app, drives the actual system-browser
OIDC/PKCE callback, checks Files/Calendar/Matrix behavior and denial, restarts
the app and core services, checks restoration and revocation, and fails if a
required phase cannot run. The stack is torn down by the existing `testApp`
cleanup. CI uploads only support-safe evidence; native build and Flutter test
logs remain local and private when they may contain sensitive diagnostics.

An exact candidate is accepted only when the required `Full Compose E2E` job
and the two Flutter processes finish successfully, with source, spec, client,
provider, and IdP versions recorded. A local pass, build-only run, skipped
integration test, or unavailable signing identity does not count as CI proof.
The macOS lane does not assert iOS or Android device compatibility.

The expired-credential negative retains the bearer returned by the real native
PKCE login only in the run-isolated Keychain namespace. The restored process
reads and deletes that probe entry, waits for its actual signed expiry plus
the validator's clock-skew allowance, and refreshes the normal session through
production AppAuth. Before any room leave or logout, the expired bearer must
receive HTTP 401 from the generated User Files operation and Matrix facade,
while the fresh bearer with the same current member/device grant succeeds.
The probe does not inject a token into the application's authenticated state,
alter claims or IdP lifetimes, or write a bearer to checkpoint files or evidence.
A bounded expiry wait, missing probe or unavailable assertion fails the native
lane. Separate real-signature/JWKS decoder tests verify expired User, Admin and
MCP token rejection; successful fresh tokens with the same claims are controls.

Logout must also reject a retained, still-unexpired refreshed member bearer at
the generated User Files operation, as well as Matrix. Local credential/cache
clearing alone is insufficient for the accepted identity-lifecycle scenario.
The normal User decoder reuses the existing durable, organization/issuer/subject/
OIDC-session-scoped revocation state. It must not create another session ledger
or make contract generation depend on loading the native Matrix runtime.

The Matrix member ID used by the native client and facade is the same stable,
opaque account reference derived from the validated issuer and full subject.
It must not normalize case, discard colon prefixes, or replace characters in
the subject. The server's `IdentityReferences.accountId` projection, Rust
Matrix sender/state projection, and Flutter's expected `whoami` ID must agree.
For a pre-release installation with an older projected Matrix ID, canonical
Chat memberships and messages keep their Weave actor references and reproject
under the new ID, so authorized business-room history remains readable.
The older server-side identity/device binding and local crypto store remain
quarantined under the old ID; they are not silently rebound to the new ID.
The member establishes a fresh scoped device proof for the new ID. Any
encrypted-room continuity needs a separately verified key recovery or
migration before access is advertised, and those rooms remain fail-closed in
this release profile. Tests include subjects that previously collided, issuer
changes, room event senders, and denied reuse of another account's local store.

Incremental Matrix `/sync` does not reuse its chat/E2EE `since` token as a
room-history `prev_batch`. When no canonical per-room history cursor is
available, that field is omitted. An advertised `prev_batch` must be accepted
by the authorized `/rooms/{roomId}/messages` route.

The native Flutter test binding enables semantics before each widget test's
handle baseline is recorded. macOS Accessibility may activate the platform's
semantics owner after a test starts; that owner must not be mistaken for an
application leak. Semantics remain enabled for the entire journey, and the
normal Flutter handle-leak assertion still catches additional undisposed
handles.

For the cross-organization release denial, the disposable Keycloak fixture
creates one additional organization and a dedicated owner only inside the
isolated E2E namespace. The normal realm import and production bootstrap still
declare one primary organization. First-owner bootstrap runs against an empty
realm. Only after that owner has authenticated does the fixture briefly create
a disposable one-shot Keycloak administrator, restart Keycloak, provision the
foreign identity, delete the temporary administrator, and verify that its
credential cannot obtain another token. The product flow stores only the foreign
owner's disposable credentials in a mode-0600 local file and obtains a real
Keycloak-issued member token through browser Authorization Code with PKCE,
verifies that its sole organization claim is foreign, and proves that primary
User/Admin/Matrix resources and mutations are denied. Only boolean results
and exact candidate provenance may enter uploaded evidence.
The Files and Calendar workload-aware decoders reject a validated foreign
member during deployment-organization admission with HTTP 401 and the
`unauthorized` error envelope. The test requires that exact response after
comparing the foreign bearer with a freshly refreshed primary bearer. Matrix
and Admin use their own authorization gates and are asserted separately.
The Matrix Client-Server northbound must return a Matrix `errcode` and `error`
object even when a request is denied by the Spring Security filter before the
facade controller. Missing and invalid bearers use the Matrix authentication
error profile; a valid foreign-organization bearer uses `M_FORBIDDEN`.

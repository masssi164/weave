# ISO 9241-110:2020 Dogfood UX checklist

Status: implementation checklist projecting the pinned Weave Specification Corpus
Owner: `client/` interaction evidence, with server/infra evidence for authorization and recovery
Related release issues: #1470, #1481, #1592

## Authority and scope

The accepted corpus `WEAVE-STEERING-ASA` owns the seven interaction criteria;
`WEAVE-STEERING-RELEASE-2026-10-CONSOLIDATION` owns supported release scope;
`WEAVE-STEERING-DEVOPS-CONFORMANCE` and ADR 0022 own local inspection and qualification.
Read their exact revision in `specs/weave-specs.lock.json`, as described by the
[specification source of truth](specification-source-of-truth.md).
This checklist helps collect implementation evidence and cannot add product scope or redefine
capability states. Its earlier #258/#259 product-contract wording and individualization principle
are superseded by the accepted 2020 criteria. This is not an ISO certification claim.

## Interaction review

Apply all seven criteria to supported member tasks in EN and DE. Record actual observations,
not a conclusion inferred from a successful click or a copy assertion.

| Principle | Review focus |
| --- | --- |
| Task fit | Organization entry/invitation, browser SSO, Chat, workspace Files and Calendar can be completed without provider knowledge or unnecessary steps. |
| Self-description | State, progress, success, errors and the next useful action are understandable. |
| Expected behavior | Navigation, labels and shared content behave consistently across the supported surfaces and locales. |
| Learnability | Members can find entry points and essential actions without a developer runbook. |
| User control | Back, cancel, retry and confirmation work predictably; recoverable errors retain appropriate input. |
| Error robustness | Failures preserve safe recovery without silent loss, private details or cross-user leakage. |
| User engagement | Useful progress and clear outcomes support continued work without distraction. |

Include keyboard obstruction, text scaling, focus order, accessible names and states. Keep essential
controls available. Never hide an essential action merely to simplify a screen.

## Release-scope display states

These descriptions project the accepted readiness policy; they do not introduce another API enum.
**Preview is not a release-scope state.** Do not show deferred products as member setup obligations.

| Display condition | Member experience | Admin/operator experience |
| --- | --- | --- |
| Ready for users | Supported task and product controls are available with required evidence. | Same, with authorized health information. |
| Admin setup required | Impact and a safe next action when relevant. | Exact authorized setup action. |
| Disabled by policy | Understandable policy impact. | Authorized policy controls. |
| Broken/degraded | Clear impact, retained recoverable input and safe retry or recovery. | Support-safe diagnostics and the next useful action. |
| Not in this release | Absent from normal member navigation. | Historical planning does not make it a release prerequisite. |
| Provider diagnostics | Hidden from members except impact-level status. | Authorized readiness and support evidence. |

Use plain, localized product terms. Do not put preview, scaffold, coming-soon, roadmap or future
copy in normal member paths. Raw provider setup, stack traces, credentials and token-bearing URLs
are not member-facing recovery instructions. Protocol capability values remain owned by their
canonical contract; they are not labels to display directly.

## First-use acceptance criteria

A member enters only the public organization URL, or follows the existing invitation path.
Host input normalizes to HTTPS. The single OrgManifest v2 contract delivered by the generated
User `GET /api/platform/config` operation supplies validated bootstrap information. Native
system-browser OIDC Authorization Code with PKCE completes authentication; Flutter never collects
passwords or asks for issuers, realms, client IDs or provider addresses.

The member can find Chat, Files and Calendar for the authorized workspace, understand current
state, and finish supported tasks through Weave controls. Files and Calendar use the generated
User API; the accepted Matrix facade is the explicit native protocol exception. Deferred products
are not added to the review checklist. Invitation onboarding is distinct from authorized same-org
direct chat, which does not require a recipient Accept action.

## Local evidence and defect records

Use the existing isolated startup and native runner in its explicit inspection lifecycle. Keep two
real iOS apps concurrent with independent sessions, Keychains, application storage and Matrix
devices against one owned real stack. Inspect actual Flutter UI and accessibility state with
Computer Use; use existing native browser/picker tooling where needed. Keep the session warm,
identify the running source, and record reload/restart/rebuild operations and iteration timings.
Default qualification cleanup and evidence rules remain unchanged. Local work must refuse or yield
to CI and clean only its own resources when the session ends or loses ownership.

For each defect record:

- Principle, task/role, locale and input/display conditions.
- Reproduction steps, expected behavior, observed behavior and severity.
- Demonstrated cause, implemented correction and behavioral regression evidence.
- Relevant existing Gherkin/scenario mapping and any remaining limits.

A mandatory-task, security, recovery or required-accessibility blocker prevents promotion. Exercise
actual peer message exchange, file bytes/version changes, Calendar CRUD/removal, unauthorized access,
independent restart/background recovery, expiry/refresh/revocation and logout isolation. Do not seed
backend outcomes or substitute API calls for required UI interactions.

Computer Use is exploratory interaction evidence. It does not establish human usability,
satisfaction, physical-device accessibility or VoiceOver acceptance. Record unavailable observations
explicitly. After local blockers are fixed, freeze a clean candidate and run protected Dev/Dogfood
Compose, macOS and full dual-iOS qualification. Changed source or pin requires fresh qualification.
Deployment, physical iPhone preparation and explicit human acceptance remain distinct gates.

## Prompting checklist for future agent work

A scoped implementation brief identifies the accepted corpus criteria, supported task and role,
actual defect reproduction, allowed files, required behavioral tests and evidence limits. It must
not use this checklist to add deferred features or treat copy checks as interaction proof.

## Required validation

Discover commands in the current repository and use the smallest meaningful checks:

- `./gradlew specCorpusConformance` for the exact accepted corpus pin.
- `./gradlew acceptanceContract` when Gherkin or mappings change.
- Relevant Flutter analysis, widget/integration regressions and native runner tests.
- The protected exact-candidate gates after local validation.
- Physical/human accessibility evidence from [the accessibility release gate](accessibility-release-gate.md).

## Ordinary persisted Dogfood functional check

The pinned delivery contract additionally requires the bounded phase in
`tools/dogfood_functional_smoke.py`. It supplements full isolated Compose/macOS/
two-iOS qualification. Deployment uses two owned simulators running normal
`lib/main.dart` in debug mode; physical preparation uses the retained owner iPhone
profile build and one owned normal-main peer simulator. Simulator profile builds
are not claimed. The existing XCTest BrowserDriver performs ordinary controls,
real system-browser PKCE sign-in and native document pickers. No integration
entrypoint, actor file in the application, relay, database seed, owner session
copy, Keychain read or account replacement is permitted.

The private runner input is
`~/.weave/dogfood/functional-smoke-identities.json`, or the explicit
`--identity-file` CLI argument. Its parent must be owned/private (0700) and the
regular, nonsymlink input owned/private (0600). It is not an artifact or a public
environment variable. The closed JSON shape is:

- `schemaVersion`: `weave.dogfood-functional-identities/v1`.
- `organizationOrigin`: the reviewed public HTTPS organization origin.
- `members`: exactly two distinct approved normal test members, each with only
  `email`, `password` and `displayName`.
- `ownerDisplayName`: the existing owner's visible display name, with no owner
  password, bearer, session or setup authority.

The Admin Console obtains its current actor from the generated Admin control-plane response,
including the canonical account reference, organization, issuer and current roles. It validates
that actor before exposing setup controls; an Admin bearer is never sent to User `/api/me`.
The separate User and Admin authorized-party checks remain enforced. Isolated qualification
also verifies the packaged Console's admitted identity path before default-Space confirmation.

Missing input invokes the explicit `./gradlew --no-daemon dogfoodPrepare` path;
the owner does not manufacture a credential file or manually create a Space.
The protected ordinary functional producer's existing missing-input bridge explicitly
selects that operator preparation stage after exact-candidate deployment. Merely
selecting a Dogfood environment, ordinary startup or a readiness poll does not
invoke or authorize it. The same reviewed host, candidate, private-plan and ledger
guards apply to the bridge and the separate root command.
This command uses the fixed reviewed Dogfood environment file and the shared
exclusive session lock. It projects only the existing public CA into a local
setup bundle and the two exact Dogfood-only gateway setup paths. It verifies
ordinary macOS trust independently for organization, API and issuer; trusting
the CA remains an explicit OS-authenticated human action. It does not rebuild,
restart or repair the stack, change TLS, or reset any owner state.

Preparation builds two owned ordinary-main simulators before inviting anyone.
The default `--authorization-mode operator` path uses the existing trusted Dogfood
host authority for one fixed private two-member plan. It verifies the effective
reviewed file, persistent-Dogfood container labels and exact image/source before
invoking the fixed `dogfood-prepare-members` Server command. The command starts
only its dedicated non-listening validation-only context, with the existing
configuration and SecretRefs. It checks the explicit Dogfood release posture and
serving candidate before mutation; production and ordinary startup cannot use it.
No public API, service bearer, administrator account or new credential is added.

The operator is separately identified in provisioning intents and audit records.
It never acts through a fabricated human JWT or the owner's account. The selected
physical owner must already be uniquely enabled, activated and authorized in the
existing default Space. Missing, ambiguous, revoked or suspended owner/Space
state blocks; this command does not repair that access. Exactly two plan-derived
unprivileged test-member invitations are followed by ordinary member sign-in and
canonical identity proof before absent-first default-Space VIEW/EDIT grants.
Existing exact active grants are verified; broader, conflicting or revoked grants
are never replaced or restored. The command cannot choose another account, role
or Space. Exact provisioning-intent references and current versions govern resume.

The optional `--authorization-mode console` path retains the existing normal
Admin Console session and displayed-plan approval. The browser driver may review
the plan but cannot click its approval control. Missing normal administrator SSO
or approval is a precise incomplete boundary for that path; it is not a required
step in the operator path. Full Admin Console usability acceptance remains separate
from preparing the member application.

Stock registration and any VERIFY_EMAIL link stay in the private recipient-matched
Mailpit inbox and XCTest fixture. Each test member subsequently completes the
ordinary application's independent system-browser PKCE and reconciliation. Its
actual current account/organization digests must match the current canonical
Server projection before grants can be accepted. A registration ACK is not app sign-in.
These are persistent test accounts; a first-owner bootstrap record is not a
normal administrator session. Never fabricate user-info or seed E2E memberships.
If the selected preparation authorization, normal test-member authentication,
passkey/MFA or physical owner sign-in cannot be completed, retain an **incomplete**
result with its precise boundary.
Finish independent diagnostics first. Do not publish credentials or invitation
URLs in GitHub, and do not reset or replace an owner session to obtain proof.

The host atomically maintains a private
`functional-smoke-identities.preparation-ledger.json` beside the input. Its
authority-specific record retains the exact plan, authorization kind, stable
invitation request keys and provisioning-intent references, generated test-only
passwords, registration checkpoints, current readbacks, exact source metadata,
fixture digest and issuer-bound selected-owner identity digests. Operator receipts
identify the operator and real owner separately; they are never relabeled human
administrator receipts. The owner's actual visible name stays in the private
envelope and ledger, outside support-safe evidence.

The ledger contains no owner bearer, browser cookies, Keychain material or raw
session. Do not upload it or the credentials. Resume preserves test passwords and
verifies current state; lost/changed credentials block rather than reset accounts.
A legacy unexecuted plan may retain its generated names and passwords, but issued
human-authority references cannot be converted to operator authority. Fresh
invocation nonces reject stale output. Credentials publish only after both own-app
identity proofs, current grant readback and owned-simulator cleanup. This completes
preparation prerequisites, never functional, VoiceOver or human acceptance.

Before physical product writes, the retained phone's observed account and
organization must match that selected-owner binding. Deployment's two actors
instead match the two independently authenticated approved test members. A
fixture's display name or the acting administrator cannot substitute for this
identity comparison.

The producer creates namespaced run-owned content through UI, records hashed
stable resource identities and verifies the same identities through the other
normal member. It checks direct bidirectional Chat, reopening, shared folder and
uploaded file preview, an actual native export whose bytes are read from the
owned peer's Documents directory after removing the original upload source, and
Calendar create/read/update/delete with fresh peer readback. Calendar absence
requires completed fresh refresh and exact stable-ID absence; disappearance
while loading is insufficient. Account and organization references come from
current authenticated secondary diagnostics, not fixture names. Both members
must be observed and distinct. Restart and same-candidate in-place reinstall
then repeat Chat/Files readback and account/context checks. The iPhone retains
its existing account and local stores, and starts without an onboarding URL.
Installation/launch-requested evidence alone never establishes functional success.

Private Files proof creates and opens a separate run-owned private folder,
changes to the shared root and back to the same private folder identity, and
checks that another tab preserves the current private destination. The peer's
private root must not contain that folder. Both negative reads require a newly
completed successful listing and bounded ordinary scrolling to the actual end;
stale, loading, failed or unscanned listings leave the result incomplete. Restart
and in-place update must reopen the same private folder identity through the
ordinary UI. Public receipts include only its digest and required scope, peer
isolation and continuity flags; the private ledger keeps the owned fixture name.

Only precisely identified run-owned private/shared folder/file/conversation fixtures are
retained for continuity, reported as `retained-owned-content`. Calendar deletion
and independent absence are required. Owned simulators, transient credential
invocation files and raw XCTest results are removed. The approved persistent
identity input and an owned-content ledger under
`~/.weave/dogfood/functional-smoke-owned-content/` remain private for later bounded runs; unrelated owner data,
secrets, TLS, signing and applications are untouched. No global cleanup or
name-only deletion is used.

`weave.dogfood-deployment/v2` and `weave.hil-preparation/v3` include a complete
`weave.dogfood-functional-smoke/v2` receipt binding source tree, specification,
deployed image/source, ordinary app build, direct reviewed routing and final
qualification run **and attempt**. Fixed ordered observed operations, independent
identity/resource digests, actual exported bytes, restart/update readback and
cleanup must all pass. A missing, stale, discovery-only or incomplete result
blocks ready handoff and Main. `--diagnostic-only` is always incomplete and
cannot create acceptance evidence. Raw UI transcripts, xcresults and member
content stay in a temporary private directory; only the allowlisted receipt is
uploaded. Physical invocation files are delivered only to the separately signed
existing XCTest runner sandbox, consumed there and removed with that run-owned
runner; the owner application receives no actor or credential file. Automated results do not establish human VoiceOver or E2EE acceptance.

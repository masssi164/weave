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
hosted artifact composition and fresh Compose/two-ordinary-member dual-iOS qualification.
macOS desktop testing is manual-only. Changed source or pin requires fresh qualification.
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

`tools/dogfood_functional_smoke.py` supplements isolated two-member collaboration with real
ordinary-entry behavior against the deployed Dogfood stack. Deployment uses two owned simulators
running the hosted normal `lib/main.dart` application, with the existing prebuilt XCTest
BrowserDriver operating normal controls, system-browser PKCE and native document pickers.
The product app receives no integration fixture, actor file or injected session. There is no
provider-direct route, database seed, owner session copy, Keychain read or account replacement.

The shared `fixtures/member-client-baseline/v1.json` recipe determines required domains,
shared Space, two ordinary member actors and run-scoped content. The bounded
`tools/prepare_member_scenario.py` operation resolves that recipe through existing Server
identity/domain services, verifies actual account/subject/organization and current `VIEW,EDIT`
Space grants, and emits private credentials and bindings. The operation is limited to the
exact plan-owned test identities in reviewed Dogfood or an explicitly owned isolated E2E
namespace; it does not bootstrap or replace the persistent owner. Prepared credentials are
only setup inputs. Both real apps still independently authenticate and prove the same current
identities before their product assertions.

The ordinary identity file keeps schema `weave.dogfood-functional-identities/v1` and the existing
strict fields: reviewed organization origin, exactly two distinct approved test members and
existing owner display name. Its private sidecar pins the common scenario and resolved bindings.
Private directories are owned mode 0700, and regular nonsymlink credential files are mode 0600.
Credentials, subjects, raw account identifiers, invitation links, ledgers and UI transcripts
must not be uploaded. The recipe itself contains no credentials or environment-specific authority.

The separate legacy `dogfoodPrepare` invitation/Console paths remain available for explicit
onboarding investigations. Their registration and approval checkpoints do not make another
invitation or owner Console login a prerequisite for each prepared-member client iteration.
Console setup still uses the generated Admin current-actor projection; a User `/api/me` request
cannot validate an Admin session. Normal User/Admin audience and authorized-party checks remain
independent. Managed fixture provisioning cannot silently broaden, recreate or restore a revoked
Space grant or change a personal account; uncertain current state fails closed.

Both deployment actors use ordinary member roles and current shared-Space access. Setup/admin
credentials remain separate. The producer creates namespaced run-owned content through UI and
verifies the same stable resources through the peer: bidirectional Chat and reopening; shared
folder, upload, preview and native export with actual byte readback; and Calendar
create/read/update/delete. Calendar absence requires a completed fresh refresh and exact
stable-ID absence. Display names, loading states or a successful tap cannot substitute for
identity or content readback. Restart and same-artifact in-place reinstall repeat shared content
and account/context checks.

Private Files proof creates and opens a separate run-owned private folder, changes to the shared
root and back to that same private identity, and verifies tab/restart/update continuity. The
peer's private root must not contain it. Negative reads require a completed successful listing
and bounded ordinary scrolling to the real end; stale, loading, failed or unscanned listings
remain incomplete. Public receipts contain only digests and required isolation/continuity flags.

Precisely identified run-owned private/shared content is retained for continuity and recorded as
`retained-owned-content`. Calendar deletion and independent absence are required. Owned simulators,
transient invocation files and raw XCTest results are removed. Approved persistent identity input
and the owned-content ledger remain private for subsequent bounded runs. Unrelated owner data,
TLS, signing and applications are preserved; no global cleanup or name-only deletion is used.

The deployment receipt includes complete `weave.dogfood-functional-smoke/v3` evidence bound to
the current candidate composition, actual successful qualification job attempt, verified runtime
images, ordinary app artifact and direct reviewed routing. Every required operation, independent
identity/resource digest, export-byte check, restart/update readback and cleanup must pass.
`--diagnostic-only` cannot produce accepted evidence.

## Physical startup and human acceptance

Protected physical preparation consumes the already qualified device artifact. It signs locally,
installs in place and uses the prebuilt ordinary-startup driver to observe the existing workspace
or normal sign-in. The `weave.hil-preparation/v4` receipt binds candidate, composition, installed
binary, actual qualification attempt and fresh startup nonce; it records preserved owner state
and temporary-driver cleanup. `ownerHIL` remains `pending`.

The human signs in normally when needed and reports actual Chat, Files, Calendar, session and
VoiceOver observations with the tested owner/admin/member role. A legacy optional physical
functional diagnostic is not a prerequisite for that handoff, and its older v3 preparation
receipt cannot replace current v4 startup evidence. Automated collaboration and startup never
establish human usability, VoiceOver or E2EE acceptance. See
[iPhone delivery](ios-dogfood-distribution.md) and the
[human-result protocol](gitflow-pr-workflow.md).

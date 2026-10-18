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

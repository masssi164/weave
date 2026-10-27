# ISO 9241-110:2020 Product Interaction Standard

Status: implementation and review standard derived from the pinned Weave Specification Corpus.  
Scope: all supported Weave member interactions, independent of deployment lane.  
Related release issues: #1470, #1481, #1592, #1749.

## Authority and boundary

The accepted corpus `WEAVE-STEERING-ASA` owns the seven interaction principles. The exact accepted revision is pinned in `specs/weave-specs.lock.json`; this document translates those principles into implementation and review guidance and is **not an ISO certification claim**.

**Dogfood is an evidence and delivery lane, not a UX variant.** The ordinary app installed for automated or human qualification must expose the same member experience as the product. Candidate IDs, run IDs, qualification ledgers, reset controls, evidence paths, provider diagnostics and test actors belong to DevOps/test tooling and must not define normal member copy, navigation, state, or interaction.

Human acceptance answers: **“Is Weave itself usable?”** It must not depend on the app revealing that it is running in Dogfood.

## Seven interaction principles

Apply all seven principles to supported member tasks in English and German. A passing click, build, or copy assertion is not sufficient interaction evidence.

| Principle | Weave review focus |
| --- | --- |
| Suitability for the user's tasks | Organization entry, sign-in, Chat, Files and Calendar can be completed through product concepts without provider knowledge or unnecessary setup steps. |
| Self-descriptiveness | Current state, progress, outcome, error and the next useful action are understandable from the interface itself. |
| Conformity with user expectations | Navigation, labels, destructive actions, retry/back behavior and shared content follow familiar and consistent patterns. |
| Learnability | Essential actions are discoverable without a developer or operator runbook. Repeated tasks retain predictable structure. |
| Controllability | Members can back out, cancel, retry and intentionally trigger actions. Automatic recovery never impersonates a member or silently changes authorization. |
| Use-error robustness | Invalid input and failures fail safely, preserve recoverable work, avoid cross-user leakage and provide actionable recovery without exposing private diagnostics. |
| User engagement | Feedback and visual polish support orientation and confidence without distraction, unnecessary waiting, or animation required for comprehension. |

Also verify text scaling, keyboard obstruction, focus order, accessible names/states, touch targets, screen-reader navigation, and localized recovery. Essential actions stay available at large text and with assistive technology.

## Product and delivery separation

Production member code must not use deployment/test metadata as product state.

Allowed in DevOps/test tooling:

- candidate/source identities and immutable artifact digests;
- Dogfood or isolated-E2E environment names;
- run IDs, fixture identities and evidence receipts;
- reset/cleanup controls and qualification-only diagnostics;
- simulator/device orchestration.

Allowed in the product only when it is genuine user-domain data:

- validated organization origin and product configuration;
- authenticated identity and current organization membership;
- product capabilities, permissions and readiness;
- support-safe error codes that describe a user-relevant failure;
- user accessibility preferences.

A test lane may prepare inputs and observe output from outside the app. It must not require a hidden app-only reset command, preference key, telemetry ledger, or separate “Dogfood mode” to prove the product flow.

## Release-scope display states

These states project the accepted readiness policy. **Preview is not a release-scope state.** Deferred products are absent from normal member navigation rather than presented as setup obligations.

| Display condition | Member experience | Admin/operator experience |
| --- | --- | --- |
| Ready for users | Supported task and controls are available. | Same product behavior plus authorized operational information. |
| Admin setup required | Product impact and a safe next action where useful. | Exact authorized setup action. |
| Disabled by policy | Understandable policy impact. | Authorized policy controls. |
| Broken/degraded | Clear impact, retained recoverable input and retry/recovery. | Support-safe diagnostics and operational next action. |
| Not in this release | Absent from normal member navigation. | Roadmap/history does not become a member prerequisite. |
| Provider diagnostics | Hidden from members except product-level impact. | Authorized readiness and support evidence. |

Use localized product terms. Raw provider setup, service endpoints, stack traces, credentials, test labels and token-bearing URLs are not member-facing recovery instructions.

## First-use acceptance criteria

A member enters only the public organization URL or follows a supported invitation/app link. Host input normalizes to the canonical secure organization origin. OrgManifest v2 supplies validated bootstrap information. Authentication uses the native system browser and Authorization Code with PKCE. Flutter never asks members for realms, client IDs, provider addresses, Dogfood run IDs or qualification controls.

After authentication, the same ordinary client exposes only installed and authorized product capabilities. The member can find and use release-scope Chat, Files and Calendar, understand unavailable/degraded states, and recover through Weave controls.

Invitation metadata may be used to validate/rout the handoff but must not replace product identity in visible copy. Deployment names such as a fixture organization slug must never become the organization/workspace label merely because they occurred in a test link.

## Motion and reduced motion

Motion is an app-wide product policy, not a screen-local decoration.

- Define durations, curves and reduced-motion behavior centrally under `AppTheme`.
- Use semantic motion tokens instead of local hard-coded animation constants.
- Motion communicates state/hierarchy only; it never delays an available action or carries unique information.
- The effective reduced-motion decision combines platform accessibility settings with the authenticated user's product-profile accessibility preference.
- Reduced motion removes or makes immediate non-essential translation, scale, parallax and state transitions while preserving progress, content, focus changes, semantics and live-region feedback.
- Screen-reader and keyboard behavior must remain equivalent with normal and reduced motion.

New animations require a normal-motion and reduced-motion regression. A visually attractive transition does not compensate for a violation of the other six interaction principles.

## Error and recovery language

A stable support code may accompany an error, but the interface also explains the user-relevant effect and next useful action. Operator-only remediation stays outside member UI.

Destructive operations must be explicit and predictable. Test/deployment reset behavior belongs to DevOps and must not be hidden behind a production deep link or private preference. If a reset/recovery capability becomes a real product feature later, design and authorize it as such.

## Evidence model

Interaction evidence is layered:

1. unit/widget/architecture tests prove deterministic presentation and semantics;
2. real integration/native tests exercise the ordinary product client and real boundaries;
3. protected qualification binds results to the exact candidate;
4. physical HIL records actual human and assistive-technology observations.

Dogfood may execute stages 2–4, but it does not create another member UX. Qualification tooling should observe visible UI, generated APIs, process outcome and support-safe external evidence rather than asking production client code to write private test-state ledgers.

Implementation and delivery mechanics belong in [Quality and acceptance evidence](quality-and-evidence.md), [iPhone delivery](ios-dogfood-distribution.md), and the relevant DevOps runbooks.

Computer/automation interaction is not proof of human satisfaction or VoiceOver acceptance. Record unexecuted human observations as pending.

## Prompting checklist for future agent work

For interaction changes, require:

- the supported user task and affected roles/locales;
- the relevant ISO 9241-110 principles;
- existing product behavior and a reproducible problem;
- one canonical app/theme/state owner rather than local workarounds;
- accessibility, reduced-motion and error/recovery behavior;
- focused behavioral tests plus the applicable integration/HIL boundary;
- removal of superseded compatibility/test-only product code and contradictory documentation.

Do not create a separate test UX, expose DevOps metadata to members, or satisfy an interaction requirement through documentation alone.

## Required validation

Use the smallest applicable checks during development, then the protected exact-candidate gates:

- `./gradlew specCorpusConformance`;
- `./gradlew acceptanceContract` when Gherkin or mappings change;
- relevant Flutter formatting, analysis, widget and integration tests;
- accessibility/reduced-motion regressions;
- protected qualification using the ordinary client;
- physical/human evidence from the [accessibility release gate](accessibility-release-gate.md) when required.

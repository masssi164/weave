# iOS dogfood for active development

The delivery loop prepares a working member client before asking the owner to test it.
For one protected `dogfood` candidate, hosted builders produce or reuse verified runtime and
iOS artifacts. Full Compose E2E then runs two concurrent independent real iOS Simulator apps
against an isolated stack, using two ordinary members of the same prepared organization and
shared Space. Successful collaboration permits Dogfood deployment and physical iPhone
preparation. The human then signs in and reports the observed product behavior.

macOS desktop qualification is manual-only. Candidate Cut, Fresh Start, TestFlight,
production publication and a manually assembled release manifest are outside this loop.
The generated artifact composition remains mandatory: it binds the current candidate to
the precise component inputs, payload digests, toolchains and original producer runs.
Reusing an unchanged component does not reuse the live collaboration result of another candidate.

## Manual hosted macOS build

Use the registered `Hosted build artifacts` workflow to build or reuse the desktop component
from protected Dev, including before these workflows are promoted to Main:

```sh
gh workflow run build-artifacts.yml --repo masssi164/weave --ref dev -f components=macos-desktop
```

The manual `components` choice selects either `macos-desktop` or the default
`runtime-linux-amd64,ios-simulator,ios-device` set. Automatic Dev pushes retain the latter set;
they do not build macOS. Desktop artifacts use the same component fingerprints and trusted
producer verification as the other hosted artifacts. Desktop testing remains a separate manual
activity and does not satisfy iPhone collaboration or human acceptance.

GitHub permits API/CLI dispatch on another ref once a workflow has run at least once; the
registered builder has already run on Dev. A new dispatch-only workflow must first exist on the
default branch for its normal manual entry point to be available. Keep using the command above
until the dedicated `Build macOS desktop manually` wrapper is available. See GitHub's
[workflow dispatch rules](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_dispatch)
and [dispatch API](https://docs.github.com/en/rest/actions/workflows#create-a-workflow-dispatch-event).

## One member scenario across environments

`fixtures/member-client-baseline/v1.json` defines the common organization domains, shared
Space, two ordinary `member` actors with current `VIEW` and `EDIT` grants, and collaboration
content. `tools/member_test_scenario.py` resolves that recipe for each execution. Integration,
simulator and ordinary Dogfood recipes consume the same semantics and canonical recipe digest.
Each environment has its own runtime data and private identity bindings; they do not share a
mutable database. Readback of current organization membership, account identity and Space
grants is required before a product actor writes anything.

Prepared test accounts and the Space are setup inputs. The mandatory native test still performs
normal independent PKCE sign-in and real Chat, Files and Calendar collaboration through Weave's
User API and Matrix facade. Separate setup/admin credentials are used only for preparation and
authorization probes. Full admin onboarding and MCP acceptance remain separately selectable;
they do not make the owner's ordinary client iteration depend on another onboarding rehearsal.

## Preparation sequence

The protected Dogfood push qualifies and deploys the candidate. Signing and physical
installation are requested through the manual `Prepare Human Test` workflow, when the paired
iPhone and the runner's development signing key are available. The workflow:

1. verifies successful exact-candidate Compose, two-member iOS collaboration and deployment,
   including the actual successful qualification job attempt;
2. consumes the qualified hosted runtime and unsigned device artifacts, preserving each
   component's original producer identity;
3. starts Dogfood twice and proves unchanged CA and leaf certificate fingerprints;
4. selects stable ordinary Dogfood member `A` or `B` and revalidates the existing
   audited account, organization role and exact current shared-Space grant without writes;
5. signs the already built device artifact locally, installs it in place and observes the
   installed app's ordinary startup through the prebuilt native driver.

The protected path does not compile missing applications on the Mac. A missing, incompatible
or mismatched artifact fails preparation. The Mac provides native execution, local signing,
pairing and installation; hosted builders provide compilation.

The read-only `verify-members` operation uses the existing managed Dogfood fixture and its
immutable operator-plan/account binding. It verifies enabled, activated ordinary membership and
the exact existing `workspace-default` grant version under the Space lock. It does not create
accounts, reset passwords, issue invitations or repair grants. Missing, revoked, elevated or
rebound identities, stale grants and unavailable authority fail preparation. The fixture owner
is selected by its recorded account binding, rather than assuming a globally unique owner.

First-owner invitation and activation are separate setup operations for an empty installation.
An invitation alone cannot prove owner Space access. Once that account exists, routine
preparation preserves it and does not require another invitation or registration run.
Activation links remain private in Mailpit at the reviewed `WEAVE_MAILPIT_URL`; the standard-port
Dogfood example uses `https://mail.weave.test`.

## What successful preparation proves

The support-safe `weave.hil-preparation/v5` receipt binds the current candidate, source tree,
specification pin, successful qualification run/attempt and verified artifact composition.
Its `memberWorkspace` contains a `weave.member-workspace-preparation/v1` proof with selected
slot, ordinary role, issuer-bound account/organization digests and current grant version. It
records installed-binary and public OrgManifest/UI routing verification, preserved device state,
driver cleanup and a fresh nonce-bound ordinary-startup result. A retained workspace must match
the designated member. Otherwise the app must show normal sign-in for the reviewed origin;
preparation never silently switches the person's account.

`humanAcceptance` remains `pending`, and the member proof explicitly keeps
`authenticationEvidence` and `humanAcceptance` false. Personal sign-in, passkeys or MFA are the
human's next normal app action when needed. Preparation does not automate login or infer authenticated
Chat, Files, Calendar or accessibility acceptance from a launch. Broken startup, stale readback,
invalid trust or missing required access still block preparation.

## Stable device identity

Every normal iteration is an update in place. Signing and installation preserve:

- bundle identifier `com.massimotter.weave`;
- Apple team `KNDHGC2KV6`;
- Apple Development certificate team `6RUS2Z848X`;
- the development Keychain application identity;
- the existing app container and Developer App trust.

The human-test workflow uses `update_in_place`; it never selects `destructive_uninstall` recovery.
Reused artifacts retain their original compiled build/source identity, while the qualified
composition identifies the current candidate and proves compatible unchanged inputs. Do not
rewrite an older binary's producer identity to look like a new build.

The Mac records verified runtime image IDs in a private, bounded retention ledger. Each
consumer keeps the current and previous verified image sets. Cleanup removes only older
recorded, untagged images that no running or stopped container references; operator tags,
registry digests and unrelated images remain outside its removal authority. The cleanup
runs before capacity checks and again after each protected consumer job.

The `Profile` configuration uses Personal Team-compatible development entitlements: it
preserves the Keychain application identity and omits Associated Domains. Production `Release`
retains its full Associated Domains entitlement. Dogfood authentication uses the normal
AppAuth/custom-scheme callback.

TLS material lives at the reviewed environment's `WEAVE_TLS_ROOT`, outside Docker. `dogfoodUp`,
`dogfoodDown` and `dogfoodReset` preserve it. The Weave Local Development CA therefore normally
needs installation and full trust on the iPhone only once. The private reviewed environment
supplies the sole public organization origin; API and issuer endpoints come from its validated
OrgManifest, not provider URLs or manual endpoint overrides.

## Before pressing Run

Dispatch from the protected `dogfood` ref with its current full commit:

```sh
gh workflow run ios-dogfood.yml --ref dogfood -f candidate_sha=<full-dogfood-sha> -f tester_slot=A
```

The preparation workflow itself must run at that exact protected commit. A matching title or
copied receipt cannot substitute for the trusted producer.

- The iPhone is unlocked, paired with `weave-live-mac-mini`, reachable over the reviewed network,
  and has Developer Mode enabled.
- The development signing key is accessible to the runner process.
- The Weave Local Development CA is trusted, and LAN DNS resolves the configured organization
  origin and its authentication host to the Dogfood installation.
- `WEAVE_IOS_DEVICE_ID` identifies the paired physical phone.
- `tester_slot` selects existing ordinary Dogfood member `A` or `B`; the default is `A`.
  The selected private fixture must already exist from the persistent Dogfood scenario setup.

First-owner initialization remains a separate deployment/setup operation. App Store Connect and
distribution secrets are unnecessary for development Dogfood.

## What the human verifies

Use the designated ordinary member account, retaining its existing session or signing in when
shown. Report `actor-role: member` together with the exact candidate and installed artifact/build.
Owner or admin journeys remain separate and cannot satisfy this member acceptance.

- Sign-in, session refresh, logout and sign-in again.
- Home, Chat, Files, Calendar, Settings and Profile navigation.
- Real shared-Space Chat, Files and Calendar behavior, including current access and content
  visibility for the intended collaborators.
- Revocation/regrant behavior and identity continuity through supported product journeys.
- VoiceOver labels/order, actionable errors, keyboard/focus behavior and relaunch continuity.

Record each required outcome as `passed`, `failed` or `not-available`. Automated collaboration,
installation and startup do not manufacture a human pass. Use the `Weave human test v2` comment
in [the protected promotion workflow](gitflow-pr-workflow.md); its `actor-role` is mandatory.
Optional Weaver/MCP and broader admin journeys retain their separate acceptance scope.

Contract marker: `HUMAN_TESTING_HAS_NO_MANIFEST_GATE`.


## External qualification owners

This document is the operational source for iOS qualification. The pinned
[delivery contract](gitflow-pr-workflow.md) governs the required lanes; this table
maps them to their existing implementation owners. Qualification stays outside the
production app and uses normal member controls and public APIs.

| Behavior or boundary | Existing evidence owner | What the result establishes |
| --- | --- | --- |
| Isolated invitation activation and first-owner setup | `FreshProductFlow` in `weave-product-e2e`, explicitly selected `full` mode, with `OidcBrowserJourney` and `MailpitActivationInbox` | Real browser activation, PKCE and current identity/authority readback in a disposable environment. Its full-mode success result is collected under `test-app-full`; a member-collaboration run cannot qualify this scenario. |
| Two independent ordinary members, native browser login, authorization, Chat/Files/Calendar and actual bytes | `client/tool/run_ios_simulator_product_acceptance.py`, existing dual-iOS Flutter suite and XCTest `BrowserDriver` | Protected Full Compose qualification of the exact artifact composition and scenario. Native cancellation/retry and denial checks remain in the existing suite. |
| Deployed ordinary-app readback, private Files isolation, restart and in-place update | `tools/dogfood_functional_smoke.py` and XCTest `OrdinaryProductSmoke.swift` | Two ordinary members use the installed `lib/main.dart` app against the reviewed direct Dogfood context. Current identity, independent content readback and exported bytes must agree. Only `--stage deployment` is accepted. |
| Physical artifact preparation and current application context | `tools/prepare_iphone_artifact.py`, signing/consumption validators and the same XCTest driver | The qualified artifact is installed in place. Public OrgManifest discovery is checked with the reviewed CA and no proxy/redirect; the normal UI must show the exact origin at sign-in or the expected issuer-bound account and organization references in Settings → Help. |
| Real physical product and VoiceOver acceptance | The designated tester's protected human result | The actual candidate/build, member role and observed outcomes. Automatic startup leaves this human acceptance pending. |

The first-owner Gherkin scenario keeps its stable tag but now describes only the
existing isolated full-mode onboarding proof. Deployment, native collaboration and
physical human scenarios keep their separate mappings. When collecting a full-mode
TestApp log with `client/tool/acceptance_contract.dart`, use `--source test-app-full`;
its marker includes `activation=browser pkce=S256`. The prepared two-member delivery
path continues to use `test-app` and cannot supply first-owner activation evidence.

`tools/ios_dogfood_distribution_check.py` prevents retired private-plist tools,
app-only ledgers and their former success markers from returning to active callers.
The release-evidence aggregate runs the normal UI receipt/routing negative tests
and existing native qualification tests. Those offline checks validate the evidence
boundary; fresh protected execution is still required for a live pass.

The URL/certificate output from `tools/dogfood_handoff_bundle.py` is informational.
The old local iPhone installer, simulator onboarding gate, private preference
validators and owner-plus-member physical diagnostic branch have been removed.
No app preference, Keychain inspection, synthetic success marker or test-only reset
screen can establish qualification.

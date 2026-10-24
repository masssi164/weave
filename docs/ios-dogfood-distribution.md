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
4. optionally performs the explicit first-owner invitation setup, if requested;
5. establishes or verifies the activated owner's durable default Space and current access;
6. signs the already built device artifact locally, installs it in place and observes the
   installed app's ordinary startup through the prebuilt native driver.

The protected path does not compile missing applications on the Mac. A missing, incompatible
or mismatched artifact fails preparation. The Mac provides native execution, local signing,
pairing and installation; hosted builders provide compilation.

The Space prerequisite uses `compose.sh dogfood bootstrap-space --evidence-file <private-path>`
with the verified runtime composition. Server selects the configured organization's unique
enabled owner from live identity authority, applies current policy, and creates only a
never-existing `workspace-default`. Existing Spaces and grants are verified without modification.
Missing or revoked owner access, suspended Space state, ambiguous owners or unavailable readiness
fail closed. Temporary setup authority is removed on success and failure; preparation must not
silently regrant access.

First-owner invitation and activation are separate setup operations for an empty installation.
An invitation alone cannot prove owner Space access. Once that account exists, routine
preparation preserves it and does not require another invitation or registration run.
Activation links remain private in Mailpit at the reviewed `WEAVE_MAILPIT_URL`; the standard-port
Dogfood example uses `https://mail.weave.test`.

## What successful preparation proves

The support-safe `weave.hil-preparation/v4` receipt binds the current candidate, source tree,
specification pin, successful qualification run/attempt and verified artifact composition.
It records installed-binary and endpoint verification, current owner Space access, preserved
owner state, cleanup of the temporary driver, and a fresh nonce-bound ordinary-startup result.
The observed screen is either the retained workspace or normal sign-in.

`ownerHIL` remains `pending`. Personal sign-in, passkeys or MFA are the human's next normal app
action when needed. Preparation does not automate another owner login or infer authenticated
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
gh workflow run ios-dogfood.yml --ref dogfood -f candidate_sha=<full-dogfood-sha>
```

The preparation workflow itself must run at that exact protected commit. A matching title or
copied receipt cannot substitute for the trusted producer.

- The iPhone is unlocked, paired with `weave-live-mac-mini`, reachable over the reviewed network,
  and has Developer Mode enabled.
- The development signing key is accessible to the runner process.
- The Weave Local Development CA is trusted, and LAN DNS resolves the configured organization
  origin and its authentication host to the Dogfood installation.
- `WEAVE_IOS_DEVICE_ID` identifies the paired physical phone.
- `bootstrap_owner` defaults to `false`; enable it only for the explicit first-owner setup.

The first-owner path additionally uses the existing Dogfood member email secret and display-name
variable. App Store Connect and distribution secrets are unnecessary for development Dogfood.

## What the human verifies

Use the prepared app normally, retaining an existing session or signing in when shown. Report
the actual account role (`owner`, `admin` or `member`) together with the exact candidate and
installed artifact/build. A member test must not be recorded as an owner test.

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

Manual developer build and diagnostic scripts remain available for scoped investigation. Their
local output is not the protected hosted-artifact preparation receipt and cannot be substituted
for the qualified composition or human result.

Contract marker: `HUMAN_TESTING_HAS_NO_MANIFEST_GATE`.

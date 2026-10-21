# Release qualification implementation progress for #1592

## October 21 UTC: keep real conversations and messages primary on phones

[PR #1659](https://github.com/masssi164/weave/pull/1659) merged into Dev
`ba84c4f942f01af50b5c7891be2021723a4da6a3`, tree
`00567ed4169a2cecb648d50d4806a88a96840ec3`, with accepted corpus
`92ef89141f2b58ed8dd507b6b3df15f7ffd5b1de` unchanged. Its
[Core CI](https://github.com/masssi164/weave/actions/runs/39515864483) passed.
This integrates the input/history harness repairs below, not native acceptance.

A subsequent isolated inspection kept two independent real Flutter iOS apps and
their common real stack warm. Both users independently completed system-browser
PKCE and reached their own workspace. A created a conversation and sent content;
B received it and replied; A received the reply. After A's process restart, its
own session restored and actual timeline drags exposed the original ordered
messages beneath newer content. This was exploratory interaction, followed by
mixed-source Dart iteration, not a frozen-candidate qualification run. All seven
final aggregates remain not-run. The owned stack, two simulators and processes
were closed; independent inventories were zero. Startup, operation receipts,
passive observations and terminal result are retained privately. No password or
browser transcript is published. One post-relaunch host accessibility-export
observation remains unresolved; framework semantics getters alone do not prove
native accessibility or physical VoiceOver acceptance.

The bounded client correction removes the duplicate Chat overview hero and empty
Favorites section. Populated favorites, personal messages, channels, unread
metadata, creation, invitations, admission, errors and recovery remain. Explicitly
captured room decisions/evidence remain available through a localized, collapsed
standard Room details disclosure and the existing channel Decisions tab. Its
scrolling area shares remaining space with the timeline after the composer,
instead of reserving a percentage of the entire room. The composer placeholder
uses one line; typed messages retain the existing four-line allowance. When the
native list has no preview, the row says Open conversation rather than claiming
that a retained conversation has no recent messages. No extra history fetch,
client, authentication state or configuration mechanism is introduced.

Compact interaction defect record (ISO 9241-110 principles, not certification):

| Principle / task | Conditions and reproduction | Expected / actual before correction | Severity and correction | Verification |
| --- | --- | --- | --- | --- |
| Task suitability, learnability; member opens a conversation | 402×874 logical phone, EN/DE, text scale 1 and 2; one real room in the list | First conversation discoverable without passing duplicate overview/empty sections; previously below the initial viewport | Discoverability defect; remove duplicate hero and empty categories, retain populated groups | Four phone widget cases actually tap the row, open its saved identity and read the timeline |
| Controllability, task suitability; member reads and composes | Same phone, EN/DE, scale 1/2, keyboard inset 0/350; expand/collapse details with a draft | Messages and Send remain operable and draft survives; verbose context and multiline placeholder previously obscured the primary task | Mandatory interaction/accessibility blocker at increased scale; collapsed localized disclosure, flexible detail scrolling and single-line placeholder | Eight phone cases assert hit testing, semantic tap action, real timeline scroll when needed, preserved draft and actual repository send/readback; no overflow |
| Self-descriptiveness; member opens retained native history | Native conversation has no preview but contains messages | Absence of preview must not claim absence of history; old fallback said No recent messages | Misleading state; localized neutral Open conversation fallback | Four phone cases open a no-preview row and render retained history; actual warm inspection independently observed retained history |

All 12 new phone cases failed against the previous presentation. The corrected
Chat screen, room and existing dual harness suites passed **134 tests** in one
focused run, retaining authorization, draft isolation, retry, archive, provenance,
chronology and negative harness cases. Fixtures now use populated lists/longer
history where the regression specifically requires offscreen or lazy content;
they do not create outcomes for the real native scenarios. These are widget and
harness responsibilities, not backend or native collaboration proof. The next
frozen source still requires all seven real dual-iOS aggregates and the protected
Compose/macOS gates before deployment, physical preparation or HIL.

The first feature CI failed because the existing Home daily-loop mapping still
required literal copy from the removed Chat hero. Both acceptance conformance and
its client mapping regression reported the same three missing fragments. The
mapping now names the actual Home cross-app/semantic test and retained Chat
populated-group/unread behavior. It preserves the scenario and evidence mode;
it does not restore duplicate UI or turn offline evidence into runtime proof.

Focused real dual run `local-ios-focus-20261021-0049` on feature
`a83bbd5b7bb00472df774906c47775a04ac18c47` passed 32 initial/restored UI actions:
independent native PKCE, two-way Chat, shared Calendar create/update/cancel/delete,
native Files upload/save/cancel/replace and peer content, independent network
recovery, background send/catch-up, and both process-restored history readbacks.
It then failed before B's grant-revocation readback: the existing generated Space
journey reported **Stable File relation was lost after Space regrant**. All seven
final aggregates, including required encryption qualification, remained not-run.
No partial action success becomes acceptance. Cleanup completed and independent
owned container/network/volume/simulator/process inventories were zero; 20
original diagnostics were retained. A separate focused relationship diagnosis is
required before another protected candidate. This run does not qualify the
subsequently corrected mapping revision or replace mandatory macOS coverage.

Storage after owned inspection cleanup was 21 GiB free. Warm compiler caches,
original CI evidence, reviewed TLS and persistent Dogfood were preserved. No
additional persistent reset occurred. Existing comparable generation timings
below remain the bounded velocity evidence; this presentation correction claims
no new whole-CI percentage improvement.

## October 20 UTC: locate the background-send failure before another promotion

[#1657](https://github.com/masssi164/weave/pull/1657) integrated the bounded
same-member admission refresh repair. Normal [promotion #1658](https://github.com/masssi164/weave/pull/1658)
produced frozen Dogfood `7180e00446ac48cf125dd43d75eedf393f16bc6b`, tree
`8daa2d2d07f0687ca3ce6cd18b7a7aac6dc8d3b0`, with unchanged accepted corpus
`92ef89141f2b58ed8dd507b6b3df15f7ffd5b1de`. Its
[Full qualification](https://github.com/masssi164/weave/actions/runs/39501934667)
failed in 34m56s. macOS passed. Both independent real iOS apps authenticated and
completed actions 1–26, then B's background-send action failed. All seven final
aggregates remained not-run and deployment was skipped. The
[support-safe artifact](https://github.com/masssi164/weave/actions/runs/39501934667/artifacts/12303049582)
and original private native logs are retained. Nested dual cleanup=true and
outer cleanup=false remain distinct; independent exact-scope container, network,
volume and simulator inventories were zero. No physical, HIL or Main result is
inferred.

Three read-only observations bound to the live candidate, app process and loaded
source found B's expected message absent from its four-message model, with no
pending send, the room visible and foregrounded, and access not denied. The
receive request completed between observations. These are diagnostic snapshots,
not qualification or proof that the send reached the server. In particular,
composer-empty alone cannot establish successful input, send or persistence.
Unassociated scroll positions were not attributed to the room. The source of
missing input or missing history/projection remains under investigation.

A focused Flutter regression reproduced lost input after the platform closes its
connection while the test binding still remembers the same editor. Both accepted
and denied-send cases failed before the repository was called. The pinned
Flutter binding requests a keyboard only when its remembered editor changes;
reassigning the same editor does not reopen the closed connection. The existing
dual-iOS helper now actually taps the composer to reacquire current native input,
verifies that keyboard entry reached it, then rechecks immediately before the real
Send tap. It does not
retry or write the controller directly. Failure reports may read existing cached
native receive counters through the current stored profile, with before/after
binding checks and a five-second diagnostic bound. They do not restore auth,
synchronize, request history or acquire another cursor owner. Counts describe the
last profile-level receive observation, not a particular sent event. The existing
Python relay accepts only complete fixed boolean/count records, excluding private
fields, reasons and trailing text. Original failures survive diagnostic failure.

The lost-input regression failed with the previous helper behavior; the corrected
harness passed 41 tests, including both closed-connection cases, delayed successful
and denied sends, and input loss that must never tap Send. The native diagnostic
relay passed 78 tests and dual-runner contracts passed 41 tests. Changed-file
analysis passed after correcting one interpolation lint. These are
harness regressions, not executed native acceptance or a claim that the protected
background-send failure is fixed. The focused real dual-iOS reproduction and
remaining checks follow before another candidate promotion. Any changed source
requires fresh complete protected qualification.

The first focused local attempt on `97c092a4ff22ec5675eae899a066c4a45182f5a3`
failed before apps started: local storage cleanup removed compiler outputs while
cached hook/build metadata still referenced them. The original build failure was
retained; matching inactive iOS cache metadata was invalidated. A pinned simulator
build then passed, with actual arm64 and x86_64 native libraries verified. This
was an operator cache-recovery failure, not a product test result.

The fresh focused run `local-ios-focus-20261020-2247` passed 30 real UI actions,
including B's background send and A's foreground catch-up, before failing at A's
restored-product readback. The restored session reached Chat, but the harness
immediately tapped the saved conversation title after seeing the create button;
the title was not yet in the widget tree. All seven final aggregates remained
not-run. Native cleanup completed, and independent owned-resource inventories
were zero. Original private logs and results remain separate from qualification.

Restore readback now reuses the existing actual room-entry helper: navigate to
Chat, wait for the real room list, open the conversation and verify its saved
stable ID under the existing action deadline. Persisted message/file assertions
remain unchanged. Existing delayed re-entry regressions passed in English and
German, and changed-file analysis passed. Fresh real restored-product execution
and complete protected qualification are still required; the prior 30 actions
are not a final aggregate pass and will not be stitched into another candidate.

The fresh focused replay `local-ios-focus-20261020-2308`, on clean
`c630d87a610447267fbc0e70c75e61bed211da46`, again completed 30 initial UI
actions. Both applications restored their own sessions, and the corrected room
entry opened A's saved conversation. The next wait failed because the two oldest
message widgets were not found. All seven final aggregates remained not-run.
Twenty original logs/receipts were retained, native cleanup completed and
independent container, network, volume, simulator and known-process inventories
were zero. [Current-head Core CI](https://github.com/masssi164/weave/actions/runs/39513053518)
and its release-label gate passed; neither result qualifies native recovery.

A phone-sized regression reproduced the same oldest-message lookup failure with
all five expected messages supplied to the actual Chat widget. Its reversed lazy
list does not initially build the oldest row. This demonstrates a harness
assumption, not absence from the native message model. Restore verification now
scrolls the actual room timeline to the oldest pair, checks their rendered order
while both are visible, then reads each newer retained message. Saved room identity,
the original action deadline, bounded gestures, actual visible controls and the
existing Files identity/version/content assertions remain required. No history,
cryptography, authentication or product API behavior was changed. The complete
harness passed 51 tests, including English/German missing, reordered, wrong-room
and expired-deadline failures. Fresh real native verification remains pending;
no earlier partial actions are transferred to the changed candidate.

## October 20: admit a refreshed member without rebasing authority

[Promotion #1656](https://github.com/masssi164/weave/pull/1656) produced Dogfood
`e4bca3e60a1972cfe4f67c61178b47c3e5e08668`, tree
`3a29193f07667ae45cb9f2935fa16a88eaf143d2`, accepted corpus
`92ef89141f2b58ed8dd507b6b3df15f7ffd5b1de`.
[Full 39483251529](https://github.com/masssi164/weave/actions/runs/39483251529)
failed after 35m58s. macOS passed, and both real iOS applications completed 26
acknowledged actions, including Calendar cancellation/deletion, Files native
picker/export and peer content readback, authorization and independent network
failure/retry. B's following background send cleared its composer without the
expected message widget appearing. All seven final aggregates remain not-run;
deployment was skipped. The original
[artifact](https://github.com/masssi164/weave/actions/runs/39483251529/artifacts/12294442319)
and native cleanup flags are retained. Guarded finalization and independent
inventory verified zero resources owned by that run. These partial actions do
not qualify the candidate or permit HIL.

An isolated warm session on the same accepted source tree independently
onboarded two real Flutter apps through the organization URL and system-browser
PKCE. Actual UI actions proved two-way Chat, B sending while A was backgrounded,
A-only network failure with retained draft and visible retry, B remaining online,
peer readback and foreground catch-up. Additional short and long background
messages succeeded. The protected background-send failure did not reproduce;
its model, transport and viewport cause remains unproven.

The warm session separately observed B's room reporting the fixed
`M_WEAVE_MATRIX_SESSION_CHANGED` category. Visible Retry restored the same room
without another login. That category alone does not establish which identity,
configuration or bearer comparison changed. A focused regression then proved
that an ordinary same-member bearer refresh **during** admission is rejected by
the existing token-equality check, including after native activation. All five
new positive cases failed with that category before the correction.

The coordinator now restarts the existing admission once only for a private,
typed bearer-rotation signal, after confirming the current configuration and
member identity. The retry pins the original issuer, client, User API, Matrix
facade, subject and organization, repeats current server admission with the
fresh bearer, and recomputes the native session fingerprint. Changed
configuration is rejected before authentication restoration can contact it;
changed subject is rejected before server admission, and changed organization
before store/native side effects. Another rotation or any ordinary denial,
logout, restore failure or binding change fails closed. Device identity,
device proof, passphrase and encrypted state remain intact. Auth remains the
only bearer owner; this adds no refresh mechanism, cached grant, configuration
contract or general HTTP retry. This correction does not establish a fix for
the protected background-send failure.

The 53 coordinator tests and 49 existing Chat room tests pass together (102,
zero skips). Cases cover new/reused clients, rotation before/after asynchronous
admission and native activation, second rotation, concurrent identity/config/
organization changes, grant denial, and binding changes before retry restoration.
The initial combined invocation used a nonexistent Chat test path; its 53
coordinator passes were retained, but the command failed. The corrected invocation
uses the discovered existing path and passes all 102. Both warm apps accepted a
Dart reload; a subsequent actual A send committed and B visibly received it.
That dirty-source inspection is diagnostic only, with no release qualification.

| Responsibility | Existing entry point | Boundary and evidence |
| --- | --- | --- |
| Member admission decisions and lifecycle invariants | `client/test/integrations/rust_matrix_core/data/services/weave_member_matrix_session_coordinator_test.dart` | Fake auth/server/native ports exercise actual coordinator control flow, pinned retry authority, fresh admission, disposal and crypto continuity; this is not JWT validation or native E2EE proof. |
| Chat interaction and recovery state | `client/test/features/chat/chat_room_screen_test.dart` | Real Flutter widgets with repository fixtures protect drafts, denial, send/retry, lifecycle, history visibility and archive behavior; this is not real cross-user transport proof. |
| Integrated native collaboration | Existing `testApp` / dual-iOS runner | Two independent real Flutter apps, browser PKCE, generated User API and native Rust/Matrix against one isolated stack; only a complete exact-candidate run establishes all seven aggregates. |

Only an inactive, ignored x64 simulator compiler output with no open files was
reclaimed during this continuation: observed free capacity increased from
9.877 to 10.989 GiB. Active ARM64 assets, evidence, TLS, secrets and persistent
Dogfood data were preserved. Cold host native preparation was required after
prior cleanup. Two overlapping local Flutter commands collided at `lipo`;
both stopped, and sequential validation succeeded. These different build/cache
conditions are not a controlled runtime comparison. Keeping the same isolated
stack and two native processes warm avoided recreating them for the Dart change.

#1592, #1481 and #1470 remain open. The next gate is reviewed Dev integration,
then a frozen Dogfood candidate with complete Compose, macOS, dual-iOS and
owned-cleanup evidence. No deployment, physical installation, VoiceOver or human
acceptance is inferred from these local results.


## October 20: retain actionable restoration failures

[Promotion #1654](https://github.com/masssi164/weave/pull/1654) produced Dogfood
`a4deef967b803933e625601b1a53d90d58932fa2`, tree
`8d59f306471367a7f03b06b1ae2564fdbaa9630b`, with accepted corpus
`92ef89141f2b58ed8dd507b6b3df15f7ffd5b1de`.
[Full 39373908966](https://github.com/masssi164/weave/actions/runs/39373908966)
failed in 18m19s during restored macOS bootstrap after the guarded service
restart. Initial macOS journeys passed, but final macOS qualification failed;
iOS and all seven dual-iOS aggregates were not run, and deployment was skipped.
The [original artifact](https://github.com/masssi164/weave/actions/runs/39373908966/artifacts/12243402081)
and original false native cleanup flag are preserved. Guarded teardown and an
independent inventory verified zero owned resources. The retained bootstrap
marker identifies an inner `AppFailure`, but does not establish its HTTP status,
a timeout, decoding failure or a token-expiry cause.

A fresh isolated inspection on clean Dev
`7a9755d5ab26ed702901a91fd9a58a8da1441a65` (the same tree and pin) independently
onboarded both real Flutter iOS applications through the organization URL and
system-browser PKCE. Both reached their workspaces. After the existing guarded
PostgreSQL/Keycloak/backend restart, both restored after Dart restart and after
separate native process relaunches. Read-only observations matched the loaded
bootstrap sources to disk and found no bootstrap error state. This probe does
not reproduce the macOS failure, establish all seven scenario aggregates, or
qualify this candidate. An initial A discovery error followed a truncated
operator-entered URL; verifying and submitting the complete URL succeeded.
That attempt is not evidence of a product discovery regression.

The existing native diagnostic now shares one bounded typed-cause classifier
between authentication and bootstrap reporting. It retains fixed backend failure
categories and HTTP status values from 100 through 599, without error text,
response bodies, credentials or URLs. The iOS relay accepts only the complete,
ordered, bounded record. Reporting failures still preserve the original test
failure. Authentication, current-grant authorization, bootstrap contracts,
release gates and timeout budgets are unchanged. No speculative token refresh
or retry was added.

The four existing temporary pure-Dart probes now execute from their own temporary
directories. Running one from the Flutter package directory dispatched native
hooks and timed out at 30 seconds. With only its working directory corrected,
the intended missing-diagnostic assertion failed in 0.630 seconds. After the
correction, five focused diagnostic checks passed in 1.395 seconds, and all 118
native-runner regressions passed in 14.623 seconds with zero skips. This is one
local observation on Apple Silicon with Flutter 3.41.6/Dart 3.11.4; it is not a
controlled whole-CI speed comparison. Native runtime and ABI qualification still
use the ordinary native runners. The x64 simulator and macOS native caches were
cold after storage cleanup; ARM64 simulator assets were retained.

Only inactive generated caches and the explicitly owned isolated inspection
resources were reclaimed. Certificates, reviewed secrets, Git history, release
evidence and persistent Dogfood data were preserved. Closing the owned simulators
returned observed free capacity to approximately 27 GiB. The inspection result
is `closed`, `cleanupComplete=true`, `qualificationPassed=false`; no release pass
is inferred from local exploration.

The next required proof is a focused macOS restoration run with the nested
failure diagnostic, followed by a demonstrated conformance correction if it
fails. A new frozen candidate still requires complete Compose, macOS,
concurrent dual-iOS and owned-cleanup qualification before deployment, physical
preparation or HIL. #1592, #1481 and #1470 remain open.

## October 20: keep an explicit Chat send visible

[Promotion #1652](https://github.com/masssi164/weave/pull/1652) merged as
Dogfood `28eefafe5761404cec92ff83110e348df55bea48`, tree
`53e941723771557b7887cfd2ea76eba848cfc564`, accepted corpus
`92ef89141f2b58ed8dd507b6b3df15f7ffd5b1de`. Its
[protected Full run](https://github.com/masssi164/weave/actions/runs/39361875201)
failed after 34m20s; deployment was skipped. macOS passed. Two concurrent real
Flutter iOS apps completed 26 acknowledged actions, including the repaired
network interruption, retry and peer readback. B's next `background-peer-send`
cleared its composer but timed out waiting for the message widget. All seven
final aggregates remain not-run. The original
[artifact](https://github.com/masssi164/weave/actions/runs/39361875201/artifacts/12238707113),
private diagnostics and original cleanup flags are preserved. Exact finalization
and independent inventory verified zero owned resources; no persistent Dogfood
reset occurred.

A warm inspection on Dev `9fe85cd5402f55dea1feb48aa1458fbaedec2299`, the same
tree and pin, independently established a viewport defect. After reading older
history, a real Flutter send cleared the composer while the new message remained
outside the rendered list. Read-only observation found exactly one matching
message in the live model, an active foreground room, no pending send, and the
reversed list 375.64 pixels from newest. Actual scrolling revealed the committed
text without resending. The warm background send itself succeeded before the
correction, so the completed CI process's precise cause remains unproven; its
missing widget alone does not establish a transport or cryptographic failure.

The room now owns and disposes one timeline scroll controller. An explicit send
or retry reveals its pending message after layout, guarded by the existing
current authority, visible foreground room and active-timeline checks. Routine
receive reads do not force the reader to newest. No transport, authorization,
cryptographic state, retry semantics or public contract changes are introduced.

Four EN/DE phone-and-keyboard cases fail before this correction and pass after it.
They scroll through long history, retain a history view during an incoming
update, send and retry through usable controls, and require the pending and
single committed message to be hit-testable with the composer cleared. All 49
Chat room tests pass, including existing denial, revocation, draft, lifecycle
and archive cases. The initial local host native-asset preparation took about
4m50s before test output; the cached four-case regression executes in 3 seconds.
These different cache conditions do not establish a build-speed improvement.

| UX defect | Conditions and reproduction | Expected / actual | Correction and verification |
| --- | --- | --- | --- |
| Self-descriptiveness, conformity with expectations, controllability and use-error robustness; member sends from history; collaboration observation blocker | Actual native iOS history reading and Send; EN/DE 390×844 widget viewport with 280-pixel keyboard, including failed send and Retry | Show the explicitly submitted pending/result message / composer clears while the message remains outside the lazy list viewport | Reveal explicit sends after layout; four behavioral cases and all 49 room tests pass. Warm real B sends and retry from older history show their pending/result immediately with one model copy. Both apps' loaded source is verified; mixed-source inspection does not qualify a release. |

The existing owned inspection kept both real apps and their isolated stack warm
for these iterations. Both users independently signed in through the actual
system browser and exchanged messages through Flutter. A-only interruption and
retry, B-only interruption and retry, and B sending while A was backgrounded
were exercised. After loading the correction, B's explicit send and retry from
older history immediately showed their pending/result; A visibly received the
background message after foregrounding. Both live models contained one copy,
with verified loaded source and no pending send. An earlier independent A process
restart restored its session and persisted conversation while B's process stayed
unchanged. These are diagnostic interaction results, not seven completed final
scenario aggregates or physical VoiceOver acceptance.

Fresh exact-candidate Compose, macOS and all seven concurrent dual-iOS aggregates,
owned cleanup, deployment, physical preparation and explicit owner HIL remain
required. The release issues remain open.

## October 20: recoverable Chat admission failures

[Promotion #1649](https://github.com/masssi164/weave/pull/1649) merged as
Dogfood `bac702071e642366b979e942765bb5047c3c7a03`, tree
`7973149f983ea691ae93d6c2d60f21b78164861f`, accepted corpus
`92ef89141f2b58ed8dd507b6b3df15f7ffd5b1de`. Its own
[Core run](https://github.com/masssi164/weave/actions/runs/39350582484) passed
8 jobs with 7 valid impact skips. The subsequent
[protected Full run](https://github.com/masssi164/weave/actions/runs/39352191876)
failed after 35m00s; deployment was skipped. macOS qualification passed, including
actual browser sign-in, Files/Calendar/native Matrix, process/service restart,
expired bearer, refresh, room-leave/logout denial and owned preferences cleanup.

Two concurrent real Flutter iOS apps independently authenticated and completed
22 acknowledged actions: two-way Chat, Calendar create/update/cancel/confirmed
deletion and peer readback, native Files upload/Cancel/exports/replacement/actual
peer content checks, both authorization checks and both Chat re-entry actions.
A's `network-disconnected` action then timed out waiting for the failed-send
banner. The independent generated User transport probe was not reached. All seven
final aggregates remain not-run; partial actions do not qualify the candidate.

The original private Flutter log identifies `operation=send stage=open
type=sessionRequired code=M_WEAVE_MATRIX_ACCESS_UNCONFIRMED`. Current admission
must fail closed, but its transport/service failure was incorrectly treated as
session loss. The existing Chat handler consequently cleared the room and pending
message instead of offering its ordinary recoverable send action. The correction
uses the existing protocol-failure category for unavailable admission, preserving
session denial for HTTP 401/403 and every existing identity/grant mismatch. It
does not admit a failed check or bypass the Rust/session boundary.

Eight pre-fix generated-client regressions failed at the two existing admission
operations. All 19 contract cases pass after the correction, including transport,
timeout, service and unchanged denial cases. This pure contract test now uses
`package:test` through the existing pure Dart entry point, avoiding native builds
for direct reproduction. Native/ABI and actual Flutter qualification remain on
their existing mandatory runners. The EN/DE phone-layout regression exercises a
real generated HTTP 503 classification, retained draft/timeline, localized live
feedback and an actually hittable Retry while the keyboard occupies 280 logical
pixels. Both localized cases and all 119 affected Chat/admission/session tests
pass. Actual warm recovery replay is still required.

The keyboard regression also reproduced a layout blocker: secondary context
cards plus the send-error banner overflowed the room by 5.2 logical pixels in
English and 122 in German. Collapsing the timeline's context preview alone left
14 pixels of German overflow. The room now collapses its two secondary context
summaries while an outgoing message has failed; title, workspace tabs, timeline,
composer and recovery actions remain. The existing Decisions surface still owns
its detailed content. Successful retry restores normal context. The regression
actually scrolls to prior messages and back, preserves the unsent text, taps the
visible localized Retry, and verifies restored context without a layout error.

| UX defect | Conditions and reproduction | Expected / actual | Correction and verification |
| --- | --- | --- | --- |
| Self-descriptiveness, controllability, use-error robustness; member A Chat; mandatory recovery blocker | Real iOS phone layout, English; interrupt A's owned relay, enter a message and Send | Retain current content and unsent text with actionable Retry / admission logs session-required and the failed-send banner never appears | Preserve fail-closed admission while distinguishing confirmed denial from transport/service unavailability; 19 generated-client cases and both EN/DE keyboard regressions pass. Actual native recovery remains required. |
| Task suitability, conformity with expectations, controllability; member retries Chat; accessibility blocker | EN/DE 390×844 logical phone viewport with 280-pixel keyboard; send fails | Keep history, draft and recovery controls reachable / context cards and banner overflow and obscure the pending message | Collapse secondary summaries during the error, retaining detailed context on its existing surface. Actual scrolling, semantic feedback, hittable Retry and context restoration pass in both localized widget cases; no physical VoiceOver acceptance is inferred. |

[Support-safe evidence](https://github.com/masssi164/weave/actions/runs/39352191876/artifacts/12235265646)
and 19 original private diagnostics/receipts are preserved. The original native
artifact records macOS passed, iOS failed, all final aggregates not-run, nested
dual cleanup true and outer qualification cleanup false; none was rewritten.
The exact finalizer and independent inventory verified zero owned containers,
networks, volumes and both simulators. No persistent Dogfood reset occurred.
New-source qualification, exact deployment, physical preparation and explicit
owner HIL remain required before Main. The release issues remain open.


## October 20: owned iOS transport interruption and native Files cancellation

Chat re-entry PR1648 merged normally to Dev
`5d2173a4d904e5328d27264b7f61a361f039fcfd`, tree
`93fe2382cfe4744d69831d36cc5c0a1e909cd3a3`, accepted specification
`92ef89141f2b58ed8dd507b6b3df15f7ffd5b1de`. Its own
[Core run](https://github.com/masssi164/weave/actions/runs/39342304927) passed
8 jobs, with 7 valid impact skips. A clean local full replay on the same tree
passed macOS qualification and 21 actual dual-iOS UI actions, including both
repaired Chat re-entry actions. Actor A then failed the independent generated
User Files request assertion during `network-disconnected`: the native failed-send
banner and Retry were observed, but the HTTP request unexpectedly succeeded.
All seven final aggregates remain not-run. Original failure and cleanup flags
are retained; guarded finalization and independent inventories verified zero
owned resources. No persistent Dogfood reset occurred.

The real warm inspection reproduced the transport configuration discrepancy in
both apps: the native processes have all six reviewed proxy/bypass settings,
while iOS Dart exposes an empty `Platform.environment` and resolves the User API
to `DIRECT`. The qualification harness now reads only those six native settings
through the existing FFI dependency. It requires matching HTTP/HTTPS relay
values, the exact loopback host and valid port, and present empty bypass lists.
Missing or inconsistent configuration fails closed. The ordinary HTTP client
and TLS context remain intact; only proxy selection changes. The owned actor
lifecycle installs it before app clients and restores the previous override on
test teardown. Inspection ends with its owned process. No product endpoint,
bootstrap contract, public setting or second relay authority is introduced.

After an eligible Dart restart in the warm stack, actual A Files refresh failed
during A-only interruption; B continued loading Files; A retried successfully
after restoration without sign-in. These are mixed-source diagnostic results,
not qualification. A real loopback HTTP test independently proves routing and
failure when the relay closes. The 59-test integration-harness suite and 41
coordinator tests passed. The 76 native qualification contract tests passed
from their repository-root entry point; invoking them from `client/` first
failed three stdout assertions because Dart dispatched package build hooks.
No assertion was weakened to accommodate the wrong working directory.

Files qualification now requires an actual native Cancel before its existing
successful exports. It observes exporting/busy before accepting idle/not-busy,
requires no destination or new document, and verifies an exclusively created
unrelated owned sentinel remains byte-identical after Cancel and Save. Existing
actual selected-destination and initial/replacement content checks remain.
The native driver follows unique system navigation controls to Browse and
requires actual cancellation/dismissal. The coordinator requires the exact
selected XCTest pass and marker, zero skips/opposite markers and unchanged app
PID within the original deadline. A newly built driver completed Cancel against
the warm real app; this isolated picker observation does not establish Files
collaboration or final scenario acceptance.

| UX defect | Conditions and reproduction | Expected / actual | Correction and verification |
| --- | --- | --- | --- |
| Self-descriptiveness, use-error robustness; member A Files; mandatory recovery blocker | Real iOS phone layout, English; interrupt A's owned relay and refresh the current folder | Preserve listing and offer Retry / falsely claimed the Weave session expired, although Retry recovered without sign-in | Files and Calendar identity reads retain fail-closed admission, classify 401 as session and 403 as permission, and treat other transport/service failures as unavailable. Files uses existing EN/DE retry guidance. Generated-client regressions first reproduced six wrong classifications; all 60 affected repository tests now pass. EN/DE phone-layout widget tests verify the visible, operable Retry and preserved listing; actual warm English UI shows the corrected guidance and recovers without sign-in. Final exact-candidate recovery remains to be verified. |

[Promotion PR1649](https://github.com/masssi164/weave/pull/1649) remains held.
Its own [Core run](https://github.com/masssi164/weave/actions/runs/39343663172)
passed 8 jobs with 7 valid impact skips on the earlier Dev head; this does not
qualify these changes. A clean final candidate must complete current Compose,
macOS and all seven concurrent dual-iOS aggregates, owned cleanup, deployment,
physical preparation and explicit owner HIL before Main. The release issues
remain open. No result is stitched across changed candidates.

## October 20: bounded Chat re-entry after protected qualification

[Promotion #1639](https://github.com/masssi164/weave/pull/1639) merged normally as
Dogfood `a613476238fc34212485a1c5b75a2d21dc88095f`, tree
`2a5ae0c8644594cd95a25ab6d6b6395e616e3fb0`, accepted specification
`92ef89141f2b58ed8dd507b6b3df15f7ffd5b1de`. Its own
[Core run](https://github.com/masssi164/weave/actions/runs/39334742426) passed all
15 jobs. The subsequent [protected Full run](https://github.com/masssi164/weave/actions/runs/39336866478)
failed after 33m22s; deployment was skipped. macOS qualification passed. Two
independently authenticated real Flutter iOS apps completed 19 UI actions:
two-way Chat, shared Calendar create/update/cancel/confirmed deletion and peer
readback, Files upload/replacement/content readback/native exports, and both
authorization checks. Actor A then failed `network-ready`: expected one room-title
widget after Chat navigation, observed zero. All seven final aggregates remain
not-run. Partial actions do not qualify the release.

The original failure is retained privately. [Support-safe evidence](https://github.com/masssi164/weave/actions/runs/39336866478/artifacts/12228680899)
includes successful exact persistence-restart proof. Guarded finalization and
inventories verified zero owned containers, networks, volumes and simulators.
The original outer qualification cleanup flag remains false; it was not rewritten.

The existing re-entry helper immediately tapped the room while the actual Chat
list loads asynchronously. New English and German regressions reproduced the
same zero-title failure against that body. Re-entry now reuses the existing
bounded list-readiness helper and real tap, waits for the actual room/composer
within the original action deadline, and checks exact room ID/title on fresh and
already-open paths. Wrong-room and expired-deadline cases fail. The 35-test
Chat-readiness suite and 41 existing dual-runner tests pass locally. These prove
the harness correction, not native qualification. The existing coordinator emits
a fixed phase/actor/action/sequence start marker before each attempt, retaining
the actionable boundary without exposing resource contents or private identities.

This slice changes only the native harness, its directly affected tests and this
record. Product, authentication, bootstrap and specification contracts are
unchanged. Real isolated replay, current-source qualification, deployment,
physical preparation and explicit owner HIL remain required before Main.
The release issues remain open. No persistent Dogfood reset occurred.

## October 19: real warm collaboration and native navigation corrections

[Import-only PR1645](https://github.com/masssi164/weave/pull/1645) merged normally to
Dev `e64882dd7e32b85d167dfdb942c55fe6ad26e89a`, preserving reviewed tree
`89b55e660db8ba9b3237daa41903f56d0a6f93af` and accepted corpus
`92ef89141f2b58ed8dd507b6b3df15f7ffd5b1de`. Its [exact-head Core run](https://github.com/masssi164/weave/actions/runs/39324749377)
passed all15 jobs. The accepted corpus [Main run](https://github.com/masssi164/weave-specs/actions/runs/39322106333)
passed corpus, bundle and signing checks. No equivalent integration qualification is implied.

Warm local inspection `local-cua-20261019-2105` built clean reviewed `c7597253824340044011c92ace93ff2aa423b45c`
with that same tree and pin. Before native entry, real invitation/browser PKCE, generated User/Admin
operations, Matrix exchange, foreign-organization denials, generated/real Calendar MCP operations,
revocation/refresh denial/regrant and ordinary PostgreSQL plus dependent Keycloak restart passed.
The same runtime-state container and volume retained the exact fixture across restart. No post-start
identity repair was applied. These component results do not establish native acceptance.

Two concurrent independent real Flutter iOS apps then independently authenticated through the
system browser. Computer Use observed A create a direct conversation and send unique content,
B receive and reply, and A receive the reply once in order. A created and updated a shared workspace
Calendar event; B retrieved both states through normal range navigation. Cancel preserved the event
for both after server readback, and confirmed deletion removed it for both. A used the actual native
picker to upload and replace a shared file. B displayed the actual initial48 and replacement52 bytes.
B cancelled native export and recovered usable controls, then saved through the picker; the owned
destination exactly matched replacement bytes (SHA256 `e23a7b4a0a1f29ef8e7a2eaac12f0973c4af4895051dc8b2c8547e8aab5d6f34`).
EN/DE controls were observed; touch scrolling reached Files actions. This mixed-source exploratory
session never qualifies a candidate and does not establish physical VoiceOver or human acceptance.

| Observed defect | Correction and verification boundary |
| --- | --- |
| User control/error robustness; pre-auth member A, English and German, normal phone display; entering the organization URL reached ready state without an operable return path; mandatory-task recovery blocker | Organization entry now pushes the handoff, retaining its form. A localized Back control pops it or returns a direct invitation/error to public entry. Existing saved-profile cold start still opens Sign In; protected product routes remain gated. No configuration/session reset or second bootstrap is added. Actual A Back preserved the full host/port, continued and authenticated. EN/DE router regressions cover ready/error/direct entry, profile retention and protected routes. Shared/local sign-in busy states disable conflicting navigation. |
| Inspection lifecycle; independent process restart A; infrastructure blocker | The old relaunch stopped `flutter run`, which could terminate its app, before validating that app's PID. The run failed `process-restart: initial-process-identity-changed`; no recovery pass is claimed. Relaunch now verifies/terminates the exact app before stopping its resident and starts a distinct process with the retained owned fixture. The existing lifecycle regression verifies ordering, unchanged B, retained A storage and explicit nonqualification. Real replay remains required. |

The original inspection failure is retained. Existing teardown and independent Docker/Simulator
inventories confirmed zero owned containers, networks, volumes and simulators. Persistent Dogfood,
operator TLS and reviewed secrets were untouched. The native Calendar delete journey now cancels
first, rereads the same event/version, then confirms deletion through the existing controls. This
adds behavioral coverage within the existing scenario, without a new framework or acceptance skip.

Warm iteration reused the same stack and apps for three acknowledged Dart reloads: 1.450s,485ms
and349ms, one sample each with the existing native/dependency cache. The first value uses German
thousands formatting. Loaded-script digest verification was explicitly false; these observations
show iteration without repeated bootstrap, not a comparable full-CI speedup. Final exact-candidate
Compose, macOS, all seven dual-iOS aggregates, deployment, physical preparation, HIL and Main are
still required. The release issues remain open and promotion PR1639 remains held.


The first import-only local inspection (`local-cua-20261019-2053`, source `4c5e4f0068563878005b38481ffc06cd75670882`) stopped before Compose startup: the release-MCP workload placeholder depended on a directory previously created implicitly by migration preparation. The runner now invokes the existing Compose `configure` entry point after empty-namespace proof and teardown arming, before creating that placeholder. The original failure and successful exact namespace cleanup are retained; neither simulator launched and no native acceptance is claimed.

## October 19: import-only bootstrap and bounded pure-generation correction

The corrected `584b4569...` inspection (`local-cua-20261019-2056`) brought up the isolated stack and passed real curated workload registration. The actual invitation/browser PKCE then failed the unchanged `native-organization-id` assertion before native execution. Exact cleanup removed all owned resources. This exposed another responsibility previously hidden in post-import migration: configuring Keycloak's native organization mapper to include its ID.

The renderer now imports the pinned Keycloak26.7.1 stock scope projection explicitly in Dev/Dogfood/E2E, with automatic default-scope creation disabled. Keycloak's importer creates defaults before custom scopes and does not update an existing scope; importing another `organization` scope would duplicate its identity. The public projection was obtained by offline import/export from the already pinned stock image `sha256:7523ccfbd950f59783504cdf5a0138dae48746dfe36075bbfccdb5a9ee245ee2`, without network, credentials or product services. Runtime-generated IDs are excluded. All15 stock scopes, their default/optional membership and offline-access role mapping are retained. Only the native organization mapper is supplied from the existing reviewed migration-definition desired state; there is no duplicated mapper configuration, new realm baseline or startup mutation. Tests compare every other scope unchanged and reject version/duplicate-scope mismatches. Production keeps its existing stock-default creation and restricted post-import migration. This compatibility projection must be refreshed deliberately with a future Keycloak version update.

Pure-generation PR1644 normally merged to Dev `7131db5a9248b9fe58cfc491e4877a2e97a59b2d`; its exact Core39321922006 passed all15 jobs in13m41. Hosted generation reported37s/10 outputs and Flutter job11m20, under different runner/cache conditions from local probes. No whole-CI causal percentage is claimed. This branch incorporates that accepted Dev change without rewriting history.

The owner completion brief starts from accepted Dev `5fb1cf6319d27cbba51dd6b6dcd8eab1dac3ffe4`.
PR1643's three instruction/template changes are preserved. Existing promotion PR1639 is draft
while these foundation changes and actual collaboration qualification are completed; its earlier
`ef41d64a...` deterministic result remains historical evidence for that source.

The accepted DevOps import-only requirement conflicted with readiness schema5's migration-derived
semantic-readback fields. Corpus PR159 merged normally as `92ef89141f2b58ed8dd507b6b3df15f7ffd5b1de`
after actual corpus conformance, bundle signability and independent frozen review. This branch
pins that accepted revision. Schema6 records stable import-render provenance without claiming live
provider readback. Authentication, permitted identity operations, restart, mandatory native
qualification, deployment, physical preparation and explicit human acceptance remain required.

The implementation removes the E2E migration dispatcher and executor, includes E2E in the two
permitted identity-admin development role grants, restricts migration receipt startup and writable
migration ownership to production, and removes obsolete Dev/Dogfood migration overrides. Production
keeps its restricted migration/backup/receipt boundary. E2E retains the current reviewed runtime
image and workload policy needed by its existing curated MCP proof. No realm, persistent Dogfood
state, operator TLS or reviewed secret is deleted. Startup import skips an existing realm;
changed generated JSON is not an incremental migration.

Direct evidence producers/consumers now share the import-provenance validation. The readiness
schema loader reuses the existing exact corpus resolver instead of ignoring its accepted worktree
coordinate. Unit fixtures are version6 and are not represented as live identity or HIL evidence.
The existing [test strategy](../testing/core-test-strategy.md) maps each affected suite to its
entry point, meaningful boundary and evidence; native and generated-client consumers remain
separate obligations.

Executed focused validation: production/development migration boundaries, realm renderer,
Compose profile graph, seven realm-evidence cases, two runtime-image evidence cases, 43 automated/
readiness/assembler cases, product-flow lifecycle contracts, pinned corpus157, acceptance,
complete infraStatic and strict docs all passed. Initial stale receipt assertions and the old
schema-path failure were repaired; original failing logs remain private. Real imported Keycloak
bootstrap, ordinary restart and warm two-app interaction are the next runtime checks. No new
all-seven qualification, deployment, physical installation or HIL success is claimed here.

Independent pure-generation PR1644 uses public build_runner2.13.1 `--force-aot` with the pinned
Flutter3.41.6/Dart3.11.4 toolchain. The JIT control entered Cargo and was stopped at1.100s. First
builder AOT compilation passed12.386s; warm generation passed1.102s. A real Riverpod input rename
regenerated its provider in6.159s; exact input restoration/regeneration passed5.738s and restored
all998 tracked client/Rust file hashes. AOT probes observed no Cargo/rustc and no rejecting-wrapper
calls. Each timing is one local sample with the existing dependency/asset cache; the first AOT run
compiled the builders, and the mutation actually regenerated output. The stopped JIT run is not a
complete latency baseline or a whole-CI improvement percentage. Native hooks and tests are unchanged.


This is an implementation and evidence record. The pinned specification corpus remains normative. No automation or human result is implied by a plan or a merged specification.

## Current checkpoint — October 18, 2026 UTC

The implementation pins accepted corpus `95c51221e932e1b92f637330aaf7a51f2372abd9` from [spec PR #158](https://github.com/masssi164/weave-specs/pull/158). It records the owner's acceptance of private corpus hosting without server-enforced branch protection. Reviewed PRs, green CI, traceable accepted merges and immutable consumer pins remain required; a paid plan or public visibility is not a release prerequisite. Implementation branch protection and all product qualification, deployment, physical and human gates remain unchanged. [Accepted corpus Main CI](https://github.com/masssi164/weave-specs/actions/runs/39150856584) passed all three jobs, including signing and tamper rejection.

[PR #1634](https://github.com/masssi164/weave/pull/1634) merged normally into Dev as `aab9b252352f2a2250ba6842b1924323ab7ac271`, preserving reviewed source tree `e0e8db039bf8afae96bcf0eaa4a270f26743550f`. [Exact-head Core CI](https://github.com/masssi164/weave/actions/runs/39149030297) passed all 15 jobs on `07d3d07490dd2d396ed12cc08ae4ac38b59040ff`: 844 Flutter tests passed, with zero failures and one ordinary offline-only skip; the separate offline command passed all four cases. All three temporal zones passed seven DTO and two Home cases each. The final test-only corrections keep Home's event inside its rolling window and select the current organization-input label; their assertions and application behavior remain unchanged.

Focused actual-app replay `local-cua-20261018-1612` used clean `d0a392e3cc956b5c6e64e7f0dbff1555754938bb` with pin2c35. Two independent real iOS apps completed URL/invitation browser sign-in, actual Files 48/52-byte upload/replacement and peer readback, native export to a new destination and an operable selector after preview/scroll. Selected native background/foreground tests passed in 1.585/0.092 seconds. Inspection closed with its deliberate nonqualification result; original diagnostics, independent zero-owned-resource checks and finalization are retained. These focused observations do not establish complete Chat, Calendar, authorization, recovery or accessibility aggregates.

[PR #1636](https://github.com/masssi164/weave/pull/1636) promoted accepted Dev through a normal protected merge to Dogfood `5f96518536f123b2d0f85dec398d1ab4727106e7`, tree `c2ff890231db4f7feadb3f213101be09a511873f`, with the accepted specification above. [Full qualification, attempt 2](https://github.com/masssi164/weave/actions/runs/39154529584/attempts/2) failed; deployment was skipped. Final native macOS acceptance passed. Two concurrent independent real Flutter iOS applications completed 15 UI actions: three Chat exchanges, four Calendar create/read/update actions, and eight Files upload/export/peer/replacement actions. The next authorization exchange did not finish. Calendar deletion and later recovery were not reached; none of the seven final iOS scenario aggregates passed.

The original exception was replaced by a Java `finally` cleanup error after 90 seconds. The nested Python runner also allowed 90 seconds for child cleanup and did not reap its forced termination before final evidence construction. The public artifact therefore lacks the outer native qualification JSON. Its retained private dual result reports failure, seven not-run scenarios and completed device cleanup. Independent inspection confirmed zero owned processes, simulators, containers, networks and volumes. These observations preserve the failed qualification rather than upgrading partial actions to acceptance.

The focused harness correction preserves the first exception and cause, attaches cleanup errors as suppressed failures, attempts all owned private-file removals and reuses the existing bounded process-tree termination helper after graceful shutdown. The existing Python owner gives its child 60 seconds for graceful finalization and 10 seconds for forced termination and reaping, within Java's existing 90-second wait. It collects failed platform and collaboration results during cancellation and writes the final artifact without inferring scenario success. Forced or unverified cleanup remains a failure. No product authorization, bootstrap, API, UI, acceptance threshold or release gate changes.

Focused regressions exercise an actual termination-resistant child, first-failure preservation despite process and private-file cleanup errors, an already-interrupted Java caller, and valid, missing, malformed or unreadable collaboration results during Python cancellation. These are deterministic harness tests, not native product evidence. The changed source requires a new protected candidate and fresh complete qualification. HIL and Main remain unavailable until their real evidence gates pass. The October12 persistent Dogfood reset is complete and must not repeat.

### Native authorization fixture respects User and Admin session boundaries

[PR #1637](https://github.com/masssi164/weave/pull/1637) merged normally into Dev as `a70fbef6fc81bb50c011fb07d5838f595ac9def9` after [exact-head Core CI](https://github.com/masssi164/weave/actions/runs/39160040515) passed 11 selected jobs with four valid impact skips. Local clean-source replay `local-qual-20261018-1945` on reviewed `4b8ce3ddd5a9974f911cec27afcbe088d2a7d6b8` and accepted95 passed final macOS acceptance and repeated the 15 actual iOS UI actions. It then failed with the preserved original HTTP401 from the authorized file-version read. The initiating exception from protected Full attempt2 remains unrecoverable; this local failure is separately verified. The final native JSON was retained: macOS passed, iOS failed, all seven aggregates not-run, nested device cleanup true and outer cleanup false. Independent inspection and the guarded exact-run finalizer confirmed no owned resources remain. None of these failure flags were rewritten.

The native authorization harness had authenticated `weave-admin-console` for baseline Files/Calendar reads. The accepted User API requires `weave-app`; the server correctly rejected the Admin token. The correction obtains a fresh owner User session through the existing real browser, validates workspace claims and the original A identity, then uses that session for all four baseline/readback requests. Third-identity read/mutation denials and strong-version/content checks remain unchanged. Admin grant/revocation operations continue to use the separately admitted Admin session. The helper rejects Admin/workload sessions before issuing any User request. No server admission or product contract changes; the new replay and protected qualification remain required.

### Native driver fixture delivery is independent of bundle installation

[PR #1638](https://github.com/masssi164/weave/pull/1638) merged into Dev as `9d9e92747aa9f20658fe7f00bd1249aceeeb4d15` after [exact-head Core CI](https://github.com/masssi164/weave/actions/runs/39162697208) passed ten selected jobs with five valid impact skips. Local clean-source replay `local-qual-20261018-2021` on reviewed `1898857d8df9fb640dbd2571f9f162b234aecc2c`, tree `24086a72770c04122d31dd783f8424703c19bd30` and accepted95 passed final macOS acceptance and fourteen actual iOS UI actions. B opened the updated file, but its next native export test skipped because the installed driver did not load the document-picker phase. Xcode returned zero; the existing no-skips verifier correctly rejected it. The authorization correction was not reached in this run. All seven final iOS aggregates remain not-run. Original failed evidence is preserved; independent inventories and the guarded exact-run finalizer confirmed zero owned resources. [Promotion PR #1639](https://github.com/masssi164/weave/pull/1639) remains held while this failure is corrected.

The driver previously read invocation data from a resource repeatedly rewritten inside its compiled XCTest bundle. A shared helper now atomically publishes one actor-private `0600` fixture outside that installed bundle and passes only its path through the standard `.xctestrun` test-host environment. It validates the single owned BrowserDriver target, preserves unrelated environment entries and rejects malformed plans and unsafe fixture paths. Authentication, picker and lifecycle invocations all use this one transport; Swift requires the explicit private file with no bundled fallback. This removes dependence on bundle resource replacement being reinstalled. It adds no product configuration, discovery endpoint, authentication bypass or orchestration layer. Actual gestures, selected-test execution, no-skips guards, content checks, process identity and deadlines are unchanged.

Deterministic regressions cover both supported Xcode plan formats, successive invocation data, independent actor paths, preservation of existing environment, credentials absent from plans, malformed/ambiguous targets, symlinks and interrupted publication. The shared native suite passed 76 regressions and the dual suite passed 41. A real owned iOS Simulator then executed seven selected fixture-delivery tests across separate A/B plans and successive initial, document-picker and lifecycle phases, plus the unsafe-file Swift regression; all eight selected tests passed with zero skips. This verifies fresh standard test-host environment delivery without reinstalling fixture resources. The owned simulator was deleted and independently listed absent. It is driver-transport evidence, not Flutter collaboration acceptance; a fresh complete native replay and protected qualification remain required. No new normative specification is needed for this implementation conformance fix.

### Calendar removal returns from Files through normal navigation

[PR #1640](https://github.com/masssi164/weave/pull/1640) merged into Dev as `7255eb798a80e093e666def34e43658c10855543` after [exact-head Core CI](https://github.com/masssi164/weave/actions/runs/39165228328) passed eight selected jobs with seven valid impact skips. Flutter passed 844 tests with one existing offline-only skip, the separate offline path passed four, and all 76 native plus 41 dual harness regressions passed.

Fresh normal local run `local-qual-20261018-2053` used clean reviewed `7a578a5ca5e8d9108d58ddfeaefa0ad7448879a5`, tree `d081fec98f34a1e5820150cb61334ac4d8a13fbc` and accepted95. Final macOS acceptance passed. The two real iOS apps completed fifteen collaboration actions, including B's updated-file export, then actual third-identity authorization passed three read and three mutation denials. Both Flutter authorization actions passed. This verifies the two preceding harness repairs in the real journey.

The next Calendar-delete action failed before opening a dialog: its tooltip finder matched zero controls at the retained Flutter stack's line1122. Both apps were still on Files after the required export actions. The removal and peer-removal handlers omitted the normal Calendar navigation used by the earlier Calendar actions. The minimal correction reuses that existing navigation helper for both actors and the existing readback helper to establish A's updated event before deletion. Original deletion, confirmation, peer-removal, identity and accessibility assertions remain unchanged; no new abstraction, product behavior, API, deadline or specification is introduced.

The original failed native JSON and private diagnostics are retained. All seven final iOS aggregates remain not-run; remaining recovery and accessibility proof is still required. Independent inventory and guarded exact-run finalization confirmed zero owned resources. Protected Dogfood remains `5f96518536f123b2d0f85dec398d1ab4727106e7`; its Full attempt2 failed and deployment was skipped. Existing promotion #1639 stays draft. No persistent reset, deployment, physical install or HIL pass is claimed.

### Exact-candidate qualification and Files accessibility correction

Normal local run `local-qual-20261016-0433` used clean commit `c125f69d803a3e01b695883d669e9496dacb72ac` and accepted pin `2c35d0304efe04f6cae1a7bba9f244004e017d29`. Mandatory macOS qualification passed, including actual bearer expiry, restoration after application/backend restarts and protected-access denial after logout. The two concurrent real iOS apps independently authenticated, exchanged Chat messages and observed shared Calendar creation and modification. Both exported the initial uploaded file through the native picker. A imported replacement content and verified the changed revision, then the unchanged iOS tap-target guideline failed. This is a failed qualification; all seven final iOS scenario aggregates remain unpassed. Peer replacement verification, Calendar removal, authorization/revocation and recovery did not complete in that run. Existing lifecycle-wait changes were not reached.

The original failed artifact and diagnostics were preserved. Independent inventory found no remaining owned simulators, processes, containers, networks or volumes; the exact-run guarded finalizer passed. No persistent Dogfood reset was repeated.

| UX defect record | Evidence and minimal correction |
| --- | --- |
| Accessibility/user control; member replaces and previews a shared file, closes preview and scrolls; English, 402×874 logical viewport, normal text; release blocker | The pinned large Files title clipped a rendered 48px workspace selector to an actionable 18px semantics fragment. The unchanged widget reproduction failed before the fix and passed with the full 48px fragment afterward. Letting the title scroll removes the overlap while preserving every Files action and persistent shell navigation. No target-size threshold or semantics exclusion changed. |
| Behavioral regression and localized coverage | The maintained update → preview → close → scroll regression fails against the original pinned header and passes with the correction. English/German at normal and 1.5× text scale retain the unchanged tap-target/label checks; each case also returns to the selector and changes workspace scope. These are layout tests with explicit unit fixtures, not real iOS collaboration evidence. |

The focused actual-app replay is recorded above; complete clean-candidate protected qualification remains required. The correction changes no public contract, bootstrap authority, provider boundary or release scope. The accepted accessibility contract requires the fix; a new normative specification is unnecessary.

## Prior checkpoint — October 16, 2026 UTC

### Latest actual two-app observations

Local Computer Use run `local-cua-20261015-2314` built the real Flutter/Rust apps and isolated backend from `f79b8f1339c1d56c4b5fba1458ad6f55752f03fa`, pinned to the accepted corpus. A entered the organization host; B entered its invitation. Both authenticated in the native system browser and ran concurrently with distinct current identities, storage and Matrix device identities. Later profile and Home Dart corrections were loaded into the resident apps; read-only comparison verified all 21 selected current library sources in both processes. These mixed-source observations are exploratory evidence, not exact-candidate release qualification.

- Files: A uploaded 48 bytes through the actual native picker; B opened the actual contents. A replaced the same shared file with 52 bytes; both current views retained the same ID and a changed matching revision, and B displayed the replacement contents. The rebuilt maintained picker preserved an independently created same-name sentinel before confirmation and after actual Cancel. A subsequent native Save to a new selected destination produced the original 48 bytes and left the sentinel unchanged. The OS-appended extension differed from the planned name; feedback and verification used the actual destination. No existing file was deleted or overwritten for the test.
- Chat: A sent, B received and replied, and A received the reply in Flutter. Both current timelines contained the same two unique message IDs in order. After B changed its own profile through Flutter, A's existing reply displayed the new canonical member name without a new message. These observations do not establish encrypted-room recovery or an unchanged SDK sync cursor.
- Calendar: A created an event, B read it, A updated it, and B displayed the changed title in the shared workspace calendar. Both current provider views matched on event ID, scope and version. Delete followed by Cancel retained the event. Confirmed deletion and peer removal were not repeated before this run stopped.

### Demonstrated conformance corrections

| Observed problem | Minimal correction and reason | Verified boundary |
| --- | --- | --- |
| A long-running profile editor reused the initial bearer and returned HTTP 401; German UI displayed an English failure | Reuse one current-session resolver in the existing provider and per-operation profile callback. The authentication repository continues to own expiry, restoration and refresh. Localize the safe failure without another cache, token store, provider or blind mutation retry. | Three compiled pre-fix assertions failed; 39 focused tests passed afterward. Reload alone retained the old failure; recreating B's Dart providers allowed actual Save, and another warm Save succeeded without new browser login. Elapsed time alone is not independent expiry proof. |
| Home displayed 04:00–05:00 while the same Calendar event displayed 02:00–03:00 | Remove Home's three extra local-time conversions. CalendarEvent already carries wall-clock fields in UTC containers with separate temporal intent; actual-instant recent activity formatting remains unchanged. | Two compiled Berlin regressions failed before; EN/DE rendering and semantics passed in UTC, Berlin and Apia through the existing temporal gate. All four Home tests and analysis passed. Actual B Home then matched Calendar at 02:00–03:00. |
| C's Files denial stopped the harness on an error-category mismatch after HTTP 403 | Bind expected Files/Calendar denial to the known guest's capability-policy boundary, which precedes resource permission. Preserve exact HTTP 403, exact Matrix denial, current conditional versions, protected content/version checks and the separate B Space revocation/regrant journey. Restrict mismatch diagnostics to fixed known codes. | The failed run did not retain the actual response code. Real MVC/security/error-handler regressions with the real services and capability policy independently verified all four guest read/mutation routes; 48 Server tests passed. The complete product-E2E unit module passed 75 tests, including exact-code rejection, content/version preservation and bounded diagnostics. |

These corrections implement the accepted contracts. OrgManifest v2, generated User Files/Calendar consumption, native Matrix, one system-browser PKCE flow and the existing delivery authorities remain unchanged. The Auth agent instruction now follows that canonical bootstrap instead of the retired manual issuer/client configuration.

### Current release limits

The existing guarded C exchange stopped the local run with a Files denial-category assertion; no six-probe authorization proof or revoke/regrant proof was produced. Original failures were retained. Normal cleanup and an independent inventory confirmed zero owned containers, networks, volumes, simulators and recorded processes. The persistent Dogfood reset was not repeated.

Next execute the corrected security checks and complete the unchanged normal local qualification flow, including mandatory macOS and both iOS apps. Then use protected Dev/Dogfood qualification, exact deployment, physical development signing and explicit human acceptance. No human usability, VoiceOver, physical installation, production release or HIL result is implied by local UI observations. #1470 and #1481 remain open; Project delivery remains E2E Testing. The last protected [Full #38781249459](https://github.com/masssi164/weave/actions/runs/38781249459) failed and deployment was skipped. No new comparable whole-CI runtime improvement is claimed.

## Historical checkpoint — October 15, 2026 UTC

The accepted corpus is `2c35d0304efe04f6cae1a7bba9f244004e017d29`, accepted through [specification PR #157](https://github.com/masssi164/weave-specs/pull/157) and pinned by the local release branch. It adds the reviewed local-feedback lifecycle and seven ISO 9241-110:2020 operational UX criteria. The corpus remains normative; this document records implementation and evidence. Protected Dogfood still points to `18a18bd573b9c190448e81c49ec899f72011c4a3` with its earlier pin. The release is **not ready for HIL**.

[PR1634](https://github.com/masssi164/weave/pull/1634) remains draft HOLD while local feedback is completed. Local implementation commit `6869a4c584da08d9fa65c87889855977ab96b332` includes reviewed Server/protocol member-name projection, just-in-time browser authentication for negative-probe fixtures, and the current native client batch. No qualification or deployment is implied by these commits.

### Architecture and demonstrated corrections

- OrgManifest v2 at generated User `GET /api/platform/config` remains the only bootstrap contract. Files and Calendar use the generated User API; native Matrix uses the accepted Weave facade. Retired discovery routes, provider configuration and proprietary Chat REST are not additional client authorities.
- Actual native export inspection demonstrated that the old picker removed and rewrote a colliding Documents file before Save confirmation. Maintained `file_picker` 13.1.0 with `file_picker_darwin` 2.3.2 stages in app temporary storage before presenting the export picker. Its required compatible closure includes `package_info_plus` 10.2.2 and Android Gradle Plugin 8.12.1. Both maintained picker 12 and 13 actually failed resolution against the prior win32 dependency. Existing platform minimums already satisfy the update; no product OS minimum changed. Central import/export uses the supported single-file/lazy-stream/URI API, removes obsolete manual path adapters, preserves bounded reads and cancellation, and keeps opaque platform URIs out of feedback. Actual new-native collision/cancellation behavior remains to be executed. Android compilation was not run because the local SDK is absent.
- Server uses the existing canonical member-profile authority for authorized Matrix membership names. A deterministic membership-state revision advances the opaque sync token when names change or clear, even when the message cursor is unchanged. The client reads each distinct sender once from the existing SDK member store within its receive transaction and passes optional presentation metadata through the existing JSON boundary. Identity, admission and encryption checks are unchanged. This adds no REST profile lookup, name cache, database, bridge ABI or duplicate Chat ledger.
- Long builds or inspection can outlast fixture refresh-grant idle limits. Existing C/admin/peer backend probes now obtain fresh fixture sessions through the existing real browser PKCE journey at request time, then verify immutable subject/client/role/organization. Strict read/mutation denials, current versions, protected-content readback and revoke/regrant remain. Native A/B sessions are not replaced by fixture tokens.
- Actual logout exposed an obsolete invitation/Dogfood card with missing metadata on the saved-organization sign-in gate. The gate now presents the validated organization and system-browser guidance; internal support-stage recording is preserved. Files dates use the current app locale. Existing Chat title, own-message/local-time, Calendar guidance, selected-export verification and screen-specific accessibility corrections remain in the batch.

### Executed local evidence and limits

The [local Computer Use checkpoint](https://github.com/masssi164/weave/issues/1592#issuecomment-6203774607) records two concurrent real Flutter iOS apps with independent URL/invitation entry and browser sessions. The host/backend/native build was `548421a12aeaa286cae7273c5fd1c5fe7413f1b9`; resident Dart subsequently loaded reviewed `7c81f28b974e1c3fa07a179329573dd8ed04ceaa`, verified from current library sources. Actual UI outcomes included bidirectional Chat, two network-loss/recovery cycles without duplicates, shared Calendar create/read/update/read/delete/peer removal, real Files upload/replacement/fresh upload/peer content and selected export bytes, and A logout with B still refreshing/opening shared Files. These mixed-source observations are exploratory checkpoints, not exact-candidate release qualification.

| Focused validation | Verified outcome |
| --- | --- |
| Native client adapters, repositories, bridge and screens | 100 Flutter tests passed; a restored-source 55-test closure cohort passed and overlaps the first cohort |
| Native SDK | 27 unit and 3 integration tests passed; a subsequently strengthened unchanged-message-cursor profile test passed separately |
| Old UI controls |Compiled old German dates and old EN/DE sign-in UI failed the expected behavioral assertions; corrected source bytes were restored and verified |
| Analysis and source consistency |Full client analysis, changed Dart/Rust format checks, enforced dependency lock and both Apple pod installations passed |
| Server/protocol and JVM fixtures | 71 focused Server tests, 8 protocol tests and 74 product-E2E module tests passed at their reviewed source boundaries |

The local run closed deliberately through the existing nonqualification operation. Native and outer teardown completed; independent inventories found zero remaining owned simulators, processes, containers, networks, volumes or private run directories. Private evidence and original failures were retained. No persistent Dogfood reset was repeated. No new comparable whole-CI runtime improvement is claimed.

The next local run must execute the rebuilt picker and SDK consumer, actual peer-name updates, current authorization/revocation and lifecycle/expiry/refresh/accessibility requirements. Protected Compose, macOS and dual-iOS qualification, exact deployment, physical development-signing preparation and explicit human outcomes remain required. The last protected [Full #38781249459](https://github.com/masssi164/weave/actions/runs/38781249459) failed and deployment was skipped; its macOS pass does not qualify changed source. #1470 and #1481 remain open, and Project delivery remains E2E Testing.

Earlier dated sections below preserve historical decisions and evidence; their revision-specific statements do not override this checkpoint or the pinned corpus.

## Scope and authority

Primary task type: `cross-repo-contract`. The accepted corpus governs bootstrap, delivery and persistence; this monorepo implements and records evidence. Scoped client, Server, infrastructure and native-test work uses separate worktrees; the integration branch preserves the existing release issue graph and unrelated checkout changes.

| Contradiction | Authority and historical origin | Affected consumers and resolution |
| --- | --- | --- |
| Two discovery routes/models | Accepted OrgManifest v2 and release profile supersede the earlier v1 organization manifest projection | Flutter, generated User/Admin consumers and browser completion consume one v2 schema at the existing User route; remove v1 and its generator workaround |
| Provider configuration in persisted profiles | Canonical v2 contains the necessary transport values; older profiles independently stored duplicated fields | Store only origin, validated manifest and timestamp; derive transport and refresh origin-bearing legacy records once |
| Hibernate update versus immutable Flyway chain | Earlier ADR0018 conflicted with shipped migration history and later initializer receipts | Accepted PR115/ADR0023 establishes Flyway-only DDL, early serving fence, independently replayed catalog and separate migration credentials |
| Creator-only versus shared Files access | Previously private-only implementation did not satisfy accepted shared workspace UI collaboration | V13 preserves PRIVATE rows; explicit SPACE scope uses current grants for A/B and denies C, revoked users and stale writes |
| Single iOS proof versus collaboration acceptance | Historical native v1 run predates owner clarification in #1592 and accepted PR155 | Native v2 requires overlapping independent real Flutter apps and seven scenario proofs; old evidence remains historical |
| Historical enterprise/Candidate Cut bootstrap controlling release | Superseded documents and optional fixture tasks still appeared in an old aggregate gate | Align the deterministic release aggregate with accepted direct Compose delivery; keep historical provenance without reinstating its architecture |
| Issue, branch and candidate statuses conflated | Historical Project fields have different meanings; accepted delivery requires verified candidate evidence | Keep existing Status and Delivery stage fields; actual promotion validator gates HIL/Main and rejects superseded candidates |

## Earlier accepted decisions and contradiction resolution

- Corpus PR155 merged at `b8b5f11f18b5cee9ddca8b095f498a721351a61e`: OrgManifest v2 is the single bootstrap contract delivered through generated User `GET /api/platform/config`. The obsolete v1 `/api/organization/manifest` route/model and generated-client postprocessor were redundant authorities and have been removed. Domain capability queries remain authenticated per-member product authorization, not a second bootstrap contract.
- Existing corpus PR115 was narrowed to the persistence authority discrepancy and merged at `2f73c9d0962d86cd797c95b7ed0894c9337ccc11`. ADR0023 supersedes the older Hibernate-update epoch while preserving Java/Spring/JPA boundaries and the immutable Flyway migration history already on Main. The implementation initially pinned that accepted revision; the current checkpoint identifies the later accepted pin.
- Files and Calendar remain generated User API consumers; Matrix uses the accepted native Client-Server facade. No provider discovery, northbound DAV, independent Matrix OAuth registration, deferred Runner or Calls scope is added.
- Existing private Files rows already carried workspace references. A forward V13 PRIVATE/SPACE discriminator preserves existing privacy instead of inferring shared access. Shared access uses current Space grants; creator provenance is not global permission.
- Persisted client profiles now contain only organization origin, validated manifest and validation time. Transport values are derived, and origin-bearing legacy profiles are one-way migration input requiring fresh bootstrap before authentication.
- Standard Dogfood 80/443 bindings are represented in its example profile; other profiles retain configurable ports. Explicit HTTP redirects target each configured public HTTPS origin, including nonstandard ports.
- The new native evidence version rejects missing, sequential, single-client, reused-identity, API-only or incomplete collaboration proof. Human acceptance remains an explicit owner action after exact automated/deployment/physical preparation evidence.

## Verified results on 2026-10-12

- Accepted corpus Main CI [38280978951](https://github.com/masssi164/weave-specs/actions/runs/38280978951) passed for `2f73c9d0962d86cd797c95b7ed0894c9337ccc11`.
- Authorized guarded reset executed against protected Dogfood `d9a25cb31a6a5f9f9324e66876dad0fa2e2853ad`, preserving TLS/CA, reviewed secrets and unrelated resources. Reset/schema initialization/receipt check passed; backend, MCP, gateway, Keycloak, PostgreSQL and Mailpit were healthy. This restores the prior deployment; it does not qualify the new source. Support-safe pre-reset schema diagnostics and historical failure evidence were preserved.
- Server canonical-bootstrap/authorization acceptance: 40 focused executions passed. Member entry page: 18 focused executions passed, with canonical link/QR, English/German labels and no request-derived organization authority.
- Shared Files: 37 deterministic executions and four real PostgreSQL tests passed, including A/B shared access, C denial, private preservation, revocation, stale versions and V12-to-V13 upgrade.
- Matrix participant directory: 69 deterministic executions and six real PostgreSQL tests passed, including bounded paging, current Space membership and organization/issuer isolation. Native SDK tests: 26 passed; focused Flutter Chat UI/repository tests: 36 passed.
- Native invitation acceptance: 98 focused Flutter Chat/bridge tests passed; the isolated client suite passed 640 tests with one existing offline-only skip. Actual native-hook Apple Silicon Rust compilation passed. The subsequent Files scope-rebuild correction passed 73 focused tests. These are deterministic checks, not iOS collaboration proof.
- Canonical profile persistence/refresh: 28 focused Flutter tests and static analysis passed. The earlier full-client baseline passed 655 tests with one existing offline-only skip; source changes require subsequent focused and CI validation.
- Promotion and Project verifier: 31 regression tests passed, including actual rejection of historical/single-iOS artifacts before physical preparation, negative dual-simulator/authorization vectors and superseded protected-head rejection at each physical boundary. These test the gate, not real native journeys.
- Schema authority follow-up: 23 serving checks and 12 real PostgreSQL checks passed on the selectively preserved PR1445 early guard and extended catalog. Unexpected view/function/type/sequence/relation cases fail; exact owned-lock termination recovers to valid receipt.
- PostgreSQL role probe against the pinned 16.9 image passed repeated convergence, runtime DML, CREATE/ALTER/DROP/schema denial and preservation of representative existing rows. Only the probe-owned disposable container was removed.
- Infrastructure static checks passed after updating obsolete port assertions. Pinned Caddy adaptation passed; product `/api` paths are preserved and HTTP redirects use configured public origins. No live 80/443 deployment change has occurred yet.
- Integrated Server manifest/controller validation passed 28 executions, including unsafe ports/paths and canonical browser completion. Documentation, 252 acceptance mappings, 157 corpus entries and 12 spec packages passed.
- Project2 was read before changes. Existing Status and Delivery stage fields were retained; #1592/#1470/#1481 reflect In Progress. No HIL or Main transition was made.

## Integrated follow-up verification

- Draft [PR1593](https://github.com/masssi164/weave/pull/1593) continues the accepted delivery work. Its first protected [Core run38287247132](https://github.com/masssi164/weave/actions/runs/38287247132) failed on two stale Server expectations, two originless sign-in fixtures, and JVM Files callers missing the new optional Space argument. API freshness, PostgreSQL, Admin/generated Admin, infrastructure, specification, documentation and Rust jobs passed. These failures were repaired; that historical run remains failed.
- On integration source `1e3bed86c26fef22972f32170248d54599492277`, full Flutter verification passed 645 tests with one existing offline-only skip, localization/generation, analysis, screenshot/offline contracts and generated temporal checks. The lower test count follows removal of tests for unreachable manual endpoint-editing paths, not a skipped product journey. Full Server verification passed 1,294 tests with ten declared skips and all 155 PostgreSQL executions. Admin verification passed 36 tests/build/typechecks; both generated JVM clients reproduced and their checks passed. Acceptance252, corpus157, spec12, documentation, release evidence, native-runner and impact checks passed.
- Independent review closed the alternate bootstrap-authority finding: only the canonical organization origin selects the generated GET request, legacy alternate hints are discarded, and redirects are denied. Attack and redirect regressions passed. The schema finding was closed after actual PostgreSQL tests rejected external-function triggers, rules, RLS/policies and partition drift. Catalog v3/receipt v5 code has never been deployed; persistent Dogfood still has v2/v4, so no compatibility layer for unpublished local variants is warranted.
- Removed more than 1,200 lines of unused manual issuer/client/API configuration UI, controller and save cascade. The active saved-profile provider has a direct, purpose-specific file and the same automatic disposal behavior. Active reset, sign-out, manifest validation and Matrix identity/endpoint binding remain covered. A separate scoped review found no remaining production caller or security regression from removal.
- Real Files picker/upload/preview/replacement/export automation and the JVM-owned third-identity denial exchange are implemented. Their deterministic checks passed. Actual concurrent iOS execution and independent recovery are still pending; implementation is not UI evidence.
- Only completed task-owned compiler outputs and matching hook metadata were removed, preserving source, reports, private diagnostics, Docker and device state. The unchanged 20GiB/Docker preflight passed with 21.9GiB available before the native build window. This is a capacity observation, not integration proof.
- Project2's 444 existing items were inspected. Coarse Status for #1564/#1566 was corrected from Todo to In Progress while preserving their existing historical E2E Testing stage. #1565 now records In Progress. #1592/PR1593/#1470/#1481 remain In Progress; no HIL/Main transition was made.

## Native and physical evidence boundary follow-up

- Frozen native source `d010c6ca828930bfaac754757ae3475c17e4174e` implements actual per-actor encrypted transport interruption, peer UI continuity, background/foreground catch-up, independent process termination/restoration, real bearer expiry/refresh, current grant revocation/regrant and independent logout. The producer and shared Main/HIL/Project validator bind initial/restored process intervals, identity/device/storage digests, positive transport counters and UI-created resource digests. Focused Python native/promotion/Project checks passed 105 tests; JVM product flow passed 57 tests; changed Dart analysis/format and supported Swift SDK type checking passed. Independent review found no concrete blocker and separately passed 20 shared promotion tests. Those are implementation checks, not executed collaboration results.
- A disposable focused iOS-only `testApp` run started from clean `d010c6ca828930bfaac754757ae3475c17e4174e`, accepted spec `2f73c9d0962d86cd797c95b7ed0894c9337ccc11`, after the unchanged 20GiB/Docker preflight passed. It is preparing the isolated real environment. No macOS qualification, protected Full result or simulator scenario pass is inferred from this focused run.
- Physical preparation now derives origin and TLS/CA paths from the existing reviewed Dogfood Compose context. The member handoff contains only the organization origin; its bootstrap path is derived. Fresh typed device-recorded canonical discovery, saved configuration and visible SSO readiness are required, while final candidate/specification/build/run/handoff identity is preserved. Behavioral distribution checks reject stale, failed, malformed and wrong-authority evidence and exercise the offline bundle CLI; distribution, shell syntax, ShellCheck, Actionlint and core documentation checks passed. No actual installation, launch or CA trust result has yet been recorded for the new candidate.
- Issue #1564 now records the completed authorized reset and accepted spec changes; #1592 records accepted dual-iOS/bootstrap clauses. Weaver dependency PR43 and PR45 are already merged. Current Weaver Main `00a1714055763e162b545399add1df791c400f92` preserves signed upstream `fc23bc864e4553c2d215e479eeec47b67a0bf943` as the GitHub compare merge base (ahead 231/behind 0), and its Security Review38290774353 passed. Issue #1481 reflects that checked dependency state and remains open for Weave release acceptance.

## Runtime wiring and bounded reset corrections

- The first real disposable focus on `d010c6ca828930bfaac754757ae3475c17e4174e` failed before native startup: schema initialization passed, but the serving guard rejected a missing exact candidate environment value. Compose supplied that value to initializer/receipt verification but omitted it from backend. The failed environment was removed by the existing guarded teardown: zero owned containers, networks, volumes or other resources remained. Private diagnostics and support-safe exact-run receipts were preserved; no simulator scenario passed.
- Correction `c21e2062d310ce1c21943b1e05a763f9748262e8` supplies the same required existing candidate input to backend. The guard is unchanged. Actual Compose rendering tests passed for Dogfood, production and isolated E2E, proving candidate equality across serving/initialization/receipt services and failure on missing input. Scoped independent review found no blocker. A fresh disposable focus from this clean commit passed initial backend readiness but failed before native startup during real collaboration service restart: the serving guard rejected schema DDL capability restored by role convergence. Guarded teardown and independent Docker inventory checks confirmed zero owned resources. No iOS scenario executed.
- Physical review found the optional bounded app-state reset still required an obsolete profile parameter. Correction `e4eb45bc05c0ce41cb4ab735c9650e1df72c564a` validates the canonical organization link before local cleanup and strips only the reset control before discovery. The positive case and 12 unsafe/ordinary/legacy variants passed standalone Dart behavioral execution; distribution checks and formatting passed, and independent review closed the finding. The actual focused Flutter router run subsequently passed all 30 tests, including those reset vectors, with the ordinary native hook build executed.
- Standard host ports 80 and 443 were checked with actual socket binds and were available. The reviewed persistent profile still uses 44080/44443; no live port or public-origin change is claimed before protected exact-candidate qualification/deployment.

## Focused runtime corrections and hosted verification

- Protected [Core run38293926402](https://github.com/masssi164/weave/actions/runs/38293926402), source `5ef88e6b3d06a5b58d1f2123990c2042d35ad038`, passed specification, API freshness, both generated consumers, Flutter, Server, PostgreSQL, Rust, Admin and documentation checks. Infrastructure rendering failed on an absent generated production env file under hosted Compose2.38.2. The run remains failed. Correction `563cec83d3` supplies only an owned empty temporary fixture for the render-only check; the actual full modular contract passed under official Compose2.38.2 and local Compose5.1.2, including every profile and the missing-candidate negative. Independent review found no blocker; production and persistent generated state were untouched.
- Integrated Matrix correction `b2554990a8` resolves canonical member display names through existing identity administration and product profile authorities after organization, issuer and current Space admission. Opaque Matrix account IDs remain stable. No additional client, configuration authority or identity cache was introduced. The normal focused Server task compiled JNI and Java and passed 42 tests, including real hashed identifiers, human-name search, bounded results and pre-hydration denial.
- Integrated product-flow correction `1cedb19f3c` retains failed control transcripts only in the existing private, non-uploaded native scope with owner-only permissions. Public output contains a fixed classification and filename; successful transcripts are removed. The existing Gradle task now runs from the repository root, fixing an empirically verified handoff-directory mismatch. All 59 product unit tests passed; actual working-directory inspection and redaction/subprocess regressions passed.
- Schema corrections `e5faa27140` and `486e3eeec4` preserve product DML while keeping migration history and authority read-only throughout repeated role convergence. The actual acceptance-lock session now commits the marker and supplies its exact PostgreSQL completion to the existing receipt. Independent review exposed a filesystem replacement race after that session dies; a bounded stable output mutex, acquired before the database mutex and held through publication/cleanup, prevents a losing initializer from overwriting a successful replacement's proof. This synchronization is necessary because database session lifetime cannot atomically cover a filesystem move; it introduces no receipt format, manifest, configuration input or authority. All 18 real PostgreSQL tests passed, including actual owned-session termination, paused-before-move replacement, exact marker/receipt completion, first install and three repeated role convergence cycles with an open serving connection. Independent re-review found no material issue; owned Testcontainers resources were cleared.
- Integration-root product tests independently executed all 59 tests successfully after the diagnostic/cwd changes. The complete local infrastructure static gate passed in 18 seconds. Completed root Flutter and scoped Matrix/schema Cargo outputs were removed after preserving reports, restoring an observed 21.27GiB available before the unchanged native preflight. No shared SDK, persistent data or Docker/device state was removed.
- Fresh disposable focus `focus-dual-ios-20261012-1922` on exact `bef5a766353c807f4e733b1ad51ac9be11bb2666` and spec `2f73c9d0962d86cd797c95b7ed0894c9337ccc11` passed capacity/Docker checks, built Server/MCP and progressed through real product prerequisites and service restart to native handoff. It failed before iOS launch because the handoff compared a root-slash environment URI with an equivalent slashless canonical manifest origin. The existing guarded teardown and independent label inventories confirmed zero owned resources. This run provides no iOS result.
- The scoped native handoff repair accepts only equivalent root forms of the same HTTPS authority, retaining unique anchor, organization-only query, credential/path/query/fragment restrictions. It also reads current A/B profile names through the existing generated API before owner logout and binds each to the expected account identity. This is required because the real profile-isolation prerequisites had changed those names; native UI still must find and select the actual peer. No fixture resource or backend outcome substitutes for a UI result. All 60 product tests and 13 dual-runner tests passed, with independent source review and zero remaining owned resources.

## First concurrent iOS application launch

- Disposable focus `focus-dual-ios-20261012-1938` used clean source `e8e692e5bac54477159ff2c95b3cd8b9b3a796e7` and accepted specification `2f73c9d0962d86cd797c95b7ed0894c9337ccc11`. Actual product prerequisites and the private canonical actor handoff passed. Two owned iOS simulators booted concurrently, the real signed Flutter/Rust simulator bundle built, and both devices launched distinct application processes. The bundle SHA-256 was `c3bf4e8423bbdb0e39506cee61ab2d86dea9b82cd3512d3a3885825224aa6b24`.
- Both Flutter driver hosts then exited before authentication because the harness supplied `--reporter` to `flutter drive`, whose installed CLI does not support that option. Authentication, Chat, Calendar, Files, authorization, recovery and accessibility are all **not run** for this attempt. Failed logs remain private with owner-only permissions; the support-safe result records the failure and completed cleanup. Guarded teardown and independent inventory checks verified zero owned Compose resources and zero owned simulator UUIDs.
- The narrow harness correction removes the unsupported option from initial and restored driver invocations while preserving unbuffered output, stage detection and all actual UI assertions. The installed Flutter CLI accepts the resulting command and rejects the old reporter option. Fourteen dual-runner regressions passed. This verifies command construction, not product interaction.
- Final independent source review at `e8e692e5bac54477159ff2c95b3cd8b9b3a796e7` found no material issue and is recorded on [PR1593](https://github.com/masssi164/weave/pull/1593#issuecomment-6131599254). Protected [Core run38298997812](https://github.com/masssi164/weave/actions/runs/38298997812) has passed thirteen jobs; Flutter and the required aggregate are pending at this observation. The native failure and pending CI aggregate remain release blockers.
- Completed root-only Compose fixture tooling, documentation virtual environment and Flutter test cache were removed after preserving reports. The actual signed iOS bundle and native Rust compilation cache were retained. The unchanged 20GiB/Docker preflight passed again with an observed 20.086GiB available.

## Executed onboarding oracle correction

- Focus `focus-dual-ios-20261012-2002` on clean `a370cbe5b94a5cede8746aa046aec631b7fc1ada` passed isolated product prerequisites and canonical actor handoff, built the real signed simulator application, and launched two independent owned app processes concurrently. The Rust framework's arm64 slice was independently identified as the iOS Simulator platform. Both corrected Flutter driver commands attached and executed the actual Flutter targets.
- Both tests failed before authentication waiting for `weave.auth.sign-in` after ordinary URL-only Continue. Source inspection identified an impossible test expectation: Continue correctly routes to the actual `MemberHandoffScreen`, which exposes its localized accessible Sign In action; that key belongs to the separate `SignInScreen`. This finding does not prove discovery succeeded in the failed run. Private logs remain preserved. Safe cleanup evidence and independent inventories verified zero owned Compose resources and zero owned simulator UUIDs.
- Correction `3d9e768f16` requires the unique enabled Sign In control in the real handoff ready-state UI, existing persisted `ready_for_sso`, validated saved manifest/time and the matching root-only organization origin before unchanged XCTest begins browser authentication. It changes no production routing, discovery or authentication behavior. Fixed-format, allowlisted and bounded failure diagnostics preserve the original assertion even if reporting fails. Seven actual focused Flutter widget tests and 75 Python harness checks passed, with changed-source analysis and independent review. No authentication or collaboration result is inferred from these checks.
- Protected [Core run38301576862](https://github.com/masssi164/weave/actions/runs/38301576862) passed all fifteen jobs, including the required Gradle CI aggregate, on `a370cbe5b94a5cede8746aa046aec631b7fc1ada`. Release Notes Label Check also passed. The run took 21m32s overall; Flutter took 19m30s and generated User consumers 10m26s. This all-impact run does not establish a material total CI runtime improvement.
- A read-only latency audit identified native hook execution during the pure generated Calendar DTO test, whose first seven UTC results arrived approximately eight minutes after the test command began. A proposed invocation-scoped upstream native-assets disable was tested and rejected by pinned stable Flutter before any test or Cargo invocation. That failed mode is not integrated; mandatory full Flutter and native qualification remain enabled. Any simpler alternative must be executed before a performance improvement is claimed.

## Pure contract verification and current native findings

- Integrated `20eddb8de2dd57e8a7f9a2b195807c6ecbf1e03c` runs the existing generated Calendar DTO assertions using the pinned Flutter SDK's Dart VM and the locked `package:test` public entry point. The test has no Flutter engine or native dependency, so invoking Flutter's native hook dispatcher provided no coverage benefit. All seven assertions passed in each of UTC, Berlin and Apia: 21 executions in 11.013 seconds, with rejecting Cargo/Rust wrappers recording zero calls. Dependency resolution, analysis, formatting, shell checks and fail-closed SDK checks passed. The already-locked test dependency is now explicit; package versions, hashes and the dependency set are unchanged. Full Flutter and mandatory native qualification commands remain unchanged.
- Independent impact review confirmed that the temporal script selects all deterministic gates, and both dependency files and the exact DTO test select generated User consumer and full Flutter checks. Generated freshness and JVM/Admin public consumers remain covered. The prior hosted generated User job took 626 seconds in Core38301576862 and 480 seconds in Core38304851543. These baseline jobs passed; hosted timing of the optimized source is still required before claiming a comparable CI improvement.
- Disposable focus `focus-dual-ios-20261012-2032` used clean `7ea64a0f2cfbd7aa061a7133d94357581c4f7b9c` and accepted specification `2f73c9d0962d86cd797c95b7ed0894c9337ccc11`. Actual environment prerequisites, signed simulator build and concurrent independent application execution completed. The actual Flutter target observed the enabled handoff Sign In control and persisted `ready_for_sso`. It failed before AppAuth when the saved-configuration provider returned its earlier null snapshot. No authentication or collaboration scenario passed. Safe cleanup evidence and independent inventories confirmed zero owned Compose resources and zero owned simulator UUIDs.
- Source investigation traced a production cache defect: canonical discovery saves the repository, but consumers of the existing saved-profile FutureProvider can retain a previous null value. The existing integration assertion remains useful and is retained. The scoped correction belongs at successful handoff orchestration, invalidating that provider after canonical save and before exposing ready UI; it requires an executed regression holding the initial null subscription. No second configuration authority or repository event abstraction is needed.
- Handoff correction `a36149e89c` refreshes that existing provider after successful discovery, within the mounted guard and before ready UI. All nine actual handoff widget tests passed, including retained null subscription to canonical save/current profile and HTTP 503 preservation of the same prior cached authority. Analysis, formatting and independent review passed. The integration oracle remains unchanged.
- The same cache defect was independently reproduced after successful bootstrap manifest refresh: the repository contained the new accepted User API endpoint and bootstrap was ready, but a retained profile subscriber still returned the old endpoint. Correction `e910e6f41b4010eb71e1623a0e595144a8d56f0e` uses the existing provider callback to await successful refresh/save, invalidate the existing profile provider while mounted and return the refreshed configuration. The combined eight bootstrap and nine handoff tests passed, including the authenticated session's current API URL and preservation of prior repository/cache on HTTP 503. Analysis and independent review passed. Matrix independently rereads and revalidates its canonical repository; this cache finding is not evidence of a Matrix authorization bypass.
- Protected [Core38304851543](https://github.com/masssi164/weave/actions/runs/38304851543) passed all fifteen jobs and the required aggregate on `7ea64a0f2cfbd7aa061a7133d94357581c4f7b9c`. Its actual creation-to-final-job interval was 19m25s; generated User consumers took 480 seconds and the three-zone temporal interval 374.301 seconds. These are pre-optimization baselines, not results for the newly corrected source.

## Latest concurrent execution and deterministic CI reduction

- Protected [Core38307610078](https://github.com/masssi164/weave/actions/runs/38307610078) passed all fifteen jobs, including the required aggregate, on `d6c8d32d1ea7b263dd5ebfb7117319cc67a77a50`; [Release Notes Label Check38307610179](https://github.com/masssi164/weave/actions/runs/38307610179) also passed. All 21 temporal checks passed in 24.401 seconds, compared with 374.301 and 484.700 seconds in the two earlier successful runs: a measured 93–95% phase reduction. Generated User API consumers took 163 seconds, compared with 480 and 626 seconds: a 66–74% job reduction. Whole Core took 16m30s versus 19m25s and 21m32s, but Flutter remained the critical path; the DTO job cannot explain that whole-run reduction. Runner variation and other source changes limit attribution outside the narrow temporal phase. Flutter's still-unoptimized architecture interval took 334.002 seconds; the local pure architecture change is absent from this hosted source.
- Disposable focus `focus-dual-ios-20261012-2058` used that clean source and accepted specification `2f73c9d0962d86cd797c95b7ed0894c9337ccc11`. The unchanged capacity/Docker preflight passed, real isolated prerequisites and canonical handoff completed, two simulators booted, and the signed real Flutter/Rust simulator application built with SHA-256 `4d08465107749de007ab9442237f07424ab169fb5bbabc33df1a64587802af0d`. Simulator A reached the validated saved profile, attached driver, observed app and Sign In tap. There is no successful authentication claim. Simulator B stayed on organization entry after a pasted invitation link and timed out before handoff readiness. Authentication and all collaboration/recovery/accessibility scenarios remain unqualified. Guarded cleanup and independent inventories confirmed zero owned Compose resources and zero owned simulator UUIDs; private failure transcripts were retained.
- Source investigation found a routing mismatch: startup app links use the existing join-route normalizer, while pasted custom-scheme invitations were passed directly to the router and could be intercepted by its setup redirect. The correction must reuse that one normalizer and be validated through the real router, preserving strict handoff validation rather than rewriting the integration input.
- Correction `131452c3fd` moves the existing normalizer into the shared route module and uses it for startup and pasted entry. An actual pre-fix regression with the production router reproduced the setup redirect. All 50 router/organization-entry tests passed after correction, including canonical discovery/save/readiness and zero-request/no-profile rejection of extra controls, pasted reset controls, fragments and outer/encoded credentials. Outer credentials and fragments are rejected before normalization so unsafe data cannot disappear. No second parser, compatibility export or integration-side link rewrite was added. Independent source review found no material issue; actual simulator authentication remains unqualified.
- Pure source-boundary checks also require no Flutter engine. The existing five OpenAPI architecture assertions are unchanged and now use the same pinned Dart VM/locked test entry point as the generated temporal assertions. A small shared script removes duplicated SDK/runner resolution. Five actual architecture tests passed in 12.243 seconds under concurrent native load, and all seven temporal assertions passed separately in each of three zones; rejecting Rust wrappers recorded zero calls. Shell, analysis, formatting, invalid-path and accidental engine-test rejection checks passed, with independent review. Full Flutter and mandatory native qualification remain unchanged. Hosted timing for this architecture-runner change is pending.
- The supported `dart run build_runner` generation command is retained. Read-only inspection of locked build_runner 2.13.1 found that its public runner bootstraps child `compile kernel` and `run` commands through the resolved Dart CLI; the bare VM cannot supply those CLI operations. Jumping to a private generated entry point would bypass bootstrap freshness and add coupling without an executed correctness benefit. The pure-test optimization therefore does not extend to generation. This is a source-based decision, not an executed code-generation failure or measured saving.

## Sign-in delivery and removal of obsolete configuration

- Disposable focus `focus-dual-ios-20261012-2124` used clean source `f09971ec0ee49ee1b28728a4de0fdd99e208d33d` and accepted specification `2f73c9d0962d86cd797c95b7ed0894c9337ccc11`. The unchanged capacity/Docker guard passed; real isolated invitations, browser activation, service/API prerequisites and canonical actor handoff completed. Two independent simulators launched the real signed Flutter/Rust application concurrently, with bundle SHA-256 `fb5f42c0275af8105ad289374adcdaf8bf01902b8d3673a89f208304282c4015`. Both reached validated `ready_for_sso` and native drivers invoked the hittable Sign In control, but neither reached issuer authentication or workspace. The unchanged workspace deadline failed. These are actual failed authentication results, not collaboration proof. Guarded cleanup and independent inventories confirmed zero owned Compose resources and simulator UUIDs; private failure diagnostics remain preserved.
- Correction `46785ebb4f` gives the actual Flutter integration target sole ownership of the localized Sign In tap, as for its other product UI actions. XCTest must first verify the unique hittable native control; only its complete fixed observation marker and the original process PID release the existing barrier. XCTest continues handling system consent and the actual issuer form. The dual-only private option leaves single-client and physical native-tap behavior unchanged. Credentials remain outside Flutter, and workspace/authentication assertions and deadlines remain unchanged. This removes an unreliable cross-driver gesture handoff without adding an authentication path. The precise cause of the earlier native event not reaching the callback is unproven. All 77 Python regression tests, production XCTest build-for-testing, four actual Swift fixture-decoder vectors, changed Dart analysis/format and independent review passed. Actual repaired authentication is still pending.
- Correction `424ff13897` removes the unused independent Matrix OAuth browser, duplicate issuer/client fields and its separate Android/iOS/macOS callback registration. Canonical OrgManifest OIDC, member AppAuth callbacks, invitations and the accepted Matrix facade remain. Existing cryptographic storage keys and Rust/FRB APIs remain because they preserve active device state; their names do not create another configuration authority. All 85 focused Flutter tests passed, with six-file analysis, formatting, native XML/plist continuity checks and independent review. No generated API, accepted bootstrap schema, token security or native qualification requirement changed.

## System-browser execution and lifecycle findings

- Disposable focus `focus-dual-ios-20261012-2152` used clean source `3f2f6504e8ca349127e2aa11c7f5acb388d4a070` and accepted specification `2f73c9d0962d86cd797c95b7ed0894c9337ccc11`. The unchanged 20GiB/Docker preflight and real isolated prerequisites passed. Two independent real Flutter/Rust simulator applications ran concurrently, with signed bundle SHA-256 `63d3777a6cdd712cddd09b041bcecb64b70ea68fb297c79593d90634ba540991`. Both actual Flutter targets tapped the localized Sign In control after native observation, reached the exact issuer in the system browser and submitted the independently assigned credentials with successful bounded field readback. This proves browser interaction, not authentication completion. Neither workspace was proved; the run exited 1 with `initial-product/integration-app-exited-before-workspace`. Chat, Calendar, Files, authorization, recovery and accessibility remain unqualified.
- Private diagnostics exposed a disposed widget reference in the handoff error handler, obscuring the original exception. An executed controlled regression with the actual Weave root, production router and authentication controller showed bootstrap refresh unmounting the active sign-in route. Subsequent source review also identified stale bootstrap data permitting discovery during a pending refresh, mismatched-origin readiness attribution and ordinary sign-in readiness attribution after a bootstrap error. These findings require narrow lifecycle/evidence corrections; unit lifecycle results must remain distinct from real provider and simulator acceptance. The precise original authentication failure remains unproven.
- Corrections `4fa3facc0e` and `e2705683ce` retain one existing router across bootstrap refresh while the existing gate hides stale routed content and excludes pointer, keyboard-focus and accessibility interaction. Bootstrap keeps ordinary pending/error states rather than cached ready authorization; retry records and rethrows the original exception and stack. Both sign-in paths guard disposed continuations and require actual workspace readiness. Authenticated handoff reuse requires the saved canonical HTTPS root host/effective port, without discovery or organization switching on mismatch. A held-profile-load regression actually reproduced false readiness during a newer refresh before the final current-state recheck. Only the transient handoff page is nonanimated: component comparisons showed the outer-shell and frame-wait changes were unnecessary and they were removed. No second configuration authority, authentication controller or authorization cache was introduced.
- The exact focused command was `flutter test test/core/router/app_router_test.dart test/core/bootstrap/app_bootstrap_provider_test.dart test/core/bootstrap/bootstrap_gate_test.dart test/features/onboarding/member_handoff_screen_test.dart test/features/auth/sign_in_screen_test.dart test/features/auth/presentation/providers/auth_flow_controller_test.dart`: all 80 tests passed with zero failures or skips. Coverage includes pending and cold-error incoming links, stale content/focus/semantics exclusion, ordinary and handoff disposal/cancellation, returned and thrown bootstrap failures, non-ready authentication, current-origin reuse/rejection and the executed held-load race. Actual supported build generation, seven-file analysis, formatting and diff checks passed; independent frozen-source review found no material finding. These controlled lifecycle tests are not OIDC or dual-simulator acceptance.
- Guarded teardown recorded failed status and complete cleanup. Independent inventories confirmed zero owned Compose containers, networks or volumes in `weave-e2e-d430d35dc6eab2d5` and zero owned simulator UUIDs for the private run scope. Owner-only failure diagnostics remain preserved; no private credentials or raw transcripts were uploaded. The persistent approved Dogfood reset was not repeated.
- Protected [Core38313306115](https://github.com/masssi164/weave/actions/runs/38313306115) passed all fifteen jobs on that exact source; [Release Notes Label Check38313306128](https://github.com/masssi164/weave/actions/runs/38313306128) passed. Full Flutter passed 672 tests with one existing skip. The five pure architecture checks took 1.100 seconds, compared with 334.002 seconds before their runner change. The 21 Calendar checks took 17.998 seconds and the generated User job took 152 seconds. Whole Core took 21m25s: 101 seconds faster than the previous successful run, but 295 seconds slower than the earlier 16m30s run. Required generation and full Flutter startup remain the critical path; narrow phase improvements do not justify a whole-CI improvement claim.

## Bounded native prompt handling follow-up

- Disposable focus `focus-dual-ios-20261012-2241` used clean source `e3ce921c3b8619dbb94367b76b1dd1599cd39ec3` and accepted specification `2f73c9d0962d86cd797c95b7ed0894c9337ccc11`. The unchanged capacity/Docker preflight, real isolated prerequisites and concurrent signed Flutter/Rust app launch passed. Signed bundle SHA-256 was `04df330f08c050b4e48208ee20482a1138444ebcf99dcf4bc990938084ba0925`. Both actual issuer forms were submitted; one actual Flutter workspace/Home observation and native completion signal occurred. The run then exited 1 with `integration/dual-authentication/browser-failed` at the password-save decline check; coordinated failure stopped the peer. No complete dual authentication or collaboration scenario passed. Guarded cleanup and independent inventories confirmed zero owned resources and simulator UUIDs; private diagnostics remain preserved.
- The retained timeline shows credential Return at 32.07 seconds and prompt failure at 34.40 seconds, before the nominal five-second settling budget. Query descriptions do not establish actual returned accessibility counts, so the exact accessibility cause remains unverified. Source inspection found that the existing waiter treated ambiguous observations as immediately terminal rather than allowing them to settle.
- Correction `f724557470` refreshes ambiguous and inaccessible observations within the same five-second/global deadline. Exact global prompt/control uniqueness, hittability, no-save behavior, one-Return constraints, completion priority and all product assertions remain unchanged; persistent ambiguity still fails. New diagnostics expose only bounded counts and fixed readiness values through the strict capped relay. No label aliases, provider retry, alternate authentication or prompt bypass was added.
- Actual production XCTest build-for-testing passed. Two extracted Swift helper tests executed fresh-state/deadline vectors; all 78 Python native/dual checks passed with zero skips. Diff checks and independent frozen-source review passed. These deterministic results do not establish corrected dual authentication; a fresh real replay remains required.

## Outstanding release evidence

### October 16 native Files selection diagnosis

- Normal local qualification at `a822b1dea24d67e2be93d4f6069d9bd7f827f8f1`, pinned to accepted corpus `2c35d0304efe04f6cae1a7bba9f244004e017d29`, passed mandatory macOS qualification. The concurrent iOS run independently authenticated both apps and completed two-way Chat and shared Calendar create/read/update interactions before Files selection failed. The seven final iOS scenario aggregates did not run; this is not a combined qualification pass.
- A subsequent actual-app inspection reproduced the native helper's false-positive selection marker: tapping the exact filename did not dismiss the picker. Tapping the corresponding preview did dismiss it, after which the unchanged Flutter app uploaded the initial 48-byte file and the peer displayed its exact contents. Both current app views agreed on file identity, revision and shared scope. This establishes initial Files collaboration only; replacement, export, recovery and final qualification remain separate requirements.
- The helper now identifies the unique native picker cell by the owned filename and taps the center of its unique preview. The observed cell center lay outside that preview, and the image itself did not report accessibility hittability. The correction therefore requires a hittable owned cell, finite positive preview geometry, center containment and actual picker dismissal within the existing deadline. It adds no fallback selector, global coordinate, retry or timeout extension and changes no product API or upload behavior. A first intermediate correction failed before tapping on the image-hittability assertion; its failed result is retained. Final real replay is required before claiming the correction works.
- The inspection's failure cleanup completed, and independent inventories confirmed no remaining owned stack resources, simulators or recorded processes. The original results remain unchanged. No persistent Dogfood reset, CI cancellation, deployment, physical installation or human acceptance was performed by this diagnostic iteration.

### Subsequent exact-candidate import replay and export blocker

- Normal local run `local-qual-20261016-0210` used clean commit `a881b878efad1872598337a8a5b07a6fb57bbd3b` and accepted corpus `2c35d0304efe04f6cae1a7bba9f244004e017d29`. Full macOS qualification passed. Both concurrent iOS apps independently authenticated and completed two-way Chat and shared Calendar create/read/update/read interactions.
- The corrected native import passed its dismissal assertion in 4.643 seconds, followed by successful Flutter upload and initial preview. This verifies the import correction on the actual app. The next action, A's first native export, timed out before B's peer-open step. No export result, peer readback, replacement or recovery pass is inferred from that upload.
- The timeout handler did not retain partial XCTest output. The exact export failure remains undetermined; neither the retained Flutter logs nor the available Xcode result establishes whether picker opening, navigation or Save stalled. Retain partial command output privately and reproduce the actual export before changing its selector or deadline.
- The run exited 1 after 21m37s, and all seven final dual-iOS aggregates remained not-run. Original outer cleanup=false and nested dual cleanup=true are preserved. Independent known-resource inventories were zero, and the exact-run guarded finalizer passed. Mandatory combined qualification, protected delivery, physical preparation and explicit HIL remain pending.


### Export picker ancestry correction

- Warm inspection `local-cua-20261016-0240` preserved both actual independent browser-authenticated apps, successful native import, Flutter upload and exact initial preview. Read-only native hierarchy inspection showed that Save opens directly in the owned Weave folder; the original driver incorrectly waited for a folder-title text element before considering Save.
- An initial diagnostic correction still failed: it scoped the filename field beneath the folder, while the observed iOS hierarchy places that field in a sibling footer. Its earlier source-review clearance was withdrawn. The failed replay produced no Save or export pass; bounded partial XCTest output was retained privately by the corrected timeout handler.
- The minimal final correction resolves one common native picker, keeps the owned folder/navigation/Save queries narrowly scoped, and resolves the filename field beneath their common picker ancestor. A single enabled, hittable Save action must dismiss the picker within the original deadline. Existing import navigation, selected-destination ownership and byte verification remain unchanged. This is a native test-driver correction; it introduces no product API, discovery contract, compatibility path or configuration mechanism.
- Warm replay `local-cua-20261016-0321` executed the corrected A Save test in 2.552 seconds with zero skips, then displayed Flutter success and created a new owned 48-byte destination with the exact uploaded contents. B refreshed the shared scope and displayed those exact contents in its own Flutter preview. B had no local source file; its owned directory sentinel remained intact.
- An earlier export command exited zero after skipping the selected native test. Its reported helper success was rejected when the actual Save sheet remained visible and the destination was absent. The current result guard retains output privately, requires the expected mode completion and actual selected-test pass without skips, and checks the unchanged app process before reporting success. Thirty-eight dual-runner regressions passed, including the retained skip shape, conflicting/missing markers and process replacement. The earlier skip's fixture cause remains undetermined: a read-only runtime probe later loaded the expected installed export fixture. Temporary observation code was removed.
- The same proof guard now covers selected native background/foreground tests. Their existing Swift app-state assertions and completion markers must execute; unchanged PID and successful peer messages alone cannot establish a lifecycle transition. The guard retains diagnostics and rejects missing, conflicting, duplicate or skipped action evidence before reporting success. No new lifecycle API, action or timeout is introduced, and no current-candidate background/recovery pass is claimed.
- B's first native export then timed out. Its retained tail repeatedly resolved the common picker but did not match the exact folder element, so navigation and Save were not reached. At that checkpoint, an empty-folder representation difference remained a hypothesis pending its actual hierarchy. The later inspection below resolves that blocker from actual native structure; this failed run itself still proves no B export, replacement, remaining recovery or final aggregate.
- Both failed inspections' original interrupted results remain unchanged. Independent checks found zero owned stack resources, simulators and known processes, and the exact-run guarded finalizers passed. These local partial results do not establish combined qualification, protected deployment, physical preparation or HIL. No persistent reset was repeated.

### Actual empty-folder export and replacement verification

- Warm inspection `local-cua-20261016-0401`, based on clean `1d2c0363027ffcb11d6d49558fc5a4063731f69b` and accepted corpus `2c35d0304efe04f6cae1a7bba9f244004e017d29`, authenticated both independent apps. Actual native import and Flutter upload produced the initial 48-byte shared file, which B displayed in its own Flutter preview. These are local inspection outcomes, not qualification aggregates.
- B's first read-only native snapshot omitted the remote picker hierarchy despite the visible Save sheet. A second unchanged read-only probe captured it: the LocalStorage browsing-root identifier described the parent “Auf meinem iPhone”, while the navigation button identified the current Weave folder. The exact `Title: Weave` root query therefore did not match this actual empty-folder representation. The filename field remained beneath the common picker in a sibling footer. The private second hierarchy has SHA-256 `17a7929bf92c30d391fe91578af2e7ec43f8d0906a59c9fdd9deb189a253d459`; no application activation, launch or tap was performed by these probes.
- The final driver correction identifies the unique LocalStorage browsing root by its fixed identifier prefix and separately requires the unique enabled, hittable current Weave navigation button. Save remains narrowly scoped to that navigation bar; the common filename query, single Save tap, original deadline and actual picker-dismissal assertion are retained. Temporary full-hierarchy observation code was removed. The export-replay Swift source SHA-256 is `d44403111d2b1c0a0b621bd8d38d63308bce392a3e45d8b4ed5c7700b3a327a1`. The existing 38 guard regressions remain unchanged; the native build passed in 4.457 seconds.
- Actual controlled B command 4 executed the selected export test in 2.693 seconds with no skips, dismissed the empty-folder Save sheet and displayed Flutter Saved. A new owned destination contained the exact initial 48 bytes, and the existing sentinel was unchanged. The private command transcript has SHA-256 `95e1c7bba97484aec08bd555cf8bd301ed76d72443e505a62c42f01f5b714c8e`; the destination proof is retained as `actual-B-empty-folder-export-pass.json`.
- Actual A replacement import command 5 passed in 2.385 seconds. Both actual Flutter previews then displayed the exact 52-byte replacement contents. B's updated export command 6 passed in 2.812 seconds, followed by Flutter Saved and a new owned 52-byte destination with the expected replacement hash; the sentinel remained unchanged. The complete transcript digest and destination evidence are retained privately in `actual-B-replacement-export-pass.json`. Both application PIDs remained unchanged through these interactions.
- These results resolve the observed empty-folder export blocker and verify local initial export, shared replacement/readback and updated export on the actual apps. They do not establish the seven final scenario aggregates, remaining authorization/recovery/accessibility requirements, a clean normal qualification pass, protected deployment, physical preparation or HIL. No persistent reset or release promotion is claimed.

### Lifecycle failure and framework state synchronization

- The same `local-cua-20261016-0401` inspection subsequently failed B's background lifecycle action. The selected native test executed the Home press at 0.02 seconds, then failed the background-state assertion after 10.84 seconds; the test failed in 10.862 seconds and emitted no background completion marker. The original private transcript SHA-256 is `502417b94ebcaf49ec3be9c507a2e807548592c29d22176892fd8666c6812c21`. The actual terminal state enum was not retained, so neither the observed Home screen nor the retained process establishes the assertion's cause.
- The driver now records only the fixed action/checkpoint and captured state enum before, immediately after and at the end of each lifecycle transition. Background uses XCTest's predicate expectation for the same two accepted background states; foreground uses the application's native state wait after the unchanged activation. Both retain the ten-second wait limit and assert the captured final state before emitting a success marker. Apple documents [asynchronous application-state updates](https://developer.apple.com/documentation/xcuiautomation/xcuiapplication/state-swift.property), [application state waits](https://developer.apple.com/documentation/xcuiautomation/xcuiapplication/wait(for:timeout:)) and [predicate expectations](https://developer.apple.com/documentation/xctest/xctnspredicateexpectation). This is a framework synchronization correction with bounded diagnostics; the blocking polling loop is a plausible risk, not a proven cause of the failed run.
- Actual lifecycle replay remains pending. The earlier Files results remain partial local evidence, and no final qualification aggregate is promoted. Normal cleanup and the guarded finalizer completed; independent owned-resource checks were zero. All simulator and process bindings from this inspection are now invalid. No deployment, physical preparation or HIL pass is claimed.

The protected candidate remains the prior Dogfood commit until normal reviewed integration and promotion. Shared Files/Chat controls, schema catalog/role conformance and the dual-simulator runner are implemented in the feature candidate and undergoing actual native qualification. Both iOS applications have not yet completed the mandatory concurrent journey on this source. Chat, Calendar, Files, recovery, authorization and accessibility results must be reported independently from the actual run. No new physical installation, launch, human acceptance or release promotion is claimed.

Keep #1470 and #1481 open. HIL entry requires successful mandatory automated integration, deployment and physical iPhone preparation for the same unchanged candidate; the existing single-simulator evidence cannot satisfy #1592.

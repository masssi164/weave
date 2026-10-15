# Local two-app interaction inspection

This mode reuses `testApp` and its native runner override. It holds one disposable
stack and two independent real Flutter iOS apps for Computer Use. It is not
qualification and never writes a native-qualification/v2 PASS result.

Start only after review, from a clean locally committed infrastructure batch, with
the existing native TLS, signing, Docker and specification environment configured:

```sh
WEAVE_NATIVE_LOCAL_INSPECTION=true \
WEAVE_TEST_APP_NATIVE_RUNNER="$PWD/client/tool/run_native_qualification.py" \
WEAVE_TEST_APP_RUN_ID=local-cua-YYYYMMDD-HHMM \
WEAVE_CANDIDATE_COMMIT="$(git rev-parse HEAD)" \
./gradlew --no-daemon specCorpusConformance testApp
```

The normal 20 GiB/Docker checks and isolated namespace remain mandatory. No
persistent Dogfood profile or data is used. The flag is internal runner tooling,
not product configuration. Unset it for the unchanged qualification behavior.

The operator must refuse a running or queued conflicting Mac job and monitor the
owned startup process from its beginning. After the native monitor emits the
fresh, source-bound `NATIVE_LOCAL_INSPECTION phase=priority-watch-ready` record,
the operator records its byte offset/hash and hands observation to that monitor.
It stops the startup observer rather than running two interruption owners. The
native monitor watches for a new `Runner.Worker` throughout native preparation,
browser work and the warm hold. It reports fixed `worker` or `unknown` reasons and yields
by interrupting itself, stopping only its registered groups and invoking existing
owned cleanup. This is observation-based priority, not an atomic CI mutex: a tiny
observation/cleanup race remains. Do not claim GitHub concurrency protects local
processes, stop the runner service, cancel CI or kill unrelated processes.

Wait for `NATIVE_LOCAL_INSPECTION phase=held`. Its private scope contains
`inspection-session-0.json` (with subsequent immutable per-command snapshots) with exact owned actor/process paths. Each actor uses supported resident `flutter run` with the real prebuilt simulator
app and native-assets map identified by the completed initial iOS build receipt.
The canonical client is the working directory and supplies the package config and
generated plugin registrant. Only compiler output and resident PID files are
actor-private; no copied project is used. The CLI performs normal launch and
resume. Install/update storage preservation must be observed on the owned run;
source inspection alone is not runtime proof. Native-hook cache reuse is observed,
not assumed from file presence.

Use Computer Use to enter the canonical organization URL for actor A. Stop at the
visible Sign in screen. Publish an owner-only `inspection-command.json` in that
scope, for example `{"sequence":1,"action":"browser","role":"A"}`. The existing
native BrowserDriver clicks that actual Sign in control and completes real SSO.
Repeat for B. Do not click Sign in before issuing the browser command. A passive
rendered-workspace and stored-session observer supplies the existing completion
signal; it does not restore/refresh authentication, change providers or create
product outcomes. Use Computer Use to operate the product and native picker.

Commands have exactly `sequence` (strictly increasing integer), `action`
(`browser`, `reload`, `restart`, `background`, `foreground`, `network-off`,
`network-on`, `relaunch`, `close`) and `role` (`A`, `B`, `both`; browser
requires one actor). Files are private, current-user-owned, nonsymlink and 0600.
Reload/restart records include requested source and resident acknowledgement;
loaded-script digest verification remains explicitly false until independently
checked. `restart` is a Dart hot restart; `relaunch` terminates the exact owned
app process and starts a new resident process while preserving its storage. Background
and network controls reuse the owned lifecycle and relay operations. Record actual UI outcomes, operation time and any remaining blocker in
existing work notes. Dirty iterations are identified; native/Rust/backend changes
require an affected rebuild/relaunch, not Dart reload.

`close` returns the deliberate nonqualification status 70 after owned app/device
cleanup; the original outer `testApp` teardown then removes the isolated stack.
Its failed acceptance status must not be relabeled PASS. Interrupt, failure or
lost ownership also performs cleanup. Preserve useful private receipts before
closing because temporary actor logs and fixtures are removed. Never upload raw
browser transcripts, credentials, VM URLs or device identifiers.

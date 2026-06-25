# Sprint 31 runbook — Physical iPhone LAN dogfood

Sprint 31 prepares Massimo's first real physical iPhone test on the same LAN as the Mac. It does not require public DNS or trusted internet TLS for this local dogfood profile.

## Operator command

## Delivery gate before Massimo

Treat the earlier faulty handoff PR as evidence that local unit/widget and CI checks are not enough. The dogfood/member onboarding delivery order is:

1. Implement and review the change against the `dev` integration state.
2. Run the onboarding E2E gate in the iOS Simulator from that `dev` state.
3. Proceed to dogfood promotion/candidate installation only after the simulator gate prints `SIMULATOR_E2E_GREEN`.
4. Install the dogfood candidate on Massimo's physical iPhone over Wi-Fi when `devicectl` can reach it; if Wi-Fi reachability fails, use USB as the fallback transport after pairing/authorization.
5. Verify trust stability before handing the build to Massimo: normal stack restart/recreate must preserve the local TLS CA and leaf certificate unless explicit rotation is requested, and normal iOS update/app-state reset must not require Massimo to trust the development team/profile again.
6. Report `READY_FOR_MASSIMO_TEST` only after the candidate is installable and all relevant onboarding E2E evidence is green, or report `BLOCKED` with the exact failing command/check/device step.

From the repo root, after starting the local stack on a phone-reachable Mac LAN address:

```sh
WEAVE_LAN_HOST=<Mac LAN IP> tools/weavectl profile apply \
  --profile local-lan-dogfood \
  --lan-host "$WEAVE_LAN_HOST" \
  --emit-handoff \
  --emit-evidence \
  --preflight-mode tcp
```

For dry validation before the stack is listening, use `--preflight-mode validate-only`. The command writes one run id under `build/evidence/local-lan-dogfood/<run-id>/` with `readiness.json`, `handoff.json`, and `evidence.json`.

The command rejects `localhost`, `127.0.0.1`, `0.0.0.0`, container-only names, and Mac-only `.local` assumptions before handoff output. Handoff evidence stores refs and endpoint classes, not raw secrets or provider diagnostics. The handoff includes one canonical product join URL (`/join`) plus a `weave:/join?...` local-dev fallback. These are non-secret enrollment handoff links, not bearer access. Real access control is the provisioned account, organization/workspace membership, and identity-provider session. Both handoff links point at the public `/api/platform/config` app-start discovery contract; the member client must not derive provider topology from a guessed base URL.

For the persistent `weave.test` dogfood lane, generate the current handoff bundle before sending tester instructions:

```sh
tools/dogfood_handoff_bundle.py
```

This writes `build/dogfood/handoff.json` and `build/dogfood/handoff.md` with the current `weave://join` deeplink, web join URL, local CA URLs, CA/leaf fingerprints when local cert files exist, stack commit, and iOS profile/release smoke requirements. Send the generated artifact contents, not a stale chat transcript. If the CA or leaf fingerprint changes between normal reruns, treat that as a trust-stability blocker unless the run explicitly requested certificate rotation.

Installed iOS client smoke must use a profile or release build. Debug builds and raw `devicectl` process launch success are not valid dogfood evidence for iOS custom-scheme launch. Use `tools/dogfood_ios_deeplink_smoke.sh` with `WEAVE_IOS_BUILD_MODE=profile` or `release`; this proves `last_handoff_consumed_v1`, `dogfood_visible_state_v1=handoff_ready`, and `dogfood_auth_state_v1=ready_for_sso` from the app container. It is still only the handoff gate. Full member onboarding evidence additionally requires Sign In, account activation, saved session, workspace entry, restore, reinstall/manual login, and Mailpit capture. Wi-Fi install is preferred, USB is a fallback only, and both install paths use the same stable bundle ID, provisioning Team ID `KNDHGC2KV6`, developer certificate label `Apple Development: massimo164@me.com (6RUS2Z848X)`, signing identity/profile class, and developer-trust assumptions. If a normal update or trust-preserving app-state reset prompts Massimo to trust the development team/profile again, the dogfood path is not ready for Massimo.

Do not uninstall the physical iPhone app by default. Apple can remove the Developer App trust anchor when the last app signed by a developer is deleted, which turns the next launch into a manual `Apple Development: massimo164@me.com (6RUS2Z848X)` trust step. The physical runner therefore defaults to `WEAVE_IOS_RESET_MODE=update_in_place`. For fresh-install semantics without deleting the app, use `WEAVE_IOS_RESET_MODE=app_state`; the runner appends a dogfood-only reset marker to the handoff so Weave clears saved configuration, handoff evidence, auth session, Nextcloud session, and dogfood evidence before consuming the link. Use `WEAVE_IOS_RESET_MODE=destructive_uninstall` only as an explicit physical-trust-disruptive reset and record that it may require a new Developer App trust approval. If repeated trust remains part of the development-runner path, prefer TestFlight/internal or ad-hoc distribution for professional dogfood handoff instead of asking Massimo to repeat device-management trust.

Current physical-device evidence shows stable Developer App trust is possible: after the device-side approval, update-in-place plus `WEAVE_IOS_RESET_MODE=app_state` can install and launch without another `Apple Development: massimo164@me.com (6RUS2Z848X)` prompt. This is trust/runner evidence only. It is not member-onboarding success and must not be reported as a professional entry until handoff-ready, Sign In, activation, session, workspace, restore, app-state reset/manual login, and Mailpit evidence are all green.

Trust evidence has four separate domains and must not collapse them into one generic "trust" step:

- Local TLS trust: the Weave Local Development CA and leaf certificate let Safari, AppAuth, and the app trust `weave.test` and service HTTPS.
- Apple Developer Mode: Settings > Privacy & Security > Developer Mode. This allows development builds and may require a restart.
- iOS Developer App trust: Settings > General > VPN & Device Management > Developer App > `Apple Development: massimo164@me.com (6RUS2Z848X)` > Trust. A normal dogfood update or app-state reset must not ask for this again.
- iOS app signing/provisioning stability: the installed app must keep stable bundle ID `com.massimotter.weave`, provisioning Team ID `KNDHGC2KV6`, developer certificate label `6RUS2Z848X`, and profile class so iOS does not require repeated developer trust after normal update/app-state reset.
- AppAuth/OIDC trust/session state: the browser-based identity session, token storage, refresh/offline policy, and workspace restore are proved by onboarding/session evidence, not by certificate or signing checks.

After generating the handoff and installing a physical candidate, run:

```sh
python3 tools/dogfood_trust_stability_check.py \
  --install-transport wifi \
  --install-reset-mode update_in_place \
  --developer-trust-status trusted
```

Use `--install-transport usb` only when Wi-Fi reachability failed and the same signing/trust assumptions still hold. Use `--install-reset-mode app_state` for trust-preserving fresh semantics and `--install-reset-mode destructive_uninstall` only when the run intentionally deletes the app and accepts a possible Developer App trust reset. If Apple device policy prevents automated developer-trust proof, run with `--developer-trust-status blocked_by_device_policy --allow-blocked-device-policy` and report the emitted `DOGFOOD_TRUST_STABILITY_BLOCKED` marker as a pending device-policy state. Do not ask Massimo to test while repeated developer trust prompts remain possible.

When Massimo's physical iPhone is not reachable, run the simulator handoff gate instead:

```sh
tools/dogfood_ios_simulator_onboarding_smoke.sh
```

The simulator smoke boots or reuses an iPhone simulator, installs the local dogfood CA, builds the iOS simulator app, clears app data, installs Weave, injects the same pending native deeplink URL that the iOS URL bridge consumes, and verifies `handoff_ready` plus `ready_for_sso` support-safe preferences. It prints `SIMULATOR_E2E_GREEN` only for that simulator-covered scope. It does not replace physical-device evidence because `simctl openurl` can stop on Apple's "Open in Weave?" consent sheet and simulator runs do not prove the real iPhone trust profile, AppAuth browser handoff, account activation, saved session restore, reinstall/manual login, or Mailpit capture.

## What Massimo should see

For tomorrow's local dogfood, the customer-friendly start page is:

```text
https://weave.test:44443/
```

It includes the local CA downloads, iPhone trust steps, DNS-first service links, and the no-secrets warning. To generate the deterministic no-secret invite source from the repo:

```sh
infra/weave-workspace/local-invite-link.sh --json
```

Default non-secret enrollment handoff link to give Massimo:

```text
https://weave.test:44443/join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood
```

Give Massimo the QR/link from `handoff.json` or the deterministic local handoff above and say:

> Open this Weave enrollment handoff on the iPhone while it is on the same LAN as the Mac. It is not a secret access token; your account and membership control access. It starts sign-in and then opens the Weave workspace home.

Expected tester path before Massimo is asked to try the build:

1. Install or update Weave as a profile or release app with stable bundle ID `com.massimotter.weave`, provisioning Team ID `KNDHGC2KV6`, developer certificate label `Apple Development: massimo164@me.com (6RUS2Z848X)`, and signing/provisioning profile class. A normal update or trust-preserving app-state reset must not require trusting the developer profile again.
2. For local self-signed HTTPS, install and fully trust the generated Weave Local Development CA on the iPhone before SSO. Safari/AppAuth use system trust; the app must not hide certificate trust failures. Normal stack restart/recreate must not rotate this CA or the leaf certificate unless explicit rotation is requested.
3. Tap or scan the invite/QR/handoff.
4. The app fetches `/api/platform/config`, saves OIDC/API/facade configuration, writes `last_handoff_consumed_v1`, writes `dogfood_auth_state_v1=ready_for_sso`, and shows a handoff-aware workspace sign-in state.
5. Tap Sign In. The copied app preferences must move to `dogfood_auth_state_v1=sso_in_progress`; a no-op Sign In button is a hard failure.
6. Complete the identity first-login path, including account activation and password setup. Passkey/WebAuthn setup should be offered when the dogfood identity profile supports it; otherwise record the support-safe unsupported reason.
7. After returning to Weave, copied app preferences must show `dogfood_auth_state_v1=workspace_ready`, and Weave must enter the authenticated workspace/home without raw provider/setup copy.
8. Force-quit and reopen the app. The saved mobile session must restore without another interactive login.
9. Run the trust-preserving app-state reset path, open the current handoff or organization sign-in path, and complete manual login to the workspace again. This proves fresh-session/manual-login recovery from a user perspective without deleting the Developer App trust anchor. A true uninstall/reinstall is a separate physical-trust-disruptive reset and must be explicitly requested.
10. Verify local dogfood mail in Mailpit at `http://127.0.0.1:8025` or its API. Mailpit must capture identity mail locally and must not send dogfood mail externally.
11. Verify `DOGFOOD_TRUST_STABILITY_RESULT` before handoff. If the trust checker emits `DOGFOOD_TRUST_STABILITY_BLOCKED`, report that blocker instead of asking Massimo to retest.

After the full path, copy the app preferences and run:

```sh
python3 tools/dogfood_onboarding_evidence_check.py \
  --prefs-plist build/dogfood/appdata/com.massimotter.weave.plist \
  --expected-handoff-ref handoff-s32-massimo-dogfood-home \
  --expected-run-id s32-massimo-dogfood
```

The command emits `DOGFOOD_MEMBER_ONBOARDING_RESULT` only when the copied app state is support-safe and reaches `workspace_ready`. Use `--skip-mailpit` only for a local dry run; it is not sufficient dogfood completion evidence.

If the simulator gate is green but the phone is unplugged or Wi-Fi-unreachable, report `SIMULATOR_E2E_GREEN` and `PHYSICAL_DEVICE_E2E_PENDING` separately. The smallest remaining physical command sequence is:

```sh
WEAVE_IOS_DEVICE_ID=<plugged-iphone-id> \
WEAVE_IOS_RESET_MODE=app_state \
WEAVE_DOGFOOD_DEEPLINK='<current weave://join... URL>' \
tools/dogfood_ios_deeplink_smoke.sh

python3 tools/dogfood_onboarding_evidence_check.py \
  --prefs-plist build/dogfood/appdata/com.massimotter.weave.plist \
  --expected-handoff-ref handoff-s32-massimo-dogfood-home \
  --expected-run-id s32-massimo-dogfood
```

The first command proves installed-client handoff readiness on the plugged iPhone. The second command is valid only after Sign In, first-login activation/password/passkey handling, workspace entry, force-quit restore, trust-preserving app-state reset/manual login, and Mailpit capture have all completed.

The member path must not ask for OIDC issuer, OIDC client ID, Matrix URL, Nextcloud URL, provider hostname, provider diagnostics, SecretRef, or credential URL. Local development may ask the tester to enable Developer Mode once, trust the local CA once, and trust the Developer App once when Apple policy requires it. Repeated local CA or Developer App trust after normal stack restart, app update, or trust-preserving app-state reset is a release blocker; production/customer links must use publicly trusted TLS and production-signed app distribution.

## Physical evidence

Use `docs/evidence/sprint-31-physical-iphone-lan-dogfood/physical-iphone-checklist.md`. If the only remaining step is Massimo's real iPhone, record that as the smallest remaining action: run the command with the real LAN IP, open the handoff on the iPhone, complete SSO, and mark workspace/home result.

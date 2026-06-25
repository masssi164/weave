# Sprint 31 runbook — Physical iPhone LAN dogfood

Sprint 31 prepares Massimo's first real physical iPhone test on the same LAN as the Mac. It does not require public DNS or trusted internet TLS for this local dogfood profile.

## Operator command

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

This writes `build/dogfood/handoff.json` and `build/dogfood/handoff.md` with the current `weave://join` deeplink, web join URL, local CA URLs, CA/leaf fingerprints when local cert files exist, stack commit, and iOS profile/release smoke requirements. Send the generated artifact contents, not a stale chat transcript.

Installed iOS client smoke must use a profile or release build. Debug builds and raw `devicectl` process launch success are not valid dogfood evidence for iOS custom-scheme launch. Use `tools/dogfood_ios_deeplink_smoke.sh` with `WEAVE_IOS_BUILD_MODE=profile` or `release`; the follow-up evidence still requires `last_handoff_consumed_v1` from the app container and Massimo-visible handoff-aware sign-in state.

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

Expected tester path:

1. Install Weave once with a stable bundle identity, or open the already-installed app.
2. For local self-signed HTTPS, install and fully trust the generated Weave Local Development CA on the iPhone before SSO. Safari/AppAuth use system trust; the app must not hide certificate trust failures.
3. Tap or scan the invite/QR/handoff. Do not reinstall the app as part of startup.
4. The app fetches `/api/platform/config`, saves OIDC/API/facade configuration, writes `last_handoff_consumed_v1`, shows a handoff-aware workspace sign-in state, starts SSO, and lands in Weave workspace/home.

The member path must not ask for OIDC issuer, OIDC client ID, Matrix URL, Nextcloud URL, provider hostname, provider diagnostics, SecretRef, or credential URL. Local development may ask the tester to trust the local CA once; production/customer links must use publicly trusted TLS.

## Physical evidence

Use `docs/evidence/sprint-31-physical-iphone-lan-dogfood/physical-iphone-checklist.md`. If the only remaining step is Massimo's real iPhone, record that as the smallest remaining action: run the command with the real LAN IP, open the handoff on the iPhone, complete SSO, and mark workspace/home result.

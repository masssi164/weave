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

The command rejects `localhost`, `127.0.0.1`, `0.0.0.0`, container-only names, and Mac-only `.local` assumptions before handoff output. Handoff evidence stores refs and endpoint classes, not raw secrets or provider diagnostics. The handoff includes one canonical product join URL (`/join`) plus a `weave:/join?...` local-dev fallback. Both point at the public `/api/platform/config` app-start discovery contract; the member client must not derive provider topology from a guessed base URL.

## What Massimo should see

For tomorrow's local dogfood, the customer-friendly start page is:

```text
https://weave.local:44443/
```

It includes the local CA downloads, iPhone trust steps, DNS-first service links, and the no-secrets warning. To generate the deterministic no-secret invite source from the repo:

```sh
infra/weave-workspace/local-invite-link.sh --json
```

Default invite link to give Massimo:

```text
https://weave.local:44443/join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood
```

Give Massimo the QR/link from `handoff.json` or the deterministic local invite above and say:

> Open this Weave invite on the iPhone while it is on the same LAN as the Mac. It starts sign-in and then opens the Weave workspace home.

Expected tester path:

1. Install Weave once with a stable bundle identity, or open the already-installed app.
2. For local self-signed HTTPS, install and fully trust the generated Weave Local Development CA on the iPhone before SSO. Safari/AppAuth use system trust; the app must not hide certificate trust failures.
3. Tap or scan the invite/QR/handoff. Do not reinstall the app as part of startup.
4. The app fetches `/api/platform/config`, saves OIDC/API/facade configuration, starts SSO, and lands in Weave workspace/home.

The member path must not ask for OIDC issuer, OIDC client ID, Matrix URL, Nextcloud URL, provider hostname, provider diagnostics, SecretRef, or credential URL. Local development may ask the tester to trust the local CA once; production/customer links must use publicly trusted TLS.

## Physical evidence

Use `docs/evidence/sprint-31-physical-iphone-lan-dogfood/physical-iphone-checklist.md`. If the only remaining step is Massimo's real iPhone, record that as the smallest remaining action: run the command with the real LAN IP, open the handoff on the iPhone, complete SSO, and mark workspace/home result.

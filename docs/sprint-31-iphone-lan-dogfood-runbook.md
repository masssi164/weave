# Sprint 31 runbook — Physical iPhone LAN dogfood

Sprint 31 prepares Massimo's first real iPhone test on the same LAN as the Mac. It does not require public DNS or trusted internet TLS for this local dogfood profile.

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

The command rejects `localhost`, `127.0.0.1`, `0.0.0.0`, container-only names, and Mac-only `.local` assumptions before handoff output. Handoff evidence stores refs and endpoint classes, not raw secrets or provider diagnostics. The handoff includes both a LAN URL/QR payload and a separate `weave:/join?...` deep link; the mobile manifests register that member handoff scheme separately from the OIDC callback scheme.

## What Massimo should see

Give Massimo the QR/link from `handoff.json` and say:

> Open this Weave invite on the iPhone while it is on the same LAN as the Mac. It starts sign-in and then opens the Weave workspace home.

Expected tester path:

1. Install or open Weave on the physical iPhone.
2. Tap or scan the invite/QR/handoff.
3. Continue with SSO.
4. Land in Weave workspace/home.

The member path must not ask for OIDC issuer, OIDC client ID, Matrix URL, Nextcloud URL, provider hostname, TLS certificate, provider diagnostics, SecretRef, or credential URL.

## Physical evidence

Use `docs/evidence/sprint-31-physical-iphone-lan-dogfood/physical-iphone-checklist.md`. If the only remaining step is Massimo's real iPhone, record that as the smallest remaining action: run the command with the real LAN IP, open the handoff on the iPhone, complete SSO, and mark workspace/home result.

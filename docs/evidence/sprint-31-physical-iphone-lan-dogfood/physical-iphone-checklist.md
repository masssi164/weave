# Sprint 31 physical iPhone LAN dogfood checklist

Use this checklist after `weavectl profile apply` emits the run id. Do not paste screenshots or logs that contain tokens, credential URLs, provider payloads, Matrix homeserver internals, member private content, or raw downstream errors.

## Operator preflight

- Run id:
- Command: `tools/weavectl profile apply --profile local-lan-dogfood --lan-host <Mac LAN IP> --emit-handoff --emit-evidence --preflight-mode tcp`
- Evidence directory:
- Handoff artifact:
- Readiness artifact:
- LAN assumption: iPhone and Mac are on the same LAN, with client isolation disabled.
- Preflight result: pass / fail with stable code only.
- Local TLS trust result: `DOGFOOD_TRUST_STABILITY_RESULT` / `DOGFOOD_TRUST_STABILITY_BLOCKED`.
- iOS signing trust result: stable Team ID/profile / blocked by device policy / not verified.
- Install transport: Wi-Fi preferred / USB fallback.

## Tester script for Massimo

1. Install or update Weave on the physical iPhone over Wi-Fi when reachable; use USB only as fallback.
2. Confirm the install keeps bundle ID `com.massimotter.weave`, Team ID `KNDHGC2KV6`, and the same signing/provisioning trust assumptions. A normal reinstall/update must not require trusting the developer profile again.
3. Confirm local TLS trust separately: the Weave Local Development CA and leaf certificate match the latest handoff fingerprint baseline unless explicit rotation was requested.
4. Scan the QR or open the handoff link from the emitted handoff artifact.
5. Confirm the handoff opens Weave sign-in without asking for OIDC issuer, OIDC client ID, Matrix URL, Nextcloud URL, provider hostname, TLS certificate, provider diagnostics, SecretRef, or credential URL.
6. Complete SSO.
7. Confirm Weave workspace/home appears.
8. If it fails, record only the stable next-action code, for example `WEAVE-LAN-UNREACHABLE`, `WEAVE-HANDOFF-INVALID`, `WEAVE-IOS-DEVELOPER-TRUST-NOT-VERIFIED`, or `WEAVE-SSO-NOT-COMPLETE`.

## Result

- Device: physical iPhone, model/iOS version optional.
- Handoff type: QR / link / deep link / organization URL.
- SSO result: pass / fail.
- Workspace/home result: pass / fail.
- Local TLS trust: stable / rotated by explicit request / blocked.
- iOS app signing trust: stable / repeated developer trust prompt / blocked by Apple device policy.
- AppAuth/OIDC trust/session: pass / fail / pending.
- Accessibility note: QR/link prompt was readable by screen reader and did not rely on color alone.
- Redaction confirmation: no secrets, tokens, credential-bearing URLs, raw provider payloads, raw provider diagnostics, homeserver internals, or member content captured.

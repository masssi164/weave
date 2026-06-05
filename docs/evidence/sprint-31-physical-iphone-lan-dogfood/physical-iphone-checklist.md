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

## Tester script for Massimo

1. Install or open the Weave app on the physical iPhone.
2. Scan the QR or open the handoff link from the emitted handoff artifact.
3. Confirm the handoff opens Weave sign-in without asking for OIDC issuer, OIDC client ID, Matrix URL, Nextcloud URL, provider hostname, TLS certificate, provider diagnostics, SecretRef, or credential URL.
4. Complete SSO.
5. Confirm Weave workspace/home appears.
6. If it fails, record only the stable next-action code, for example `WEAVE-LAN-UNREACHABLE`, `WEAVE-HANDOFF-INVALID`, or `WEAVE-SSO-NOT-COMPLETE`.

## Result

- Device: physical iPhone, model/iOS version optional.
- Handoff type: QR / link / deep link / organization URL.
- SSO result: pass / fail.
- Workspace/home result: pass / fail.
- Accessibility note: QR/link prompt was readable by screen reader and did not rely on color alone.
- Redaction confirmation: no secrets, tokens, credential-bearing URLs, raw provider payloads, raw provider diagnostics, homeserver internals, or member content captured.

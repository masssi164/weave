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
- Physical iPhone local CA preflight: `WEAVE_IOS_LOCAL_CA_TRUST_STATUS=trusted` / `PHYSICAL_DEVICE_TLS_PENDING`.
- Platform config fetch boundary: physical iPhone can fetch `/api/platform/config` over trusted TLS / `WEAVE-APP-START-TLS-FAILED`.
- iOS signing trust result: stable bundle ID / stable provisioning Team ID `KNDHGC2KV6` / stable developer certificate `6RUS2Z848X` / blocked by device policy / not verified.
- Install transport: Wi-Fi preferred / USB fallback.
- Install reset mode: update-in-place / trust-preserving app-state reset / explicit destructive uninstall.
- Developer Mode status: enabled / not verified.
- Developer App trust status for `Apple Development: massimo164@me.com (6RUS2Z848X)`: trusted once / repeated prompt / not verified.

## Tester script for Massimo

1. Install or update Weave on the physical iPhone over Wi-Fi when reachable; use USB only as fallback.
2. Confirm the install keeps bundle ID `com.massimotter.weave`, provisioning Team ID `KNDHGC2KV6`, developer certificate label `Apple Development: massimo164@me.com (6RUS2Z848X)`, and the same signing/provisioning trust assumptions. A normal update or trust-preserving app-state reset must not require trusting the Developer App again.
3. Confirm local TLS trust separately: the Weave Local Development CA and leaf certificate match the latest handoff fingerprint baseline unless explicit rotation was requested, and the iPhone has installed the CA profile and enabled full trust before Weave is launched.
4. Scan the QR or open the handoff link from the emitted handoff artifact.
5. Confirm the handoff opens Weave sign-in without asking for OIDC issuer, OIDC client ID, Matrix URL, Nextcloud URL, provider hostname, TLS certificate, provider diagnostics, SecretRef, or credential URL.
6. Complete SSO.
7. Confirm Weave workspace/home appears.
8. If it fails, record only the stable next-action code, for example `PHYSICAL_DEVICE_TLS_PENDING`, `WEAVE-APP-START-TLS-FAILED`, `WEAVE-LAN-UNREACHABLE`, `WEAVE-HANDOFF-INVALID`, `WEAVE-IOS-DEVELOPER-TRUST-NOT-VERIFIED`, or `WEAVE-SSO-NOT-COMPLETE`.

## Result

- Device: physical iPhone, model/iOS version optional.
- Handoff type: QR / link / deep link / organization URL.
- SSO result: pass / fail.
- Workspace/home result: pass / fail.
- Local TLS trust: stable / rotated by explicit request / blocked.
- Physical platform-config TLS fetch: pass / `WEAVE-APP-START-TLS-FAILED` / pending manual CA trust.
- Apple Developer Mode: enabled / not verified.
- iOS Developer App trust: trusted once / repeated trust prompt / blocked by Apple device policy.
- iOS app signing/provisioning stability: stable / bundle ID changed / Team ID changed / developer certificate changed / profile hash changed.
- Physical reset behavior: update-in-place / app-state reset / destructive uninstall.
- AppAuth/OIDC trust/session: pass / fail / pending.
- Accessibility note: QR/link prompt was readable by screen reader and did not rely on color alone.
- Redaction confirmation: no secrets, tokens, credential-bearing URLs, raw provider payloads, raw provider diagnostics, homeserver internals, or member content captured.

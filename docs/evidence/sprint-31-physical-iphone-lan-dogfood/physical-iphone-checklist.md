# Sprint 31 physical iPhone LAN dogfood checklist

Status: **Historical observation template.** Current delivery and human acceptance follow
[iOS Dogfood distribution](../../ios-dogfood-distribution.md). The former local installer,
private-plist probes and trust-stability script are retired; their output is not current
qualification evidence. Record only support-safe observations, never activation links,
credentials, raw provider errors or private member content.

## Preparation reference

- Exact candidate and qualified artifact/build:
- Successful protected Full Compose E2E and Dogfood deployment runs:
- Prepare Human Test run and selected ordinary member slot:
- Device reachability and install transport:
- TLS certificate continuity: `tools/dogfood_cert_persistence_smoke.py` result from protected preparation.
- iOS signature/build identity: `tools/sign_iphone_artifact.py` and `tools/prepare_iphone_artifact.py` validation.
- Installation mode: update in place, retaining the app container and session.
- Developer Mode: enabled / not verified.
- Developer App trust: trusted / repeated prompt / not verified.
- Physical TLS trust and ordinary origin discovery: observed / blocked / not available.

## Human observations

1. Open the qualified app on the physical iPhone.
2. Confirm the intended organization and selected ordinary member identity. Complete normal SSO if sign-in is shown.
3. Confirm workspace/home and ordinary Chat, Files, Calendar and Settings navigation.
4. Exercise the required shared content and current-access journeys with the intended peer.
5. Check relaunch continuity and the required VoiceOver labels, order, focus and error actions.
6. Record the actual result using the current protected human-test format and exact candidate/build.

## Result

- Actual account role: member.
- SSO and current identity: passed / failed / not available.
- Workspace/home: passed / failed / not available.
- Shared content and access: passed / failed / not available.
- Relaunch continuity: passed / failed / not available.
- VoiceOver: passed / failed / not available.
- Developer App trust after the update: stable / repeated prompt / not verified.
- TLS trust: established / blocked / not verified.
- Support-safe explanation of any blocked or unavailable outcome:

Preparation and automated readback leave this human result pending until the tester reports it.

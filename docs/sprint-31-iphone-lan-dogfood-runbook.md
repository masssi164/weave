# Sprint 31 runbook — Physical iPhone LAN dogfood

Status: **Historical reference.** Sprint 31 established the initial LAN handoff and
trust investigation. Its private-plist probes, app-only reset bridge, simulator
onboarding gate and local iPhone installer were retired in #1751 after #1750 removed
their app-side evidence. Git history retains the original investigation; none of its
former success labels qualifies a current candidate.

## Current procedure

Use [iOS Dogfood distribution](ios-dogfood-distribution.md) as the single operational
entry point. The current path is hosted candidate artifacts, protected **Full Compose
E2E** with two ordinary members, deployment readback, then **Prepare Human Test**.
The existing `tools/prepare_iphone_artifact.py` signs and installs the qualified
artifact in place, observes ordinary startup and leaves human acceptance pending.
It does not build a replacement app or automate the tester's SSO login.

[The authentication acceptance inventory](dogfood-auth-onboarding-plan.md) records
normal sign-in, cancellation/retry, current account and organization identity,
authorization, session restore and workspace/home coverage. Those behaviors are
observed through normal UI and API paths. Preparation, first-owner setup and the
human result remain separate evidence claims.

`tools/dogfood_handoff_bundle.py` can still generate a secret-free organization link
and the configured TLS certificate fingerprints. This bundle is informational;
it does not prove that a phone opened the link, completed discovery or passed a
product journey. Operator setup does not inspect app preferences or Keychain data.

## Trust boundaries retained from the investigation

- A physical iPhone must trust the configured TLS authority and resolve the reviewed
  organization and identity hosts. Prefer a public HTTPS route with a publicly trusted certificate
  when that is the selected deployment; a stable, already trusted local
  CA remains valid for the private LAN installation.
- Simulator CA injection is a separate test precondition: simulator CA injection is not physical dogfood proof.
- An observed simulator entry cannot establish a phone result: simulator handoff evidence is not physical-device E2E.
- Normal installation is an update in place. The app container, Keychain identity,
  signing team and Developer App trust remain stable. There is no product-only
  reset URL or hidden qualification screen.
- Apple Developer Mode, Developer App trust, TLS trust, signing identity and the
  AppAuth/OIDC session are distinct prerequisites and observations.
- Mailpit remains private to the Dogfood deployment. Activation links and message
  bodies are private identity artifacts, never public evidence or app fixtures.

The physical tester records the actual account role, exact candidate/build and
observed product/VoiceOver outcomes. A launch, certificate fingerprint, offline
fixture or simulator pass cannot manufacture that human result.

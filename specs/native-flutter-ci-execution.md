# Native Flutter acceptance execution for #1533

Status: implementation evidence contract for #1475, #1479, and #1480. The
accepted product behavior remains governed by the pinned Weave Specification
Corpus and its 2026-10 consolidation release contract.

The `Full Compose E2E` workflow owns this lane. It runs the exact candidate
source on a logged-in macOS runner with Xcode, Flutter, Java, Docker, a valid
Apple Development signing identity, Accessibility permission for the runner,
and a preconfigured native acceptance TLS CA. The runner's personal keychain
must not be exposed to unreviewed fork code. GitHub must require maintainer
approval for every external contributor's workflow; the required job routes
fork pull requests to a GitHub-hosted runner and fails before checkout.

For a trusted candidate, `testApp` creates a fresh disposable Compose stack,
waits for Keycloak, Server, PostgreSQL, Files, Calendar, and Matrix readiness,
creates dedicated member/admin/workload identities and product fixtures, and
executes the existing Gherkin-mapped Java/OpenClaw and native Flutter journeys.
The native runner builds a signed macOS app, drives the actual system-browser
OIDC/PKCE callback, checks Files/Calendar/Matrix behavior and denial, restarts
the app and core services, checks restoration and revocation, and fails if a
required phase cannot run. The stack is torn down by the existing `testApp`
cleanup. CI uploads only support-safe evidence; native build and Flutter test
logs remain local and private when they may contain sensitive diagnostics.

An exact candidate is accepted only when the required `Full Compose E2E` job
and the two Flutter processes finish successfully, with source, spec, client,
provider, and IdP versions recorded. A local pass, build-only run, skipped
integration test, or unavailable signing identity does not count as CI proof.
The macOS lane does not assert iOS or Android device compatibility.

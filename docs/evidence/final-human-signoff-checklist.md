# Final human signoff checklist

Human testing is a distinct gate after successful qualification, deployment and physical preparation for one exact protected Dogfood candidate. Apply the pinned corpus [DevOps steering](https://github.com/masssi164/weave-specs/blob/b02bd60620b1a0a7585756798ba5330155fc0ee2/steering/devops-conformance.md), ADR 0022 and human-testing acceptance contract. This checklist records implementation evidence; it does not replace those contracts.

## Entry criteria

- Record the exact Dogfood commit, source tree and pinned specification revision. Required deterministic CI and applicable spec/conformance, acceptance, documentation and release gates have passed for that candidate.
- Mandatory Full Compose E2E, native macOS and Apple Silicon iOS Simulator qualification have succeeded for that same candidate, including exact-run cleanup. Smoke, build-only or simulator-launch results cannot substitute for functional qualification.
- Dogfood deployment has succeeded and the stack is ready from that same qualified commit.
- The same commit is installed in place on the physical iPhone, with preparation evidence bound to the successful qualification/deployment and stable app/signing/TLS identity. Follow [iOS dogfood preparation](../ios-dogfood-distribution.md).
- Missing, failed, unavailable mandatory or mismatched entry evidence blocks HIL. Record support-safe blockers and the next action; documenting a blocker never satisfies entry criteria. A changed candidate source tree requires new qualification.

## Human checks

- Verify the physical Dogfood endpoints and trusted TLS against the selected [environment contract](../dev-test-main-promotion-flow.md). Physical LAN and isolated E2E environments have distinct endpoint profiles; do not require one hostname for every environment.
- Observe real physical-device system-browser sign-in, session persistence across in-place updates/restarts, Chat, Files, Calendar, authorization revoke/regrant and identity continuity through the supported product journeys.
- Exercise VoiceOver reading order, labels, focus, navigation and actionable error states on the physical iPhone; record actual accessibility outcomes.
- Report each mandatory human outcome as `passed`, `failed` or `not-available`, with support-safe notes. Automated results, installation and absent surfaces never imply a human pass. Failed or unavailable mandatory outcomes block Main promotion.
- Record date, tester, exact commit/specification, installed build number and links to automated, deployment and physical-preparation evidence. Use the explicit owner human-result format in [the protected promotion workflow](../gitflow-pr-workflow.md); Main verifies existing unchanged-candidate evidence rather than repeating equivalent integration.
- Keep release notes and readiness claims accurate while blockers remain. Human acceptance and Main promotion do not publish a production release.

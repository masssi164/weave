# Final human signoff checklist

Human testing is final signoff only after automated gates/evidence are complete or exact blockers are documented.

## Entry criteria

- GitHub CI is green for the release/merge candidate.
- Spec/conformance gates have passed: `specCorpusConformance`, `specContract`, `acceptanceContract`.
- Relevant stack gates have passed or have support-safe blockers: `clientCi`, `serverCi`, `adminCi`, `infraStatic`, docs/release evidence.
- the forbidden-domain drift scan returns no active local-domain drift.
- Live Stack E2E is run only when safe; otherwise record exact blocker and rerun command.

## Human checks

- Confirm local dogfood URLs resolve under `weave.test` only.
- Walk core member/admin paths and note accessibility regressions.
- Verify release notes avoid release-ready claims while blockers remain.
- Sign off with date, tester, build/commit, and links to automated evidence.

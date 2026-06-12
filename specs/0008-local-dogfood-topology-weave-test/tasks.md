# Tasks

- [x] Replace active local dogfood URL drift in PR #723.
- [x] Update generated client artifacts after formatter/screenshot generation.
- [x] Verify GitHub CI for PR #723 after push `25783cc`.
  - Current reality: PR #723 merged to `main` as `bdba20d7b178263cb6a3bc0f4787d346b80c95e2`; GitHub reported `Gradle CI` success; current `origin/dev` contains that merge commit.
- [x] Run final the forbidden-domain drift scan before merge.
  - Evidence: `python3 tools/forbidden_domain_drift_scan.py` returns no forbidden obsolete `.local` / `weave.local` domain drift in tracked active repo text; see `docs/evidence/spec-0008-local-dogfood-topology-closure.md`.

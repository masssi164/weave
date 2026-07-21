# Sprint 24 historical closure — superseded runtime factory

Status: closed historical milestone; its implementation and evidence were retired on 2026-07-20.

Sprint 24 issues #631–#634 described an in-process/per-user Docker runtime factory and fixture-based release gates. The accepted Agent Runtime Control architecture replaces that model without backward compatibility: disposable cells are orchestrated outside the product server, durable runtime state is encrypted and externally stored, Keycloak issues per-cell workload identity, and RuntimeProfile v2 is signed policy input rather than authorization.

The former provider-lab fixtures, Docker runtime manifest, custom validators, and Gradle gates were removed because they could produce false-positive release evidence for a design that no longer exists. This historical report remains only to explain why those closed issues must not be reopened as implementation truth.

Current sources:

- canonical Agent Runtime Control specification and ADR 0012 in `weave-specs`;
- `docs/architecture/weaver-openclaw-profile.md`;
- `docs/evidence/weaver-security-privacy-accessibility-report.md`;
- ARC backend, infrastructure lifecycle, workload identity, state-store, and live lifecycle tests.

No Sprint 24 artifact is valid evidence for production readiness, autonomous execution, sandbox strength, or current ARC conformance.

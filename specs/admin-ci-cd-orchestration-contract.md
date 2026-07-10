# Obsolete Admin CI/CD orchestration contract

Status: obsolete historical conformance artifact.

This repo-local Sprint 26 contract is no longer an active specification or implementation target. It described a generic `PipelineProvider` abstraction and a local Forgejo Actions proof seam. Both were retired by #1052 after canonical specification commit `78f6c58fef0ebb53684c5c861c2453b8c4c577c9` made GitHub the sole Weave source-control, issue, pull-request, CI, and release-delivery authority.

The binding replacement is `steering/devops-conformance.md` in the pinned `weave-specs` corpus. Active implementation evidence lives in `.github/workflows/`, the protected `dev` -> `dogfood` -> `main` promotion chain, and current release evidence gates.

Historical Sprint 26/27 closure reports may still name Forgejo to preserve the audit record. They do not register a provider, enable a runtime, satisfy a current acceptance scenario, or contribute to a release claim.

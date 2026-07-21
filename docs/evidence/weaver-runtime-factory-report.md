# Retired Weaver Runtime Factory fixture report

Status: superseded by the Agent Runtime Control v2 implementation.

The Sprint 24 fixture-only Docker factory, durable per-user volume, and
synthetic reconciliation evidence were removed. The current architecture uses
an upstream-first Weaver/OpenClaw runtime, zero durable cell-local bytes,
encrypted external state, fenced lifecycle reconciliation, one Keycloak
workload identity per cell, and signed RuntimeProfile v2 projections.

Historical issue references remain useful as provenance, but the old fixture
scoreboards and their Python claim gate are not current release evidence. Use
`docs/evidence/weaver-security-privacy-accessibility-report.md`, the backend and
MCP tests, and the local-stack lifecycle/authentication smoke results instead.

# Retired Weaver customization fixture report

Status: superseded by RuntimeProfile v2, Agent Runtime Control, and the
workload-only MCP boundary.

The Sprint 25/32 synthetic policy, `weaver.enabled`, approval-receipt, and
in-memory MCP mutation fixtures were removed. They represented a v1 design in
which an admin projection and fixture data could appear to grant domain tools.
That is incompatible with the current contract:

- Keycloak group entitlement is authoritative;
- RuntimeProfile v2 is desired state, not authorization;
- approvals remain OpenClaw/Matrix-native and produce action-bound evidence;
- MCP has no human access and currently exposes an empty catalog; and
- collaboration-domain writes require their own explicit facade contracts and
  evidence before any tool is published.

Do not cite this historical report or the removed Sprint fixtures as current
implementation or release evidence. Current evidence is maintained in
`docs/evidence/weaver-security-privacy-accessibility-report.md`.

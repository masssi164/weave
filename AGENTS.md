# Weave Monorepo Agent Rules

Weave is now developed as one product stack. Treat `client/`, `server/`, `infra/`, and `e2e/` as one release unit.

## Release discipline

- v0.1 is a dogfood-production release. Do not add preview-only product claims to v0.1 scope.
- No release surface may be described as shipped unless it has executable evidence.
- Replace “preview” and “read-only” behavior in v0.1 surfaces with real user flows, permission checks, audit trails, and fail-closed error states.
- Agent runtime integration is out of v0.1. Do not implement product agent features until the sandboxing/tool-whitelist research ADR is accepted.

## Architecture boundaries

- `client/`: Flutter app only. It must not hold provider secrets, LiveKit API secrets, backend service tokens, or raw credential-bearing URLs.
- `server/`: all provider access goes through backend-owned facades with authorization, audit, support-safe errors, and contract tests.
- `infra/`: OpenTofu-preferred infrastructure, deployment, backup/restore, operator checks, and support-bundle redaction.
- `e2e/`: product-language Gherkin, scenario mappings, and sanitized evidence artifacts.

## ATDD / Gherkin rules

- Start product behavior with a Gherkin scenario in `e2e/features/`.
- Add or update `e2e/scenario_mappings.json` in the same change.
- Do not commit unmapped scenarios or mappings that reference missing tests/evidence markers.
- Live-stack E2E is reserved for critical end-to-end contracts; detailed coverage belongs in lower-level tests.
- Evidence artifacts must be deterministic and sanitized.

## Validation gates

For nontrivial changes, run the smallest meaningful subset and report it:

- `make acceptance-contract` for Gherkin/mapping changes.
- `make client-ci` for Flutter/client changes.
- `make server-ci` for backend/provider changes.
- `make infra-static` for infrastructure/operator-script changes.
- Live-stack E2E only when explicitly authorized and runner budget is available.

## Weave co-leader operating model

- Protect release scope and product coherence over local code cleverness.
- Prefer issues/ADRs over implicit TODOs for cross-cutting decisions.
- Stop and escalate when a change risks data loss, secret leakage, history rewrites, live infra mutation, or hidden scope expansion.
- Keep accessibility, supportability, auditability, and deployability as release blockers, not polish.

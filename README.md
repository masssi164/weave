# Weave Monorepo

Weave is the accessibility-first, self-hostable collaboration workspace for chat, files, calendar, boards, meetings, decisions, and operator health.

This repository is now the single source of truth for the Weave product stack:

- `client/` — Flutter app and client-side product tests.
- `server/` — Spring Boot backend, provider facades, authorization, audit, and server-side acceptance tests.
- `infra/` — local/self-hosted stack, OpenTofu-preferred infrastructure, provider profiles, operator scripts, backup/restore, and support bundles.
- `e2e/` — product-language Gherkin scenarios, scenario mappings, and sanitized evidence contracts.
- `docs/` — product architecture, roadmap boundaries, acceptance contracts, and release documentation.
- `release/` — release manifests and stack compatibility metadata.

## v0.1 release principle

Weave v0.1 is a dogfood-production release, not a preview showcase. A surface is allowed into the release only when it is useful as a daily work tool and backed by executable evidence.

Required v0.1 product surfaces:

- Weave Home.
- Channels as workspaces with chat, files, boards, calendar, meetings, and decisions.
- Files through the Weave backend facade.
- Channel/workspace calendar events.
- Boards with user writes, permission checks, and audit trail.
- Meeting Capsules backed by the LiveKit token facade.
- Decision Ledger.
- Workspace/Admin Health cockpit.
- Deploy, backup, restore, rollback, smoke-test, and support-bundle paths.

Explicitly out of v0.1:

- Agent runtime integration in the product.
- Autonomous or team-scoped agent writes.
- Public connector SDK.
- Teams/Slack migration tooling.
- Broad SaaS administration beyond boundaries needed for safe self-hosting.

## Evidence contract

Gherkin scenarios are product contracts, not decorative documentation:

1. Write/update the product scenario in `e2e/features/`.
2. Map it in `e2e/scenario_mappings.json`.
3. Add executable unit, contract, widget, integration, server, or infra evidence.
4. Keep live-stack E2E sparse and focused on critical end-to-end contracts.
5. Store only sanitized evidence artifacts; never include secrets, tokens, cookies, private keys, raw provider errors, or personal data.

## Common local gates

```bash
make ci
make client-ci
make server-ci
make infra-static
make acceptance-contract
```

The expensive live-stack E2E remains opt-in and must only run with explicit runner power/storage budget.

## Infrastructure direction

OpenTofu is preferred for Weave infrastructure. Existing Terraform-compatible modules are migrated through compatibility-preserving wrappers first, then hardened into OpenTofu-first workflows. State-destructive operations require explicit operator confirmation and a rollback/backup path.

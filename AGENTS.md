# Weave agent rules

Weave is one monorepo. `client/`, `server/`, `infra/`, `e2e/`, `docs/`, and `release/` ship as one product.

Old `weave-backend` and `weave-infra` checkouts are stale. Ignore them.

Current truth: this repo, README/docs, GitHub issues/PRs, and executable evidence. `~/code/specs` is orientation/cross-session contract context; repo-local docs remain the implementation authority when they are more current.

Product-line truth: read `docs/product-line-and-weaver-plan.md` before product direction, admin/provider, RBAC/whitelist, or Weaver work. Preserve the order: Weave provider-neutral organization suite first; admin portal/IDM/RBAC/readiness/whitelisting second; Weaver governed per-user PA runtime later. Do not regress to agent-first planning or a fixed Nextcloud/Matrix-only product boundary.

v0.1 is dogfood-production, not preview. No scaffold, roadmap, or coming-soon UX in normal member paths.

Work spec-driven: intent → issue/spec note → acceptance/evidence → implementation → review.

Route work:
- `client/`: Flutter UX, accessibility, l10n.
- `server/`: facades, authz, audit, provider boundaries.
- `infra/`: OpenTofu, deploy, backup/restore, support bundles.
- `e2e/`: Gherkin contracts and evidence mapping.
- `docs/`: current product truth.

Default gates: `make acceptance-contract`, `make client-ci`, `make server-ci`, `make infra-static`; use `make ci` for cross-stack changes.

`weave-co-leader` coordinates specialists. PRs should request `@copilot` review when review-ready.

Stop before secrets, data loss, live infra mutation, history rewrite, or hidden scope expansion.

Accessibility, supportability, auditability, and deployability are release blockers.

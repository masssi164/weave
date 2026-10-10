# Main promotion evidence contract

Status: implementation conformance for the pinned Weave Specification Corpus
`c726993168651f1109259f9a80cc23117d24a37f`, especially
`steering/release-2026-10-product-consolidation.md` and the compatible
delivery requirements in `steering/devops-conformance.md` and
`acceptance/features/delivery-lane-validation.feature`. This packet does not
define product or release policy independently.

## Scope and ownership

The Weave repository owns the protected `dev -> dogfood -> main` workflow.
The `Main Promotion Gate` evaluates an exact candidate commit on a pull request
to `main`; ordinary feature PRs still merge into `dev`. The retained required
check name is `Verify dev → dogfood evidence before main` until branch
protection is deliberately updated with replacement evidence.

For a normal promotion, the candidate must contain the current protected
`dev` and `dogfood` heads and have the same tree as both. The exact protected
dogfood head must have a successful push-triggered `Full Compose E2E` and
`Deploy dogfood with Compose` run. A successful PR E2E from a different head
does not substitute for that deployed result. A documented emergency hotfix
continues to require current `main` ancestry and immediate forward-port. The
hotfix lane can be selected only by a same-repository `hotfix/*` PR; a fork
branch cannot select the exception by name alone.

The human result must be attached to the same-repository promotion PR whose
head is the exact candidate and whose base is `main`; a workflow dispatch
cannot point at a different issue or older PR as evidence.

For a `merge_group` event, the gate resolves the constituent PR from GitHub's
current main merge-queue entry whose head commit equals the event candidate.
Exactly one matching entry is required. Its open same-repository PR head must
be an ancestor of the tested synthetic candidate and have the same tree.
Human evidence remains bound to that PR head; the synthetic candidate must
independently satisfy the protected-lane ancestry and tree checks. Missing,
ambiguous or changed queue/PR state fails closed.

The human test result is separate from automated E2E. The only human tester
records a support-safe pass/fail/not-available result for the exact deployed
dogfood commit and the requested member surfaces. The promotion PR links that
result. No agent or CI job may manufacture a human pass from automated checks.
The protected promotion check fails when that exact-commit result is absent,
failed, or incomplete. Production publication remains a separate decision.

## Evidence and validation

- The gate reads current protected refs and GitHub Actions job conclusions at
  runtime; it never trusts branch names, stale PR text, a label or a previous
  source revision as proof of the exact candidate.
- Human evidence is an owner-authored issue comment on the promotion PR with
  one exact dogfood SHA and explicit result fields. The gate validates author,
  SHA, field completeness and pass values. The latest owner result on the
  promotion PR after deployment is authoritative, ordered by the latest of its
  creation and modification timestamps; a later edited failure or incomplete
  result cannot be hidden by an earlier pass. An older comment edited after
  deployment is eligible only with its current complete exact-commit result.
  A new or edited result triggers a fresh
  gate run on the unchanged PR head; the comment alone does not turn a stale
  check green.
- Unit fixtures cover missing/foreign/stale human comments, failed E2E or
  deployment, wrong commit, edited failures, merge-queue candidate binding and
  a valid exact-commit promotion. Static workflow
  lint and protected checks validate the workflow before integration.
- The workflow logs only commit IDs, check conclusions and support-safe human
  result fields, never member content, tokens or provider credentials.

## Distribution notices

The Server and MCP OCI images identify the current repository-default licence
for Weave-authored material as `EUPL-1.2-or-later`, consistent with the accepted
`LICENSE`, `NOTICE.md` and `THIRD_PARTY_NOTICES.md`. The images carry those
notices under `/app/legal`; the label does not replace third-party grants or
existing file-level notices, which remain in force. The Server image also
retains the incorporated Matrix vendor licence texts. This corrects stale
Apache-only image metadata without changing licence scope or upstream grants.

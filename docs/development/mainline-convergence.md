# Mainline convergence

Status: active one-time source-line convergence for issue #1299 and pull request #1413.

## Purpose

`main` still contains seven commits that are not ancestors of the current `dev` line. The current product, architecture, persistence, CI, and documentation tree lives on `dev`.

The histories are joined without restoring obsolete `main` files:

```text
current dev head -----------\
                            merge-history commit -> dev -> PR #1413 -> main
current main head ----------/
```

Textual equivalent: the convergence commit has the current `dev` head as its first parent and the current `main` head as its second parent. Its resulting file tree is the current `dev` tree plus this convergence note. This preserves both histories while resolving every historical tree conflict in favor of the reviewed current `dev` implementation.

## Invariants

- no force-push or history rewrite;
- no source file is selected from old `main` merely because it exists there;
- no Candidate Cut, Fresh Start, dogfood promotion, TestFlight, or human-signoff workflow becomes architecture authority;
- no Home-core integration or named-provider cutover is introduced;
- no backward-compatibility promise is created for unreleased historical stores, APIs, or workflows;
- protected checks run against the exact convergence heads;
- PR #1413 remains the only source-line promotion to `main`.

## Main promotion gate

The protected compatibility context is still named `Verify dev → dogfood evidence before main`, but its executable rule is already different: a normal promotion candidate must contain the current protected `dev` head and be tree-identical to it.

After #1413 merges, branch protection can rename that context to an explicit current name such as `Verify exact dev tree before main` or retire the two-branch promotion model entirely.

## Work after convergence

Mainline implementation continues in this order:

1. #1320 — finish Flyway/JPA schema, upgrade, concurrency, restart, and restore guarantees;
2. #1326 — complete canonical Files and WebDAV;
3. #1301 — complete canonical Calendar and CalDAV/iCalendar;
4. #1302 — complete canonical Chat and Matrix Client-Server;
5. #1263 and #1415 — Files/Calendar MCP and cross-facade equivalence;
6. #1014 — reusable provider-connector conformance;
7. #1412 — exact-commit clean-start, transfer, restart, backup, and restore E2E.

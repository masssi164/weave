# Sprint bootstrap and Delivery Board flow

Status: implementation/evidence workflow, 2026-05-31.

This page defines how Weave creates or audits a sprint delivery surface after the specification truth has been established in the pinned Weave Specification Corpus.

## Truth split

- Specification truth: the pinned Weave Specification Corpus referenced by `specs/weave-specs.lock.json`.
- DevOps/evidence truth: GitHub milestone, GitHub issues, Weave Delivery Board, PRs, CI, local gates, and checked-in closure reports.

A sprint does not create product truth. A sprint executes or validates conformance to product/domain truth.

## Required sprint control-plane surfaces

Every new sprint must have:

- a GitHub milestone with clear title and concise description;
- an epic/program issue;
- issue DAG with concrete implementation, evidence, docs, accessibility, security/privacy, and release-ops issues as needed;
- issue labels for priority, type, track, domain/area, and evidence expectations;
- membership in the Weave Delivery Board;
- default board status `Todo` until work starts;
- exactly one release-notes label on every implementation PR;
- a closure report or explicit blocker report before the sprint is called done.

## Bootstrap/audit script

Use the Gradle gate:

```sh
./gradlew sprintBootstrapAudit
```

or run the script directly:

```sh
python3 tools/sprint_bootstrap.py --plan tools/fixtures/sprint_bootstrap_sprint12.json --audit-only
```

The script audits:

- milestone existence/state;
- expected issues by number;
- required labels;
- expected milestone assignment;
- Weave Delivery Board membership;
- Delivery Board Status field value.

For future sprints, create a new JSON plan based on `tools/fixtures/sprint_bootstrap_sprint12.json` and run:

```sh
python3 tools/sprint_bootstrap.py --plan tools/fixtures/sprint_bootstrap_sprint13.json --apply
```

`--apply` may create missing milestone/issues, add issues to the Weave Delivery Board, and set their status. Use it only after the governing spec corpus files are identified.

## Plan schema

Minimum shape:

```json
{
  "schemaVersion": 1,
  "sprint": {
    "number": 13,
    "goal": "Short sprint goal rooted in the spec corpus."
  },
  "specCorpus": {
    "lockFile": "specs/weave-specs.lock.json",
    "requiredGate": "./gradlew specCorpusConformance"
  },
  "milestone": {
    "title": "Sprint 13 — Example",
    "description": "Concise milestone description."
  },
  "project": {
    "owner": "masssi164",
    "number": 2,
    "title": "Weave Delivery Board",
    "defaultStatus": "Todo"
  },
  "issues": [
    {
      "title": "area(domain): issue title",
      "labels": ["priority:p0", "type:story", "track:sprint-13", "evidence:required"],
      "projectStatus": "Todo",
      "body": "Issue body with spec id, scope, dependencies, acceptance, gates, and release-note expectation."
    }
  ]
}
```

After `--apply`, commit the generated plan with issue numbers filled in or update the plan manually. Sprint setup must remain reproducible from file, not from chat memory.

## Issue body requirements

Every issue should include:

- governing spec corpus IDs and paths;
- user/admin/operator value;
- allowed scope and forbidden scope;
- dependencies and blocked-by notes;
- acceptance criteria and required gates;
- release-notes expectation;
- whether it is parallel or sequential;
- support-safe evidence requirements.

## Board status rules

The pinned delivery contract projects Open → To Do → In Progress → Review → E2E Testing → HIL → Main onto actual Project #2 fields/options. Inspect those fields before changing configuration; reuse semantically equivalent options. Issue progress, branch names and candidate readiness are distinct. Advance only explicitly associated items using verified evidence. Dogfood merge alone cannot imply successful E2E or HIL readiness; human acceptance is explicit. Missing permissions or evidence fails closed, and repeat transitions are idempotent.

The legacy bootstrap fixture below uses coarse statuses for setup/audit only; it is not release-candidate or HIL authority.

- `Todo`: planned and not started.
- `In Progress`: active branch/worktree or delegated worker exists.
- `Done`: issue closed and linked PR/evidence is merged or explicitly not required.

If a PR is green but not merged, the issue is not done.

## Co-leader handoff

`weave-co-leader` may close a sprint only after:

1. `tools/sprint_bootstrap.py --audit-only` passes for the target sprint plan or the sprint-specific equivalent evidence is recorded.
2. All sprint issues are closed or explicitly moved out with rationale.
3. All linked PRs are merged or blocked with exact evidence.
4. `origin/main` contains the closure report.
5. Final CI/evidence gates are green or a product-owner blocker is recorded.

## Inspected Project #2 and evidence projection

On 2026-10-11, authenticated API inspection found the private, open Weave Delivery Board, owned and created by `masssi164`, linked to `masssi164/weave`, with one view, 442 items and 13 original fields. The authenticated account can update the Project. Existing `Status` options are `Todo`, `In Progress`, and `Done`; their IDs and all existing item values were preserved. `Todo` is semantically To Do for issue planning; `Done` remains issue completion, never inferred candidate acceptance.

Seven existing built-in workflows are enabled: Auto-add sub-issues to project (4), Auto-add to project (7), Auto-close issue (3), Item added to project (6), Item closed (1), Pull request linked to issue (5), and Pull request merged (2). GraphQL exposes their names/enabled state but not their filter/action configuration. No hidden settings or effects were assumed or modified.

A separate single-select `Delivery stage` field was created after inspecting that model. It projects explicitly associated work, independently of coarse issue Status and those built-in automations. No bulk mapping was performed.

| Logical stage | Delivery stage option ID | Evidence meaning |
| --- | --- | --- |
| Open | `391fa4c6` | Explicit planning projection on associated open work. |
| To Do | `d3adbb25` | Explicit planned work; existing issue `Todo` remains unchanged. |
| In Progress | `708a5be1` | Associated source is contained in the named candidate and its PR is open. |
| Review | `2740f980` | Associated PR is open and no longer draft. |
| E2E Testing | `6e788545` | Exact protected Dogfood automated testing started; this does not imply a pass. |
| HIL | `b63d9800` | Existing verifier accepts mandatory automated qualification, exact deployment, and same-attempt physical preparation. |
| Main | `20596cc0` | Explicit owner human acceptance and an actual merged promotion in protected Main, with matching qualified source tree/specification. |

Field ID: `PVTSSF_lAHOBbjTms4BZPgzzhlMg6w`. IDs are inspection evidence, not hardcoded automation inputs. The existing `tools/sprint_bootstrap.py` resolves fields, options, membership and exact item values anew on every invocation. It reuses `verify_main_promotion_evidence.py` for qualification, deployment, physical preparation and human evidence; no second evidence manifest or deployment authority exists.

Dry run is the default:

```sh
python3 tools/sprint_bootstrap.py --delivery-stage HIL \
  --item-url https://github.com/masssi164/weave/issues/1564 \
  --association-pr 1563 --candidate-sha <exact-protected-dogfood-sha>
```

Add `--apply` only to project that verified observation onto one existing item. Main additionally requires `--promotion-pr <merged-main-promotion-pr>`. Associations use PR closing references, or an explicit same-repository PR body reference plus the actual issue cross-reference timeline. They never add closing keywords merely to qualify an association. Source containment is verified. Missing credentials, permissions, membership, options, graph association, or mandatory evidence fails before mutation. Apply rechecks evidence at the write boundary, and repeated verified same-stage writes are idempotent.

The output records the exact candidate/tree/spec and qualification/preparation identities as an operational audit result. A board value alone never proves current readiness; reverify the exact candidate when acting on it. This command does not deploy, install, accept human results, close issues, or merge PRs. It preserves both GitHub's protected gates and the explicit human action boundary.

Regression gate: `python3 -m unittest tools.test_project_delivery_projection -v`.

Executed on 2026-10-11: all eleven projection regressions and fourteen impact regressions passed; `devCiImpactTest`, `coreDocsCheck` and strict `docsCheck` passed. A live dry run verified #1564 ↔ PR #1563 for candidate `7ce75d78dc71f6448d6f8b8acb1b6c6ef5316ea1`, tree `28a819d746a41579bf38d164ee2133d3aabeeb2d`, corpus `b02bd60620b1a0a7585756798ba5330155fc0ee2`. Only #1564's new Delivery stage was then set to In Progress; repeating the exact operation read In Progress and reported `changed=false`. Existing Status remained Todo. No Review, E2E, HIL or Main success was inferred, and no other item was mapped.

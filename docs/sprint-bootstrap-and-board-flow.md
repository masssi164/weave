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

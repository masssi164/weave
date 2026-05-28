# Weave specs

Repo-local specs are the versioned product/system contracts for Weave. They are the source for implementation, review, evidence, and generated documentation. A wiki or NotebookLM notebook may summarize these files, but must not replace them as truth.

## Workflow

1. Copy `.specify/templates/weave-spec-template.md` into `specs/NNNN-slug/spec.md`.
2. Keep status `draft` or `proposed` while product-core questions remain.
3. Add `plan.md`, `tasks.md`, `traceability.yaml`, and contract/evidence folders only when the slice is ready for implementation planning.
4. Add or update Gherkin and `e2e/scenario_mappings.json` before product implementation.
5. Run `./gradlew specContract` and `./gradlew acceptanceContract` before review.
6. Use `weave-co-leader` to brief specialists with small scopes and required gates.

## Status rule

`draft` and `proposed` specs may contain `[NEEDS CLARIFICATION: ...]`. `accepted`, `implementing`, and `implemented` specs may not.

## Directory convention

```text
specs/0001-short-slug/
├── spec.md
├── plan.md
├── tasks.md
├── traceability.yaml
├── contracts/
├── acceptance/
└── evidence/
```

# Current workflow ownership

Status: active #1470/#1480 implementation inventory. The pinned Weave Specification
Corpus defines the product and acceptance; workflow YAML and protected branch rules
define executable checks. This page records their current purpose without promoting
an older Core or provider-migration plan into release authority.

| Workflow | Current purpose |
| --- | --- |
| `ci.yml` | Required PR and protected-line code, security, generated-consumer, documentation, and JVM checks. Its aggregate fails when a required job is skipped, abandoned, or fails. |
| `live-stack-e2e.yml` | Exact-candidate isolated Compose product proof for current #1470 journeys; also drives authorized dogfood preparation after a successful protected-line run. This does not prove native Flutter UI or #1498 migration. |
| `native-persistence-closure.yml` | Focused PostgreSQL/Flyway and native Files regression for affected paths; supplemental to integrated acceptance. |
| `native-provider-gate.yml` | Focused native composition and provider-boundary regression for affected paths; supplemental to integrated acceptance. |
| `main-promotion-gate.yml` | Protected `main` PR check for exact current `dev` ancestry and tree. Its legacy displayed check name does not restore the former dogfood-promotion requirement. |
| `ios-dogfood.yml` | Physical-device human-test preparation against an eligible exact candidate. Native Flutter/Matrix sign-in and accessibility evidence remain #1475/#1480 work. |
| `dogfood-owner-bootstrap.yml` | Manually requested first-owner invitation for dogfood; it is not ordinary PR acceptance. |

Read [Gitflow PR workflow](../gitflow-pr-workflow.md) before merging, and
[Core development workflow](core-workflow.md) for the supported local gate names.
Provider adoption, cutover, permission preservation, and rollback belong to #1498.

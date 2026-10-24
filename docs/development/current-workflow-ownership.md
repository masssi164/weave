# Current workflow ownership

Status: active #1470/#1480 implementation inventory. The pinned Weave Specification
Corpus defines the product and acceptance; workflow YAML and protected branch rules
define executable checks. This page records their current purpose without promoting
an older Core or provider-migration plan into release authority.

| Workflow | Current purpose |
| --- | --- |
| `ci.yml` | Required PR and protected-line code, security, generated-consumer, documentation, and JVM checks. Selected gates must succeed; unselected gates may skip only under the validated complete impact plan. Failures, cancellations and abandoned selected gates fail the aggregate. |
| `release-notes-label.yml` | Sole owner of the required `Release Notes Label Check`. PR and label events run the existing live-label validator independently of Core CI, preserving its constant `Gradle CI` result. |
| `build-artifacts.yml` | Hosted runtime, integration-driver and iOS compilation; complete component input fingerprints permit verified reuse while preserving original producer identity. Publishes the current candidate composition, not live acceptance. |
| `live-stack-e2e.yml` | Consumes the hosted composition and runs fresh protected Dogfood Compose/two-ordinary-member iOS Simulator collaboration, then deployment and ordinary-member functional checks. macOS desktop testing is manual-only; this does not prove #1498 migration. |
| `native-persistence-closure.yml` (retired) | Former duplicate PostgreSQL/Flyway and native Files lane. Core CI owns its server/PostgreSQL tests and strengthened migration source invariants; see [workflow disposition](workflow-disposition.md#native-persistence-closureyml) for verified replacement evidence. |
| `native-provider-gate.yml` (retired) | Former duplicate native composition lane. Core CI now owns its server and Rust tests and its provider boundary invariants; see [workflow disposition](workflow-disposition.md#native-provider-gateyml) for verified replacement evidence. |
| `main-promotion-gate.yml` | Protected `main` PR check reusing qualified Dogfood source/tree/spec, automated, deployment, physical-preparation and explicit human evidence. Newer Dev work does not invalidate an unchanged candidate. |
| `ios-dogfood.yml` | Verifies qualified composition and actual successful run attempt, signs the hosted device artifact, installs in place and observes ordinary startup. Preserves owner state and leaves HIL pending; no local compilation or extra automated personal login. |
| `dogfood-owner-bootstrap.yml` | Manually requested first-owner invitation for dogfood; it is not ordinary PR acceptance. |

Read [Gitflow PR workflow](../gitflow-pr-workflow.md) before merging, and
[Core development workflow](core-workflow.md) for the supported local gate names.
Provider adoption, cutover, permission preservation, and rollback belong to #1498.

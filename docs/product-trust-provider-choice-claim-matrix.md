# Product trust and provider-choice claim matrix

Status: Sprint 14 claim-control artifact. Public/customer wording must not exceed the evidence class recorded here.

## Approved positioning

Use this framing:

- Weave is a **provider-neutral collaboration control plane** for organizations that need real provider choice, admin governance, auditability, reversibility, and consistent member UX.
- The reference self-hosted stack is the strongest proof path for data sovereignty and operational control, but Weave's product architecture is domain/provider-neutral rather than self-hosting-only.
- Weave reduces lock-in by separating member experience, admin policy, identity, credentials, audit, and provider implementation behind stable domain facades.
- Provider switching is a governed operational workflow: preflight, export/import evidence, consequence preview, cutover, rollback planning, audit, and post-cutover validation.

Avoid this wording until explicitly evidenced and reviewed:

- GDPR-proof, Cloud-Act-proof, guaranteed compliant, legally sovereign, or compliant by default.
- No vendor lock-in without scope. Prefer "reduces lock-in" or "makes switching operationally testable".
- Lossless migration unless fixture evidence proves exact behavior for a named provider, domain, version, and subset.
- Sovereign cloud unless legally and operationally defined for a concrete deployment.

## Claim matrix

| Public/customer claim | Approved wording | Evidence class | Current evidence | Release/customer-copy state |
| --- | --- | --- | --- | --- |
| Provider-neutral control plane | "Weave models collaboration domains behind governed provider facades." | Architecture evidence | Product line plan, domain context map, canonical domain registry, provider replacement contract. | Usable with architecture link. |
| Consistent member UX across providers | "Members see stable Weave capability states while providers are managed by admins/operators." | Architecture + client/accessibility evidence | Product line plan, user/admin handbooks, client capability-state tests from prior sprints. | Usable; do not imply every provider swap is implemented. |
| Data sovereignty | "Self-hosted/reference deployments make operational control over data location, keys, logs, backups, and exports testable." | Operator docs + public-source research | Control-plane infra bootstrap, admin/operator handbook, Sprint 14 risk framing. | Usable as design/operational-control claim; not a legal guarantee. |
| Auditability | "Provider setup, policy, readiness, migration, and support evidence are designed to be auditable and support-safe." | Architecture + test evidence | Admin/operator handbook, portability schemas, audit refs in portability contracts. | Usable where backed by implemented slice; mark future providers separately. |
| Reversibility | "Provider changes require export/import, rollback, retention, and validation evidence before apply." | Contract/fixture evidence | Provider portability schema v2 and no-unaccounted-data-loss docs. Matrix fixtures still pending. | Use as contract direction; do not claim automated reversibility for Matrix until fixtures land. |
| Reduced provider lock-in | "Weave reduces lock-in by making provider dependencies explicit and switch evidence reviewable." | Architecture + public-source research | Interoperability/vendor-lock-in source anchors and portability contracts. | Usable; avoid "no lock-in". |
| Matrix-first migration proof | "Sprint 14 starts with a conservative self-hosted Matrix/Synapse Chat migration proof." | Source-backed research + future fixture tests | Matrix proof doc; fixtures pending. | Coming_later for executable migration proof until tests/fixtures merge. |
| E2EE Chat migration | "Encrypted Matrix history is unsupported/coming_later until a client-side key/export strategy is solved." | Risk classification | Matrix proof doc and Matrix API semantics. | Must be stated as unsupported/coming_later. |
| GDPR/DSGVO vs Cloud Act risk | "Weave helps admins separate formal compliance posture from residual operational, jurisdictional, subprocessor, telemetry, and exportability risk." | Source-backed research + legal-review caveat | EDPB/EDPS/Schrems II sources listed below. | Usable as engineering/procurement-risk framing; not legal advice. |
| Admin provider-switch journey | "Admins review readiness, consequences, user disruption, cutover, rollback, audit, and validation before provider changes." | Product/UX contract + future Admin Console evidence | Provider replacement contract and Sprint 14 board. | Contract direction; implementation evidence still per-domain. |
| Stable Weaver `weave-chat` channel | "The governed Weaver runtime should normally receive stable `channels.weave-chat`; providerRefs stay behind Weave Chat-domain routing." | Architecture + cross-repo implementation evidence | Governed Weaver runtime security contract; #519 remains open. | Do not claim complete until #519 child evidence is merged. |
| Credential secrecy | "Runtime profiles contain SecretRefs, short-lived runtime-token references, and audit receipts only; raw provider secrets stay behind Weave." | Security/contract tests | Governed Weaver runtime security contract; #519 pending. | Contract claim only until profile fixtures/tests land. |

## Procurement-risk checklist

This checklist is an admin/operator evidence aid, not legal advice. Public legal/compliance claims need explicit legal review.

| Risk area | What Weave should surface | Claim boundary |
| --- | --- | --- |
| Data location/region | Selected provider region, self-hosted location, backup location, and evidence freshness. | Do not say region selection alone guarantees compliance or sovereignty. |
| Controller/processor roles | Which organization/provider roles are assumed for the deployment and where documentation lives. | Do not provide legal conclusions. |
| Subprocessors/onward transfers | Provider-subprocessor references, telemetry paths, support access, and unresolved unknowns. | Unknowns are blockers or manual-review items, not green states. |
| Cloud Act/extraterritorial access | Whether a provider is subject to non-EU or foreign-law access concerns and what technical controls exist. | Describe residual procurement/jurisdictional risk; never claim Cloud-Act-proof. |
| Telemetry/metadata | What metadata may leave the environment and whether it can be disabled, minimized, logged, or audited. | Do not conflate message/content encryption with metadata invisibility. |
| Key custody/cleartext access | Who can access keys/plaintext and whether E2EE, key backup, or client-side export exists. | Do not claim E2EE migration unless keys/history strategy is proven. |
| Export/reversibility | Export APIs, archive shape, lossy fields, rollback window, and post-restore validation. | Do not claim lossless or full reversibility without fixtures. |
| Deletion/retention | Provider deletion APIs, retention/legal-hold behavior, and archived evidence boundaries. | Distinguish deletion request from verified deletion across providers/backups. |

## Source anchors

- European Commission OSS strategy: <https://commission.europa.eu/about/departments-and-executive-agencies/digital-services/open-source-software-strategy_en>
- Interoperable Europe on interoperability and vendor lock-in: <https://interoperable-europe.ec.europa.eu/collection/ict-standards-procurement/interoperability-and-vendor-lock>
- EDPB supplementary measures summary: <https://www.edpb.europa.eu/news/news/2021/edpb-adopts-final-version-recommendations-supplementary-measures-letter-eu_en>
- EDPB Recommendations 01/2020: <https://edpb.europa.eu/our-work-tools/our-documents/recommendations/recommendations-012020-measures-supplement-transfer_en>
- EDPB/EDPS joint response on the US Cloud Act: <https://www.edpb.europa.eu/our-work-tools/our-documents/letters/edpb-edps-joint-response-libe-committee-impact-us-cloud-act_en>
- CJEU Schrems II C-311/18: <https://curia.europa.eu/juris/documents.jsf?num=C-311%2F18>
- Matrix Client-Server API: <https://spec.matrix.org/latest/client-server-api/>
- Slack workspace export docs: <https://slack.com/help/articles/201658943-Export-your-workspace-data>
- Slack import/export guide: <https://slack.com/help/articles/204897248-Guide-to-Slack-import-and-export-tools>
- Microsoft Teams export API: <https://learn.microsoft.com/en-us/microsoftteams/export-teams-content>
- Microsoft Graph `chatMessage`: <https://learn.microsoft.com/en-us/graph/api/resources/chatmessage>

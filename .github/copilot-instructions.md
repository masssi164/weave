# Weave Copilot review instructions

Weave is a regulated-quality monorepo. Review for evidence, traceability, and claim hygiene before style.

## Quality Reset review rules

- Product/domain claims are frozen unless the PR links repo-local specs plus mapped Gherkin acceptance contracts.
- Do not accept fixture/contract evidence as isolated E2E evidence. Require the PR to use canonical evidence terms from `docs/acceptance-contracts.md` (`offline-spec` vs `live-runtime`) and `docs/product-reality-foundation.md` (`realityLevel`).
- Weaver/Governed PA is a first-class Weave product component, but Weaver/security/product claims require specs, mapped Gherkin acceptance, evidence, and a relevant Fachveto.
- Do not allow issues/PR bodies or docs to invent product meaning outside specs. Ask for a spec update instead.
- For approval semantics, distinguish OpenClaw exec approval states from product user permissions; never treat `allow always` as blanket product permission.

## What to flag

- Missing spec link for changed product behavior.
- Missing mapped Gherkin scenario for user/admin/operator-observable behavior.
- Missing evidence gate, CI command, or support-safe artifact.
- Misleading release/customer-ready/product claims.
- Secrets, bearer tokens, raw provider payloads, raw endpoints, `openclaw.json`, or SecretRef/CredentialRef values in logs/docs/evidence.
- Accessibility, localization, privacy, audit, supportability, deployability, or provider-portability gaps.
- Missing exactly-one release-notes label plan.
- Missing Fachveto owner for product/security/privacy/a11y/provider/release changes.

## Review style

Prefer actionable blocking comments with file/line and the missing gate. Keep suggestions concise. If evidence is fixture/contract-only, require `offline-spec` / `contract_only` wording and reject isolated E2E, live-runtime, release-ready, or customer-ready claims.

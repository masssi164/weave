# Sprint 3 Admin readiness evidence

This note records the support-safe readiness evidence added for Sprint 3 issues #250 and #315.

## Contract

Workspace Health/Admin Console consumes provider-neutral domain category readiness from `/providers/status`. Each category can include `adapterEvidence` entries with only:

- `domain`
- `adapterKey`
- `configured`
- `reachable`
- `health`
- `failClosed`
- `supportSafeDiagnostics`
- `evidenceTimestamp`

`supportSafeDiagnostics` is limited to booleans, counts, and stable codes. It must not include provider endpoint URLs, bearer tokens, credential values, raw provider errors, stack traces, or room/user tokens.

## Member/admin split

- Normal member UX receives stable Weave capability/member-impact states only: ready/usable, disabled, degraded, policy-blocked, or admin setup required.
- Admin/operator Workspace Health may show adapter keys, configured/reachable/fail-closed evidence, redacted support-safe error codes, and next setup action.
- Provider endpoint rotation, SecretRefs, raw operational logs, and provider-specific remediation remain Admin/Operator-side.

## Sanitized manual/live readiness note

Current expensive live-stack proof remains operator/profile-gated. The safe evidence path is:

```bash
cd infra/weave-workspace
WEAVE_SUPPORT_BUNDLE_RUN_CHECKS=false bash support-bundle.sh .generated/support-bundles
bash tests/support-bundle-redaction-test.sh
bash tests/acceptance-feature-mapping-test.sh
```

The generated support bundle now includes `checks/adapter-readiness-summary.json` with the same support-safe field names as `/providers/status`. The redaction test proves representative OpenProject/Nextcloud provider URLs and tokens are not present in the bundle; configured/provider-domain state is represented as booleans and stable adapter keys instead.

Full enabled OpenProject provider proof should be attached only from an approved operator live-stack run. Do not substitute skipped or unconfirmed Live Stack E2E for this note.

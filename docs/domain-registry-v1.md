# Canonical domain registry v1

The machine-readable source of truth for Sprint 8 domain vocabulary is `specs/0004-domain-registry/canonical-domain-registry-v1.json`. The backend carries an identical runtime resource at `server/src/main/resources/canonical-domain-registry-v1.json`; `./gradlew domainRegistryCheck` fails if the two copies drift.

The registry defines the provider-neutral Weave domain keys, stable member states, admin/operator readiness states, provider reality levels, source-of-truth modes, compatibility aliases for older provider-category names, and the required Provider Adapter Manifest fields.

## Stable member states

Member-facing code must use only these registry states:

- `available`
- `disabled_by_policy`
- `not_configured`
- `degraded`
- `unavailable`
- `coming_later`

Members must not receive provider setup controls, raw provider diagnostics, secrets, or migration reports. Admin/operator surfaces translate these member states into setup and readiness actions.

## Stable admin states

Admin-facing code must use only these readiness states:

- `provider_not_configured`
- `secret_missing`
- `ready`
- `degraded`
- `dry_run_required`
- `lossy_mapping_pending`
- `apply_blocked`
- `migration_ready`

Unknown provider states fail closed into a support-safe admin state and a stable member impact state.

## Provider reality levels

Every provider candidate has a stable `providerRealityLevel` in the canonical registry:

- `contract_only`
- `configured_readiness`
- `live_adapter_read`
- `live_adapter_write`
- `migration_apply_ready`
- `release_ready`

Member capability states are derived from policy, readiness, admin selection, and reality level. A `contract_only` provider must never produce member state `available`; Admin Console and support bundles must instead show the remediation needed to promote the candidate without exposing URLs, tenant IDs, credentials, provider-internal IDs, or raw downstream bodies.

## Adapter declarations

A provider adapter declares supported domains by publishing a Provider Adapter Manifest with the fields listed in the registry-level `adapterManifestRequirements` array:

- `adapterKey`
- `domainKeys`
- `apiProfile`
- `canonicalObjects`
- `capabilityKeys`
- `readinessChecks`
- `unsupportedFields`
- `migrationLimits`
- `auditEvents`
- `secretBoundary`

The adapter's `domainKeys` must reference canonical domain keys, not provider names. Existing provider-category names such as `identity-idm`, `files-docs`, `documents-collaboration`, `boards-tasks`, `meetings-calls`, `decisions-evidence`, `admin-control-plane`, and `release-evidence` are compatibility aliases only. Slash-style labels used in member copy or acceptance language, for example `boards/tasks` or `meetings/calls`, are display text; they must map to canonical keys or registry aliases before they enter machine-readable contracts.

## Portability evidence

Every domain entry names the required portability evidence classes. Provider replacement and migration language must use **no unaccounted data loss**: unsupported fields, lossy mappings, conflicts, rollback limits, and retention boundaries are reported and approved before any guarded apply path is enabled.

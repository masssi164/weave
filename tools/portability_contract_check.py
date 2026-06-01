#!/usr/bin/env python3
"""Validate portability schemas for no-unaccounted-data-loss migrations."""
from __future__ import annotations
import json, re, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SCHEMA_DIR=ROOT/'server/src/main/resources/contracts/portability'
DOC=ROOT/'docs/architecture/no-unaccounted-data-loss.md'
FIXTURES=ROOT/'specs/0006-portability-contract'
LEGACY_ROOT_CONTRACTS=ROOT/'contracts'
LOSS=['portable','lossy','unsupported','manual_review','vendor_locked','archive_only']
REQUIRED={
 'provider-adapter-manifest.schema.json':'ProviderAdapterManifest',
 'provider-mapping.schema.json':'ProviderMapping',
 'export-manifest.schema.json':'ExportManifest',
 'import-manifest.schema.json':'ImportManifest',
 'import-feasibility-report.schema.json':'ImportFeasibilityReport',
 'lossy-mapping-report.schema.json':'LossyMappingReport',
 'conflict-report.schema.json':'ConflictReport',
 'permission-impact-report.schema.json':'PermissionImpactReport',
 'archive-manifest.schema.json':'ArchiveManifest',
 'rollback-retention-report.schema.json':'RollbackRetentionReport',
 'migration-run.schema.json':'MigrationRun',
 'migration-audit-ref.schema.json':'MigrationAuditRef',
 'loss-class.schema.json':'LossClass',
}
def fail(m): print(f'portability-contract-check: {m}', file=sys.stderr); raise SystemExit(1)
def load(p):
 try: return json.loads(p.read_text(encoding='utf-8'))
 except FileNotFoundError: fail(f'missing {p.relative_to(ROOT)}')
 except json.JSONDecodeError as e: fail(f'invalid JSON in {p.relative_to(ROOT)}: {e}')
def check_no_legacy_portability_contracts():
 if not LEGACY_ROOT_CONTRACTS.exists(): return
 legacy=[]
 for path in LEGACY_ROOT_CONTRACTS.rglob('*.schema.json'):
  rel=path.relative_to(ROOT)
  if 'portability' in str(rel) or path.name in REQUIRED:
   legacy.append(str(rel))
 if legacy:
  fail('legacy root portability schema(s) are not canonical; use server/src/main/resources/contracts/portability only: '+', '.join(sorted(legacy)))
def required_contains(schema, names, label):
 req=schema.get('required', [])
 for n in names:
  if n not in req: fail(f'{label} must require {n}')
def main():
 check_no_legacy_portability_contracts()
 schemas={name:load(SCHEMA_DIR/name) for name in REQUIRED}
 for name,title in REQUIRED.items():
  if schemas[name].get('title')!=title: fail(f'{name} title must be {title}')
  if schemas[name].get('$schema')!='https://json-schema.org/draft/2020-12/schema': fail(f'{name} must declare draft 2020-12')
  expected_id=f'https://weave.local/contracts/portability/{name}'
  if schemas[name].get('$id')!=expected_id: fail(f'{name} $id must be {expected_id}')
 if schemas['loss-class.schema.json'].get('enum')!=LOSS: fail('loss classes must match canonical list/order')
 required_contains(schemas['provider-adapter-manifest.schema.json'], ['adapterKey','domainKeys','apiProfile','canonicalObjects','capabilityKeys','readinessChecks','unsupportedFields','migrationLimits','auditEvents','secretBoundary'], 'ProviderAdapterManifest')
 required_contains(schemas['export-manifest.schema.json'], ['objectCounts','contentHashes','mappingRef','auditRef','redaction'], 'ExportManifest')
 required_contains(schemas['import-manifest.schema.json'], ['objectCounts','contentHashes','mappingRef','dryRunReportRef','auditRef','redaction'], 'ImportManifest')
 required_contains(schemas['migration-run.schema.json'], ['objectCounts','contentHashes','providerMappingRef','auditRefs','dryRunReportRef','applyAllowed','redaction'], 'MigrationRun')
 for name,schema in schemas.items():
  raw=json.dumps(schema)
  if 'support_safe' not in raw and name!='loss-class.schema.json': fail(f'{name} missing support_safe redaction')
  unsafe_raw = raw.replace('secretBoundary', '').replace('no_secrets', '')
  if re.search(r'(token|password|clientSecret)', unsafe_raw): fail(f'{name} must not require raw secret fields')
 doc=DOC.read_text(encoding='utf-8')
 for item in LOSS + list(REQUIRED.values()) + ['provider portability schema v2', 'server/src/main/resources/contracts/portability/', 'Provider migration apply is impossible', 'support-safe identifiers', 'no-unaccounted data loss']:
  if item not in doc: fail(f'documentation missing {item}')
 for domain in ['files','calendar','boards','chat']:
  fixture=load(FIXTURES/f'provider-portability-v2-{domain}-dry-run.json')
  if fixture.get('domainKey') != domain or fixture.get('redaction') != 'support_safe': fail(f'{domain} v2 dry-run fixture must be support_safe')
  classes=fixture.get('fieldClasses', [])
  if not classes or any(item not in LOSS for item in classes): fail(f'{domain} fixture must use v2 field classes')
 matrix=load(FIXTURES/'provider-portability-v2-chat-matrix-dry-run.json')
 if matrix.get('domainKey')!='chat' or matrix.get('sourceProvider')!='matrix-synapse-chat' or matrix.get('redaction')!='support_safe':
  fail('Matrix Chat Sprint 14 dry-run fixture must be support_safe chat evidence for matrix-synapse-chat')
 matrix_classes=set(matrix.get('fieldClasses', []))
 for required in ['portable','lossy','unsupported','manual_review','archive_only']:
  if required not in matrix_classes: fail(f'Matrix Chat fixture must classify {required} fields')
 matrix_raw=json.dumps(matrix).lower()
 for required in ['matrix_power_levels','matrix_e2ee_ciphertext_payload','server-side matrix migration cannot decrypt encrypted history','mxc_media_uri','stable weave capability states']:
  if required not in matrix_raw: fail(f'Matrix Chat fixture missing required Sprint 14 evidence phrase: {required}')
 if any(item not in LOSS for item in matrix.get('fieldClasses', [])): fail('Matrix Chat fixture must use canonical v2 field classes')
 for negative in ['provider-portability-v2-silent-drop-negative.json','provider-portability-v2-raw-provider-leak-negative.json']:
  fixture=load(FIXTURES/negative)
  if fixture.get('expectedOutcome') != 'reject': fail(f'{negative} must reject unsafe portability behavior')
 matrix=load(FIXTURES/'matrix-synapse-chat-migration-proof.json')
 if matrix.get('domainKey')!='chat' or matrix.get('sourceProvider')!='matrix-synapse' or matrix.get('redaction')!='support_safe':
  fail('Matrix Synapse Chat proof must be a support_safe chat-domain fixture')
 manifest=matrix.get('adapterManifest', {})
 if manifest.get('adapterKey')!='matrix-synapse-chat' or manifest.get('secretBoundary')!='server_only':
  fail('Matrix proof manifest must identify matrix-synapse-chat with server_only secret boundary')
 raw=json.dumps(matrix).lower()
 for forbidden in ['access_token','refresh_token','clientsecret','password','homeserverurl','mxc://','https://matrix']:
  if forbidden in raw: fail(f'Matrix proof must not leak raw provider credential or endpoint data: {forbidden}')
 mapping_classes=[item.get('fieldClass') for item in matrix.get('providerMapping', {}).get('objectMappings', [])]
 for required_class in ['portable','lossy','unsupported','manual_review','archive_only']:
  if required_class not in mapping_classes: fail(f'Matrix proof mapping missing {required_class} classification')
 blockers=' '.join(matrix.get('importFeasibilityReport', {}).get('blockers', [])).lower()
 if 'encrypted-room-history' not in blockers or 'client-side key/export' not in blockers:
  fail('Matrix proof must block encrypted-room history until client-side key/export strategy exists')
 run=matrix.get('migrationRun', {})
 if run.get('applyAllowed') is not False or run.get('state')!='blocked' or not run.get('dryRunReportRef'):
  fail('Matrix proof must include a blocked dry-run migration run before apply')
 for state in ['available','disabled_by_policy','not_configured','degraded','unavailable','coming_later']:
  if state not in matrix.get('memberCapabilityStates', []): fail(f'Matrix proof missing member capability state {state}')
 boundary=matrix.get('claimBoundary', '').lower()
 for phrase in ['does not prove lossless migration', 'legal compliance', 'e2ee history migration']:
  if phrase not in boundary: fail(f'Matrix proof claim boundary missing {phrase}')
 lifecycle_fixture=load(FIXTURES/'matrix-synapse-chat-lifecycle-fixture.json')
 if lifecycle_fixture.get('domainKey')!='chat' or lifecycle_fixture.get('sourceProvider')!='matrix-synapse' or lifecycle_fixture.get('targetProvider')!='weave-chat-domain' or lifecycle_fixture.get('redaction')!='support_safe':
  fail('Matrix lifecycle fixture must be support_safe chat-domain evidence for matrix-synapse to weave-chat-domain')
 lifecycle_raw=json.dumps(lifecycle_fixture).lower()
 for forbidden in ['access_token','refresh_token','clientsecret','password','homeserverurl','mxc://','https://matrix']:
  if forbidden in lifecycle_raw: fail(f'Matrix lifecycle fixture must not leak raw provider credential or endpoint data: {forbidden}')
 preflight=lifecycle_fixture.get('preflight', {})
 if preflight.get('state')!='preflight_failed' or preflight.get('redaction')!='support_safe':
  fail('Matrix lifecycle preflight must fail closed with support_safe redaction')
 checks={item.get('checkKey'): item for item in preflight.get('checks', [])}
 for required_check in ['matrix-api-reachable','export-permission-present','audit-sink-ready','media-retention-policy-declared','encrypted-room-detection-complete','rollback-archive-ready']:
  if required_check not in checks: fail(f'Matrix lifecycle preflight missing {required_check}')
 e2ee=checks.get('encrypted-room-detection-complete', {})
 if e2ee.get('decision')!='blocked' or e2ee.get('fieldClass')!='unsupported' or 'client-side key/export strategy' not in e2ee.get('reason',''):
  fail('Matrix lifecycle preflight must block unsupported encrypted-room history until client-side key/export strategy exists')
 if checks.get('media-retention-policy-declared', {}).get('fieldClass')!='manual_review':
  fail('Matrix lifecycle preflight must keep media retention under manual review')
 for state in preflight.get('memberImpactStates', []):
  if state not in ['available','disabled_by_policy','not_configured','degraded','unavailable','coming_later']:
   fail(f'Matrix lifecycle preflight uses non-canonical member impact state {state}')
 dry_run=lifecycle_fixture.get('dryRun', {})
 if dry_run.get('state')!='blocked' or dry_run.get('applyAllowed') is not False or not dry_run.get('dryRunReportRef') or dry_run.get('redaction')!='support_safe':
  fail('Matrix lifecycle dry-run must record blocked post-dry-run evidence before apply')
 blocked_reasons=' '.join(dry_run.get('blockedReasons', [])).lower()
 for phrase in ['encrypted-room-history', 'matrix_power_levels', 'media retention']:
  if phrase not in blocked_reasons: fail(f'Matrix lifecycle dry-run missing blocked reason {phrase}')
 apply_attempt=lifecycle_fixture.get('applyAttempt', {})
 if apply_attempt.get('state')!='blocked' or apply_attempt.get('applyAllowed') is not False or apply_attempt.get('appliedObjects')!=0:
  fail('Matrix lifecycle apply attempt must remain blocked with zero applied objects')
 rollback=lifecycle_fixture.get('rollbackPlan', {})
 if rollback.get('restoreSmokeRequired') is not True or rollback.get('sourceRetentionRequired') is not True or rollback.get('redaction')!='support_safe':
  fail('Matrix lifecycle rollback plan must require source retention, restore smoke, and support_safe redaction')
 rollback_limits=' '.join(rollback.get('limitations', [])).lower()
 for phrase in ['cannot decrypt', 'unsupported e2ee history', 'power-level parity']:
  if phrase not in rollback_limits: fail(f'Matrix lifecycle rollback plan missing limitation {phrase}')
 lifecycle_boundary=lifecycle_fixture.get('claimBoundary', '').lower()
 for phrase in ['does not prove lossless migration', 'legal compliance', 'e2ee history migration']:
  if phrase not in lifecycle_boundary: fail(f'Matrix lifecycle claim boundary missing {phrase}')

 bounded=load(FIXTURES/'matrix-synapse-chat-bounded-apply-cutover-rollback-proof.json')
 if bounded.get('domainKey')!='chat' or bounded.get('sourceProvider')!='matrix-synapse' or bounded.get('redaction')!='support_safe':
  fail('Matrix bounded apply/cutover/rollback proof must be support_safe chat-domain evidence')
 bounded_raw=json.dumps(bounded).lower()
 for forbidden in ['access_token','refresh_token','clientsecret','password','homeserverurl','mxc://','https://matrix']:
  if forbidden in bounded_raw: fail(f'Matrix bounded proof must not leak raw provider credential or endpoint data: {forbidden}')
 if bounded.get('limitedApplyAllowed') is not True or bounded.get('productionCutoverAllowed') is not False:
  fail('Matrix bounded proof must allow only limited fixture apply and block production cutover')
 lifecycle=bounded.get('lifecycle', {})
 if lifecycle.get('limitedApply', {}).get('providerMutationPerformed') is not False:
  fail('Matrix bounded proof must not perform provider mutation')
 if lifecycle.get('rollback', {}).get('state')!='rolled_back' or not lifecycle.get('rollback', {}).get('rollbackRestoreSmokeRef'):
  fail('Matrix bounded proof must include rollback restore-smoke evidence')
 no_loss=bounded.get('noUnaccountedDataLossReport', {})
 for field in ['supportedCount','lossyCount','unsupportedCount','manualReviewCount','archiveOnlyCount','vendorLockedCount']:
  if field not in no_loss: fail(f'Matrix bounded no-unaccounted-data-loss report missing {field}')
 for phrase in ['does not prove production provider migration availability', 'lossless migration', 'e2ee history migration']:
  if phrase not in ' '.join(no_loss.get('releaseClaimBoundaries', [])).lower(): fail(f'Matrix bounded proof claim boundary missing {phrase}')

 success=load(FIXTURES/'migration-run-dry-run-success.json')
 blocked=load(FIXTURES/'migration-run-apply-blocked.json')
 for fixture in [success, blocked]:
  for field in ['objectCounts','contentHashes','providerMappingRef','auditRefs','redaction']:
   if not fixture.get(field): fail(f'{fixture.get("runId")} missing {field}')
  if fixture.get('redaction')!='support_safe': fail(f'{fixture.get("runId")} must be support_safe')
 lifecycle=schemas['migration-run.schema.json'].get('properties',{}).get('state',{}).get('enum',[])
 expected_lifecycle=['discovered','preflight_failed','preflight_passed','exported','dry_run_completed','blocked','approved','applying','applied','verified','rolled_back','archived']
 if lifecycle!=expected_lifecycle: fail('MigrationRun lifecycle states must match the canonical migration engine order')
 if success.get('applyAllowed') is not True or success.get('state') not in {'approved','applying','applied','verified'} or not success.get('dryRunReportRef'):
  fail('successful fixture must require approved post-dry-run evidence before apply')
 if blocked.get('applyAllowed') is not False or blocked.get('state')!='blocked' or blocked.get('dryRunReportRef'):
  fail('blocked fixture must prove apply impossible without dry-run report')
 print('portability-contract-check: ok')
if __name__=='__main__': main()

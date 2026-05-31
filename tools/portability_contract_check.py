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
LOSS=['lossless_canonical','lossless_extension','archive_only','lossy_with_report','blocked_nonportable','provider_unexportable']
REQUIRED={
 'provider-adapter-manifest.schema.json':'ProviderAdapterManifest',
 'provider-mapping.schema.json':'ProviderMapping',
 'export-manifest.schema.json':'ExportManifest',
 'import-manifest.schema.json':'ImportManifest',
 'lossy-mapping-report.schema.json':'LossyMappingReport',
 'conflict-report.schema.json':'ConflictReport',
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
 for item in LOSS + list(REQUIRED.values()) + ['server/src/main/resources/contracts/portability/', 'Provider migration apply is impossible', 'support-safe identifiers']:
  if item not in doc: fail(f'documentation missing {item}')
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


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


ALLOWED_FIXTURE_CLASSIFICATIONS = {
 'available','disabled_by_policy','not_configured','degraded','unavailable','coming_later','unsupported'
}
SECRET_PATTERNS = [
 re.compile(r'Bearer\s+[A-Za-z0-9._~+/=-]+', re.I),
 re.compile(r'xox[baprs]-[A-Za-z0-9-]+', re.I),
 re.compile(r'access[_-]?token\s*[:=]\s*[^\s,;}]+', re.I),
 re.compile(r'secret\s*[:=]\s*[^\s,;}]+', re.I),
 re.compile(r'https?://', re.I),
 re.compile(r'mxc://', re.I),
]
FORBIDDEN_CLAIMS = [
 re.compile(r'\bGDPR[-\s]?proof\b', re.I),
 re.compile(r'\bCloud[-\s]?Act[-\s]?proof\b', re.I),
 re.compile(r'\bguaranteed\s+compliant\b', re.I),
 re.compile(r'\blossless\s+migration\b', re.I),
]
CLAIM_ALLOW_TERMS = re.compile(r'\b(avoid|avoids|non-goals?|forbid|forbids|forbidden|block|blocks|blocked|blockers?|reject|rejects|rejected|until|unless|never|not|no|without|overclaim|overclaims|must not|does not|do not|cannot|unsupported)\b', re.I)
DEFAULT_CLAIM_GLOBS = ['README.md', 'docs/**/*.md', 'infra/docs/**/*.md', 'server/docs/**/*.md']

def _walk_strings(value, prefix='$'):
 if isinstance(value, str):
  yield prefix, value
 elif isinstance(value, dict):
  for key, child in value.items(): yield from _walk_strings(child, f'{prefix}.{key}')
 elif isinstance(value, list):
  for index, child in enumerate(value): yield from _walk_strings(child, f'{prefix}[{index}]')

def _load_fixture_for_errors(path):
 try: return json.loads(Path(path).read_text(encoding='utf-8'))
 except Exception as exc: return {'__load_error__': str(exc)}

def validate_fixture(path):
 path=Path(path)
 errors=[]
 data=_load_fixture_for_errors(path)
 if '__load_error__' in data: return [f'{path}: invalid JSON: {data["__load_error__"]}']
 if not isinstance(data, dict): return [f'{path}: root must be an object']
 ctx=f"{data.get('domain','unknown')}:{data.get('provider', data.get('sourceProvider','unknown'))}:{data.get('operation','unknown')}"
 if data.get('supportSafe') is not True: errors.append(f'{path}: {ctx} must set supportSafe=true')
 if data.get('providerDiagnosticsRedacted') is not True: errors.append(f'{path}: {ctx} must set providerDiagnosticsRedacted=true')
 for string_path,text in _walk_strings(data):
  for pattern in SECRET_PATTERNS:
   if pattern.search(text): errors.append(f'{path}: {string_path} leaks provider secret/url shaped value')
  for pattern in FORBIDDEN_CLAIMS:
   if pattern.search(text): errors.append(f'{path}: {string_path} uses forbidden overclaim {pattern.pattern!r}')
 caps=data.get('capabilities')
 if not isinstance(caps, list) or not caps:
  errors.append(f'{path}: {ctx} must include non-empty capabilities[]')
  return errors
 by_key={}
 for index, cap in enumerate(caps):
  if not isinstance(cap, dict): errors.append(f'{path}: capabilities[{index}] must be an object'); continue
  key=str(cap.get('key',''))
  classification=cap.get('classification')
  by_key[key]=cap
  if classification not in ALLOWED_FIXTURE_CLASSIFICATIONS: errors.append(f'{path}: capability {key or index} has invalid classification {classification!r}')
  if classification in {'degraded','unavailable','coming_later','unsupported'} and not cap.get('evidence'): errors.append(f'{path}: capability {key} needs evidence for {classification}')
  if classification == 'available' and cap.get('losslessClaim') is not True: errors.append(f'{path}: available capability {key} must be explicitly fixture-backed with losslessClaim=true')
  if classification != 'available' and cap.get('losslessClaim'): errors.append(f'{path}: non-available capability {key} cannot assert losslessClaim=true')
 encrypted=by_key.get('encrypted_room_history')
 if not encrypted: errors.append(f'{path}: {ctx} must classify encrypted_room_history')
 elif encrypted.get('classification') != 'unsupported': errors.append(f'{path}: encrypted_room_history must be unsupported until client/user export exists')
 elif 'e2ee_server_side_decryption_unsupported' not in encrypted.get('blockers', []): errors.append(f'{path}: encrypted_room_history must carry e2ee_server_side_decryption_unsupported blocker')
 blocked=data.get('blockedOperations')
 if not isinstance(blocked, list) or 'apply' not in blocked: errors.append(f'{path}: destructive apply must be listed in blockedOperations')
 if isinstance(blocked, list) and 'cutover' not in blocked: errors.append(f'{path}: destructive cutover must be listed in blockedOperations')
 rollback=data.get('rollback')
 if not isinstance(rollback, dict) or rollback.get('classification') not in ALLOWED_FIXTURE_CLASSIFICATIONS: errors.append(f'{path}: rollback must have an explicit classification')
 elif rollback.get('classification') == 'available' and not rollback.get('evidence'): errors.append(f'{path}: available rollback must include fixture evidence')
 return errors

def claim_files(root):
 root=Path(root)
 files=set()
 for glob in DEFAULT_CLAIM_GLOBS: files.update(path for path in root.glob(glob) if path.is_file())
 return files

def validate_claim_text(root):
 root=Path(root)
 errors=[]
 for path in sorted(claim_files(root)):
  text=path.read_text(encoding='utf-8', errors='replace')
  allow_context=False
  for line_number,line in enumerate(text.splitlines(),1):
   lowered=line.lower()
   if any(marker in lowered for marker in ['avoid this wording', 'non-goals', 'forbidden wording', 'blocked overclaims']): allow_context=True
   if line.startswith('## ') and not any(marker in lowered for marker in ['non-goals', 'blocked overclaims']): allow_context=False
   if not line.strip(): continue
   for pattern in FORBIDDEN_CLAIMS:
    if pattern.search(line) and not allow_context and not CLAIM_ALLOW_TERMS.search(line): errors.append(f'{path.relative_to(root)}:{line_number}: forbidden overclaim {pattern.pattern!r}')
 return errors

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

 extra_fixture=ROOT/'tools/fixtures/portability/matrix_chat_preflight_apply_blocked.json'
 extra_errors=validate_fixture(extra_fixture)
 if extra_errors: fail('; '.join(extra_errors))
 claim_errors=validate_claim_text(ROOT)
 if claim_errors: fail('; '.join(claim_errors[:5]))
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

#!/usr/bin/env python3
"""Validate the canonical domain registry contract."""
from __future__ import annotations
import json, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
REGISTRY=ROOT/'specs/0004-domain-registry/canonical-domain-registry-v1.json'
SERVER_COPY=ROOT/'server/src/main/resources/canonical-domain-registry-v1.json'
REQUIRED_KEYS=['identity','people','spaces','chat','files','documents','calendar','boards','calls','decisions','notifications','health','weaver']
MEMBER=['available','disabled_by_policy','not_configured','degraded','unavailable','coming_later']
ADMIN=['provider_not_configured','secret_missing','ready','degraded','dry_run_required','lossy_mapping_pending','apply_blocked','migration_ready']
MANIFEST=['adapterKey','domainKeys','apiProfile','canonicalObjects','capabilityKeys','readinessChecks','unsupportedFields','migrationLimits','auditEvents','secretBoundary']
def fail(m):
 print(f'domain-registry-check: {m}', file=sys.stderr); raise SystemExit(1)
def load(p):
 try: return json.loads(p.read_text(encoding='utf-8'))
 except FileNotFoundError: fail(f'missing {p.relative_to(ROOT)}')
 except json.JSONDecodeError as e: fail(f'invalid JSON in {p.relative_to(ROOT)}: {e}')
def main():
 data=load(REGISTRY); server=load(SERVER_COPY)
 if data!=server: fail('server resource copy differs from specs/domain-registry source')
 if data.get('schemaVersion')!=1 or data.get('registryVersion')!='canonical-domain-registry-v1': fail('unexpected schema or registry version')
 if data.get('memberStates')!=MEMBER: fail('top-level member states are not canonical')
 if data.get('adminStates')!=ADMIN: fail('top-level admin states are not canonical')
 if data.get('adapterManifestRequirements')!=MANIFEST: fail('top-level adapter manifest requirements are incomplete')
 domains=data.get('domains')
 if not isinstance(domains,list): fail('domains must be a list')
 by_key={d.get('key'):d for d in domains if isinstance(d,dict)}
 if list(by_key.keys())!=REQUIRED_KEYS: fail(f'domain keys/order mismatch: {list(by_key.keys())}')
 aliases={}
 for key,d in by_key.items():
  if key.lower()!=key or '/' in key: fail(f'{key} must be a lowercase canonical key without slash-style category syntax')
  for field in ['displayName','purpose']:
   if not str(d.get(field,'')).strip(): fail(f'{key} missing {field}')
  for field in ['canonicalObjects','capabilityKeys','providerCandidates','portabilityRequirements','sourceOfTruthModes','compatibilityAliases','portabilityRisks']:
   if not isinstance(d.get(field),list) or not d[field]: fail(f'{key} {field} must be a non-empty list')
   for field in ['canonicalObjects','capabilityKeys','providerCandidates','portabilityRequirements','sourceOfTruthModes','compatibilityAliases','portabilityRisks']:
    values=d[field]
    if len(values)!=len(set(values)): fail(f'{key} {field} contains duplicate values')
  if d.get('memberStates')!=MEMBER: fail(f'{key} member states differ from canonical list')
  if d.get('adminStates')!=ADMIN: fail(f'{key} admin states differ from canonical list')
  if d.get('adapterManifestRequirements')!=MANIFEST: fail(f'{key} manifest requirements differ from canonical list')
  for obj in d['canonicalObjects']:
   if not isinstance(obj,str) or not obj[:1].isupper() or '/' in obj: fail(f'{key} canonical object {obj!r} must be a PascalCase Weave object name')
  for capability in d['capabilityKeys']:
   if not isinstance(capability,str) or capability.lower()!=capability or '/' in capability: fail(f'{key} capability {capability!r} must be a lowercase provider-neutral key without slash-style category syntax')
  for alias in d['compatibilityAliases']:
   if '/' in alias: fail(f'{key} alias {alias!r} must use canonical hyphen/camel compatibility syntax, not slash-style display text')
   if alias in by_key: fail(f'{key} alias duplicates canonical key {alias}')
   prior=aliases.setdefault(alias,key)
   if prior!=key: fail(f'alias {alias} points to both {prior} and {key}')
 for required_alias in ['identity-idm','files-docs','documents-collaboration','boards-tasks','meetings-calls','decisions-evidence','admin-control-plane','release-evidence']:
  if required_alias not in aliases: fail(f'missing compatibility alias {required_alias}')
 print('domain-registry-check: ok')
if __name__=='__main__': main()

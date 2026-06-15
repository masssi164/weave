#!/usr/bin/env python3
"""Validate the domain registry implementation conformance fixture."""
from __future__ import annotations
import json, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
REGISTRY=ROOT/'specs/0004-domain-registry/canonical-domain-registry-v1.json'
SERVER_COPY=ROOT/'server/src/main/resources/canonical-domain-registry-v1.json'
REQUIRED_KEYS=['identity','people','spaces','chat','files','documents','calendar','boards','calls','decisions','notifications','health','weaver']
MEMBER=['available','disabled_by_policy','not_configured','degraded','unavailable','coming_later']
ADMIN=['provider_not_configured','secret_missing','ready','degraded','dry_run_required','lossy_mapping_pending','apply_blocked','migration_ready']
MANIFEST=['adapterKey','domainKeys','apiProfile','canonicalObjects','capabilityKeys','readinessChecks','unsupportedFields','migrationLimits','auditEvents','secretBoundary','adapterMapperKey','activeBindingStatus','provenanceReport','lossReport','permissionImpactReport','conflictReport','portabilityManifest','auditRef']
REALITY=['contract_only','configured','live_read','live_write','migration_dry_run','migration_apply_ready','rollback_ready','release_ready']
BINDING_STATUSES=['active','candidate','discovery_read_only','migration_source','migration_target','coexistence_preflight','deprecated','superseded']
CONTRACT_REQUIRED=['domainId','userObjects','organizationObjects','readCapabilities','writeCapabilities','minimumOpenProtocols','authIdentityAssumptions','auditRequirements','portabilityExportContract','jurisdictionVendorExposureDescriptor','weaverToolMode','primaryAdapterCandidates','secondaryAdapterCandidates']
WEAVER_MODES={'none','read-only','approval-write','governed-write'}
PLACEHOLDER_IDS={'ai-runtime-gateway','mcp-tool-registry','search','notes','secrets','mail','backup-export'}
COMMERCIAL_OR_LICENSE_CAVEAT_TOKENS=('commercial','license caveat','jurisdiction caveat','candidate blocked','vendor exposure')
REQUIRED_CANONICAL={
 'chat':{'WeaveSpace','WeaveConversation','WeaveMessage','WeaveThread','WeaveReaction','WeaveAttachment','WeaveMembership','WeaveHistoryPolicy','ProviderRef','MigrationReceipt','RollbackReceipt','LossyFieldReport'},
 'files':{'WeaveDrive','WeaveFolder','WeaveFile','WeaveVersion','WeaveShare','WeavePermission','WeaveLock','WeaveQuota','ProviderRef'},
 'calendar':{'WeaveCalendar','WeaveEvent','WeaveRecurrence','WeaveAttendee','WeaveResource','WeaveAvailability','ProviderRef'},
 'weaver':{'WeaverRuntimeProfile','WeaverRuntimeInstance','WeaverUserWorkspace','WeaverToolGrant','WeaverApprovalReceipt','WeaverAuditEvent','WeaverCustomizationProfile'},
}
OLD_REALITY={'configured_readiness','live_adapter_read','live_adapter_write'}
def fail(m):
 print(f'domain-registry-check: {m}', file=sys.stderr); raise SystemExit(1)
def load(p):
 try: return json.loads(p.read_text(encoding='utf-8'))
 except FileNotFoundError: fail(f'missing {p.relative_to(ROOT)}')
 except json.JSONDecodeError as e: fail(f'invalid JSON in {p.relative_to(ROOT)}: {e}')
def main():
 data=load(REGISTRY); server=load(SERVER_COPY)
 if data!=server: fail('server resource copy differs from repo domain-registry conformance fixture')
 if data.get('schemaVersion')!=1 or data.get('registryVersion')!='canonical-domain-registry-v1': fail('unexpected schema or registry version')
 if data.get('memberStates')!=MEMBER: fail('top-level member states are not canonical')
 if data.get('adminStates')!=ADMIN: fail('top-level admin states are not canonical')
 if data.get('adapterManifestRequirements')!=MANIFEST: fail('top-level adapter manifest requirements are incomplete')
 if data.get('providerRealityLevels')!=REALITY: fail('top-level provider reality levels are not canonical')
 if data.get('bindingStatuses')!=BINDING_STATUSES: fail('top-level binding statuses are not canonical')
 active_policy=data.get('activeBindingPolicy') or {}
 if active_policy.get('perDomainActiveBinding')!='exactly_one' or active_policy.get('memberProviderLeakage')!='forbidden': fail('active binding policy must require exactly one active domain binding and forbid member provider leakage')
 if data.get('domainContractRequiredFields')!=CONTRACT_REQUIRED: fail('top-level domain contract required fields are incomplete')
 placeholders=data.get('futureDomainPlaceholders')
 if not isinstance(placeholders,list): fail('futureDomainPlaceholders must be a list')
 placeholder_ids={p.get('domainId') for p in placeholders if isinstance(p,dict)}
 if not PLACEHOLDER_IDS.issubset(placeholder_ids): fail(f'missing Wave 2/later placeholder(s): {sorted(PLACEHOLDER_IDS-placeholder_ids)}')
 for p in placeholders:
  if p.get('domainId')=='search' and p.get('sourceOfTruthMode')!='derived_rebuildable': fail('search placeholder must be marked derived/rebuildable, not canonical source of truth')
  if p.get('status')!='placeholder': fail(f"{p.get('domainId')} placeholder must not imply shipped capability")
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
  for field in CONTRACT_REQUIRED:
   value=d.get(field)
   if isinstance(value,list):
    if not value: fail(f'{key} {field} must be a non-empty list')
   elif not str(value or '').strip(): fail(f'{key} missing {field}')
  if d.get('wave')!='wave1': fail(f'{key} must be marked wave1')
  if d.get('weaverToolMode') not in WEAVER_MODES: fail(f'{key} has invalid Weaver tool mode')
  primary=d.get('primaryAdapterCandidates',[]); secondary=d.get('secondaryAdapterCandidates',[])
  if len(primary)+len(secondary)<2: fail(f'{key} needs at least two adapter paths where realistic')
  caveated=[c for c in primary+secondary if any(token in c.lower() for token in COMMERCIAL_OR_LICENSE_CAVEAT_TOKENS)]
  if caveated and not any(c in secondary for c in caveated): fail(f'{key} commercial/license/jurisdiction-caveated candidates must be secondary, not primary')
  if secondary and any('license caveat' in c.lower() or 'commercial' in c.lower() or 'jurisdiction caveat' in c.lower() for c in primary): fail(f'{key} primary adapter candidates must prefer free/open/self-hostable paths before caveated candidates')
  for candidate in secondary:
   lowered=candidate.lower()
   if any(marker in lowered for marker in ('onlyoffice','slack','teams','microsoft','entra','auth0')) and not any(token in lowered for token in COMMERCIAL_OR_LICENSE_CAVEAT_TOKENS): fail(f'{key} secondary adapter candidate {candidate!r} must record commercial/license/jurisdiction caveat')
  if key=='health' and 'derived' not in d.get('sourceOfTruthNote',''): fail('health/control-room views must be marked derived')
  if d.get('memberStates')!=MEMBER: fail(f'{key} member states differ from canonical list')
  if d.get('adminStates')!=ADMIN: fail(f'{key} admin states differ from canonical list')
  if d.get('adapterManifestRequirements')!=MANIFEST: fail(f'{key} manifest requirements differ from canonical list')
  if d.get('activeBindingPolicy')!='exactly_one_active_adapter_binding_per_domain': fail(f'{key} must require exactly one active adapter binding')
  if d.get('bindingStatusVocabulary')!=BINDING_STATUSES: fail(f'{key} binding statuses differ from canonical list')
  mapper=d.get('adapterMapperRequirements',[])
  for required in ['map_provider_objects_to_canonical_weave_objects','map_capabilities_permissions_events_errors','emit_provenance_report','emit_loss_report','emit_permission_impact_report','emit_conflict_report','emit_portability_manifest','link_support_safe_audit_refs']:
   if required not in mapper: fail(f'{key} AdapterMapper requirements missing {required}')
  if d.get('setupScenarios')!=['deploy_new','attach_existing','hybrid']: fail(f'{key} must support deploy_new, attach_existing, and hybrid setup scenarios')
  for obj in d['canonicalObjects']:
   if not isinstance(obj,str) or not obj[:1].isupper() or '/' in obj: fail(f'{key} canonical object {obj!r} must be a PascalCase Weave object name')
  for capability in d['capabilityKeys']:
   if not isinstance(capability,str) or capability.lower()!=capability or '/' in capability: fail(f'{key} capability {capability!r} must be a lowercase provider-neutral key without slash-style category syntax')
  reality=d.get('providerRealityLevelByCandidate')
  if not isinstance(reality,dict): fail(f'{key} providerRealityLevelByCandidate must be an object')
  if set(reality.keys())!=set(d['providerCandidates']): fail(f'{key} provider reality candidates must match providerCandidates')
  missing_required=REQUIRED_CANONICAL.get(key,set())-set(d['canonicalObjects'])
  if missing_required: fail(f'{key} canonicalObjects missing Sprint 21 required object(s): '+', '.join(sorted(missing_required)))
  for candidate,level in reality.items():
   if level in OLD_REALITY: fail(f'{key} provider candidate {candidate!r} uses rejected old reality level {level!r}')
   if level not in REALITY: fail(f'{key} provider candidate {candidate!r} has invalid reality level {level!r}')
  for alias in d['compatibilityAliases']:
   if '/' in alias: fail(f'{key} alias {alias!r} must use canonical hyphen/camel compatibility syntax, not slash-style display text')
   if alias in by_key: fail(f'{key} alias duplicates canonical key {alias}')
   prior=aliases.setdefault(alias,key)
   if prior!=key: fail(f'alias {alias} points to both {prior} and {key}')
 for required_alias in ['identity-idm','files-docs','documents-collaboration','boards-tasks','meetings-calls','decisions-evidence','admin-control-plane','release-evidence']:
  if required_alias not in aliases: fail(f'missing compatibility alias {required_alias}')
 print('domain-registry-check: ok')
if __name__=='__main__': main()

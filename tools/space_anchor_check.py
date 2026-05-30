#!/usr/bin/env python3
"""Validate the Space anchor contract fixture."""
from __future__ import annotations
import json, re, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
FIXTURE=ROOT/'specs/0005-spaces-anchor/space-anchor-fixture.json'
DOC=ROOT/'docs/space-anchor-contract.md'
REQUIRED_BINDINGS={'chat','files','boards','calendar'}
READINESS={'provider_not_configured','secret_missing','ready','degraded','dry_run_required','lossy_mapping_pending','apply_blocked','migration_ready'}
SOURCE={'weave_owned','provider_owned','shared_with_precedence','external_reference'}
def fail(m): print(f'space-anchor-check: {m}', file=sys.stderr); raise SystemExit(1)
def main():
 data=json.loads(FIXTURE.read_text(encoding='utf-8'))
 if data.get('schemaVersion')!=1: fail('schemaVersion must be 1')
 space=data.get('space')
 if not isinstance(space,dict): fail('space must be an object')
 for key in ['spaceId','key','name','type','defaultSurface','membership','contextPolicy','bindings']:
  if key not in space: fail(f'space missing {key}')
 if not isinstance(space['membership'],list) or not space['membership']: fail('space membership must be non-empty')
 for member in space['membership']:
  if not {'personId','spaceRole','capabilityProfile'} <= set(member): fail('membership entries must be provider-neutral')
 policy=space['contextPolicy']
 if policy.get('memberProviderDetailsVisible') is not False: fail('members must not see provider details')
 if policy.get('weaverMayReferenceSpace') is not True: fail('Weaver context reference flag must be explicit')
 if policy.get('weaverDirectProviderAccess') is not False: fail('Weaver must not get direct provider access')
 bindings=space['bindings']
 if set(bindings)!=REQUIRED_BINDINGS: fail(f'bindings must be exactly {sorted(REQUIRED_BINDINGS)}')
 for name,binding in bindings.items():
  if binding.get('domain')!=name: fail(f'{name} binding domain mismatch')
  if binding.get('readiness') not in READINESS: fail(f'{name} readiness is not canonical admin state')
  if binding.get('sourceOfTruth') not in SOURCE: fail(f'{name} sourceOfTruth is not canonical')
  ref=str(binding.get('providerObjectRef',''))
  if not ref.startswith('redacted-provider-ref:'): fail(f'{name} providerObjectRef must be redacted')
  if re.search(r'(![A-Za-z0-9]+:|https?://|secret|token|password)', ref, re.I): fail(f'{name} providerObjectRef leaks provider/secret detail')
  if not str(binding.get('memberRef','')).strip(): fail(f'{name} memberRef missing')
  if not isinstance(binding.get('lossyNotes'),list): fail(f'{name} lossyNotes must be a list')
  if not str(binding.get('migrationStatus','')).strip(): fail(f'{name} migrationStatus missing')
 doc=DOC.read_text(encoding='utf-8')
 for fragment in ['cross-domain organization context anchor','DomainBinding','SpaceMembership','no unaccounted data loss','Weaver']:
  if fragment not in doc: fail(f'doc missing {fragment!r}')
 print('space-anchor-check: ok')
if __name__=='__main__': main()

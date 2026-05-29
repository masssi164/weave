import { describe, expect, it, vi } from 'vitest';
import { AdminControlPlaneApi, sampleControlPlane } from './api';

// V01_ADMIN_CONSOLE_MVP: Admin Console may call only Weave backend admin APIs, not optional provider APIs.
describe('AdminControlPlaneApi provider boundary', () => {
  it('uses backend admin endpoints for provider selection, readiness, and replacement dry-runs', async () => {
    const calls: string[] = [];
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      calls.push(String(input));
      const path = String(input);
      const body = path.includes('/identity/readiness')
        ? {
            contractVersion: 'identity-provider-readiness-v1',
            category: 'identity-idm',
            providerKey: 'keycloak-realm',
            overallState: 'admin-action-required',
            supportSafe: true,
            providerDiagnosticsRedacted: true,
            backendOwnedFacade: true,
            memberClientMayConfigureIdentityProvider: false,
            optionalForMemberFlows: true,
            stableStates: [
              'ready',
              'degraded',
              'policy-blocked',
              'admin-action-required',
              'disabled',
            ],
            cards: [
              {
                key: 'realm-import',
                label: 'Realm import readiness',
                state: 'admin-action-required',
                summary: 'Select an identity provider mapping.',
                memberImpact: 'degraded',
                remediation: 'Run backend realm dry-run.',
                nextActions: ['Run dry-run'],
                evidenceRefs: ['identity-realm-dry-run'],
              },
            ],
            nextActions: ['Resolve admin-action-required cards'],
          }
        : path.includes('/replacements/dry-run')
          ? {
            status: 'dry_run_ready',
            category: 'chat',
            currentAdapter: 'synapse-homeserver',
            targetAdapter: 'slack',
            readinessState: 'degraded',
            memberImpactStates: [
              'usable',
              'disabled',
              'degraded',
              'policy-blocked',
            ],
            supportSafe: true,
            providerDiagnosticsRedacted: true,
            lossyMappingReport: {
              canonicalObjects: ['Conversation'],
              contractRisks: [],
              adminNotes: [],
              conflicts: [],
            },
            lifecycleExpectations: {
              exportExpectation: 'export first',
              deleteExpectation: 'delete after cutover',
            },
          }
        : path.includes('/readiness-tests')
          ? { providerKey: 'slack', state: 'ready', readiness: 'ready' }
          : {};
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });
    const api = new AdminControlPlaneApi(
      {
        apiBaseUrl: 'https://api.example.invalid/api',
        oidcIssuerUrl: 'https://auth.example.invalid',
        oidcClientId: 'weave-admin-console',
      },
      fetchImpl as typeof fetch,
    );
    const category = {
      ...sampleControlPlane.providerCategories[1],
      selectedAdapter: 'synapse-homeserver',
    };

    await api.selectProvider('chat', 'slack');
    const identityReadiness = await api.getIdentityProviderReadiness();
    await api.testProviderReadiness('slack');
    const report = await api.dryRunProviderReplacement(category, 'slack');

    expect(identityReadiness.overallState).toBe('admin-action-required');
    expect(identityReadiness.memberClientMayConfigureIdentityProvider).toBe(false);
    expect(identityReadiness.cards[0]?.memberImpact).toBe('degraded');
    expect(report.supportSafe).toBe(true);
    expect(report.memberImpactStates).toEqual([
      'available',
      'disabled_by_policy',
      'degraded',
    ]);
    expect(calls).toEqual([
      'https://api.example.invalid/api/admin/providers/selections',
      'https://api.example.invalid/api/admin/identity/readiness',
      'https://api.example.invalid/api/admin/providers/readiness-tests',
      'https://api.example.invalid/api/admin/providers/replacements/dry-run',
    ]);
    expect(calls.join('\n')).not.toMatch(
      /slack\.com|graph\.microsoft\.com|nextcloud|matrix|livekit/i,
    );
  });

  it('fails closed when older backends omit the optional identity readiness contract', async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      const body = path.includes('/audit/events')
        ? []
        : {
            organizationId: 'weave-dogfood',
            categories: [],
            whitelist: { denyByDefault: true },
          };
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });
    const api = new AdminControlPlaneApi(
      {
        apiBaseUrl: 'https://api.example.invalid/api',
        oidcIssuerUrl: 'https://auth.example.invalid',
        oidcClientId: 'weave-admin-console',
      },
      fetchImpl as typeof fetch,
    );

    const controlPlane = await api.getControlPlane();

    expect(controlPlane.identityProviderReadiness.overallState).toBe(
      'admin-action-required',
    );
    expect(controlPlane.identityProviderReadiness.cards[0]).toEqual(
      expect.objectContaining({
        key: 'identity-readiness-contract-missing',
        state: 'admin-action-required',
        memberImpact: 'degraded',
      }),
    );
    expect(controlPlane.identityProviderReadiness.nextActions).toContain(
      'Treat missing identity readiness as admin-action-required and fail closed.',
    );
  });
});

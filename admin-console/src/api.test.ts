import { describe, expect, it, vi } from 'vitest';
import { AdminControlPlaneApi, sampleControlPlane } from './api';

// V01_ADMIN_CONSOLE_MVP: Admin Console may call only Weave backend admin APIs, not optional provider APIs.
describe('AdminControlPlaneApi provider boundary', () => {
  it('uses backend admin endpoints for provider selection, readiness, and replacement dry-runs', async () => {
    const calls: string[] = [];
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      calls.push(String(input));
      const path = String(input);
      const body = path.includes('/replacements/dry-run')
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
    await api.testProviderReadiness('slack');
    const report = await api.dryRunProviderReplacement(category, 'slack');

    expect(report.supportSafe).toBe(true);
    expect(report.memberImpactStates).toEqual([
      'usable',
      'disabled',
      'degraded',
      'policy-blocked',
    ]);
    expect(calls).toEqual([
      'https://api.example.invalid/api/admin/providers/selections',
      'https://api.example.invalid/api/admin/providers/readiness-tests',
      'https://api.example.invalid/api/admin/providers/replacements/dry-run',
    ]);
    expect(calls.join('\n')).not.toMatch(
      /slack\.com|graph\.microsoft\.com|nextcloud|matrix|livekit/i,
    );
  });
});

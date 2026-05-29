import '@testing-library/jest-dom/vitest';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { AdminControlPlaneApi, sampleControlPlane } from './api';
import { adminConsoleMessages } from './copy';

function mockApi(
  overrides: Partial<AdminControlPlaneApi> = {},
): AdminControlPlaneApi {
  return {
    getControlPlane: vi.fn().mockResolvedValue(sampleControlPlane),
    updateWhitelistPolicy: vi.fn().mockResolvedValue({
      ...sampleControlPlane.whitelistPolicy,
      allowedCapabilities: ['files.read'],
    }),
    selectProvider: vi.fn().mockResolvedValue(undefined),
    dryRunProviderReplacement: vi.fn().mockResolvedValue({
      dryRunId: 'chat-slack-dry-run',
      status: 'dry_run_ready',
      category: 'identity-idm',
      currentAdapter: 'keycloak-realm',
      targetAdapter: 'keycloak-realm',
      readinessState: 'ready',
      migrationDryRunRequired: true,
      memberImpactStates: ['available', 'degraded', 'disabled_by_policy'],
      supportSafe: true,
      providerDiagnosticsRedacted: true,
      cutoverGates: ['Run backend migration dry-run before apply'],
      lossyMappingReport: {
        canonicalObjects: ['IdentitySubject', 'GroupMembership'],
        contractRisks: ['External claims need mapping review'],
        adminNotes: ['Support-safe dry-run'],
        conflicts: [],
        replacementRequirement: 'Review before apply',
      },
      lifecycleExpectations: {
        sourceOfTruthPolicy: 'Weave effective policy remains source of truth',
        exportExpectation: 'Export evidence is required before cutover.',
        deleteExpectation:
          'Delete/deprovision evidence is required after cutover.',
        deprovisionExpectation: 'Deactivate old adapter after verification.',
        rollbackSupportBoundary: 'Rollback bounded by provider export support.',
      },
    }),
    testProviderReadiness: vi.fn().mockResolvedValue({
      providerKey: 'keycloak-realm',
      state: 'ready',
      summary: 'Ready',
    }),
    listAuditEvents: vi.fn().mockResolvedValue(sampleControlPlane.auditEvents),
    ...overrides,
  } as unknown as AdminControlPlaneApi;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// V01_ADMIN_CONSOLE_MVP: admin console manages org/provider policy through backend APIs only.
describe('Admin Console MVP', () => {
  it('renders organization, provider, policy, and audit sections from backend control-plane data', async () => {
    render(<App api={mockApi()} />);

    expect(
      await screen.findByRole('heading', {
        name: /weave organization admin console/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /organization overview/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /provider categories/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /guided setup assistant/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /readiness dashboard/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /identity provider readiness/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: /provider selection and readiness/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /effective policy explanation/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /member capability preview/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: /provider replacement dry-run results/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /policy and whitelist/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /secretref inventory/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /audit trail/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/identity \/ idm status is ready/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/provider source of truth/i)).toBeInTheDocument();
    expect(screen.getByText(/backend-owned facade/i)).toBeInTheDocument();
    expect(screen.getByText(/Stable states:/i)).toHaveTextContent(
      /admin action required/i,
    );
    expect(
      screen.getAllByText(/policy is deny-by-default/i).length,
    ).toBeGreaterThan(0);
  });

  it('renders identity readiness as backend-owned support-safe Workspace Health cards', async () => {
    render(<App api={mockApi()} />);

    expect(
      await screen.findByRole('heading', { name: /identity provider readiness/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/realm import readiness state is ready/i),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/oidc client readiness state is ready/i),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/roles and groups mapping state is ready/i),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/login readiness state is ready/i),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/policy readiness state is ready/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/member provider setup:/i),
    ).toHaveTextContent(/blocked/i);
    expect(document.body).not.toHaveTextContent(/client_secret|access_token/i);
  });

  it('specifies guided setup and per-domain readiness before member go-live', async () => {
    render(<App api={mockApi()} />);

    expect(
      await screen.findByRole('heading', { name: /guided setup assistant/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/admin setup assistant steps/i),
    ).toHaveTextContent(/identity \/ idm: ready for member go-live/i);
    expect(
      screen.getByLabelText(/admin setup assistant steps/i),
    ).toHaveTextContent(/meetings \/ calls: repair before inviting affected members/i);
    expect(
      screen.getByText(/requires dry-run\/preflight, member impact preview/i),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/domain readiness dashboard/i),
    ).toHaveTextContent(/next action: run a readiness test/i);
    expect(
      screen.getByLabelText(/domain readiness dashboard/i),
    ).toHaveTextContent(/member preview: degraded/i);
  });

  it('keeps admin/provider setup separate from the member client and direct providers', async () => {
    render(<App api={mockApi()} />);

    expect(
      await screen.findByText(/calls only Weave backend admin APIs/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /it does not call Keycloak, Nextcloud, Matrix, Microsoft Graph, Slack, Teams/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Secrets stay as SecretRef handles/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/use the provider-agnostic weave client/i),
    ).toBeInTheDocument();
  });

  it('saves whitelist policy through the backend API and announces the result', async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    const policyField = await screen.findByRole('textbox', {
      name: /allowed capabilities/i,
    });
    await user.clear(policyField);
    await user.type(policyField, 'files.read');
    await user.click(
      screen.getByRole('button', { name: /save whitelist policy/i }),
    );

    await waitFor(() =>
      expect(api.updateWhitelistPolicy).toHaveBeenCalledWith(['files.read']),
    );
    expect(await screen.findByRole('status')).toHaveTextContent(
      /whitelist policy saved/i,
    );
  });

  it('applies selected providers as Admin Console source of truth through the backend API', async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.click(
      await screen.findByRole('button', { name: /apply selected provider/i }),
    );

    await waitFor(() =>
      expect(api.selectProvider).toHaveBeenCalledWith(
        'identity-idm',
        'keycloak-realm',
        'recommended_self_hosted_default',
        false,
      ),
    );
    expect(await screen.findByRole('status')).toHaveTextContent(
      /provider selection applied/i,
    );
  });

  it('dry-runs selected providers through the backend API before applying', async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.click(
      await screen.findByRole('button', {
        name: /dry-run provider selection/i,
      }),
    );

    await waitFor(() =>
      expect(api.selectProvider).toHaveBeenCalledWith(
        'identity-idm',
        'keycloak-realm',
        'recommended_self_hosted_default',
        true,
      ),
    );
    expect(await screen.findByRole('status')).toHaveTextContent(
      /dry-run validated/i,
    );
  });

  it('renders support-safe provider replacement dry-run evidence', async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.click(
      await screen.findByRole('button', {
        name: /dry-run replacement contract/i,
      }),
    );

    await waitFor(() =>
      expect(api.dryRunProviderReplacement).toHaveBeenCalledWith(
        expect.objectContaining({ key: 'identity-idm' }),
        'keycloak-realm',
        'recommended_self_hosted_default',
      ),
    );
    expect(await screen.findByRole('status')).toHaveTextContent(
      /replacement dry-run completed/i,
    );
    expect(screen.getByText(/support-safe: yes/i)).toBeInTheDocument();
    expect(screen.getByText(/diagnostics redacted: yes/i)).toBeInTheDocument();
    expect(
      screen.getByText(
        /member impact states: available, degraded, disabled_by_policy/i,
      ),
    ).toBeInTheDocument();
  });

  it('shows distinct owner, operator, and member boundaries without provider-admin controls in member preview', async () => {
    const { unmount } = render(<App api={mockApi()} />);

    expect(
      await screen.findByText(
        /configure provider categories, replacement dry-runs/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /inspect readiness, audit evidence, and support-safe diagnostics/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /use weave product capabilities with only available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later states/i,
      ),
    ).toBeInTheDocument();
    unmount();

    render(<App api={mockApi()} viewerRole="member" />);
    const memberPreview = await screen.findByLabelText(
      /member-visible capability states/i,
    );
    expect(memberPreview).toHaveTextContent(/Member state: available/i);
    expect(memberPreview).not.toHaveTextContent(/secretref:\/\//i);
    expect(document.body).not.toHaveTextContent(/secretref:\/\//i);
    expect(document.body).not.toHaveTextContent(
      /keycloak|nextcloud|matrix|microsoft graph|slack|teams|livekit/i,
    );
    expect(
      screen.queryByRole('heading', {
        name: /identity provider readiness/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('heading', {
        name: /provider selection and readiness/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /apply selected provider/i }),
    ).not.toBeInTheDocument();
  });

  it('keeps new Admin Console copy in the localization catalog', () => {
    expect(adminConsoleMessages.en.effectivePolicyHeading).toBe(
      'Effective policy explanation',
    );
    expect(adminConsoleMessages.en.replacementButton).toBe(
      'Dry-run replacement contract',
    );
    expect(adminConsoleMessages.en.memberPreviewDescription).toContain(
      'hides provider adapters',
    );
    expect(adminConsoleMessages.en.memberStateDescription).toContain(
      'stable capability state',
    );
  });

  it('queues provider readiness tests through the backend API', async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.click(
      await screen.findByRole('button', {
        name: /test readiness through backend/i,
      }),
    );

    await waitFor(() =>
      expect(api.testProviderReadiness).toHaveBeenCalledWith('keycloak-realm'),
    );
    expect(await screen.findByRole('status')).toHaveTextContent(
      /readiness test queued/i,
    );
  });
});

import '@testing-library/jest-dom/vitest';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { AdminControlPlaneApi, sampleControlPlane } from './api';

function mockApi(overrides: Partial<AdminControlPlaneApi> = {}): AdminControlPlaneApi {
  return {
    getControlPlane: vi.fn().mockResolvedValue(sampleControlPlane),
    updateWhitelistPolicy: vi.fn().mockResolvedValue({
      ...sampleControlPlane.whitelistPolicy,
      allowedCapabilities: ['files.read'],
    }),
    selectProvider: vi.fn().mockResolvedValue(undefined),
    testProviderReadiness: vi.fn().mockResolvedValue({ providerKey: 'keycloak-realm', state: 'ready', summary: 'Ready' }),
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

    expect(await screen.findByRole('heading', { name: /weave organization admin console/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /organization overview/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /provider categories/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /provider selection and readiness/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /policy and whitelist/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /secretref inventory/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /audit trail/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/identity \/ idm status is ready/i)).toBeInTheDocument();
    expect(screen.getByText(/provider source of truth/i)).toBeInTheDocument();
    expect(screen.getByText(/policy is deny-by-default/i)).toBeInTheDocument();
  });

  it('keeps admin/provider setup separate from the member client and direct providers', async () => {
    render(<App api={mockApi()} />);

    expect(await screen.findByText(/calls only Weave backend admin APIs/i)).toBeInTheDocument();
    expect(screen.getByText(/it does not call Keycloak, Nextcloud, Matrix, Microsoft Graph, Slack, Teams/i)).toBeInTheDocument();
    expect(screen.getByText(/Secrets stay as SecretRef handles/i)).toBeInTheDocument();
    expect(screen.getByText(/use the provider-agnostic weave client/i)).toBeInTheDocument();
  });

  it('saves whitelist policy through the backend API and announces the result', async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    const policyField = await screen.findByRole('textbox', { name: /allowed capabilities/i });
    await user.clear(policyField);
    await user.type(policyField, 'files.read');
    await user.click(screen.getByRole('button', { name: /save whitelist policy/i }));

    await waitFor(() => expect(api.updateWhitelistPolicy).toHaveBeenCalledWith(['files.read']));
    expect(await screen.findByRole('status')).toHaveTextContent(/whitelist policy saved/i);
  });

  it('applies selected providers as Admin Console source of truth through the backend API', async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.click(await screen.findByRole('button', { name: /apply selected provider/i }));

    await waitFor(() => expect(api.selectProvider).toHaveBeenCalledWith('identity-idm', 'keycloak-realm', 'recommended_self_hosted_default', false));
    expect(await screen.findByRole('status')).toHaveTextContent(/provider selection applied/i);
  });

  it('dry-runs selected providers through the backend API before applying', async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.click(await screen.findByRole('button', { name: /dry-run provider selection/i }));

    await waitFor(() => expect(api.selectProvider).toHaveBeenCalledWith('identity-idm', 'keycloak-realm', 'recommended_self_hosted_default', true));
    expect(await screen.findByRole('status')).toHaveTextContent(/dry-run validated/i);
  });

  it('queues provider readiness tests through the backend API', async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.click(await screen.findByRole('button', { name: /test readiness through backend/i }));

    await waitFor(() => expect(api.testProviderReadiness).toHaveBeenCalledWith('keycloak-realm'));
    expect(await screen.findByRole('status')).toHaveTextContent(/readiness test queued/i);
  });
});

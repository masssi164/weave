export type CapabilityState = 'ready' | 'disabled' | 'degraded' | 'policy-blocked' | 'misconfigured' | 'unsupported';

export interface ProviderCategory {
  key: string;
  label: string;
  selectedAdapter: string;
  state: CapabilityState;
  summary: string;
  supportSafe: boolean;
  lastCheckedAt?: string;
  secretRefs: string[];
}

export interface WhitelistPolicy {
  denyByDefault: boolean;
  allowedCapabilities: string[];
  blockedCapabilities: string[];
}

export interface AuditEvent {
  id: string;
  action: string;
  actor: string;
  createdAt: string;
  summary: string;
}

export interface ControlPlaneResponse {
  organization: {
    id: string;
    displayName: string;
    manifestUrl: string;
    authIssuerUrl: string;
  };
  providerCategories: ProviderCategory[];
  whitelistPolicy: WhitelistPolicy;
  auditEvents: AuditEvent[];
}

export interface AdminConsoleConfig {
  apiBaseUrl: string;
  oidcIssuerUrl: string;
  oidcClientId: string;
}

type RuntimeEnv = Partial<Record<'VITE_WEAVE_API_BASE_URL' | 'VITE_WEAVE_OIDC_ISSUER_URL' | 'VITE_WEAVE_ADMIN_OIDC_CLIENT_ID', string>>;

const runtimeEnv: RuntimeEnv = (import.meta as ImportMeta & { env?: RuntimeEnv }).env ?? {};

export const adminConsoleConfig: AdminConsoleConfig = {
  apiBaseUrl: (runtimeEnv.VITE_WEAVE_API_BASE_URL ?? 'https://api.weave.local:44443/api').replace(/\/$/, ''),
  oidcIssuerUrl: runtimeEnv.VITE_WEAVE_OIDC_ISSUER_URL ?? 'https://auth.weave.local:44443/realms/weave',
  oidcClientId: runtimeEnv.VITE_WEAVE_ADMIN_OIDC_CLIENT_ID ?? 'weave-admin-console',
};

export class AdminApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message);
  }
}

export class AdminControlPlaneApi {
  constructor(
    private readonly config: AdminConsoleConfig = adminConsoleConfig,
    private readonly fetchImpl: typeof fetch = fetch,
    private readonly tokenProvider: () => string | undefined = () => undefined,
  ) {}

  async getControlPlane(): Promise<ControlPlaneResponse> {
    return this.request<ControlPlaneResponse>('/admin/control-plane');
  }

  async updateWhitelistPolicy(allowedCapabilities: string[]): Promise<WhitelistPolicy> {
    return this.request<WhitelistPolicy>('/admin/policies/capability-whitelist', {
      method: 'PUT',
      body: JSON.stringify({ allowedCapabilities }),
    });
  }

  async testProviderReadiness(providerKey: string): Promise<{ providerKey: string; state: CapabilityState; summary: string }> {
    return this.request('/admin/providers/readiness-tests', {
      method: 'POST',
      body: JSON.stringify({ providerKey }),
    });
  }

  async listAuditEvents(): Promise<AuditEvent[]> {
    return this.request<AuditEvent[]>('/admin/audit/events');
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const token = this.tokenProvider();
    const headers = new Headers(init.headers);
    headers.set('Accept', 'application/json');
    if (init.body) headers.set('Content-Type', 'application/json');
    if (token) headers.set('Authorization', `Bearer ${token}`);

    const response = await this.fetchImpl(`${this.config.apiBaseUrl}${path}`, { ...init, headers });
    if (!response.ok) {
      throw new AdminApiError(`Admin API request failed with HTTP ${response.status}`, response.status);
    }
    return response.json() as Promise<T>;
  }
}

export const sampleControlPlane: ControlPlaneResponse = {
  organization: {
    id: 'weave-dogfood',
    displayName: 'Weave Dogfood',
    manifestUrl: '/api/organization/manifest',
    authIssuerUrl: 'https://auth.weave.local/realms/weave',
  },
  providerCategories: [
    {
      key: 'identity-idm',
      label: 'Identity / IDM',
      selectedAdapter: 'keycloak-realm',
      state: 'ready',
      summary: 'Central Keycloak realm is the recommended self-hosted identity broker.',
      supportSafe: true,
      lastCheckedAt: '2026-05-24T18:00:00Z',
      secretRefs: ['secretref://weave/provider/keycloak-realm/client-secret'],
    },
    {
      key: 'files',
      label: 'Files',
      selectedAdapter: 'nextcloud-files',
      state: 'degraded',
      summary: 'Backend facade can answer manifest requests; live provider smoke still needs operator proof.',
      supportSafe: true,
      secretRefs: ['secretref://weave/provider/nextcloud-files/backend-token'],
    },
    {
      key: 'weaver',
      label: 'Weaver runtime',
      selectedAdapter: 'weaver-runtime-disabled',
      state: 'policy-blocked',
      summary: 'Governed per-user PA runtime remains disabled by default.',
      supportSafe: true,
      secretRefs: [],
    },
  ],
  whitelistPolicy: {
    denyByDefault: true,
    allowedCapabilities: ['chat.read', 'files.read'],
    blockedCapabilities: ['weaver.exec', 'provider.direct_call'],
  },
  auditEvents: [
    {
      id: 'audit-1',
      action: 'provider.readiness.tested',
      actor: 'operator@weave.local',
      createdAt: '2026-05-24T18:00:00Z',
      summary: 'Readiness tested for keycloak-realm; result redacted and support-safe.',
    },
  ],
};

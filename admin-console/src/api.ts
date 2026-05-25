export type CapabilityState = 'ready' | 'disabled' | 'degraded' | 'policy-blocked' | 'misconfigured' | 'unsupported' | 'not_configured' | 'configured';

export interface ProviderCategory {
  key: string;
  label: string;
  selectedAdapter: string;
  state: CapabilityState;
  summary: string;
  supportSafe: boolean;
  selectedByAdmin: boolean;
  bootstrapSuggestionOnly: boolean;
  choiceModel: string;
  providerCandidates: string[];
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
  providerConfigSource: string;
  bootstrapDefaultsAreSuggestionsOnly: boolean;
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

interface ServerControlPlaneResponse {
  organizationId?: string;
  organizationName?: string;
  providerConfigSource?: string;
  bootstrapDefaultsAreSuggestionsOnly?: boolean;
  generatedAt?: string;
  categories?: ServerProviderCategory[];
  selectedProviderMappings?: Array<{ category?: string; providerKey?: string; secretRef?: string }>;
  whitelist?: ServerWhitelistPolicy;
  secretRefs?: Array<{ ref?: string; providerKey?: string }>;
}

interface ServerProviderCategory {
  category?: string;
  label?: string;
  readiness?: string;
  memberImpact?: string;
  providerCandidates?: string[];
  selectedProviderKey?: string;
  choiceModel?: string;
  selectedByAdmin?: boolean;
  bootstrapSuggestionOnly?: boolean;
  diagnostics?: Record<string, unknown>;
}

interface ServerWhitelistPolicy {
  denyByDefault?: boolean;
  profileCapabilities?: Record<string, string[]>;
  effectiveCapabilities?: string[];
}

interface ServerAuditEvent {
  idempotencyKey?: string;
  action?: string;
  actorRef?: string;
  occurredAt?: string;
  sourceRef?: string;
  payload?: Record<string, unknown>;
}

export class AdminControlPlaneApi {
  constructor(
    private readonly config: AdminConsoleConfig = adminConsoleConfig,
    private readonly fetchImpl: typeof fetch = fetch,
    private readonly tokenProvider: () => string | undefined = () => undefined,
  ) {}

  async getControlPlane(): Promise<ControlPlaneResponse> {
    const controlPlane = await this.request<ServerControlPlaneResponse>('/admin/control-plane');
    const auditEvents = await this.listAuditEvents().catch(() => []);
    return normalizeControlPlane(controlPlane, auditEvents);
  }

  async updateWhitelistPolicy(allowedCapabilities: string[], profileKey = 'workspace-admin'): Promise<WhitelistPolicy> {
    const response = await this.request<ServerWhitelistPolicy>('/admin/policies/capability-whitelist', {
      method: 'PATCH',
      body: JSON.stringify({ profileKey, capabilityKeys: allowedCapabilities, reason: 'Updated through Organization/Admin Console' }),
    });
    return normalizeWhitelist(response);
  }

  async selectProvider(category: string, providerKey: string, choiceModel = 'recommended_self_hosted_default', dryRun = false): Promise<void> {
    await this.request('/admin/providers/selections', {
      method: 'POST',
      body: JSON.stringify({
        category,
        providerKey,
        choiceModel,
        dryRun,
        secretRef: `secretref://weave/provider/${providerKey}`,
        reason: dryRun ? 'Dry-run through Organization/Admin Console' : 'Selected through Organization/Admin Console',
      }),
    });
  }

  async testProviderReadiness(providerKey: string): Promise<{ providerKey: string; state: CapabilityState; summary: string }> {
    const response = await this.request<{ providerKey?: string; state?: string; readiness?: string }>('/admin/providers/readiness-tests', {
      method: 'POST',
      body: JSON.stringify({ providerKey }),
    });
    return {
      providerKey: response.providerKey ?? providerKey,
      state: normalizeState(response.state ?? response.readiness),
      summary: response.readiness ?? 'readiness tested through backend control plane',
    };
  }

  async listAuditEvents(): Promise<AuditEvent[]> {
    const events = await this.request<ServerAuditEvent[]>('/admin/audit/events');
    return events.map((event) => ({
      id: event.idempotencyKey ?? `${event.action ?? 'audit'}-${event.occurredAt ?? 'unknown'}`,
      action: event.action ?? 'unknown',
      actor: event.actorRef ?? 'unknown-actor',
      createdAt: event.occurredAt ?? '',
      summary: supportSafeSummary(event),
    }));
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

function normalizeControlPlane(controlPlane: ServerControlPlaneResponse, auditEvents: AuditEvent[]): ControlPlaneResponse {
  const selections = controlPlane.selectedProviderMappings ?? [];
  const secretRefs = controlPlane.secretRefs ?? [];
  return {
    organization: {
      id: controlPlane.organizationId ?? 'weave-dogfood',
      displayName: controlPlane.organizationName ?? 'Weave Dogfood',
      manifestUrl: '/api/v1/organization/manifest',
      authIssuerUrl: adminConsoleConfig.oidcIssuerUrl,
    },
    providerConfigSource: controlPlane.providerConfigSource ?? 'admin-control-plane-selected-provider-mappings',
    bootstrapDefaultsAreSuggestionsOnly: controlPlane.bootstrapDefaultsAreSuggestionsOnly ?? true,
    providerCategories: (controlPlane.categories ?? []).map((category) => normalizeCategory(category, selections, secretRefs, controlPlane.generatedAt)),
    whitelistPolicy: normalizeWhitelist(controlPlane.whitelist),
    auditEvents,
  };
}

function normalizeCategory(
  category: ServerProviderCategory,
  selections: Array<{ category?: string; providerKey?: string }>,
  secretRefs: Array<{ ref?: string; providerKey?: string }>,
  generatedAt?: string,
): ProviderCategory {
  const key = category.category ?? 'unknown';
  const selectedAdapter = category.selectedProviderKey ?? selections.find((selection) => selection.category === key)?.providerKey ?? 'awaiting_admin_selection';
  return {
    key,
    label: category.label ?? key,
    selectedAdapter,
    state: normalizeState(category.readiness),
    summary: category.memberImpact ?? 'Backend control-plane status is support-safe.',
    supportSafe: category.diagnostics?.secretsReturned === false && category.diagnostics?.rawProviderErrorsReturned === false,
    selectedByAdmin: category.selectedByAdmin ?? false,
    bootstrapSuggestionOnly: category.bootstrapSuggestionOnly ?? true,
    choiceModel: category.choiceModel ?? 'not_selected',
    providerCandidates: category.providerCandidates ?? [],
    lastCheckedAt: generatedAt,
    secretRefs: secretRefs.filter((secretRef) => secretRef.providerKey === selectedAdapter).map((secretRef) => secretRef.ref ?? '').filter(Boolean),
  };
}

function normalizeWhitelist(whitelist?: ServerWhitelistPolicy): WhitelistPolicy {
  const profileCapabilities = whitelist?.profileCapabilities ?? {};
  const allowedCapabilities = Array.from(new Set([...(whitelist?.effectiveCapabilities ?? []), ...Object.values(profileCapabilities).flat()])).sort();
  return {
    denyByDefault: whitelist?.denyByDefault ?? true,
    allowedCapabilities,
    blockedCapabilities: ['provider.direct_call', 'provider.secret_export', 'weaver.exec_without_policy'],
  };
}

function normalizeState(value?: string): CapabilityState {
  switch (value) {
    case 'ready':
    case 'disabled':
    case 'degraded':
    case 'misconfigured':
    case 'unsupported':
    case 'not_configured':
    case 'configured':
      return value;
    case 'policy_blocked':
    case 'policy-blocked':
      return 'policy-blocked';
    default:
      return 'degraded';
  }
}

function supportSafeSummary(event: ServerAuditEvent): string {
  const payload = event.payload ?? {};
  const providerKey = typeof payload.providerKey === 'string' ? payload.providerKey : undefined;
  const category = typeof payload.category === 'string' ? payload.category : undefined;
  const target = providerKey ?? category ?? event.sourceRef ?? 'admin control plane';
  return `${event.action ?? 'audit'} for ${target}; payload is redacted and support-safe.`;
}

export const sampleControlPlane: ControlPlaneResponse = {
  organization: {
    id: 'weave-dogfood',
    displayName: 'Weave Dogfood',
    manifestUrl: '/api/organization/manifest',
    authIssuerUrl: 'https://auth.weave.local/realms/weave',
  },
  providerConfigSource: 'admin-control-plane-selected-provider-mappings',
  bootstrapDefaultsAreSuggestionsOnly: true,
  providerCategories: [
    {
      key: 'identity-idm',
      label: 'Identity / IDM',
      selectedAdapter: 'keycloak-realm',
      state: 'ready',
      summary: 'Central Keycloak realm is the recommended self-hosted identity broker; admin selection is the source of truth.',
      supportSafe: true,
      selectedByAdmin: true,
      bootstrapSuggestionOnly: false,
      choiceModel: 'recommended_self_hosted_default',
      providerCandidates: ['keycloak-realm', 'entra-id', 'authentik', 'auth0', 'generic-oidc', 'generic-saml', 'scim-ldap'],
      lastCheckedAt: '2026-05-24T18:00:00Z',
      secretRefs: ['secretref://weave/provider/keycloak-realm/client-secret'],
    },
    {
      key: 'chat',
      label: 'Chat',
      selectedAdapter: 'synapse-homeserver',
      state: 'ready',
      summary: 'Chat is available through Weave conversations.',
      supportSafe: true,
      selectedByAdmin: true,
      bootstrapSuggestionOnly: false,
      choiceModel: 'recommended_self_hosted_default',
      providerCandidates: ['synapse-homeserver', 'slack', 'microsoft-teams'],
      secretRefs: ['secretref://weave/provider/synapse-homeserver'],
    },
    {
      key: 'files',
      label: 'Files',
      selectedAdapter: 'nextcloud-files',
      state: 'degraded',
      summary: 'Files are exposed through Weave canonical file facades; Nextcloud, SharePoint, S3, and SMB adapters remain backend-owned.',
      supportSafe: true,
      selectedByAdmin: true,
      bootstrapSuggestionOnly: false,
      choiceModel: 'recommended_self_hosted_default',
      providerCandidates: ['nextcloud-files', 'sharepoint', 's3-compatible', 'smb'],
      secretRefs: ['secretref://weave/provider/nextcloud-files'],
    },
    {
      key: 'calendar',
      label: 'Calendar',
      selectedAdapter: 'nextcloud-caldav',
      state: 'degraded',
      summary: 'Calendar access is normalized through Weave; CalDAV and Microsoft Graph remain adapter choices only.',
      supportSafe: true,
      selectedByAdmin: true,
      bootstrapSuggestionOnly: false,
      choiceModel: 'recommended_self_hosted_default',
      providerCandidates: ['nextcloud-caldav', 'microsoft-graph-calendar', 'generic-caldav'],
      secretRefs: ['secretref://weave/provider/nextcloud-caldav'],
    },
    {
      key: 'boards-tasks',
      label: 'Boards / tasks',
      selectedAdapter: 'openproject-primary',
      state: 'policy-blocked',
      summary: 'Boards/tasks stay provider-neutral across OpenProject, Planner, Jira, Vikunja, and Deck adapters.',
      supportSafe: true,
      selectedByAdmin: true,
      bootstrapSuggestionOnly: false,
      choiceModel: 'recommended_self_hosted_default',
      providerCandidates: ['openproject-primary', 'microsoft-planner', 'jira', 'vikunja', 'nextcloud-deck'],
      secretRefs: ['secretref://weave/provider/openproject-primary'],
    },
    {
      key: 'meetings-calls',
      label: 'Meetings / calls',
      selectedAdapter: 'livekit',
      state: 'misconfigured',
      summary: 'Meetings are surfaced through Weave calls; LiveKit and external meeting links are backend-selected providers.',
      supportSafe: true,
      selectedByAdmin: true,
      bootstrapSuggestionOnly: false,
      choiceModel: 'recommended_self_hosted_default',
      providerCandidates: ['livekit', 'microsoft-teams-meetings', 'external-meeting-link'],
      secretRefs: ['secretref://weave/provider/livekit'],
    },
    {
      key: 'documents-collaboration',
      label: 'Documents / collaboration',
      selectedAdapter: 'onlyoffice-community',
      state: 'disabled',
      summary: 'Documents use Weave/WOPI collaboration contracts; Nextcloud/SharePoint/WOPI providers are not member-facing setup.',
      supportSafe: true,
      selectedByAdmin: true,
      bootstrapSuggestionOnly: false,
      choiceModel: 'recommended_self_hosted_default',
      providerCandidates: ['onlyoffice-community', 'collabora-code', 'wopi-host', 'microsoft-365-office-graph'],
      secretRefs: ['secretref://weave/provider/onlyoffice-community'],
    },
    {
      key: 'weaver',
      label: 'Weaver runtime',
      selectedAdapter: 'awaiting_admin_selection',
      state: 'policy-blocked',
      summary: 'Governed per-user PA runtime remains disabled by default and Weave-owned decisions stay backend-policy controlled.',
      supportSafe: true,
      selectedByAdmin: false,
      bootstrapSuggestionOnly: true,
      choiceModel: 'not_selected',
      providerCandidates: ['openclaw-governed-runtime'],
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

export type CapabilityState =
  | 'ready'
  | 'disabled'
  | 'degraded'
  | 'policy-blocked'
  | 'admin-action-required'
  | 'misconfigured'
  | 'unsupported'
  | 'not_configured'
  | 'configured';

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

export interface IdentityProviderReadinessCard {
  key: string;
  label: string;
  state: CapabilityState;
  summary: string;
  memberImpact: 'ready' | 'disabled' | 'degraded' | 'policy-blocked';
  remediation: string;
  nextActions: string[];
  evidenceRefs: string[];
}

export interface IdentityProviderReadiness {
  contractVersion: string;
  category: string;
  providerKey: string;
  overallState: CapabilityState;
  supportSafe: boolean;
  providerDiagnosticsRedacted: boolean;
  backendOwnedFacade: boolean;
  memberClientMayConfigureIdentityProvider: boolean;
  optionalForMemberFlows: boolean;
  stableStates: CapabilityState[];
  cards: IdentityProviderReadinessCard[];
  nextActions: string[];
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

export type MemberCapabilityState =
  | 'available'
  | 'disabled_by_policy'
  | 'not_configured'
  | 'degraded'
  | 'unavailable'
  | 'coming_later';

export interface ProviderReplacementDryRunReport {
  dryRunId: string;
  status: string;
  category: string;
  currentAdapter: string;
  targetAdapter: string;
  readinessState: CapabilityState;
  migrationDryRunRequired: boolean;
  memberImpactStates: MemberCapabilityState[];
  supportSafe: boolean;
  providerDiagnosticsRedacted: boolean;
  cutoverGates: string[];
  lossyMappingReport: {
    canonicalObjects: string[];
    contractRisks: string[];
    adminNotes: string[];
    conflicts: string[];
    replacementRequirement: string;
  };
  lifecycleExpectations: {
    sourceOfTruthPolicy: string;
    exportExpectation: string;
    deleteExpectation: string;
    deprovisionExpectation: string;
    rollbackSupportBoundary: string;
  };
  portableExportImportContract: {
    exportManifestRef: string;
    importManifestRef: string;
    portabilityGuarantee: string;
    excludedAutomation: string[];
    evidenceRefs: string[];
  };
  switchPlan: {
    planRef: string;
    preflightRequired: boolean;
    cutoverWindowRequired: boolean;
    rollbackRequired: boolean;
    memberFacingStateDuringSwitch: MemberCapabilityState;
    recoveryActions: string[];
  };
}

interface ServerProviderReplacementDryRunReport {
  dryRunId?: string;
  status?: string;
  category?: string;
  currentAdapter?: string;
  targetAdapter?: string;
  readinessState?: string;
  migrationDryRunRequired?: boolean;
  memberImpactStates?: string[];
  supportSafe?: boolean;
  providerDiagnosticsRedacted?: boolean;
  cutoverGates?: string[];
  lossyMappingReport?: Partial<
    ProviderReplacementDryRunReport['lossyMappingReport']
  >;
  lifecycleExpectations?: Partial<
    ProviderReplacementDryRunReport['lifecycleExpectations']
  >;
  portableExportImportContract?: Partial<
    ProviderReplacementDryRunReport['portableExportImportContract']
  >;
  switchPlan?: Partial<ProviderReplacementDryRunReport['switchPlan']>;
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
  identityProviderReadiness: IdentityProviderReadiness;
  whitelistPolicy: WhitelistPolicy;
  auditEvents: AuditEvent[];
}

export interface AdminConsoleConfig {
  apiBaseUrl: string;
  oidcIssuerUrl: string;
  oidcClientId: string;
}

type RuntimeEnv = Partial<
  Record<
    | 'VITE_WEAVE_API_BASE_URL'
    | 'VITE_WEAVE_OIDC_ISSUER_URL'
    | 'VITE_WEAVE_ADMIN_OIDC_CLIENT_ID',
    string
  >
>;

const runtimeEnv: RuntimeEnv =
  (import.meta as ImportMeta & { env?: RuntimeEnv }).env ?? {};

export const adminConsoleConfig: AdminConsoleConfig = {
  apiBaseUrl: (
    runtimeEnv.VITE_WEAVE_API_BASE_URL ?? 'https://api.weave.local:44443/api'
  ).replace(/\/$/, ''),
  oidcIssuerUrl:
    runtimeEnv.VITE_WEAVE_OIDC_ISSUER_URL ??
    'https://auth.weave.local:44443/realms/weave',
  oidcClientId:
    runtimeEnv.VITE_WEAVE_ADMIN_OIDC_CLIENT_ID ?? 'weave-admin-console',
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
  selectedProviderMappings?: Array<{
    category?: string;
    providerKey?: string;
    secretRef?: string;
  }>;
  whitelist?: ServerWhitelistPolicy;
  identityProviderReadiness?: ServerIdentityProviderReadiness;
  secretRefs?: Array<{ ref?: string; providerKey?: string }>;
}

interface ServerIdentityProviderReadiness {
  contractVersion?: string;
  category?: string;
  providerKey?: string;
  overallState?: string;
  supportSafe?: boolean;
  providerDiagnosticsRedacted?: boolean;
  backendOwnedFacade?: boolean;
  memberClientMayConfigureIdentityProvider?: boolean;
  optionalForMemberFlows?: boolean;
  stableStates?: string[];
  cards?: Array<{
    key?: string;
    label?: string;
    state?: string;
    summary?: string;
    memberImpact?: string;
    remediation?: string;
    nextActions?: string[];
    evidenceRefs?: string[];
  }>;
  nextActions?: string[];
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
    const controlPlane = await this.request<ServerControlPlaneResponse>(
      '/admin/control-plane',
    );
    const auditEvents = await this.listAuditEvents().catch(() => []);
    return normalizeControlPlane(controlPlane, auditEvents);
  }

  async updateWhitelistPolicy(
    allowedCapabilities: string[],
    profileKey = 'workspace-admin',
  ): Promise<WhitelistPolicy> {
    const response = await this.request<ServerWhitelistPolicy>(
      '/admin/policies/capability-whitelist',
      {
        method: 'PATCH',
        body: JSON.stringify({
          profileKey,
          capabilityKeys: allowedCapabilities,
          reason: 'Updated through Organization/Admin Console',
        }),
      },
    );
    return normalizeWhitelist(response);
  }

  async selectProvider(
    category: string,
    providerKey: string,
    choiceModel = 'recommended_self_hosted_default',
    dryRun = false,
  ): Promise<void> {
    await this.request('/admin/providers/selections', {
      method: 'POST',
      body: JSON.stringify({
        category,
        providerKey,
        choiceModel,
        dryRun,
        secretRef: `secretref://weave/provider/${providerKey}`,
        reason: dryRun
          ? 'Dry-run through Organization/Admin Console'
          : 'Selected through Organization/Admin Console',
      }),
    });
  }

  async dryRunProviderReplacement(
    category: ProviderCategory,
    targetAdapter: string,
    choiceModel = 'external_existing_provider',
  ): Promise<ProviderReplacementDryRunReport> {
    const response = await this.request<ServerProviderReplacementDryRunReport>(
      '/admin/providers/replacements/dry-run',
      {
        method: 'POST',
        body: JSON.stringify({
          category: category.key,
          currentAdapter: category.selectedAdapter,
          targetAdapter,
          choiceModel,
          secretRef: `secretref://weave/provider/${targetAdapter}`,
          sourceOfTruth:
            'Admin Console-selected provider category remains Weave source of truth until apply.',
          lossyMappingNotes: [
            'Admin Console requested support-safe preflight; backend redaction owns provider diagnostics.',
          ],
          portableExportImportRequired: true,
          requestedSwitchPlan: {
            plan: 'guided-plan-preflight-export-import-cutover-rollback',
            memberFacingStateDuringSwitch: 'degraded',
            automationBoundary:
              'v0.1 requires portable export/import evidence; full automated migration remains future work.',
          },
          reason:
            'Evaluate provider replacement before activation through Organization/Admin Console',
        }),
      },
    );
    return normalizeProviderReplacementDryRun(
      response,
      category,
      targetAdapter,
    );
  }

  async getIdentityProviderReadiness(): Promise<IdentityProviderReadiness> {
    const response = await this.request<ServerIdentityProviderReadiness>(
      '/admin/identity/readiness',
    );
    return normalizeIdentityProviderReadiness(response);
  }

  async testProviderReadiness(
    providerKey: string,
  ): Promise<{ providerKey: string; state: CapabilityState; summary: string }> {
    const response = await this.request<{
      providerKey?: string;
      state?: string;
      readiness?: string;
    }>('/admin/providers/readiness-tests', {
      method: 'POST',
      body: JSON.stringify({ providerKey }),
    });
    return {
      providerKey: response.providerKey ?? providerKey,
      state: normalizeState(response.state ?? response.readiness),
      summary:
        response.readiness ?? 'readiness tested through backend control plane',
    };
  }

  async listAuditEvents(): Promise<AuditEvent[]> {
    const events = await this.request<ServerAuditEvent[]>(
      '/admin/audit/events',
    );
    return events.map((event) => ({
      id:
        event.idempotencyKey ??
        `${event.action ?? 'audit'}-${event.occurredAt ?? 'unknown'}`,
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

    const response = await this.fetchImpl(`${this.config.apiBaseUrl}${path}`, {
      ...init,
      headers,
    });
    if (!response.ok) {
      throw new AdminApiError(
        `Admin API request failed with HTTP ${response.status}`,
        response.status,
      );
    }
    return response.json() as Promise<T>;
  }
}

function normalizeControlPlane(
  controlPlane: ServerControlPlaneResponse,
  auditEvents: AuditEvent[],
): ControlPlaneResponse {
  const selections = controlPlane.selectedProviderMappings ?? [];
  const secretRefs = controlPlane.secretRefs ?? [];
  return {
    organization: {
      id: controlPlane.organizationId ?? 'weave-dogfood',
      displayName: controlPlane.organizationName ?? 'Weave Dogfood',
      manifestUrl: '/api/v1/organization/manifest',
      authIssuerUrl: adminConsoleConfig.oidcIssuerUrl,
    },
    providerConfigSource:
      controlPlane.providerConfigSource ??
      'admin-control-plane-selected-provider-mappings',
    bootstrapDefaultsAreSuggestionsOnly:
      controlPlane.bootstrapDefaultsAreSuggestionsOnly ?? true,
    providerCategories: (controlPlane.categories ?? []).map((category) =>
      normalizeCategory(
        category,
        selections,
        secretRefs,
        controlPlane.generatedAt,
      ),
    ),
    identityProviderReadiness: normalizeIdentityProviderReadiness(
      controlPlane.identityProviderReadiness,
    ),
    whitelistPolicy: normalizeWhitelist(controlPlane.whitelist),
    auditEvents,
  };
}

function normalizeIdentityProviderReadiness(
  readiness?: ServerIdentityProviderReadiness,
): IdentityProviderReadiness {
  const cards = (readiness?.cards ?? []).map((card) => ({
    key: card.key ?? 'identity-readiness-card',
    label: card.label ?? 'Identity provider readiness',
    state: normalizeState(card.state),
    summary:
      card.summary ??
      'Identity readiness is provided by the backend control-plane facade.',
    memberImpact: normalizeIdentityMemberImpact(card.memberImpact),
    remediation:
      card.remediation ??
      'Run the backend readiness contract and resolve admin-action-required items.',
    nextActions: card.nextActions ?? [],
    evidenceRefs: card.evidenceRefs ?? [],
  }));
  const versionSkewCards = [
    {
      key: 'identity-readiness-contract-missing',
      label: 'Identity readiness contract missing',
      state: 'admin-action-required' as CapabilityState,
      summary:
        'The backend did not return identity readiness details; Admin Console fails closed during version skew.',
      memberImpact: 'degraded' as const,
      remediation:
        'Upgrade or restart the backend control-plane facade, then run the identity readiness check again.',
      nextActions: [
        'Verify GET /api/admin/identity/readiness on the backend',
        'Keep member provider setup blocked until readiness evidence exists',
      ],
      evidenceRefs: ['version-skew-fail-closed'],
    },
  ];
  return {
    contractVersion:
      readiness?.contractVersion ?? 'identity-provider-readiness-v1',
    category: readiness?.category ?? 'idm-rbac',
    providerKey: readiness?.providerKey ?? 'awaiting_admin_selection',
    overallState: normalizeState(
      readiness?.overallState ?? 'admin-action-required',
    ),
    supportSafe: readiness?.supportSafe ?? true,
    providerDiagnosticsRedacted:
      readiness?.providerDiagnosticsRedacted ?? true,
    backendOwnedFacade: readiness?.backendOwnedFacade ?? true,
    memberClientMayConfigureIdentityProvider:
      readiness?.memberClientMayConfigureIdentityProvider ?? false,
    optionalForMemberFlows: readiness?.optionalForMemberFlows ?? true,
    stableStates: (readiness?.stableStates ?? [
      'ready',
      'degraded',
      'policy-blocked',
      'admin-action-required',
      'disabled',
    ]).map(normalizeState),
    cards: cards.length > 0 ? cards : versionSkewCards,
    nextActions: readiness?.nextActions ?? [
      'Treat missing identity readiness as admin-action-required and fail closed.',
    ],
  };
}

function normalizeCategory(
  category: ServerProviderCategory,
  selections: Array<{ category?: string; providerKey?: string }>,
  secretRefs: Array<{ ref?: string; providerKey?: string }>,
  generatedAt?: string,
): ProviderCategory {
  const key = category.category ?? 'unknown';
  const selectedAdapter =
    category.selectedProviderKey ??
    selections.find((selection) => selection.category === key)?.providerKey ??
    'awaiting_admin_selection';
  return {
    key,
    label: category.label ?? key,
    selectedAdapter,
    state: normalizeState(category.readiness),
    summary:
      category.memberImpact ?? 'Backend control-plane status is support-safe.',
    supportSafe:
      category.diagnostics?.secretsReturned === false &&
      category.diagnostics?.rawProviderErrorsReturned === false,
    selectedByAdmin: category.selectedByAdmin ?? false,
    bootstrapSuggestionOnly: category.bootstrapSuggestionOnly ?? true,
    choiceModel: category.choiceModel ?? 'not_selected',
    providerCandidates: category.providerCandidates ?? [],
    lastCheckedAt: generatedAt,
    secretRefs: secretRefs
      .filter((secretRef) => secretRef.providerKey === selectedAdapter)
      .map((secretRef) => secretRef.ref ?? '')
      .filter(Boolean),
  };
}

function normalizeWhitelist(
  whitelist?: ServerWhitelistPolicy,
): WhitelistPolicy {
  const profileCapabilities = whitelist?.profileCapabilities ?? {};
  const allowedCapabilities = Array.from(
    new Set([
      ...(whitelist?.effectiveCapabilities ?? []),
      ...Object.values(profileCapabilities).flat(),
    ]),
  ).sort();
  return {
    denyByDefault: whitelist?.denyByDefault ?? true,
    allowedCapabilities,
    blockedCapabilities: [
      'provider.direct_call',
      'provider.secret_export',
      'provider.unapproved_runtime_execution',
    ],
  };
}

function normalizeProviderReplacementDryRun(
  response: ServerProviderReplacementDryRunReport,
  category: ProviderCategory,
  targetAdapter: string,
): ProviderReplacementDryRunReport {
  const lossyMapping = response.lossyMappingReport ?? {};
  const lifecycle = response.lifecycleExpectations ?? {};
  const portability = response.portableExportImportContract ?? {};
  const switchPlan = response.switchPlan ?? {};
  return {
    dryRunId: response.dryRunId ?? `${category.key}-replacement-dry-run`,
    status: response.status ?? 'dry_run_ready',
    category: response.category ?? category.key,
    currentAdapter: response.currentAdapter ?? category.selectedAdapter,
    targetAdapter: response.targetAdapter ?? targetAdapter,
    readinessState: normalizeState(response.readinessState),
    migrationDryRunRequired: response.migrationDryRunRequired ?? true,
    memberImpactStates: normalizeMemberImpactStates(
      response.memberImpactStates,
    ),
    supportSafe: response.supportSafe ?? true,
    providerDiagnosticsRedacted: response.providerDiagnosticsRedacted ?? true,
    cutoverGates: response.cutoverGates ?? [],
    lossyMappingReport: {
      canonicalObjects: lossyMapping.canonicalObjects ?? [],
      contractRisks: lossyMapping.contractRisks ?? [],
      adminNotes: lossyMapping.adminNotes ?? [],
      conflicts: lossyMapping.conflicts ?? [],
      replacementRequirement:
        lossyMapping.replacementRequirement ??
        'Backend migration dry-run required before apply.',
    },
    lifecycleExpectations: {
      sourceOfTruthPolicy:
        lifecycle.sourceOfTruthPolicy ??
        'Source of truth is declared per provider-backed category/object by the backend dry-run; Weave preserves provider-neutral member capability state only.',
      exportExpectation:
        lifecycle.exportExpectation ??
        'Export expectations are evaluated by backend migration contracts.',
      deleteExpectation:
        lifecycle.deleteExpectation ??
        'Delete expectations are evaluated by backend migration contracts.',
      deprovisionExpectation:
        lifecycle.deprovisionExpectation ??
        'Deprovision expectations are evaluated by backend migration contracts.',
      rollbackSupportBoundary:
        lifecycle.rollbackSupportBoundary ??
        'Rollback is bounded by provider export/delete support.',
    },
    portableExportImportContract: {
      exportManifestRef:
        portability.exportManifestRef ??
        `${category.key}-portable-export-manifest-v0.1`,
      importManifestRef:
        portability.importManifestRef ??
        `${category.key}-portable-import-manifest-v0.1`,
      portabilityGuarantee:
        portability.portabilityGuarantee ??
        'v0.1 guarantees a documented portable export/import contract before claiming automated migration.',
      excludedAutomation: portability.excludedAutomation ?? [
        'no full cross-provider automated migration promise in v0.1',
      ],
      evidenceRefs: portability.evidenceRefs ?? [
        'provider-switch-preflight',
        'portable-export-import-contract',
        'rollback-recovery-plan',
      ],
    },
    switchPlan: {
      planRef: switchPlan.planRef ?? `${category.key}-switch-plan-v0.1`,
      preflightRequired: switchPlan.preflightRequired ?? true,
      cutoverWindowRequired: switchPlan.cutoverWindowRequired ?? true,
      rollbackRequired: switchPlan.rollbackRequired ?? true,
      memberFacingStateDuringSwitch:
        normalizeMemberCapabilityState(
          switchPlan.memberFacingStateDuringSwitch,
        ) ?? 'degraded',
      recoveryActions: switchPlan.recoveryActions ?? [
        'keep current adapter active until export/import evidence is accepted',
        'block apply when rollback evidence or support-safe audit refs are missing',
      ],
    },
  };
}

function normalizeIdentityMemberImpact(
  value?: string,
): 'ready' | 'disabled' | 'degraded' | 'policy-blocked' {
  switch (value) {
    case 'ready':
    case 'disabled':
    case 'degraded':
    case 'policy-blocked':
      return value;
    case 'policy_blocked':
      return 'policy-blocked';
    case 'usable':
      return 'ready';
    default:
      return 'degraded';
  }
}

function normalizeMemberImpactStates(values?: string[]): MemberCapabilityState[] {
  const normalized = (values ?? [])
    .map(normalizeMemberCapabilityState)
    .filter((value): value is MemberCapabilityState => value !== null);
  return normalized.length > 0
    ? Array.from(new Set(normalized))
    : ['available', 'disabled_by_policy', 'not_configured', 'degraded'];
}

function normalizeMemberCapabilityState(value?: string): MemberCapabilityState | null {
  switch (value) {
    case 'available':
    case 'not_configured':
    case 'degraded':
    case 'unavailable':
    case 'coming_later':
      return value;
    case 'disabled_by_policy':
    case 'policy-blocked':
    case 'policy_blocked':
    case 'disabled':
      return 'disabled_by_policy';
    case 'ready':
    case 'usable':
      return 'available';
    case 'admin-action-required':
    case 'admin_action_required':
    case 'misconfigured':
      return 'degraded';
    case 'unsupported':
      return 'unavailable';
    default:
      return null;
  }
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
    case 'admin_action_required':
    case 'admin-action-required':
      return 'admin-action-required';
    default:
      return 'degraded';
  }
}

function supportSafeSummary(event: ServerAuditEvent): string {
  const payload = event.payload ?? {};
  const providerKey =
    typeof payload.providerKey === 'string' ? payload.providerKey : undefined;
  const category =
    typeof payload.category === 'string' ? payload.category : undefined;
  const target =
    providerKey ?? category ?? event.sourceRef ?? 'admin control plane';
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
      key: 'idm-rbac',
      label: 'IDM / RBAC',
      selectedAdapter: 'keycloak-realm',
      state: 'ready',
      summary:
        'Central Keycloak realm is the recommended self-hosted identity broker; admin selection is the source of truth.',
      supportSafe: true,
      selectedByAdmin: true,
      bootstrapSuggestionOnly: false,
      choiceModel: 'recommended_self_hosted_default',
      providerCandidates: [
        'keycloak-realm',
        'entra-id',
        'authentik',
        'auth0',
        'generic-oidc',
        'generic-saml',
        'scim-ldap',
      ],
      lastCheckedAt: '2026-05-24T18:00:00Z',
      secretRefs: ['secretref://weave/provider/keycloak-realm/client-secret'],
    },
    {
      key: 'chat-channels',
      label: 'Chat / channels',
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
      key: 'files-docs',
      label: 'Files / docs',
      selectedAdapter: 'nextcloud-files',
      state: 'degraded',
      summary:
        'Files are exposed through Weave canonical file facades; Nextcloud, SharePoint, S3, and SMB adapters remain backend-owned.',
      supportSafe: true,
      selectedByAdmin: true,
      bootstrapSuggestionOnly: false,
      choiceModel: 'recommended_self_hosted_default',
      providerCandidates: [
        'nextcloud-files',
        'sharepoint',
        's3-compatible',
        'smb',
      ],
      secretRefs: ['secretref://weave/provider/nextcloud-files'],
    },
    {
      key: 'calendar-events',
      label: 'Calendar / events',
      selectedAdapter: 'nextcloud-caldav',
      state: 'degraded',
      summary:
        'Calendar access is normalized through Weave; CalDAV and Microsoft Graph remain adapter choices only.',
      supportSafe: true,
      selectedByAdmin: true,
      bootstrapSuggestionOnly: false,
      choiceModel: 'recommended_self_hosted_default',
      providerCandidates: [
        'nextcloud-caldav',
        'microsoft-graph-calendar',
        'generic-caldav',
      ],
      secretRefs: ['secretref://weave/provider/nextcloud-caldav'],
    },
    {
      key: 'boards-tasks',
      label: 'Boards / tasks',
      selectedAdapter: 'openproject-primary',
      state: 'policy-blocked',
      summary:
        'Boards/tasks stay provider-neutral across OpenProject, Planner, Jira, Vikunja, and Deck adapters.',
      supportSafe: true,
      selectedByAdmin: true,
      bootstrapSuggestionOnly: false,
      choiceModel: 'recommended_self_hosted_default',
      providerCandidates: [
        'openproject-primary',
        'microsoft-planner',
        'jira',
        'vikunja',
        'nextcloud-deck',
      ],
      secretRefs: ['secretref://weave/provider/openproject-primary'],
    },
    {
      key: 'meetings',
      label: 'Meetings',
      selectedAdapter: 'livekit',
      state: 'misconfigured',
      summary:
        'Meetings are surfaced through Weave calls; LiveKit and external meeting links are backend-selected providers.',
      supportSafe: true,
      selectedByAdmin: true,
      bootstrapSuggestionOnly: false,
      choiceModel: 'recommended_self_hosted_default',
      providerCandidates: [
        'livekit',
        'microsoft-teams-meetings',
        'external-meeting-link',
      ],
      secretRefs: ['secretref://weave/provider/livekit'],
    },
    {
      key: 'forms-contacts',
      label: 'Forms / contacts',
      selectedAdapter: 'weave-managed-forms-contacts',
      state: 'disabled',
      summary:
        'Forms and contacts use Weave canonical contracts; provider-specific address book or form backends stay admin/operator-side.',
      supportSafe: true,
      selectedByAdmin: true,
      bootstrapSuggestionOnly: false,
      choiceModel: 'recommended_self_hosted_default',
      providerCandidates: [
        'weave-managed-forms-contacts',
        'nextcloud-forms-contacts',
        'google-workspace-contacts',
      ],
      secretRefs: ['secretref://weave/provider/weave-managed-forms-contacts'],
    },
  ],
  identityProviderReadiness: {
    contractVersion: 'identity-provider-readiness-v1',
    category: 'idm-rbac',
    providerKey: 'keycloak-realm',
    overallState: 'ready',
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
        state: 'ready',
        summary:
          'Backend dry-run evidence confirms realm desired-state readiness without exposing realm internals.',
        memberImpact: 'ready',
        remediation: 'Run the realm dry-run again before apply if drift is suspected.',
        nextActions: ['Run /api/admin/identity/realm/dry-run before apply'],
        evidenceRefs: ['identity-realm-dry-run'],
      },
      {
        key: 'oidc-client-readiness',
        label: 'OIDC client readiness',
        state: 'ready',
        summary:
          'OIDC client readiness is summarized by backend contracts; client identifiers are redacted from support views.',
        memberImpact: 'ready',
        remediation: 'Keep client secrets as SecretRef handles only.',
        nextActions: ['Validate client scopes through backend dry-run output'],
        evidenceRefs: ['identity-client-contract'],
      },
      {
        key: 'roles-groups-mapping',
        label: 'Roles and groups mapping',
        state: 'ready',
        summary:
          'Roles and groups map into canonical Weave capability profiles with deny-by-default fallback.',
        memberImpact: 'ready',
        remediation: 'Map unknown roles/groups before activation.',
        nextActions: ['Review effective policy simulation'],
        evidenceRefs: ['effective-policy-simulation'],
      },
      {
        key: 'login-readiness',
        label: 'Login readiness',
        state: 'ready',
        summary:
          'Member login is exposed only as product-level availability; provider endpoints stay backend-owned.',
        memberImpact: 'ready',
        remediation: 'Keep member sign-in fail-closed until readiness evidence exists.',
        nextActions: ['Verify member clients expose only stable capability states'],
        evidenceRefs: ['member-boundary'],
      },
      {
        key: 'policy-readiness',
        label: 'Policy readiness',
        state: 'ready',
        summary:
          'Capability policy gates identity claims before product access.',
        memberImpact: 'ready',
        remediation: 'Retain deny-by-default and last-admin recovery capabilities.',
        nextActions: ['Review policy simulation before realm apply'],
        evidenceRefs: ['capability-whitelist'],
      },
    ],
    nextActions: ['Monitor audit/readiness transitions and keep support bundles redacted.'],
  },
  whitelistPolicy: {
    denyByDefault: true,
    allowedCapabilities: ['chat.read', 'files.read'],
    blockedCapabilities: ['provider.direct_call', 'provider.secret_export'],
  },
  auditEvents: [
    {
      id: 'audit-1',
      action: 'provider.readiness.tested',
      actor: 'operator@weave.local',
      createdAt: '2026-05-24T18:00:00Z',
      summary:
        'Readiness tested for keycloak-realm; result redacted and support-safe.',
    },
  ],
};

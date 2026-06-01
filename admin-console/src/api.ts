export type CapabilityState =
  | "ready"
  | "disabled"
  | "degraded"
  | "policy-blocked"
  | "admin-action-required"
  | "misconfigured"
  | "unsupported"
  | "not_configured"
  | "configured";

export type ProviderRealityLevel =
  | "sample_only"
  | "configured_readiness"
  | "adapter_runtime_verified"
  | "live_mutation_guarded";

export type EvidenceFreshness = "fresh" | "stale" | "missing" | "sample_only";

export interface ProviderSwitchApplyGates {
  applySupported: boolean;
  preflightPassed: boolean;
  sourceReadinessValid: boolean;
  targetReadinessValid: boolean;
  identityMappingComplete: boolean;
  exportSnapshotExists: boolean;
  dryRunSuccessful: boolean;
  lossyMappingReportAccepted: boolean;
  conflictsResolvedOrWaived: boolean;
  rollbackBoundaryExists: boolean;
  rbacAllowsMutation: boolean;
  auditSinkAvailable: boolean;
  memberImpactPreviewConfirmed: boolean;
}

export interface ProviderCategory {
  key: string;
  label: string;
  selectedAdapter: string;
  state: CapabilityState;
  summary: string;
  memberImpact: MemberCapabilityState;
  requiredNextAction: string;
  secretRefStatus: "present" | "missing" | "not_required" | "sample_only";
  policyState: "allowed" | "blocked" | "disabled" | "review_required";
  migrationState:
    | "not_required"
    | "dry_run_required"
    | "dry_run_passed"
    | "blocked"
    | "sample_only";
  evidenceRefs: string[];
  applyGates: ProviderSwitchApplyGates;
  supportSafe: boolean;
  selectedByAdmin: boolean;
  bootstrapSuggestionOnly: boolean;
  choiceModel: string;
  providerCandidates: string[];
  lastCheckedAt?: string;
  realityLevel: ProviderRealityLevel;
  evidenceFreshness: EvidenceFreshness;
  safeNextAction: string;
  restartSurvivalEvidenceRef?: string;
  dryRunEvidenceRef?: string;
  dryRunEvidenceIssuedAt?: string;
  dryRunEvidenceExpiresAt?: string;
  secretRefs: string[];
}

export interface IdentityProviderReadinessCard {
  key: string;
  label: string;
  state: CapabilityState;
  summary: string;
  memberImpact: "ready" | "disabled" | "degraded" | "policy-blocked";
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

export interface WeaverModelAlias {
  alias: string;
  provider: string;
  model: string;
  userSelectable: boolean;
}

export interface WeaverMcpGrant {
  serverKey: string;
  tools: string[];
  approvalRequired: boolean;
}

export interface McpServerBinding {
  serverKey: string;
  displayName: string;
  transport: "streamable-http";
  endpointRef: string;
  authRef: string;
  allowedTools: string[];
  allowedCapabilities: string[];
  approvalRequiredForWrites: boolean;
  enabled: boolean;
  readinessState: CapabilityState;
  supportSafe: boolean;
  rawEndpointExposed: boolean;
  rawServerConfigExposed: boolean;
  secretValuesExposed: boolean;
  auditRefs: string[];
  nextActions: string[];
}

export interface WeaverRuntimeProfileChange {
  version: string;
  runtimeProfileHash: string;
  createdAt: string;
  status: "draft" | "active" | "revoked" | "rollback_available";
  summary: string;
}

export interface WeaverDistributionPolicy {
  enabledByDefault: boolean;
  chatProviderKey: string;
  chatReadinessState: CapabilityState;
  chatMigrationConsequences: string[];
  profileRegenerationBlockedReasons: string[];
  modelAliases: WeaverModelAlias[];
  defaultModelAlias: string;
  fallbackModelAliases: string[];
  allowedTools: string[];
  allowedSkills: string[];
  mcpServers: WeaverMcpGrant[];
  deniedTools: string[];
  approvalRequiredFor: string[];
  effectivePolicyPreview: string[];
  runtimeProfileHash: string;
  pendingRuntimeProfileHash?: string;
  revocationState: "not_revoked" | "revocation_pending" | "revoked";
  rollbackProfileHash?: string;
  auditRefs: string[];
  changeHistory: WeaverRuntimeProfileChange[];
}

export interface AuditEvent {
  id: string;
  action: string;
  actor: string;
  createdAt: string;
  summary: string;
}

export type WeaverProjectionCategory =
  | "chat"
  | "model"
  | "tool"
  | "skill"
  | "mcp";

export interface WeaverProjectionItem {
  id: string;
  category: WeaverProjectionCategory;
  label: string;
  state: CapabilityState;
  memberImpact: MemberCapabilityState;
  policyImpact: string;
  readinessSummary: string;
  receiptRefs: string[];
  userSelectable?: boolean;
  defaultSelected?: boolean;
  fallbackOrder?: number;
}

export interface WeaverRuntimeProjection {
  profileVersion: string;
  runtimeProfileHash: string;
  expiresAt: string;
  regeneratedAt?: string;
  supportSafe: boolean;
  providerDiagnosticsRedacted: boolean;
  rawRuntimeInternalsExposed: boolean;
  disabledByDefault: boolean;
  groupChatConsentRequired: boolean;
  sandboxPosture: string;
  pendingRevocationRefs: string[];
  auditReceiptRefs: string[];
  items: WeaverProjectionItem[];
}

export interface SuiteDomainReadiness {
  domain: string;
  label: string;
  adminReadiness: CapabilityState;
  memberState: MemberCapabilityState;
  selectedAdapterPosture: string;
  sourceOfTruthMode: string;
  providerCategoryKeys: string[];
  canonicalObjectKinds: string[];
  capabilityStates: string[];
  supportSafeErrors: string[];
  portabilityNotes: string[];
  auditRefs: string[];
  nextAction: string;
  backendOwnedFacade: boolean;
  providerMappingOwnedByServer: boolean;
  rawProviderConfigExposedToMembers: boolean;
}

export interface GoLiveReadiness {
  state: CapabilityState;
  memberPreviewState: MemberCapabilityState;
  blockers: string[];
  adminActions: string[];
  auditRefs: string[];
  supportSafe: boolean;
  normalMembersMayAccessSetupControls: boolean;
  rawProviderDiagnosticsExposed: boolean;
}

export type MemberCapabilityState =
  | "available"
  | "disabled_by_policy"
  | "not_configured"
  | "degraded"
  | "unavailable"
  | "coming_later"
  | "unsupported";

export interface ProviderSelectionResult {
  category: string;
  providerKey: string;
  choiceModel: string;
  dryRun: boolean;
  evidenceRef?: string;
  dryRunId?: string;
  issuedAt?: string;
  expiresAt?: string;
  restartSurvivalEvidenceRef?: string;
  supportSafe: boolean;
}

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
  auditRefs: string[];
  consequencePreview: {
    preservedCount: number;
    lossyCount: number;
    unsupportedCount: number;
    manualReviewCount: number;
    archiveOnlyCount: number;
    memberImpactCopy: string[];
    rollbackLimits: string[];
    applyBlockers: string[];
  };
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
  auditRefs?: string[];
  consequencePreview?: Partial<ProviderReplacementDryRunReport["consequencePreview"]>;
  lossyMappingReport?: Partial<
    ProviderReplacementDryRunReport["lossyMappingReport"]
  >;
  lifecycleExpectations?: Partial<
    ProviderReplacementDryRunReport["lifecycleExpectations"]
  >;
  portableExportImportContract?: Partial<
    ProviderReplacementDryRunReport["portableExportImportContract"]
  >;
  switchPlan?: Partial<ProviderReplacementDryRunReport["switchPlan"]>;
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
  weaverRuntimeProjection: WeaverRuntimeProjection;
  suiteDomainReadiness: SuiteDomainReadiness[];
  goLiveReadiness: GoLiveReadiness;
  whitelistPolicy: WhitelistPolicy;
  weaverDistributionPolicy: WeaverDistributionPolicy;
  mcpServerBindings: McpServerBinding[];
  auditEvents: AuditEvent[];
}

export interface AdminConsoleConfig {
  apiBaseUrl: string;
  oidcIssuerUrl: string;
  oidcClientId: string;
}

type RuntimeEnv = Partial<
  Record<
    | "VITE_WEAVE_API_BASE_URL"
    | "VITE_WEAVE_OIDC_ISSUER_URL"
    | "VITE_WEAVE_ADMIN_OIDC_CLIENT_ID",
    string
  >
>;

const runtimeEnv: RuntimeEnv =
  (import.meta as ImportMeta & { env?: RuntimeEnv }).env ?? {};

export const adminConsoleConfig: AdminConsoleConfig = {
  apiBaseUrl: (
    runtimeEnv.VITE_WEAVE_API_BASE_URL ?? "https://api.weave.local:44443/api"
  ).replace(/\/$/, ""),
  oidcIssuerUrl:
    runtimeEnv.VITE_WEAVE_OIDC_ISSUER_URL ??
    "https://auth.weave.local:44443/realms/weave",
  oidcClientId:
    runtimeEnv.VITE_WEAVE_ADMIN_OIDC_CLIENT_ID ?? "weave-admin-console",
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
  weaverDistributionPolicy?: ServerWeaverDistributionPolicy;
  identityProviderReadiness?: ServerIdentityProviderReadiness;
  weaverRuntimeProjection?: ServerWeaverRuntimeProjection;
  suiteDomainReadiness?: ServerSuiteDomainReadiness[];
  goLiveReadiness?: ServerGoLiveReadiness;
  secretRefs?: Array<{ ref?: string; providerKey?: string }>;
  mcpServerBindings?: ServerMcpServerBinding[];
}

interface ServerMcpServerBinding {
  serverKey?: string;
  displayName?: string;
  transport?: string;
  endpointRef?: string;
  authRef?: string;
  allowedTools?: string[];
  allowedCapabilities?: string[];
  approvalRequiredForWrites?: boolean;
  enabled?: boolean;
  readinessState?: string;
  supportSafe?: boolean;
  rawEndpointExposed?: boolean;
  rawServerConfigExposed?: boolean;
  secretValuesExposed?: boolean;
  auditRefs?: string[];
  nextActions?: string[];
}

interface ServerWeaverDistributionPolicy {
  enabledByDefault?: boolean;
  chatProviderKey?: string;
  chatReadinessState?: string;
  chatMigrationConsequences?: string[];
  profileRegenerationBlockedReasons?: string[];
  modelAliases?: Array<{
    alias?: string;
    provider?: string;
    model?: string;
    userSelectable?: boolean;
  }>;
  defaultModelAlias?: string;
  fallbackModelAliases?: string[];
  allowedTools?: string[];
  allowedSkills?: string[];
  mcpServers?: Array<{
    serverKey?: string;
    tools?: string[];
    approvalRequired?: boolean;
  }>;
  deniedTools?: string[];
  approvalRequiredFor?: string[];
  effectivePolicyPreview?: string[];
  runtimeProfileHash?: string;
  pendingRuntimeProfileHash?: string;
  revocationState?: string;
  rollbackProfileHash?: string;
  auditRefs?: string[];
  changeHistory?: Array<{
    version?: string;
    runtimeProfileHash?: string;
    createdAt?: string;
    status?: string;
    summary?: string;
  }>;
}

interface ServerWeaverRuntimeProjection {
  profileVersion?: string;
  runtimeProfileHash?: string;
  expiresAt?: string;
  regeneratedAt?: string;
  supportSafe?: boolean;
  providerDiagnosticsRedacted?: boolean;
  rawRuntimeInternalsExposed?: boolean;
  disabledByDefault?: boolean;
  groupChatConsentRequired?: boolean;
  sandboxPosture?: string;
  pendingRevocationRefs?: string[];
  auditReceiptRefs?: string[];
  items?: Array<{
    id?: string;
    category?: string;
    label?: string;
    state?: string;
    memberImpact?: string;
    policyImpact?: string;
    readinessSummary?: string;
    receiptRefs?: string[];
    userSelectable?: boolean;
    defaultSelected?: boolean;
    fallbackOrder?: number;
  }>;
}

interface ServerSuiteDomainReadiness {
  domain?: string;
  label?: string;
  adminReadiness?: string;
  memberState?: string;
  selectedAdapterPosture?: string;
  sourceOfTruthMode?: string;
  providerCategoryKeys?: string[];
  canonicalObjectKinds?: string[];
  capabilityStates?: string[];
  supportSafeErrors?: string[];
  portabilityNotes?: string[];
  auditRefs?: string[];
  nextAction?: string;
  backendOwnedFacade?: boolean;
  providerMappingOwnedByServer?: boolean;
  rawProviderConfigExposedToMembers?: boolean;
}

interface ServerGoLiveReadiness {
  state?: string;
  memberPreviewState?: string;
  blockers?: string[];
  adminActions?: string[];
  auditRefs?: string[];
  supportSafe?: boolean;
  normalMembersMayAccessSetupControls?: boolean;
  rawProviderDiagnosticsExposed?: boolean;
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
  requiredNextAction?: string;
  secretRefStatus?: ProviderCategory["secretRefStatus"];
  policyState?: ProviderCategory["policyState"];
  migrationState?: ProviderCategory["migrationState"];
  evidenceRefs?: string[];
  applyGates?: Partial<ProviderSwitchApplyGates>;
  providerCandidates?: string[];
  selectedProviderKey?: string;
  choiceModel?: string;
  selectedByAdmin?: boolean;
  bootstrapSuggestionOnly?: boolean;
  diagnostics?: Record<string, unknown>;
  realityLevel?: string;
  evidenceFreshness?: string;
  safeNextAction?: string;
  restartSurvivalEvidenceRef?: string;
  dryRunEvidenceRef?: string;
  dryRunEvidenceIssuedAt?: string;
  dryRunEvidenceExpiresAt?: string;
}

interface ServerProviderSelectionResult {
  category?: string;
  providerKey?: string;
  choiceModel?: string;
  dryRun?: boolean;
  evidenceRef?: string;
  dryRunEvidenceRef?: string;
  dryRunId?: string;
  issuedAt?: string;
  dryRunEvidenceIssuedAt?: string;
  expiresAt?: string;
  dryRunEvidenceExpiresAt?: string;
  restartSurvivalEvidenceRef?: string;
  supportSafe?: boolean;
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
      "/admin/control-plane",
    );
    const auditEvents = await this.listAuditEvents().catch(() => []);
    return normalizeControlPlane(controlPlane, auditEvents);
  }

  async updateWhitelistPolicy(
    allowedCapabilities: string[],
    profileKey = "workspace-admin",
  ): Promise<WhitelistPolicy> {
    const response = await this.request<ServerWhitelistPolicy>(
      "/admin/policies/capability-whitelist",
      {
        method: "PATCH",
        body: JSON.stringify({
          profileKey,
          capabilityKeys: allowedCapabilities,
          reason: "Updated through Organization/Admin Console",
        }),
      },
    );
    return normalizeWhitelist(response);
  }

  async updateWeaverDistributionPolicy(
    policy: WeaverDistributionPolicy,
  ): Promise<WeaverDistributionPolicy> {
    const response = await this.request<ServerWeaverDistributionPolicy>(
      "/admin/weaver/distribution-policy",
      {
        method: "PATCH",
        body: JSON.stringify({
          ...policy,
          reason:
            "Updated Weaver distribution policy through Organization/Admin Console",
        }),
      },
    );
    return normalizeWeaverDistributionPolicy(response, policy);
  }

  async revokeRuntimeProfile(
    runtimeProfileHash: string,
    reason = "Revoked through Organization/Admin Console",
  ): Promise<WeaverDistributionPolicy> {
    const response = await this.request<ServerWeaverDistributionPolicy>(
      "/admin/weaver/runtime-profiles/revocations",
      {
        method: "POST",
        body: JSON.stringify({ runtimeProfileHash, reason }),
      },
    );
    return normalizeWeaverDistributionPolicy(response, {
      ...sampleControlPlane.weaverDistributionPolicy,
      runtimeProfileHash,
    });
  }

  async selectProvider(
    category: string,
    providerKey: string,
    choiceModel = "recommended_self_hosted_default",
    dryRun = false,
    dryRunEvidenceRef?: string,
  ): Promise<ProviderSelectionResult> {
    const response = await this.request<ServerProviderSelectionResult>(
      "/admin/providers/selections",
      {
        method: "POST",
        body: JSON.stringify({
          category,
          providerKey,
          choiceModel,
          dryRun,
          secretRef: `secretref://weave/provider/${providerKey}`,
          dryRunEvidenceRef,
          consequenceConfirmation: dryRun
            ? undefined
            : "ADMIN_CONFIRMED_PROVIDER_SWITCH_CONSEQUENCES",
          reason: dryRun
            ? "Dry-run through Organization/Admin Console"
            : "Selected through Organization/Admin Console after fresh dry-run evidence and consequence confirmation",
        }),
      },
    );
    return normalizeProviderSelectionResult(
      response,
      category,
      providerKey,
      choiceModel,
      dryRun,
    );
  }

  async dryRunProviderReplacement(
    category: ProviderCategory,
    targetAdapter: string,
    choiceModel = "external_existing_provider",
  ): Promise<ProviderReplacementDryRunReport> {
    const response = await this.request<ServerProviderReplacementDryRunReport>(
      "/admin/providers/replacements/dry-run",
      {
        method: "POST",
        body: JSON.stringify({
          category: category.key,
          currentAdapter: category.selectedAdapter,
          targetAdapter,
          choiceModel,
          secretRef: `secretref://weave/provider/${targetAdapter}`,
          sourceOfTruth:
            "Admin Console-selected provider category remains Weave source of truth until apply.",
          lossyMappingNotes: [
            "Admin Console requested support-safe preflight; backend redaction owns provider diagnostics.",
          ],
          portableExportImportRequired: true,
          requestedSwitchPlan: {
            plan: "guided-plan-preflight-export-import-cutover-rollback",
            memberFacingStateDuringSwitch: "degraded",
            automationBoundary:
              "v0.1 requires portable export/import evidence; full automated migration remains future work.",
          },
          reason:
            "Evaluate provider replacement before activation through Organization/Admin Console",
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
      "/admin/identity/readiness",
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
    }>("/admin/providers/readiness-tests", {
      method: "POST",
      body: JSON.stringify({ providerKey }),
    });
    return {
      providerKey: response.providerKey ?? providerKey,
      state: normalizeState(response.state ?? response.readiness),
      summary:
        response.readiness ?? "readiness tested through backend control plane",
    };
  }

  async listAuditEvents(): Promise<AuditEvent[]> {
    const events = await this.request<ServerAuditEvent[]>(
      "/admin/audit/events",
    );
    return events.map((event) => ({
      id:
        event.idempotencyKey ??
        `${event.action ?? "audit"}-${event.occurredAt ?? "unknown"}`,
      action: event.action ?? "unknown",
      actor: event.actorRef ?? "unknown-actor",
      createdAt: event.occurredAt ?? "",
      summary: supportSafeSummary(event),
    }));
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const token = this.tokenProvider();
    const headers = new Headers(init.headers);
    headers.set("Accept", "application/json");
    if (init.body) headers.set("Content-Type", "application/json");
    if (token) headers.set("Authorization", `Bearer ${token}`);

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
      id: controlPlane.organizationId ?? "weave-dogfood",
      displayName: controlPlane.organizationName ?? "Weave Dogfood",
      manifestUrl: "/api/v1/organization/manifest",
      authIssuerUrl: adminConsoleConfig.oidcIssuerUrl,
    },
    providerConfigSource:
      controlPlane.providerConfigSource ??
      "admin-control-plane-selected-provider-mappings",
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
    weaverRuntimeProjection: normalizeWeaverRuntimeProjection(
      controlPlane.weaverRuntimeProjection,
    ),
    suiteDomainReadiness: normalizeSuiteDomainReadiness(
      controlPlane.suiteDomainReadiness,
    ),
    goLiveReadiness: normalizeGoLiveReadiness(controlPlane.goLiveReadiness),
    whitelistPolicy: normalizeWhitelist(controlPlane.whitelist),
    weaverDistributionPolicy: normalizeWeaverDistributionPolicy(
      controlPlane.weaverDistributionPolicy,
      sampleWeaverDistributionPolicy,
    ),
    mcpServerBindings: normalizeMcpServerBindings(controlPlane.mcpServerBindings),
    auditEvents,
  };
}

function normalizeMcpServerBindings(
  bindings?: ServerMcpServerBinding[],
): McpServerBinding[] {
  const normalized = (bindings ?? []).map((binding) => ({
    serverKey: binding.serverKey ?? "weave-domain-tools",
    displayName: binding.displayName ?? binding.serverKey ?? "Weave domain tools",
    transport: "streamable-http" as const,
    endpointRef: binding.endpointRef ?? "internal://weave-mcp/streamable-http",
    authRef:
      binding.authRef ??
      "credentialref://weave/mcp/weave-domain-tools/runtime-token",
    allowedTools: binding.allowedTools ?? [],
    allowedCapabilities: binding.allowedCapabilities ?? [],
    approvalRequiredForWrites: binding.approvalRequiredForWrites ?? true,
    enabled: binding.enabled ?? false,
    readinessState: normalizeState(binding.readinessState),
    supportSafe: binding.supportSafe ?? true,
    rawEndpointExposed: binding.rawEndpointExposed ?? false,
    rawServerConfigExposed: binding.rawServerConfigExposed ?? false,
    secretValuesExposed: binding.secretValuesExposed ?? false,
    auditRefs: binding.auditRefs ?? [],
    nextActions:
      binding.nextActions ?? [
        "Enable only after org policy, runtime grants, Streamable HTTP auth, and approvals are configured.",
      ],
  }));
  return normalized.length > 0 ? normalized : sampleMcpServerBindings;
}

function normalizeSuiteDomainReadiness(
  readiness?: ServerSuiteDomainReadiness[],
): SuiteDomainReadiness[] {
  const domains = (readiness ?? []).map((domain) => ({
    domain: domain.domain ?? "suite-domain",
    label: domain.label ?? domain.domain ?? "Suite domain",
    adminReadiness: normalizeState(domain.adminReadiness),
    memberState:
      normalizeMemberCapabilityState(domain.memberState) ??
      memberStableStateFromCapability(normalizeState(domain.adminReadiness)),
    selectedAdapterPosture:
      domain.selectedAdapterPosture ?? "awaiting_admin_selection",
    sourceOfTruthMode:
      domain.sourceOfTruthMode ??
      "backend-owned facade; provider details redacted",
    providerCategoryKeys: domain.providerCategoryKeys ?? [],
    canonicalObjectKinds: domain.canonicalObjectKinds ?? [],
    capabilityStates: domain.capabilityStates ?? [],
    supportSafeErrors: domain.supportSafeErrors ?? [
      "support-safe-errors-required",
    ],
    portabilityNotes: domain.portabilityNotes ?? [],
    auditRefs: domain.auditRefs ?? [],
    nextAction:
      domain.nextAction ??
      "Resolve backend readiness evidence before member go-live.",
    backendOwnedFacade: domain.backendOwnedFacade ?? true,
    providerMappingOwnedByServer: domain.providerMappingOwnedByServer ?? true,
    rawProviderConfigExposedToMembers:
      domain.rawProviderConfigExposedToMembers ?? false,
  }));
  return domains.length > 0 ? domains : sampleSuiteDomainReadiness;
}

function normalizeGoLiveReadiness(
  readiness?: ServerGoLiveReadiness,
): GoLiveReadiness {
  return {
    state: normalizeState(readiness?.state ?? "admin-action-required"),
    memberPreviewState:
      normalizeMemberCapabilityState(readiness?.memberPreviewState) ??
      "degraded",
    blockers: readiness?.blockers ?? ["backend-go-live-readiness-required"],
    adminActions: readiness?.adminActions ?? [
      "Run backend readiness checks before member go-live.",
    ],
    auditRefs: readiness?.auditRefs ?? [],
    supportSafe: readiness?.supportSafe ?? false,
    normalMembersMayAccessSetupControls:
      readiness?.normalMembersMayAccessSetupControls ?? false,
    rawProviderDiagnosticsExposed:
      readiness?.rawProviderDiagnosticsExposed ?? false,
  };
}

function normalizeIdentityProviderReadiness(
  readiness?: ServerIdentityProviderReadiness,
): IdentityProviderReadiness {
  const cards = (readiness?.cards ?? []).map((card) => ({
    key: card.key ?? "identity-readiness-card",
    label: card.label ?? "Identity provider readiness",
    state: normalizeState(card.state),
    summary:
      card.summary ??
      "Identity readiness is provided by the backend control-plane facade.",
    memberImpact: normalizeIdentityMemberImpact(card.memberImpact),
    remediation:
      card.remediation ??
      "Run the backend readiness contract and resolve admin-action-required items.",
    nextActions: card.nextActions ?? [],
    evidenceRefs: card.evidenceRefs ?? [],
  }));
  const versionSkewCards = [
    {
      key: "identity-readiness-contract-missing",
      label: "Identity readiness contract missing",
      state: "admin-action-required" as CapabilityState,
      summary:
        "The backend did not return identity readiness details; Admin Console fails closed during version skew.",
      memberImpact: "degraded" as const,
      remediation:
        "Upgrade or restart the backend control-plane facade, then run the identity readiness check again.",
      nextActions: [
        "Verify GET /api/admin/identity/readiness on the backend",
        "Keep member provider setup blocked until readiness evidence exists",
      ],
      evidenceRefs: ["version-skew-fail-closed"],
    },
  ];
  return {
    contractVersion:
      readiness?.contractVersion ?? "identity-provider-readiness-v1",
    category: readiness?.category ?? "idm-rbac",
    providerKey: readiness?.providerKey ?? "awaiting_admin_selection",
    overallState: normalizeState(
      readiness?.overallState ?? "admin-action-required",
    ),
    supportSafe: readiness?.supportSafe ?? false,
    providerDiagnosticsRedacted:
      readiness?.providerDiagnosticsRedacted ?? false,
    backendOwnedFacade: readiness?.backendOwnedFacade ?? true,
    memberClientMayConfigureIdentityProvider:
      readiness?.memberClientMayConfigureIdentityProvider ?? false,
    optionalForMemberFlows: readiness?.optionalForMemberFlows ?? true,
    stableStates: (
      readiness?.stableStates ?? [
        "ready",
        "degraded",
        "policy-blocked",
        "admin-action-required",
        "disabled",
      ]
    ).map(normalizeState),
    cards: cards.length > 0 ? cards : versionSkewCards,
    nextActions: readiness?.nextActions ?? [
      "Treat missing identity readiness as admin-action-required and fail closed.",
    ],
  };
}

function normalizeCategory(
  category: ServerProviderCategory,
  selections: Array<{ category?: string; providerKey?: string }>,
  secretRefs: Array<{ ref?: string; providerKey?: string }>,
  generatedAt?: string,
): ProviderCategory {
  const key = category.category ?? "unknown";
  const selectedAdapter =
    category.selectedProviderKey ??
    selections.find((selection) => selection.category === key)?.providerKey ??
    "awaiting_admin_selection";
  const state = normalizeState(category.readiness);
  return {
    key,
    label: category.label ?? key,
    selectedAdapter,
    state,
    summary:
      category.memberImpact ?? "Backend control-plane status is support-safe.",
    memberImpact:
      normalizeMemberCapabilityState(category.memberImpact) ??
      memberStableStateFromCapability(state),
    requiredNextAction:
      category.requiredNextAction ??
      "Review backend readiness evidence before exposing this domain.",
    secretRefStatus: category.secretRefStatus ?? "sample_only",
    policyState: category.policyState ?? "review_required",
    migrationState: category.migrationState ?? "dry_run_required",
    evidenceRefs: category.evidenceRefs ?? [],
    applyGates: normalizeApplyGates(category.applyGates),
    supportSafe:
      category.diagnostics?.secretsReturned === false &&
      category.diagnostics?.rawProviderErrorsReturned === false,
    selectedByAdmin: category.selectedByAdmin ?? false,
    bootstrapSuggestionOnly: category.bootstrapSuggestionOnly ?? true,
    choiceModel: category.choiceModel ?? "not_selected",
    providerCandidates: category.providerCandidates ?? [],
    lastCheckedAt: generatedAt,
    realityLevel: normalizeRealityLevel(category.realityLevel),
    evidenceFreshness: normalizeEvidenceFreshness(
      category.evidenceFreshness,
      category.dryRunEvidenceExpiresAt,
    ),
    safeNextAction:
      category.safeNextAction ??
      category.requiredNextAction ??
      "Review backend readiness evidence before exposing this domain.",
    restartSurvivalEvidenceRef: category.restartSurvivalEvidenceRef,
    dryRunEvidenceRef: category.dryRunEvidenceRef,
    dryRunEvidenceIssuedAt: category.dryRunEvidenceIssuedAt,
    dryRunEvidenceExpiresAt: category.dryRunEvidenceExpiresAt,
    secretRefs: secretRefs
      .filter((secretRef) => secretRef.providerKey === selectedAdapter)
      .map((secretRef) => secretRef.ref ?? "")
      .filter(Boolean),
  };
}

function normalizeWeaverRuntimeProjection(
  projection?: ServerWeaverRuntimeProjection,
): WeaverRuntimeProjection {
  const items = (projection?.items ?? [])
    .map((item): WeaverProjectionItem => ({
      id: item.id ?? `${item.category ?? "tool"}-projection-item`,
      category: normalizeWeaverProjectionCategory(item.category),
      label: item.label ?? "Governed Weaver projection",
      state: normalizeState(item.state),
      memberImpact:
        normalizeMemberCapabilityState(item.memberImpact) ?? "degraded",
      policyImpact:
        item.policyImpact ??
        "Effective policy must be simulated before RuntimeProfile regeneration.",
      readinessSummary:
        item.readinessSummary ??
        "Backend readiness evidence is required before this projection is exposed.",
      receiptRefs: item.receiptRefs ?? [],
      userSelectable: item.userSelectable,
      defaultSelected: item.defaultSelected,
      fallbackOrder: item.fallbackOrder,
    }))
    .filter((item) => isSupportSafeProjectionLabel(item.label));
  return {
    profileVersion: projection?.profileVersion ?? "weaver-runtime-profile-v1",
    runtimeProfileHash:
      projection?.runtimeProfileHash ?? "profile-hash-pending-backend-signature",
    expiresAt: projection?.expiresAt ?? "backend-expiry-required-before-apply",
    regeneratedAt: projection?.regeneratedAt,
    supportSafe: projection?.supportSafe ?? false,
    providerDiagnosticsRedacted: projection?.providerDiagnosticsRedacted ?? false,
    rawRuntimeInternalsExposed: projection?.rawRuntimeInternalsExposed ?? false,
    disabledByDefault: projection?.disabledByDefault ?? true,
    groupChatConsentRequired: projection?.groupChatConsentRequired ?? true,
    sandboxPosture:
      projection?.sandboxPosture ??
      "sandbox-readiness-recorded-runtime-execution-disabled",
    pendingRevocationRefs: projection?.pendingRevocationRefs ?? [
      "runtime-profile-revocation-check-required",
    ],
    auditReceiptRefs: projection?.auditReceiptRefs ?? [
      "runtime-profile-audit-receipt-required",
    ],
    items: items.length > 0 ? items : sampleWeaverProjectionItems,
  };
}

function normalizeWeaverProjectionCategory(
  value?: string,
): WeaverProjectionCategory {
  switch (value) {
    case "chat":
    case "model":
    case "tool":
    case "skill":
    case "mcp":
      return value;
    default:
      return "tool";
  }
}

function isSupportSafeProjectionLabel(label: string): boolean {
  return !/(secret|token|bearer|refresh|password|openclaw\.json|credential=)/i.test(
    label,
  );
}

function normalizeProviderSelectionResult(
  response: ServerProviderSelectionResult,
  category: string,
  providerKey: string,
  choiceModel: string,
  dryRun: boolean,
): ProviderSelectionResult {
  return {
    category: response.category ?? category,
    providerKey: response.providerKey ?? providerKey,
    choiceModel: response.choiceModel ?? choiceModel,
    dryRun: response.dryRun ?? dryRun,
    evidenceRef: response.evidenceRef ?? response.dryRunEvidenceRef,
    dryRunId: response.dryRunId,
    issuedAt: response.issuedAt ?? response.dryRunEvidenceIssuedAt,
    expiresAt: response.expiresAt ?? response.dryRunEvidenceExpiresAt,
    restartSurvivalEvidenceRef: response.restartSurvivalEvidenceRef,
    supportSafe: response.supportSafe ?? false,
  };
}

function normalizeRealityLevel(value?: string): ProviderRealityLevel {
  switch (value) {
    case "adapter_runtime_verified":
    case "live_mutation_guarded":
    case "sample_only":
      return value;
    case "configured_readiness":
    default:
      return "configured_readiness";
  }
}

function normalizeEvidenceFreshness(
  value?: string,
  expiresAt?: string,
): EvidenceFreshness {
  if (
    value === "fresh" ||
    value === "stale" ||
    value === "missing" ||
    value === "sample_only"
  ) {
    return value;
  }
  if (!expiresAt) return "missing";
  const expires = Date.parse(expiresAt);
  if (Number.isNaN(expires)) return "missing";
  return expires > Date.now() ? "fresh" : "stale";
}

function normalizeApplyGates(
  gates?: Partial<ProviderSwitchApplyGates>,
): ProviderSwitchApplyGates {
  return {
    applySupported: gates?.applySupported ?? false,
    preflightPassed: gates?.preflightPassed ?? false,
    sourceReadinessValid: gates?.sourceReadinessValid ?? false,
    targetReadinessValid: gates?.targetReadinessValid ?? false,
    identityMappingComplete: gates?.identityMappingComplete ?? false,
    exportSnapshotExists: gates?.exportSnapshotExists ?? false,
    dryRunSuccessful: gates?.dryRunSuccessful ?? false,
    lossyMappingReportAccepted: gates?.lossyMappingReportAccepted ?? false,
    conflictsResolvedOrWaived: gates?.conflictsResolvedOrWaived ?? false,
    rollbackBoundaryExists: gates?.rollbackBoundaryExists ?? false,
    rbacAllowsMutation: gates?.rbacAllowsMutation ?? false,
    auditSinkAvailable: gates?.auditSinkAvailable ?? false,
    memberImpactPreviewConfirmed: gates?.memberImpactPreviewConfirmed ?? false,
  };
}

function memberStableStateFromCapability(
  state: CapabilityState,
): MemberCapabilityState {
  switch (state) {
    case "ready":
    case "configured":
      return "available";
    case "policy-blocked":
    case "disabled":
      return "disabled_by_policy";
    case "not_configured":
      return "not_configured";
    case "unsupported":
      return "unsupported";
    default:
      return "degraded";
  }
}

const allApplyGatesPassed: ProviderSwitchApplyGates = {
  applySupported: true,
  preflightPassed: true,
  sourceReadinessValid: true,
  targetReadinessValid: true,
  identityMappingComplete: true,
  exportSnapshotExists: true,
  dryRunSuccessful: true,
  lossyMappingReportAccepted: true,
  conflictsResolvedOrWaived: true,
  rollbackBoundaryExists: true,
  rbacAllowsMutation: true,
  auditSinkAvailable: true,
  memberImpactPreviewConfirmed: true,
};

const blockedApplyGates: ProviderSwitchApplyGates = normalizeApplyGates();

const sampleWeaverProjectionItems: WeaverProjectionItem[] = [
  {
    id: "chat-weave-domain-route",
    category: "chat",
    label: "Weave Chat domain route",
    state: "ready",
    memberImpact: "available",
    policyImpact:
      "Provider changes preserve the stable Weave Chat projection while backend routing and migration evidence are reviewed.",
    readinessSummary:
      "Readiness is based on Chat-domain dry-run, member impact preview, and restart survival receipts.",
    receiptRefs: ["receipt://weaver/chat-domain-route/dry-run"],
    defaultSelected: true,
  },
  {
    id: "model-general-assistant",
    category: "model",
    label: "General assistant model alias",
    state: "configured",
    memberImpact: "available",
    policyImpact:
      "Users may select this alias; provider identity and credentials stay behind admin policy.",
    readinessSummary:
      "Default alias is configured with a fallback order and audit receipt.",
    receiptRefs: ["receipt://weaver/models/general-assistant"],
    userSelectable: true,
    defaultSelected: true,
    fallbackOrder: 1,
  },
  {
    id: "tool-chat-summary-read",
    category: "tool",
    label: "Chat summary read tool",
    state: "configured",
    memberImpact: "available",
    policyImpact:
      "Read-only Chat-domain access follows user rights and organization whitelist policy.",
    readinessSummary:
      "Grant-filtered discovery is allowed; writes still require approval receipts.",
    receiptRefs: ["receipt://weaver/tools/chat-summary-read"],
  },
  {
    id: "skill-workspace-triage",
    category: "skill",
    label: "Workspace triage skill package",
    state: "policy-blocked",
    memberImpact: "disabled_by_policy",
    policyImpact:
      "Blocked until owner/admin approves package provenance, version, and group scope.",
    readinessSummary:
      "Preview shows policy impact before distribution; no runtime internals are exposed.",
    receiptRefs: ["receipt://weaver/skills/workspace-triage/review"],
  },
  {
    id: "mcp-approved-knowledge",
    category: "mcp",
    label: "Approved knowledge connector",
    state: "admin-action-required",
    memberImpact: "degraded",
    policyImpact:
      "Connector remains unavailable until approval routing, revocation, and audit receipts pass.",
    readinessSummary:
      "MCP projection is label-only and support-safe; personal connector secrets never appear here.",
    receiptRefs: ["receipt://weaver/mcp/approved-knowledge/preflight"],
  },
];

function sampleDomain(
  key: string,
  label: string,
  selectedAdapter: string,
  state: CapabilityState,
  summary: string,
  candidates: string[],
  overrides: Partial<ProviderCategory> = {},
): ProviderCategory {
  return {
    key,
    label,
    selectedAdapter,
    state,
    summary,
    memberImpact: memberStableStateFromCapability(state),
    requiredNextAction:
      "Review backend readiness evidence before member rollout.",
    secretRefStatus:
      selectedAdapter === "weave-owned" ? "not_required" : "present",
    policyState:
      state === "policy-blocked"
        ? "blocked"
        : state === "disabled"
          ? "disabled"
          : "allowed",
    migrationState: state === "ready" ? "dry_run_passed" : "dry_run_required",
    evidenceRefs: [`${key}-readiness-evidence`],
    applyGates: blockedApplyGates,
    supportSafe: true,
    selectedByAdmin: true,
    bootstrapSuggestionOnly: false,
    choiceModel: "recommended_self_hosted_default",
    providerCandidates: candidates,
    realityLevel: "configured_readiness",
    evidenceFreshness: overrides.dryRunEvidenceExpiresAt
      ? normalizeEvidenceFreshness(undefined, overrides.dryRunEvidenceExpiresAt)
      : "fresh",
    safeNextAction:
      overrides.requiredNextAction ??
      "Review backend readiness evidence before member rollout.",
    restartSurvivalEvidenceRef: `${key}-restart-survival-evidence`,
    dryRunEvidenceRef: `${key}-dry-run-evidence`,
    dryRunEvidenceIssuedAt: "2026-05-31T08:00:00Z",
    dryRunEvidenceExpiresAt: "2026-06-01T08:00:00Z",
    secretRefs:
      selectedAdapter === "weave-owned"
        ? []
        : [`secretref://weave/provider/${selectedAdapter}`],
    ...overrides,
  };
}

const sampleSuiteDomainReadiness: SuiteDomainReadiness[] = [
  {
    domain: "files-docs",
    label: "Files and Documents",
    adminReadiness: "degraded",
    memberState: "degraded",
    selectedAdapterPosture:
      "files=nextcloud-files, documents-collaboration=collabora-wopi",
    sourceOfTruthMode:
      "backend-owned file/document facade with guarded editor sessions",
    providerCategoryKeys: ["files", "documents-collaboration"],
    canonicalObjectKinds: ["drive", "folder", "file", "document-session"],
    capabilityStates: ["files.read", "files.write", "documents.edit"],
    supportSafeErrors: [
      "support-safe-error-codes-only",
      "raw-provider-bodies-redacted",
    ],
    portabilityNotes: [
      "Export manifests required before provider replacement",
      "Credential-bearing editor URLs remain blocked from support views",
    ],
    auditRefs: ["receipt://suite/files-docs/readiness"],
    nextAction:
      "Confirm checksum, permission, and editor-launch evidence before member writes.",
    backendOwnedFacade: true,
    providerMappingOwnedByServer: true,
    rawProviderConfigExposedToMembers: false,
  },
  {
    domain: "boards-tasks",
    label: "Boards and Tasks",
    adminReadiness: "policy-blocked",
    memberState: "disabled_by_policy",
    selectedAdapterPosture: "boards-tasks=openproject-primary",
    sourceOfTruthMode:
      "local workspace writes; provider sync/write promotion gated by contract evidence",
    providerCategoryKeys: ["boards-tasks"],
    canonicalObjectKinds: ["board", "task", "lane", "comment"],
    capabilityStates: ["boards.read", "boards.write", "tasks.complete"],
    supportSafeErrors: [
      "support-safe-error-codes-only",
      "raw-provider-bodies-redacted",
    ],
    portabilityNotes: [
      "Lossy mapping and conflict reports are required before provider-write apply",
    ],
    auditRefs: ["receipt://suite/boards-tasks/readiness"],
    nextAction:
      "Verify keyboard task flows, conflict states, and audit events before writes.",
    backendOwnedFacade: true,
    providerMappingOwnedByServer: true,
    rawProviderConfigExposedToMembers: false,
  },
  {
    domain: "calendar-meetings",
    label: "Calendar",
    adminReadiness: "degraded",
    memberState: "degraded",
    selectedAdapterPosture: "calendar=nextcloud-caldav",
    sourceOfTruthMode:
      "workspace/team/channel calendar facade; private personal calendars blocked",
    providerCategoryKeys: ["calendar"],
    canonicalObjectKinds: ["calendar", "event", "reminder", "conference-link"],
    capabilityStates: ["calendar.read", "calendar.write"],
    supportSafeErrors: [
      "support-safe-error-codes-only",
      "raw-provider-bodies-redacted",
    ],
    portabilityNotes: [
      "Private calendar ingestion and credential profile downloads are out of scope",
    ],
    auditRefs: ["receipt://suite/calendar-meetings/readiness"],
    nextAction:
      "Confirm workspace/team/channel event readiness and private-calendar blockers.",
    backendOwnedFacade: true,
    providerMappingOwnedByServer: true,
    rawProviderConfigExposedToMembers: false,
  },
];

function normalizeRuntimeProfileStatus(
  value?: string,
): WeaverRuntimeProfileChange["status"] {
  switch (value) {
    case "active":
    case "revoked":
    case "rollback_available":
    case "draft":
      return value;
    default:
      return "draft";
  }
}

function normalizeRevocationState(
  value?: string,
): WeaverDistributionPolicy["revocationState"] {
  switch (value) {
    case "revocation_pending":
    case "revoked":
    case "not_revoked":
      return value;
    default:
      return "not_revoked";
  }
}

function normalizeWeaverDistributionPolicy(
  policy: ServerWeaverDistributionPolicy | undefined,
  fallback: WeaverDistributionPolicy,
): WeaverDistributionPolicy {
  const modelAliases = (policy?.modelAliases ?? fallback.modelAliases).map(
    (alias) => ({
      alias: alias.alias ?? "unnamed-alias",
      provider: alias.provider ?? "provider-not-selected",
      model: alias.model ?? "model-not-selected",
      userSelectable: alias.userSelectable ?? false,
    }),
  );
  const defaultAlias =
    policy?.defaultModelAlias ??
    fallback.defaultModelAlias ??
    modelAliases[0]?.alias ??
    "";
  const allowedTools = policy?.allowedTools ?? fallback.allowedTools;
  const allowedSkills = policy?.allowedSkills ?? fallback.allowedSkills;
  const mcpServers = (policy?.mcpServers ?? fallback.mcpServers).map(
    (server) => ({
      serverKey: server.serverKey ?? "unnamed-mcp-server",
      tools: server.tools ?? [],
      approvalRequired: server.approvalRequired ?? true,
    }),
  );
  const computedPreview = [
    `chat.provider=${policy?.chatProviderKey ?? fallback.chatProviderKey}`,
    `models.default=${defaultAlias}`,
    ...allowedTools.map((tool) => `tool.allow=${tool}`),
    ...allowedSkills.map((skill) => `skill.allow=${skill}`),
    ...mcpServers.map(
      (server) =>
        `mcp.allow=${server.serverKey}:${server.tools.join("|") || "no-tools"}${
          server.approvalRequired ? ":approval-required" : ""
        }`,
    ),
  ];
  return {
    enabledByDefault: policy?.enabledByDefault ?? fallback.enabledByDefault,
    chatProviderKey: policy?.chatProviderKey ?? fallback.chatProviderKey,
    chatReadinessState: normalizeState(
      policy?.chatReadinessState ?? fallback.chatReadinessState,
    ),
    chatMigrationConsequences:
      policy?.chatMigrationConsequences ?? fallback.chatMigrationConsequences,
    profileRegenerationBlockedReasons:
      policy?.profileRegenerationBlockedReasons ??
      fallback.profileRegenerationBlockedReasons,
    modelAliases,
    defaultModelAlias: defaultAlias,
    fallbackModelAliases:
      policy?.fallbackModelAliases ?? fallback.fallbackModelAliases,
    allowedTools,
    allowedSkills,
    mcpServers,
    deniedTools: policy?.deniedTools ?? fallback.deniedTools,
    approvalRequiredFor:
      policy?.approvalRequiredFor ?? fallback.approvalRequiredFor,
    effectivePolicyPreview: policy
      ? (policy.effectivePolicyPreview ?? computedPreview)
      : fallback.effectivePolicyPreview,
    runtimeProfileHash:
      policy?.runtimeProfileHash ?? fallback.runtimeProfileHash,
    pendingRuntimeProfileHash:
      policy?.pendingRuntimeProfileHash ?? fallback.pendingRuntimeProfileHash,
    revocationState: normalizeRevocationState(
      policy?.revocationState ?? fallback.revocationState,
    ),
    rollbackProfileHash:
      policy?.rollbackProfileHash ?? fallback.rollbackProfileHash,
    auditRefs: policy?.auditRefs ?? fallback.auditRefs,
    changeHistory: (policy?.changeHistory ?? fallback.changeHistory).map(
      (change) => ({
        version: change.version ?? "vNext",
        runtimeProfileHash: change.runtimeProfileHash ?? "hash-missing",
        createdAt: change.createdAt ?? "",
        status: normalizeRuntimeProfileStatus(change.status),
        summary: change.summary ?? "RuntimeProfile change recorded by backend.",
      }),
    ),
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
      "provider.direct_call",
      "provider.secret_export",
      "provider.unapproved_runtime_execution",
    ],
  };
}

function normalizeProviderReplacementDryRun(
  response: ServerProviderReplacementDryRunReport,
  category: ProviderCategory,
  targetAdapter: string,
): ProviderReplacementDryRunReport {
  const lossyMapping = response.lossyMappingReport ?? {};
  const consequence = response.consequencePreview ?? {};
  const lifecycle = response.lifecycleExpectations ?? {};
  const portability = response.portableExportImportContract ?? {};
  const switchPlan = response.switchPlan ?? {};
  return {
    dryRunId: response.dryRunId ?? `${category.key}-replacement-dry-run`,
    status: response.status ?? "dry_run_ready",
    category: response.category ?? category.key,
    currentAdapter: response.currentAdapter ?? category.selectedAdapter,
    targetAdapter: response.targetAdapter ?? targetAdapter,
    readinessState: normalizeState(response.readinessState),
    migrationDryRunRequired: response.migrationDryRunRequired ?? true,
    memberImpactStates: normalizeMemberImpactStates(
      response.memberImpactStates,
    ),
    supportSafe: response.supportSafe ?? false,
    providerDiagnosticsRedacted: response.providerDiagnosticsRedacted ?? false,
    cutoverGates: response.cutoverGates ?? [],
    auditRefs: response.auditRefs ?? [
      `provider-replacement-dry-run-${category.key}`,
    ],
    consequencePreview: {
      preservedCount: consequence.preservedCount ?? 0,
      lossyCount: consequence.lossyCount ?? 0,
      unsupportedCount: consequence.unsupportedCount ?? 0,
      manualReviewCount: consequence.manualReviewCount ?? 0,
      archiveOnlyCount: consequence.archiveOnlyCount ?? 0,
      memberImpactCopy: consequence.memberImpactCopy ?? [],
      rollbackLimits: consequence.rollbackLimits ?? [],
      applyBlockers: consequence.applyBlockers ?? [],
    },
    lossyMappingReport: {
      canonicalObjects: lossyMapping.canonicalObjects ?? [],
      contractRisks: lossyMapping.contractRisks ?? [],
      adminNotes: lossyMapping.adminNotes ?? [],
      conflicts: lossyMapping.conflicts ?? [],
      replacementRequirement:
        lossyMapping.replacementRequirement ??
        "Backend migration dry-run required before apply.",
    },
    lifecycleExpectations: {
      sourceOfTruthPolicy:
        lifecycle.sourceOfTruthPolicy ??
        "Source of truth is declared per provider-backed category/object by the backend dry-run; Weave preserves provider-neutral member capability state only.",
      exportExpectation:
        lifecycle.exportExpectation ??
        "Export expectations are evaluated by backend migration contracts.",
      deleteExpectation:
        lifecycle.deleteExpectation ??
        "Delete expectations are evaluated by backend migration contracts.",
      deprovisionExpectation:
        lifecycle.deprovisionExpectation ??
        "Deprovision expectations are evaluated by backend migration contracts.",
      rollbackSupportBoundary:
        lifecycle.rollbackSupportBoundary ??
        "Rollback is bounded by provider export/delete support.",
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
        "v0.1 guarantees a documented portable export/import contract before claiming automated migration.",
      excludedAutomation: portability.excludedAutomation ?? [
        "no full cross-provider automated migration promise in v0.1",
      ],
      evidenceRefs: portability.evidenceRefs ?? [
        "provider-switch-preflight",
        "portable-export-import-contract",
        "rollback-recovery-plan",
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
        ) ?? "degraded",
      recoveryActions: switchPlan.recoveryActions ?? [
        "keep current adapter active until export/import evidence is accepted",
        "block apply when rollback evidence or support-safe audit refs are missing",
      ],
    },
  };
}

function normalizeIdentityMemberImpact(
  value?: string,
): "ready" | "disabled" | "degraded" | "policy-blocked" {
  switch (value) {
    case "ready":
    case "disabled":
    case "degraded":
    case "policy-blocked":
      return value;
    case "policy_blocked":
      return "policy-blocked";
    case "usable":
      return "ready";
    default:
      return "degraded";
  }
}

function normalizeMemberImpactStates(
  values?: string[],
): MemberCapabilityState[] {
  const normalized = (values ?? [])
    .map(normalizeMemberCapabilityState)
    .filter((value): value is MemberCapabilityState => value !== null);
  return normalized.length > 0
    ? Array.from(new Set(normalized))
    : ["available", "disabled_by_policy", "not_configured", "degraded"];
}

function normalizeMemberCapabilityState(
  value?: string,
): MemberCapabilityState | null {
  switch (value) {
    case "available":
    case "not_configured":
    case "degraded":
    case "unavailable":
    case "coming_later":
      return value;
    case "disabled_by_policy":
    case "policy-blocked":
    case "policy_blocked":
    case "disabled":
      return "disabled_by_policy";
    case "ready":
    case "usable":
      return "available";
    case "admin-action-required":
    case "admin_action_required":
    case "misconfigured":
      return "degraded";
    case "unsupported":
      return "unsupported";
    default:
      return null;
  }
}

function normalizeState(value?: string): CapabilityState {
  switch (value) {
    case "ready":
    case "disabled":
    case "degraded":
    case "misconfigured":
    case "unsupported":
    case "not_configured":
    case "configured":
      return value;
    case "policy_blocked":
    case "policy-blocked":
      return "policy-blocked";
    case "admin_action_required":
    case "admin-action-required":
      return "admin-action-required";
    default:
      return "degraded";
  }
}

function supportSafeSummary(event: ServerAuditEvent): string {
  const payload = event.payload ?? {};
  const providerKey =
    typeof payload.providerKey === "string" ? payload.providerKey : undefined;
  const category =
    typeof payload.category === "string" ? payload.category : undefined;
  const target =
    providerKey ?? category ?? event.sourceRef ?? "admin control plane";
  return `${event.action ?? "audit"} for ${target}; payload is redacted and support-safe.`;
}

export const sampleMcpServerBindings: McpServerBinding[] = [
  {
    serverKey: "weave-domain-tools",
    displayName: "Weave governed domain tools",
    transport: "streamable-http",
    endpointRef: "internal://weave-mcp/streamable-http",
    authRef: "credentialref://weave/mcp/weave-domain-tools/runtime-token",
    allowedTools: [
      "admin.get_readiness",
      "weaver.get_runtime_profile_projection",
      "calendar.search_events",
      "boards.comment",
    ],
    allowedCapabilities: [
      "weaver.admin_readiness_read",
      "weaver.runtime_profile_read",
      "weaver.calendar_read",
      "weaver.boards_write",
    ],
    approvalRequiredForWrites: true,
    enabled: false,
    readinessState: "disabled",
    supportSafe: true,
    rawEndpointExposed: false,
    rawServerConfigExposed: false,
    secretValuesExposed: false,
    auditRefs: ["audit://weaver/mcp/weave-domain-tools/binding-preview"],
    nextActions: [
      "Enable only after org policy, runtime grants, Streamable HTTP auth, and approval receipts are configured.",
    ],
  },
];

const sampleWeaverDistributionPolicy: WeaverDistributionPolicy = {
  enabledByDefault: false,
  chatProviderKey: "synapse-homeserver",
  chatReadinessState: "ready",
  chatMigrationConsequences: [
    "Chat provider changes regenerate RuntimeProfile vNext but preserve channels.weave-chat.",
    "Backend migration dry-run must confirm room, membership, history, attachment, and mention mapping before apply.",
    "CredentialRefs rotate through the Credential Broker; raw channel tokens never enter the profile.",
  ],
  profileRegenerationBlockedReasons: [
    "member opt-in remains disabled",
    "admin must confirm model/tool/MCP effective policy preview",
  ],
  modelAliases: [
    {
      alias: "general-assistant",
      provider: "weave-approved-openai",
      model: "gpt-4.1-mini",
      userSelectable: true,
    },
    {
      alias: "sovereign-local",
      provider: "weave-local-qwen",
      model: "qwen3.5",
      userSelectable: true,
    },
    {
      alias: "fallback-safe",
      provider: "weave-approved-anthropic",
      model: "claude-3-5-haiku",
      userSelectable: false,
    },
  ],
  defaultModelAlias: "general-assistant",
  fallbackModelAliases: ["fallback-safe", "sovereign-local"],
  allowedTools: [
    "chat.search_messages",
    "files.search",
    "calendar.search_events",
    "notifications.create_action_request",
  ],
  allowedSkills: ["weave-user-help", "weave-meeting-summary"],
  mcpServers: [
    {
      serverKey: "weave-facade-mcp",
      tools: ["chat.search_messages", "files.search", "calendar.search_events"],
      approvalRequired: false,
    },
    {
      serverKey: "weave-action-request-mcp",
      tools: ["notifications.create_action_request"],
      approvalRequired: true,
    },
  ],
  deniedTools: ["exec", "gateway.config.patch", "provider.secret_export"],
  approvalRequiredFor: [
    "external-send",
    "provider-switch",
    "destructive-action",
    "shared-space-participation",
  ],
  effectivePolicyPreview: [
    "channel=channels.weave-chat via chat.provider=synapse-homeserver",
    "model.default=general-assistant",
    "model.fallback=fallback-safe -> sovereign-local",
    "tool.allow=chat.search_messages",
    "tool.allow=files.search",
    "tool.allow=calendar.search_events",
    "tool.allow=notifications.create_action_request:approval-required",
    "skill.allow=weave-user-help",
    "skill.allow=weave-meeting-summary",
    "mcp.allow=weave-facade-mcp",
    "mcp.allow=weave-action-request-mcp:approval-required",
    "tool.deny=exec,gateway.config.patch,provider.secret_export",
  ],
  runtimeProfileHash: "wrp_2026_05_31_active_hash",
  pendingRuntimeProfileHash: "wrp_2026_05_31_vnext_hash",
  revocationState: "not_revoked",
  rollbackProfileHash: "wrp_2026_05_30_previous_hash",
  auditRefs: [
    "audit://weaver/profile/wrp_2026_05_31_active_hash",
    "audit://weaver/policy-preview/2026-05-31",
  ],
  changeHistory: [
    {
      version: "v3",
      runtimeProfileHash: "wrp_2026_05_31_active_hash",
      createdAt: "2026-05-31T08:00:00Z",
      status: "active",
      summary:
        "Active profile preserves channels.weave-chat and applies admin-approved model/tool/MCP policy.",
    },
    {
      version: "vNext",
      runtimeProfileHash: "wrp_2026_05_31_vnext_hash",
      createdAt: "2026-05-31T09:00:00Z",
      status: "draft",
      summary:
        "Draft regeneration waits for chat migration readiness and effective policy confirmation.",
    },
  ],
};

export const sampleControlPlane: ControlPlaneResponse = {
  organization: {
    id: "weave-dogfood",
    displayName: "Weave Dogfood",
    manifestUrl: "/api/organization/manifest",
    authIssuerUrl: "https://auth.weave.local/realms/weave",
  },
  providerConfigSource: "admin-control-plane-selected-provider-mappings",
  bootstrapDefaultsAreSuggestionsOnly: true,
  providerCategories: [
    sampleDomain(
      "identity",
      "Identity",
      "keycloak-realm",
      "ready",
      "Identity and auth are backend-owned; members see only sign-in availability.",
      [
        "keycloak-realm",
        "entra-id",
        "authentik",
        "auth0",
        "generic-oidc",
        "generic-saml",
        "scim-ldap",
      ],
      {
        requiredNextAction:
          "Keep identity mapping evidence current before any provider switch.",
        evidenceRefs: ["identity-realm-dry-run", "effective-policy-simulation"],
        applyGates: allApplyGatesPassed,
        lastCheckedAt: "2026-05-24T18:00:00Z",
        secretRefs: ["secretref://weave/provider/keycloak-realm/client-secret"],
      },
    ),
    sampleDomain(
      "people",
      "People",
      "weave-owned",
      "ready",
      "Profiles, contact methods, avatars, org units, and external contacts are Weave-owned and not keyed by email.",
      ["weave-owned", "scim-directory", "google-directory"],
    ),
    sampleDomain(
      "spaces",
      "Spaces",
      "weave-owned",
      "ready",
      "Spaces are the cross-domain work context with bindings to chat, files, calendar, boards, calls, and decisions.",
      ["weave-owned"],
    ),
    sampleDomain(
      "chat",
      "Chat",
      "synapse-homeserver",
      "ready",
      "Conversations, messages, threads, reactions, mentions, and read state are shown through Weave facades.",
      ["synapse-homeserver", "slack", "microsoft-teams"],
    ),
    sampleDomain(
      "files",
      "Files",
      "nextcloud-files",
      "degraded",
      "Drives, folders, files, versions, permissions, checksums, shares, locks, and trash are provider-neutral.",
      ["nextcloud-files", "sharepoint", "s3-compatible", "smb"],
      {
        requiredNextAction:
          "Confirm checksum and permission-loss evidence before exposing file writes.",
      },
    ),
    sampleDomain(
      "documents",
      "Documents",
      "collabora-wopi",
      "degraded",
      "Document editing is separate from storage; launch contracts avoid raw storage credentials.",
      ["collabora-wopi", "onlyoffice-wopi", "guarded-unavailable"],
      {
        requiredNextAction:
          "Validate WOPI launch and co-authoring state before enabling editors.",
      },
    ),
    sampleDomain(
      "calendar",
      "Calendar",
      "nextcloud-caldav",
      "degraded",
      "Calendars, events, recurrence, private handling, reminders, resources, and conference links are normalized.",
      ["nextcloud-caldav", "microsoft-graph-calendar", "generic-caldav"],
    ),
    sampleDomain(
      "boards",
      "Boards",
      "openproject-primary",
      "policy-blocked",
      "Boards, lists, tasks, workflow metadata, dependencies, comments, labels, milestones, and sprints stay canonical.",
      [
        "openproject-primary",
        "placeholder-boards",
        "microsoft-planner",
        "jira",
        "vikunja",
        "nextcloud-deck",
      ],
      {
        migrationState: "blocked",
        requiredNextAction:
          "Accept lossy mapping and conflict reports before unblocking provider writes.",
      },
    ),
    sampleDomain(
      "calls",
      "Calls",
      "livekit",
      "misconfigured",
      "Meetings, rooms, participants, grants, recordings, transcripts, captions, consent, and retention are backend-governed.",
      ["livekit", "microsoft-teams-meetings", "external-meeting-link"],
      {
        requiredNextAction:
          "Repair LiveKit grant readiness and consent evidence before enabling join/start controls.",
      },
    ),
    sampleDomain(
      "decisions",
      "Decisions",
      "weave-owned",
      "ready",
      "Decision records and evidence/source references are Weave-owned rather than provider-primary.",
      ["weave-owned"],
    ),
    sampleDomain(
      "notifications",
      "Notifications",
      "weave-owned",
      "ready",
      "Notifications, action requests, approval requests, digests, and read state are Weave-owned.",
      ["weave-owned", "email-adapter", "push-adapter"],
    ),
    sampleDomain(
      "health",
      "Health",
      "weave-owned",
      "ready",
      "Readiness, risk notes, support bundles, migration runs, and SecretRef posture are support-safe and actionable.",
      ["weave-owned"],
    ),
    sampleDomain(
      "weaver",
      "Weaver",
      "weaver-governed-runtime",
      "disabled",
      "Weaver stays disabled until admin grants, member opt-in, approval policy, audit, and redaction gates pass.",
      ["weaver-governed-runtime"],
      {
        policyState: "disabled",
        migrationState: "not_required",
        requiredNextAction:
          "Approve tool/capability grants and member opt-in before enabling Weaver.",
      },
    ),
  ],
  suiteDomainReadiness: sampleSuiteDomainReadiness,
  goLiveReadiness: {
    state: "admin-action-required",
    memberPreviewState: "degraded",
    blockers: ["files-docs:degraded", "boards-tasks:policy-blocked"],
    adminActions: [
      "Resolve suite readiness blockers before member go-live.",
      "Run effective policy simulation for representative users/groups.",
    ],
    auditRefs: ["receipt://admin-control-plane/go-live-readiness"],
    supportSafe: true,
    normalMembersMayAccessSetupControls: false,
    rawProviderDiagnosticsExposed: false,
  },
  weaverRuntimeProjection: {
    profileVersion: "weaver-runtime-profile-v1",
    runtimeProfileHash: "sha256:profile-projection-sample",
    expiresAt: "2026-06-01T08:00:00Z",
    regeneratedAt: "2026-05-31T08:00:00Z",
    supportSafe: true,
    providerDiagnosticsRedacted: true,
    rawRuntimeInternalsExposed: false,
    disabledByDefault: true,
    groupChatConsentRequired: true,
    sandboxPosture: "sandbox-readiness-recorded-runtime-execution-disabled",
    pendingRevocationRefs: ["receipt://weaver/runtime/revocation-preview"],
    auditReceiptRefs: ["receipt://weaver/runtime/profile-regeneration"],
    items: sampleWeaverProjectionItems,
  },
  identityProviderReadiness: {
    contractVersion: "identity-provider-readiness-v1",
    category: "idm-rbac",
    providerKey: "keycloak-realm",
    overallState: "ready",
    supportSafe: true,
    providerDiagnosticsRedacted: true,
    backendOwnedFacade: true,
    memberClientMayConfigureIdentityProvider: false,
    optionalForMemberFlows: true,
    stableStates: [
      "ready",
      "degraded",
      "policy-blocked",
      "admin-action-required",
      "disabled",
    ],
    cards: [
      {
        key: "realm-import",
        label: "Realm import readiness",
        state: "ready",
        summary:
          "Backend dry-run evidence confirms realm desired-state readiness without exposing realm internals.",
        memberImpact: "ready",
        remediation:
          "Run the realm dry-run again before apply if drift is suspected.",
        nextActions: ["Run /api/admin/identity/realm/dry-run before apply"],
        evidenceRefs: ["identity-realm-dry-run"],
      },
      {
        key: "oidc-client-readiness",
        label: "OIDC client readiness",
        state: "ready",
        summary:
          "OIDC client readiness is summarized by backend contracts; client identifiers are redacted from support views.",
        memberImpact: "ready",
        remediation: "Keep client secrets as SecretRef handles only.",
        nextActions: ["Validate client scopes through backend dry-run output"],
        evidenceRefs: ["identity-client-contract"],
      },
      {
        key: "roles-groups-mapping",
        label: "Roles and groups mapping",
        state: "ready",
        summary:
          "Roles and groups map into canonical Weave capability profiles with deny-by-default fallback.",
        memberImpact: "ready",
        remediation: "Map unknown roles/groups before activation.",
        nextActions: ["Review effective policy simulation"],
        evidenceRefs: ["effective-policy-simulation"],
      },
      {
        key: "login-readiness",
        label: "Login readiness",
        state: "ready",
        summary:
          "Member login is exposed only as product-level availability; provider endpoints stay backend-owned.",
        memberImpact: "ready",
        remediation:
          "Keep member sign-in fail-closed until readiness evidence exists.",
        nextActions: [
          "Verify member clients expose only stable capability states",
        ],
        evidenceRefs: ["member-boundary"],
      },
      {
        key: "policy-readiness",
        label: "Policy readiness",
        state: "ready",
        summary:
          "Capability policy gates identity claims before product access.",
        memberImpact: "ready",
        remediation:
          "Retain deny-by-default and last-admin recovery capabilities.",
        nextActions: ["Review policy simulation before realm apply"],
        evidenceRefs: ["capability-whitelist"],
      },
    ],
    nextActions: [
      "Monitor audit/readiness transitions and keep support bundles redacted.",
    ],
  },
  whitelistPolicy: {
    denyByDefault: true,
    allowedCapabilities: ["chat.read", "files.read"],
    blockedCapabilities: ["provider.direct_call", "provider.secret_export"],
  },
  weaverDistributionPolicy: sampleWeaverDistributionPolicy,
  mcpServerBindings: sampleMcpServerBindings,
  auditEvents: [
    {
      id: "audit-1",
      action: "provider.readiness.tested",
      actor: "operator@weave.local",
      createdAt: "2026-05-24T18:00:00Z",
      summary:
        "Readiness tested for keycloak-realm; result redacted and support-safe.",
    },
  ],
};

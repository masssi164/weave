import type {
  GeneratedAdminAuditEventResponse,
  GeneratedAdminControlPlaneResponse,
  GeneratedCapabilityWhitelistResponse,
  GeneratedMemberInvitationRequest,
  GeneratedMemberInvitationResponse,
  GeneratedPlatformIdentityReadinessResponse,
  GeneratedProviderReadinessTestRequest,
  GeneratedProviderReadinessTestResponse,
} from "./generated/openapi";

export type CapabilityState =
  | "ready"
  | "disabled"
  | "degraded"
  | "policy-blocked"
  | "admin-action-required"
  | "misconfigured"
  | "unsupported"
  | "not_configured"
  | "coming_later"
  | "configured";

export type ProviderRealityLevel =
  | "contract_only"
  | "configured"
  | "live_read"
  | "live_write"
  | "migration_dry_run"
  | "migration_apply_ready"
  | "rollback_ready"
  | "release_ready";

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

export interface PlatformIdentityReadinessCard {
  key: string;
  label: string;
  state: CapabilityState;
  summary: string;
  memberImpact: "ready" | "disabled" | "degraded" | "policy-blocked";
  remediation: string;
  nextActions: string[];
  evidenceRefs: string[];
  diagnostics?: Record<string, unknown>;
}

export interface PlatformIdentityReadiness {
  contractVersion: string;
  platformAuthority: string;
  overallState: CapabilityState;
  supportSafe: boolean;
  diagnosticsRedacted: boolean;
  backendOwnedFacade: boolean;
  memberClientMayConfigurePlatformSecurity: boolean;
  requiredForMemberFlows: boolean;
  stableStates: CapabilityState[];
  cards: PlatformIdentityReadinessCard[];
  nextActions: string[];
}

export interface WhitelistPolicy {
  denyByDefault: boolean;
  allowedCapabilities: string[];
  blockedCapabilities: string[];
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

export interface AgentRuntimeProjection {
  personRef: string;
  cellRef?: string;
  runtimeProvider?: string;
  entitlementState: "entitled" | "not_entitled" | "revoked";
  entitlementRevision?: string;
  desiredState: string;
  observedState: string;
  runtimeProfileRef?: string;
  workspaceRevision?: string;
  lastWakeAt?: string;
  lastSyncAt?: string;
  conflicts: number;
  capabilityState: CapabilityState;
  auditRef: string;
}

export type AgentRuntimeLifecycleAction =
  | "provision"
  | "start"
  | "stop"
  | "suspend"
  | "reconcile"
  | "revoke"
  | "delete-runtime-state";

export interface AuditEvent {
  id: string;
  action: string;
  actor: string;
  createdAt: string;
  summary: string;
}

export type OrganizationRole = "owner" | "admin" | "member" | "guest";
export type InvitationProvisioningStatus =
  | "pending"
  | "applied"
  | "failed"
  | "expired"
  | "not_requested";

export interface CreateOrganizationInvitationRequest
  extends Omit<GeneratedMemberInvitationRequest, "role"> {
  role: OrganizationRole;
}

export interface OrganizationInvitation
  extends Omit<
    GeneratedMemberInvitationResponse,
    | "invitationHandle"
    | "organizationId"
    | "email"
    | "lifecycleStatus"
    | "provisioningStatus"
    | "requestedRole"
  > {
  invitationHandle: string;
  organizationId: string;
  email: string;
  lifecycleStatus: string;
  provisioningStatus: InvitationProvisioningStatus;
  requestedRole?: OrganizationRole;
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

export interface RcEvidenceGateReadiness {
  key: string;
  label: string;
  state: CapabilityState;
  evidenceFreshness: EvidenceFreshness;
  evidenceRefs: string[];
  nextAction: string;
  blocksReleaseClaim: boolean;
}

export interface ReleaseClaimControl {
  claimState: CapabilityState;
  candidateTag: string;
  pinnedSpecCorpusRef: string;
  releaseNotesSource: string;
  supportBundleRef: string;
  accessibilityEvidenceRef: string;
  unresolvedVetoes: string[];
  gates: RcEvidenceGateReadiness[];
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
  releaseClaimControl: ReleaseClaimControl;
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
  noUnaccountedDataLossReport: {
    supportedCount: number;
    lossyCount: number;
    unsupportedCount: number;
    manualReviewCount: number;
    archiveOnlyCount: number;
    vendorLockedCount: number;
    knownLosses: string[];
    unsupportedData: string[];
    rollbackLimits: string[];
    releaseClaimBoundaries: string[];
  };
  boundedProof: {
    proofBoundary: string;
    limitedApplyAllowed: boolean;
    productionCutoverAllowed: boolean;
    rollbackRestoreSmokeRequired: boolean;
    requiredEvidenceRefs: string[];
    releaseBlockers: string[];
  };
  crossDomainImpact: Array<{
    domainKey: string;
    canonicalObjectRef: string;
    mappingClass: string;
    consequenceSummary: string;
    evidenceRefs: string[];
    applyBlockers: string[];
  }>;
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
  noUnaccountedDataLossReport?: Partial<
    ProviderReplacementDryRunReport["noUnaccountedDataLossReport"]
  >;
  boundedProof?: Partial<ProviderReplacementDryRunReport["boundedProof"]>;
  crossDomainImpact?: Array<{
    domainKey?: string;
    canonicalObjectRef?: string;
    mappingClass?: string;
    consequenceSummary?: string;
    evidenceRefs?: string[];
    applyBlockers?: string[];
  }>;
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
  platformIdentityReadiness: PlatformIdentityReadiness;
  suiteDomainReadiness: SuiteDomainReadiness[];
  goLiveReadiness: GoLiveReadiness;
  whitelistPolicy: WhitelistPolicy;
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
    runtimeEnv.VITE_WEAVE_API_BASE_URL ?? "https://api.weave.test:44443/api"
  ).replace(/\/$/, ""),
  oidcIssuerUrl:
    runtimeEnv.VITE_WEAVE_OIDC_ISSUER_URL ??
    "https://auth.weave.test:44443/realms/weave",
  oidcClientId:
    runtimeEnv.VITE_WEAVE_ADMIN_OIDC_CLIENT_ID ?? "weave-admin-console",
};

interface PlatformBootstrapConfig {
  oidc?: { issuer?: string };
}

/**
 * A packaged console discovers only public coordinates from the same Server
 * process that served its immutable assets. The Vite host-development fallback
 * remains unchanged when that same-origin endpoint is not available.
 */
export async function resolveAdminConsoleConfig(
  fetchImpl: typeof fetch = fetch,
  browserOrigin: string | undefined = globalThis.location?.origin,
): Promise<AdminConsoleConfig> {
  const explicitApiBase = runtimeEnv.VITE_WEAVE_API_BASE_URL?.replace(/\/$/, "");
  const sameOriginApiBase = browserOrigin
    ? new URL("/api", browserOrigin).toString().replace(/\/$/, "")
    : undefined;
  const apiBaseUrl = explicitApiBase ?? sameOriginApiBase ?? adminConsoleConfig.apiBaseUrl;
  if (runtimeEnv.VITE_WEAVE_OIDC_ISSUER_URL) {
    return { ...adminConsoleConfig, apiBaseUrl };
  }

  try {
    const response = await fetchImpl(`${apiBaseUrl}/platform/config`, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) return { ...adminConsoleConfig, apiBaseUrl };
    const platform = (await response.json()) as PlatformBootstrapConfig;
    const issuer = platform.oidc?.issuer?.trim();
    if (!issuer) return { ...adminConsoleConfig, apiBaseUrl };
    return {
      apiBaseUrl,
      oidcIssuerUrl: issuer.replace(/\/$/, ""),
      oidcClientId: adminConsoleConfig.oidcClientId,
    };
  } catch {
    return { ...adminConsoleConfig, apiBaseUrl: explicitApiBase ?? adminConsoleConfig.apiBaseUrl };
  }
}

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
  platformIdentityReadiness?: ServerPlatformIdentityReadiness;
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
  releaseClaimControl?: ServerReleaseClaimControl;
}

interface ServerReleaseClaimControl {
  claimState?: string;
  candidateTag?: string;
  pinnedSpecCorpusRef?: string;
  releaseNotesSource?: string;
  supportBundleRef?: string;
  accessibilityEvidenceRef?: string;
  unresolvedVetoes?: string[];
  gates?: Array<{
    key?: string;
    label?: string;
    state?: string;
    evidenceFreshness?: string;
    evidenceRefs?: string[];
    nextAction?: string;
    blocksReleaseClaim?: boolean;
  }>;
}

interface ServerPlatformIdentityReadiness {
  contractVersion?: string;
  platformAuthority?: string;
  overallState?: string;
  supportSafe?: boolean;
  diagnosticsRedacted?: boolean;
  backendOwnedFacade?: boolean;
  memberClientMayConfigurePlatformSecurity?: boolean;
  requiredForMemberFlows?: boolean;
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
    diagnostics?: Record<string, unknown>;
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
    const controlPlane = await this.request<GeneratedAdminControlPlaneResponse>(
      "/admin/control-plane",
    );
    const auditEvents = await this.listAuditEvents().catch(() => []);
    return normalizeControlPlane(
      controlPlane as ServerControlPlaneResponse,
      auditEvents,
      this.config.oidcIssuerUrl,
    );
  }

  async updateWhitelistPolicy(
    allowedCapabilities: string[],
    profileKey = "workspace-admin",
  ): Promise<WhitelistPolicy> {
    const response = await this.request<GeneratedCapabilityWhitelistResponse>(
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
    return normalizeWhitelist(response as ServerWhitelistPolicy);
  }

  async getAgentRuntime(personRef: string): Promise<AgentRuntimeProjection> {
    return this.request<AgentRuntimeProjection>(
      `/admin/agent-runtimes/${encodeURIComponent(personRef)}`,
    );
  }

  async changeAgentRuntime(
    personRef: string,
    action: AgentRuntimeLifecycleAction,
    idempotencyKey: string,
    options: { reason?: string; entitlementRevision?: string } = {},
  ): Promise<AgentRuntimeProjection> {
    const base = `/admin/agent-runtimes/${encodeURIComponent(personRef)}`;
    const headers = { "Idempotency-Key": idempotencyKey };
    if (action === "revoke" && !options.entitlementRevision) {
      throw new Error("A current entitlement revision is required for revocation.");
    }
    if (action === "delete-runtime-state") {
      return this.request<AgentRuntimeProjection>(`${base}/runtime-state`, {
        method: "DELETE",
        headers,
        body: JSON.stringify({
          reason: options.reason ?? "Deleted through Organization/Admin Console",
          confirmation: "DELETE_RUNTIME_STATE_ONLY",
        }),
      });
    }

    const body =
      action === "stop"
        ? { mode: "graceful" }
        : action === "suspend"
          ? { reason: options.reason ?? "Suspended through Organization/Admin Console" }
          : action === "revoke"
            ? {
                reason: options.reason ?? "Revoked through Organization/Admin Console",
                entitlementRevision: options.entitlementRevision,
              }
            : undefined;
    return this.request<AgentRuntimeProjection>(`${base}/${action}`, {
      method: "POST",
      headers,
      body: body ? JSON.stringify(body) : undefined,
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

  async getPlatformIdentityReadiness(): Promise<PlatformIdentityReadiness> {
    const response = await this.request<GeneratedPlatformIdentityReadinessResponse>(
      "/admin/platform/identity/readiness",
    );
    return normalizePlatformIdentityReadiness(
      response as ServerPlatformIdentityReadiness,
    );
  }

  async testProviderReadiness(
    providerKey: string,
  ): Promise<{ providerKey: string; state: CapabilityState; summary: string }> {
    const request: GeneratedProviderReadinessTestRequest = { providerKey };
    const response = await this.request<GeneratedProviderReadinessTestResponse>(
      "/admin/providers/readiness-tests",
      {
        method: "POST",
        body: JSON.stringify(request),
      },
    );
    return {
      providerKey: response.providerKey ?? providerKey,
      state: normalizeState(response.state ?? response.readiness),
      summary:
        response.readiness ?? "readiness tested through backend control plane",
    };
  }

  async listAuditEvents(): Promise<AuditEvent[]> {
    const events = await this.request<GeneratedAdminAuditEventResponse[]>(
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

  async listOrganizationInvitations(
    organizationId: string,
  ): Promise<OrganizationInvitation[]> {
    return this.request<OrganizationInvitation[]>(
      `/admin/organizations/${encodeURIComponent(organizationId)}/invitations`,
    );
  }

  async createOrganizationInvitation(
    organizationId: string,
    invitation: CreateOrganizationInvitationRequest,
  ): Promise<OrganizationInvitation> {
    return this.request<OrganizationInvitation>(
      `/admin/organizations/${encodeURIComponent(organizationId)}/invitations`,
      {
        method: "POST",
        headers: {
          "Idempotency-Key": invitationIdempotencyKey("create"),
        },
        body: JSON.stringify(invitation),
      },
    );
  }

  async resendOrganizationInvitation(
    organizationId: string,
    invitationHandle: string,
  ): Promise<OrganizationInvitation> {
    return this.request<OrganizationInvitation>(
      `/admin/organizations/${encodeURIComponent(organizationId)}/invitations/${encodeURIComponent(invitationHandle)}/resend`,
      {
        method: "POST",
        headers: {
          "Idempotency-Key": invitationIdempotencyKey("resend"),
        },
      },
    );
  }

  async revokeOrganizationInvitation(
    organizationId: string,
    invitationHandle: string,
  ): Promise<void> {
    await this.request<void>(
      `/admin/organizations/${encodeURIComponent(organizationId)}/invitations/${encodeURIComponent(invitationHandle)}`,
      {
        method: "DELETE",
        headers: {
          "Idempotency-Key": invitationIdempotencyKey("revoke"),
        },
      },
    );
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    if (path !== "/admin" && !path.startsWith("/admin/")) {
      throw new Error("Admin Console requests must stay under the Weave /api/admin boundary");
    }
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
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }
}

function invitationIdempotencyKey(
  action: "create" | "resend" | "revoke",
): string {
  const entropy = new Uint8Array(16);
  globalThis.crypto.getRandomValues(entropy);
  const nonce = Array.from(entropy, (value) =>
    value.toString(16).padStart(2, "0"),
  ).join("");
  return `admin-invitation-${action}-${nonce}`;
}

function normalizeControlPlane(
  controlPlane: ServerControlPlaneResponse,
  auditEvents: AuditEvent[],
  oidcIssuerUrl: string,
): ControlPlaneResponse {
  const selections = controlPlane.selectedProviderMappings ?? [];
  const secretRefs = controlPlane.secretRefs ?? [];
  return {
    organization: {
      id: controlPlane.organizationId ?? "weave-dogfood",
      displayName: controlPlane.organizationName ?? "Weave Dogfood",
      manifestUrl: "/api/organization/manifest",
      authIssuerUrl: oidcIssuerUrl,
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
    platformIdentityReadiness: normalizePlatformIdentityReadiness(
      controlPlane.platformIdentityReadiness,
    ),
    suiteDomainReadiness: normalizeSuiteDomainReadiness(
      controlPlane.suiteDomainReadiness,
    ),
    goLiveReadiness: normalizeGoLiveReadiness(controlPlane.goLiveReadiness),
    whitelistPolicy: normalizeWhitelist(controlPlane.whitelist),
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
    releaseClaimControl: normalizeReleaseClaimControl(
      readiness?.releaseClaimControl,
    ),
  };
}

function normalizeReleaseClaimControl(
  claim?: ServerReleaseClaimControl,
): ReleaseClaimControl {
  const gates = (claim?.gates ?? []).map((gate, index) => ({
    key: gate.key ?? `rc-evidence-gate-${index + 1}`,
    label: gate.label ?? gate.key ?? "RC evidence gate",
    state: normalizeState(gate.state),
    evidenceFreshness: normalizeEvidenceFreshness(gate.evidenceFreshness),
    evidenceRefs: gate.evidenceRefs ?? [],
    nextAction:
      gate.nextAction ??
      "Attach support-safe evidence or mark a release-owner blocker before making an RC claim.",
    blocksReleaseClaim: gate.blocksReleaseClaim ?? true,
  }));
  return {
    claimState: normalizeState(claim?.claimState ?? "admin-action-required"),
    candidateTag: claim?.candidateTag ?? "candidate-not-selected",
    pinnedSpecCorpusRef:
      claim?.pinnedSpecCorpusRef ??
      "specs/weave-specs.lock.json#24c746c674da7d98e5c6abc1f1abac033a8774f2",
    releaseNotesSource:
      claim?.releaseNotesSource ??
      "release notes must be generated from merged PR metadata",
    supportBundleRef:
      claim?.supportBundleRef ?? "support-safe evidence bundle required",
    accessibilityEvidenceRef:
      claim?.accessibilityEvidenceRef ??
      "manual or scripted accessibility evidence required",
    unresolvedVetoes: claim?.unresolvedVetoes ?? ["release-owner-rc-decision-required"],
    gates: gates.length > 0 ? gates : sampleRcEvidenceGates,
  };
}

function normalizePlatformIdentityReadiness(
  readiness?: ServerPlatformIdentityReadiness,
): PlatformIdentityReadiness {
  const cards = (readiness?.cards ?? []).map((card) => ({
    key: card.key ?? "identity-readiness-card",
    label: card.label ?? "Keycloak platform identity readiness",
    state: normalizeState(card.state),
    summary:
      card.summary ??
      "Platform identity readiness is provided by the backend Keycloak control-plane facade.",
    memberImpact: normalizeIdentityMemberImpact(card.memberImpact),
    remediation:
      card.remediation ??
      "Run the platform-identity readiness contract and resolve admin-action-required items.",
    nextActions: card.nextActions ?? [],
    evidenceRefs: card.evidenceRefs ?? [],
    diagnostics: card.diagnostics ?? {},
  }));
  const versionSkewCards = [
    {
      key: "platform-identity-readiness-contract-missing",
      label: "Platform identity readiness contract missing",
      state: "admin-action-required" as CapabilityState,
      summary:
        "The backend did not return Keycloak platform-identity readiness; Admin Console fails closed during version skew.",
      memberImpact: "degraded" as const,
      remediation:
        "Upgrade or restart the backend control-plane facade, then run the platform-identity readiness check again.",
      nextActions: [
        "Verify GET /api/admin/platform/identity/readiness on the backend",
        "Keep member platform-security setup blocked until readiness evidence exists",
      ],
      evidenceRefs: ["version-skew-fail-closed"],
    },
  ];
  return {
    contractVersion:
      readiness?.contractVersion ?? "platform-identity-readiness-v1",
    platformAuthority: readiness?.platformAuthority ?? "keycloak",
    overallState: normalizeState(
      readiness?.overallState ?? "admin-action-required",
    ),
    supportSafe: readiness?.supportSafe ?? false,
    diagnosticsRedacted: readiness?.diagnosticsRedacted ?? false,
    backendOwnedFacade: readiness?.backendOwnedFacade ?? true,
    memberClientMayConfigurePlatformSecurity:
      readiness?.memberClientMayConfigurePlatformSecurity ?? false,
    requiredForMemberFlows: readiness?.requiredForMemberFlows ?? true,
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
      "Treat missing platform-identity readiness as admin-action-required and fail closed.",
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
    case "contract_only":
    case "configured":
    case "live_read":
    case "live_write":
    case "migration_dry_run":
    case "migration_apply_ready":
    case "rollback_ready":
    case "release_ready":
      return value;
    default:
      return "contract_only";
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
    realityLevel: "configured",
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


const sampleRcEvidenceGates: RcEvidenceGateReadiness[] = [
  {
    key: "pinned-spec-corpus",
    label: "Pinned specification corpus",
    state: "ready",
    evidenceFreshness: "fresh",
    evidenceRefs: ["specs/weave-specs.lock.json"],
    nextAction: "Keep the candidate tied to the pinned corpus commit.",
    blocksReleaseClaim: false,
  },
  {
    key: "sprint-18-manual-at-signoff",
    label: "Sprint 18 manual AT signoff (#591)",
    state: "admin-action-required",
    evidenceFreshness: "missing",
    evidenceRefs: [
      "https://github.com/masssi164/weave/issues/591",
      "docs/evidence/accessibility/sprint-18-manual-at-blocker.md",
    ],
    nextAction:
      "Keep public/final release claims blocked until real manual assistive-technology evidence or an accepted issue-linked split exists; Sprint 19 dogfood work may proceed.",
    blocksReleaseClaim: true,
  },
  {
    key: "conformance-gates",
    label: "Conformance and acceptance gates",
    state: "admin-action-required",
    evidenceFreshness: "missing",
    evidenceRefs: ["./gradlew acceptanceContract", "./gradlew releaseEvidenceCheck"],
    nextAction: "Run candidate-head gates and attach sanitized CI evidence.",
    blocksReleaseClaim: true,
  },
  {
    key: "support-safe-bundle",
    label: "Support-safe evidence bundle",
    state: "ready",
    evidenceFreshness: "fresh",
    evidenceRefs: ["support-bundle://admin-health/go-live-redacted-sample"],
    nextAction: "Verify the bundle contains only refs, reason codes, and redacted diagnostics.",
    blocksReleaseClaim: false,
  },
  {
    key: "accessibility-evidence",
    label: "Accessibility evidence",
    state: "degraded",
    evidenceFreshness: "stale",
    evidenceRefs: ["docs/evidence/weaver-security-privacy-accessibility-report.md"],
    nextAction: "Refresh admin apply/recovery and member-preview accessibility evidence for the candidate.",
    blocksReleaseClaim: true,
  },
  {
    key: "release-notes-input",
    label: "Release notes input",
    state: "configured",
    evidenceFreshness: "sample_only",
    evidenceRefs: ["docs/release-notes/unreleased.md"],
    nextAction: "Generate release notes from merged PR metadata before RC tagging.",
    blocksReleaseClaim: true,
  },
];

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
  const noLoss = response.noUnaccountedDataLossReport ?? {};
  const boundedProof = response.boundedProof ?? {};
  const crossDomainImpact = response.crossDomainImpact ?? [];
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
    noUnaccountedDataLossReport: {
      supportedCount: noLoss.supportedCount ?? consequence.preservedCount ?? 0,
      lossyCount: noLoss.lossyCount ?? consequence.lossyCount ?? 0,
      unsupportedCount:
        noLoss.unsupportedCount ?? consequence.unsupportedCount ?? 0,
      manualReviewCount:
        noLoss.manualReviewCount ?? consequence.manualReviewCount ?? 0,
      archiveOnlyCount:
        noLoss.archiveOnlyCount ?? consequence.archiveOnlyCount ?? 0,
      vendorLockedCount: noLoss.vendorLockedCount ?? 0,
      knownLosses: noLoss.knownLosses ?? [],
      unsupportedData: noLoss.unsupportedData ?? [],
      rollbackLimits: noLoss.rollbackLimits ?? consequence.rollbackLimits ?? [],
      releaseClaimBoundaries: noLoss.releaseClaimBoundaries ?? [
        "Provider replacement claims remain bounded by accepted dry-run evidence.",
      ],
    },
    boundedProof: {
      proofBoundary: boundedProof.proofBoundary ?? "dry_run_only",
      limitedApplyAllowed: boundedProof.limitedApplyAllowed ?? false,
      productionCutoverAllowed: boundedProof.productionCutoverAllowed ?? false,
      rollbackRestoreSmokeRequired:
        boundedProof.rollbackRestoreSmokeRequired ?? true,
      requiredEvidenceRefs: boundedProof.requiredEvidenceRefs ?? [],
      releaseBlockers: boundedProof.releaseBlockers ?? [
        "bounded apply/cutover/rollback proof is not available for this dry-run",
      ],
    },
    crossDomainImpact: crossDomainImpact.map((item, index) => ({
      domainKey: item.domainKey ?? category.key,
      canonicalObjectRef:
        item.canonicalObjectRef ??
        `weave:${category.key}:cross-domain-impact-${index + 1}`,
      mappingClass: normalizeMappingClass(item.mappingClass),
      consequenceSummary:
        item.consequenceSummary ??
        "Backend dry-run must classify cross-domain provider impact before apply.",
      evidenceRefs: item.evidenceRefs ?? [],
      applyBlockers: item.applyBlockers ?? [],
    })),
  };
}

function normalizeMappingClass(value?: string): string {
  const normalized = value?.trim();
  return normalized && [
    "portable",
    "lossy",
    "unsupported",
    "manual_review",
    "vendor_locked",
    "archive_only",
  ].includes(normalized)
    ? normalized
    : "manual_review";
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
    case "coming_later":
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
    serverKey: "weave-workload-boundary",
    displayName: "Weave workload MCP boundary",
    transport: "streamable-http",
    endpointRef: "internal://weave-mcp/streamable-http",
    authRef: "oidc://keycloak/per-cell-private-key-jwt",
    allowedTools: [],
    allowedCapabilities: [],
    approvalRequiredForWrites: true,
    enabled: false,
    readinessState: "disabled",
    supportSafe: true,
    rawEndpointExposed: false,
    rawServerConfigExposed: false,
    secretValuesExposed: false,
    auditRefs: ["audit://agent-runtime-control/mcp/workload-boundary"],
    nextActions: [
      "Keep the tool catalog empty until each domain facade has an explicit scoped contract and evidence.",
    ],
  },
];

export const sampleControlPlane: ControlPlaneResponse = {
  organization: {
    id: "weave-dogfood",
    displayName: "Weave Dogfood",
    manifestUrl: "/api/organization/manifest",
    authIssuerUrl: "https://auth.weave.test/realms/weave",
  },
  providerConfigSource: "admin-control-plane-selected-provider-mappings",
  bootstrapDefaultsAreSuggestionsOnly: true,
  providerCategories: [
    sampleDomain(
      "chat",
      "Chat",
      "synapse-homeserver",
      "ready",
      "Conversations, messages, threads, reactions, mentions, and read state are shown through Weave facades.",
      ["synapse-homeserver", "slack", "microsoft-teams"],
      {
        applyGates: allApplyGatesPassed,
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
      "agent-runtime-control",
      "Agent Runtime Control",
      "weaver-openclaw",
      "disabled",
      "Keycloak entitlement and Agent Runtime Control lifecycle gates fail closed until all runtime dependencies are ready.",
      ["weaver-openclaw"],
      {
        policyState: "disabled",
        migrationState: "not_required",
        requiredNextAction:
          "Enable only after Keycloak entitlement, profile signing, workload identity, external state, and lifecycle reconciliation are ready.",
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
    releaseClaimControl: {
      claimState: "admin-action-required",
      candidateTag: "v0.1.0-rc.next",
      pinnedSpecCorpusRef:
        "specs/weave-specs.lock.json#24c746c674da7d98e5c6abc1f1abac033a8774f2",
      releaseNotesSource: "merged PR release-notes labels and generated draft",
      supportBundleRef: "support-bundle://admin-health/go-live-redacted-sample",
      accessibilityEvidenceRef:
        "docs/evidence/accessibility/sprint-18-manual-at-blocker.md#591",
      unresolvedVetoes: [
        "#591-manual-assistive-technology-signoff-open",
        "files-docs readiness degraded",
        "boards/tasks write policy blocked",
      ],
      gates: sampleRcEvidenceGates,
    },
  },
  platformIdentityReadiness: {
    contractVersion: "platform-identity-readiness-v1",
    platformAuthority: "keycloak",
    overallState: "ready",
    supportSafe: true,
    diagnosticsRedacted: true,
    backendOwnedFacade: true,
    memberClientMayConfigurePlatformSecurity: false,
    requiredForMemberFlows: true,
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
        key: "federation-protocol-readiness",
        label: "Federation protocol readiness",
        state: "ready",
        summary:
          "OIDC/SAML and LDAP/AD federation readiness is summarized by backend contracts; client and directory identifiers are redacted from support views.",
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
  mcpServerBindings: sampleMcpServerBindings,
  auditEvents: [
    {
      id: "audit-1",
      action: "provider.readiness.tested",
      actor: "operator@weave.test",
      createdAt: "2026-05-24T18:00:00Z",
      summary:
        "Readiness tested for synapse-homeserver; result redacted and support-safe.",
    },
  ],
};

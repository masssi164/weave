import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  AppBar,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Container,
  CssBaseline,
  Divider,
  FormControl,
  FormControlLabel,
  FormHelperText,
  InputLabel,
  Link,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  TextField,
  Toolbar,
  Typography,
} from "@mui/material";
import {
  AdminControlPlaneApi,
  adminConsoleConfig,
  CapabilityState,
  ControlPlaneResponse,
  ProviderCategory,
  ProviderReplacementDryRunReport,
  ProviderSwitchApplyGates,
  sampleControlPlane,
  WeaverDistributionPolicy,
  WeaverMcpGrant,
  WeaverModelAlias,
  WeaverProjectionCategory,
} from "./api";
import { AdminConsoleLocale, adminCopy } from "./copy";

type ViewerRole = "owner" | "admin" | "operator" | "member";
type MemberStableState =
  | "available"
  | "disabled_by_policy"
  | "not_configured"
  | "degraded"
  | "unavailable"
  | "coming_later";

const stateColor: Record<
  CapabilityState,
  "success" | "default" | "warning" | "error" | "info"
> = {
  ready: "success",
  disabled: "default",
  degraded: "warning",
  "policy-blocked": "info",
  "admin-action-required": "warning",
  misconfigured: "error",
  unsupported: "error",
  not_configured: "default",
  coming_later: "default",
  configured: "info",
};

function readableState(state: string): string {
  return state.replace(/[-_]/g, " ");
}

function memberStableState(state: CapabilityState): MemberStableState {
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
      return "unavailable";
    case "admin-action-required":
    case "degraded":
    case "misconfigured":
    default:
      return "degraded";
  }
}

function setupStage(state: CapabilityState): string {
  switch (state) {
    case "ready":
    case "configured":
      return "Ready for member go-live";
    case "policy-blocked":
    case "disabled":
      return "Disabled by policy";
    case "not_configured":
    case "admin-action-required":
      return "Admin setup required";
    case "unsupported":
      return "Unavailable for this adapter";
    case "degraded":
    case "misconfigured":
    default:
      return "Repair before inviting affected members";
  }
}

function setupNextAction(category: ProviderCategory): string {
  switch (category.state) {
    case "ready":
    case "configured":
      return "Keep monitoring audit evidence and invite members when policy simulation is green.";
    case "policy-blocked":
    case "disabled":
      return "Review deny-by-default policy before exposing this domain to members.";
    case "not_configured":
    case "admin-action-required":
      return "Select and dry-run a provider adapter, then test readiness through the backend.";
    case "unsupported":
      return "Choose a supported adapter or keep the member state unavailable.";
    case "degraded":
    case "misconfigured":
    default:
      return "Run a readiness test, review support-safe diagnostics, and repair before member go-live.";
  }
}

const applyGateLabels: Array<[keyof ProviderSwitchApplyGates, string]> = [
  ["applySupported", "Backend apply support"],
  ["preflightPassed", "Preflight passed"],
  ["sourceReadinessValid", "Source readiness valid"],
  ["targetReadinessValid", "Target readiness valid"],
  ["identityMappingComplete", "Identity mapping complete"],
  ["exportSnapshotExists", "Export snapshot exists"],
  ["dryRunSuccessful", "Dry-run successful"],
  ["lossyMappingReportAccepted", "Lossy report accepted"],
  ["conflictsResolvedOrWaived", "Conflicts resolved or waived"],
  ["rollbackBoundaryExists", "Rollback boundary exists"],
  ["rbacAllowsMutation", "RBAC allows mutation"],
  ["auditSinkAvailable", "Audit sink available"],
  ["memberImpactPreviewConfirmed", "Member impact preview confirmed"],
];

function applyGatesPass(gates: ProviderSwitchApplyGates): boolean {
  return applyGateLabels.every(([key]) => gates[key]);
}

function blockedApplyGateLabels(gates: ProviderSwitchApplyGates): string[] {
  return applyGateLabels
    .filter(([key]) => !gates[key])
    .map(([, label]) => label);
}

function defaultProviderKey(category?: ProviderCategory): string {
  if (!category) return "";
  return category.selectedAdapter === "awaiting_admin_selection"
    ? (category.providerCandidates[0] ?? "")
    : category.selectedAdapter;
}

function linesFromText(value: string): string[] {
  return value
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
}

function formatModelAliases(aliases: WeaverModelAlias[]): string {
  return aliases
    .map(
      (alias) =>
        `${alias.alias}=${alias.provider}/${alias.model}${alias.userSelectable ? " selectable" : " locked"}`,
    )
    .join("\n");
}

function parseModelAliases(text: string): WeaverModelAlias[] {
  return linesFromText(text).map((line) => {
    const [left, ...rightParts] = line.split("=");
    const right = rightParts.join("=");
    const [providerModel, selectableToken] = right.trim().split(/\s+/, 2);
    const [provider, ...modelParts] = providerModel.split("/");
    return {
      alias: left.trim(),
      provider: provider?.trim() || "provider-not-selected",
      model: modelParts.join("/").trim() || "model-not-selected",
      userSelectable: selectableToken !== "locked",
    };
  });
}

function formatMcpServers(servers: WeaverMcpGrant[]): string {
  return servers
    .map(
      (server) =>
        `${server.serverKey}=${server.tools.join(",")}${server.approvalRequired ? " approval-required" : ""}`,
    )
    .join("\n");
}

function parseMcpServers(text: string): WeaverMcpGrant[] {
  return linesFromText(text).map((line) => {
    const [left, ...rightParts] = line.split("=");
    const right = rightParts.join("=");
    const approvalRequired = /approval-required/.test(right);
    const tools = right
      .replace(/approval-required/g, "")
      .split(",")
      .map((tool) => tool.trim())
      .filter(Boolean);
    return { serverKey: left.trim(), tools, approvalRequired };
  });
}

function buildEffectiveWeaverPreview(
  policy: WeaverDistributionPolicy,
): string[] {
  return [
    `channel=channels.matrix via chat.provider=${policy.chatProviderKey}`,
    `chat.readiness=${readableState(policy.chatReadinessState)}`,
    `model.default=${policy.defaultModelAlias}`,
    `model.fallback=${policy.fallbackModelAliases.join(" -> ") || "none"}`,
    ...policy.modelAliases
      .filter((alias) => alias.userSelectable)
      .map((alias) => `model.user-selectable=${alias.alias}`),
    ...policy.allowedTools.map((tool) =>
      policy.approvalRequiredFor.some(
        (approval) => tool.includes("create") || tool.includes("send"),
      )
        ? `tool.allow=${tool}:approval-required`
        : `tool.allow=${tool}`,
    ),
    ...policy.allowedSkills.map((skill) => `skill.allow=${skill}`),
    ...policy.mcpServers.map(
      (server) =>
        `mcp.allow=${server.serverKey}:${server.tools.join("|") || "no-tools"}${server.approvalRequired ? ":approval-required" : ""}`,
    ),
    `tool.deny=${policy.deniedTools.join(",") || "none"}`,
  ];
}

interface AppProps {
  api?: AdminControlPlaneApi;
  viewerRole?: ViewerRole;
  locale?: AdminConsoleLocale;
}

interface ProviderSelectionDryRunEvidence {
  categoryKey: string;
  providerKey: string;
  choiceModel: string;
  completedAt: string;
  evidenceRef?: string;
  expiresAt?: string;
  restartSurvivalEvidenceRef?: string;
  trustedBackendEvidence: boolean;
}

function isEvidenceFresh(
  evidence: ProviderSelectionDryRunEvidence | null,
): boolean {
  if (!evidence?.trustedBackendEvidence || !evidence.evidenceRef) return false;
  if (!evidence.expiresAt) return false;
  const expires = Date.parse(evidence.expiresAt);
  return !Number.isNaN(expires) && expires > Date.now();
}

function dryRunEvidenceFailureLabel(
  evidence: ProviderSelectionDryRunEvidence | null,
): string {
  if (!evidence?.evidenceRef) return "Missing trusted backend dry-run evidence";
  if (!evidence.trustedBackendEvidence) return "Untrusted dry-run evidence";
  if (!evidence.expiresAt) return "Missing dry-run evidence expiration";
  const expires = Date.parse(evidence.expiresAt);
  if (Number.isNaN(expires)) return "Unparseable dry-run evidence expiration";
  if (expires <= Date.now()) return "Stale dry-run evidence";
  return "Fresh current-session dry-run evidence";
}

const weaverProjectionCategoryLabels: Record<WeaverProjectionCategory, string> = {
  chat: "Chat projection",
  model: "Model aliases",
  tool: "Tool distribution",
  skill: "Skill distribution",
  mcp: "MCP connectors",
};

export default function App({
  api = new AdminControlPlaneApi(),
  viewerRole = "owner",
  locale = "en",
}: AppProps) {
  const copy = adminCopy(locale);
  const canConfigure = viewerRole === "owner" || viewerRole === "admin";
  const canInspectReadiness = canConfigure || viewerRole === "operator";
  const [controlPlane, setControlPlane] =
    useState<ControlPlaneResponse>(sampleControlPlane);
  const [loadState, setLoadState] = useState<
    "loading" | "loaded" | "offline-sample"
  >("loading");
  const [error, setError] = useState<string | null>(null);
  const [selectedCategory, setSelectedCategory] = useState(
    sampleControlPlane.providerCategories[0]?.key ?? "",
  );
  const [providerDraft, setProviderDraft] = useState(
    defaultProviderKey(sampleControlPlane.providerCategories[0]),
  );
  const [choiceModelDraft, setChoiceModelDraft] = useState(
    "recommended_self_hosted_default",
  );
  const [policyDraft, setPolicyDraft] = useState(
    sampleControlPlane.whitelistPolicy.allowedCapabilities.join("\n"),
  );
  const [dryRunReport, setDryRunReport] =
    useState<ProviderReplacementDryRunReport | null>(null);
  const [providerSelectionDryRun, setProviderSelectionDryRun] =
    useState<ProviderSelectionDryRunEvidence | null>(null);
  const [consequenceConfirmed, setConsequenceConfirmed] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string>(
    adminCopy(locale).loadingStatus,
  );
  const [weaverPolicyDraft, setWeaverPolicyDraft] = useState(
    sampleControlPlane.weaverDistributionPolicy,
  );
  const [weaverChatProviderDraft, setWeaverChatProviderDraft] = useState(
    sampleControlPlane.weaverDistributionPolicy.chatProviderKey,
  );
  const [weaverModelsDraft, setWeaverModelsDraft] = useState(
    formatModelAliases(
      sampleControlPlane.weaverDistributionPolicy.modelAliases,
    ),
  );
  const [weaverDefaultModelDraft, setWeaverDefaultModelDraft] = useState(
    sampleControlPlane.weaverDistributionPolicy.defaultModelAlias,
  );
  const [weaverFallbackModelsDraft, setWeaverFallbackModelsDraft] = useState(
    sampleControlPlane.weaverDistributionPolicy.fallbackModelAliases.join("\n"),
  );
  const [weaverToolsDraft, setWeaverToolsDraft] = useState(
    sampleControlPlane.weaverDistributionPolicy.allowedTools.join("\n"),
  );
  const [weaverSkillsDraft, setWeaverSkillsDraft] = useState(
    sampleControlPlane.weaverDistributionPolicy.allowedSkills.join("\n"),
  );
  const [weaverMcpDraft, setWeaverMcpDraft] = useState(
    formatMcpServers(sampleControlPlane.weaverDistributionPolicy.mcpServers),
  );
  const [weaverPolicyConfirmed, setWeaverPolicyConfirmed] = useState(false);

  useEffect(() => {
    let alive = true;
    api
      .getControlPlane()
      .then((response) => {
        if (!alive) return;
        const firstCategory = response.providerCategories[0];
        setControlPlane(response);
        setPolicyDraft(response.whitelistPolicy.allowedCapabilities.join("\n"));
        setWeaverPolicyDraft(response.weaverDistributionPolicy);
        setWeaverChatProviderDraft(
          response.weaverDistributionPolicy.chatProviderKey,
        );
        setWeaverModelsDraft(
          formatModelAliases(response.weaverDistributionPolicy.modelAliases),
        );
        setWeaverDefaultModelDraft(
          response.weaverDistributionPolicy.defaultModelAlias,
        );
        setWeaverFallbackModelsDraft(
          response.weaverDistributionPolicy.fallbackModelAliases.join("\n"),
        );
        setWeaverToolsDraft(
          response.weaverDistributionPolicy.allowedTools.join("\n"),
        );
        setWeaverSkillsDraft(
          response.weaverDistributionPolicy.allowedSkills.join("\n"),
        );
        setWeaverMcpDraft(
          formatMcpServers(response.weaverDistributionPolicy.mcpServers),
        );
        setWeaverPolicyConfirmed(false);
        setSelectedCategory(firstCategory?.key ?? "");
        setProviderDraft(defaultProviderKey(firstCategory));
        setChoiceModelDraft(
          firstCategory?.choiceModel === "not_selected"
            ? "recommended_self_hosted_default"
            : (firstCategory?.choiceModel ?? "recommended_self_hosted_default"),
        );
        setLoadState("loaded");
        setStatusMessage(copy.loadedStatus);
      })
      .catch((cause: unknown) => {
        if (!alive) return;
        setLoadState("offline-sample");
        setError(
          cause instanceof Error
            ? cause.message
            : copy.unavailableSampleError,
        );
        setStatusMessage(copy.offlineSampleStatus);
      });
    return () => {
      alive = false;
    };
  }, [api]);

  const selectedCategoryDetails = useMemo(
    () =>
      controlPlane.providerCategories.find(
        (category) => category.key === selectedCategory,
      ) ?? controlPlane.providerCategories[0],
    [controlPlane.providerCategories, selectedCategory],
  );

  const hasMatchingProviderSelectionDryRun =
    selectedCategoryDetails !== undefined &&
    providerSelectionDryRun?.categoryKey === selectedCategoryDetails.key &&
    providerSelectionDryRun.providerKey === providerDraft &&
    providerSelectionDryRun.choiceModel === choiceModelDraft;
  const hasFreshProviderSelectionDryRun =
    hasMatchingProviderSelectionDryRun &&
    isEvidenceFresh(providerSelectionDryRun);
  const evidenceFailureLabel = hasMatchingProviderSelectionDryRun
    ? dryRunEvidenceFailureLabel(providerSelectionDryRun)
    : "Missing current-session dry-run evidence";
  const selectedApplyBackendAllowed =
    canConfigure &&
    selectedCategoryDetails !== undefined &&
    applyGatesPass(selectedCategoryDetails.applyGates);
  const selectedApplyBlockedReasons = [
    ...(selectedCategoryDetails
      ? blockedApplyGateLabels(selectedCategoryDetails.applyGates)
      : applyGateLabels.map(([, label]) => label)),
    ...(hasFreshProviderSelectionDryRun ? [] : [evidenceFailureLabel]),
    ...(consequenceConfirmed ? [] : ["Explicit consequence confirmation"]),
  ];
  const selectedApplyAllowed =
    selectedApplyBackendAllowed &&
    hasFreshProviderSelectionDryRun &&
    consequenceConfirmed;
  const chatCategory = controlPlane.providerCategories.find(
    (category) => category.key === "chat" || category.key === "chat-channels",
  );
  const weaverEffectiveDraft = useMemo(() => {
    const draft: WeaverDistributionPolicy = {
      ...weaverPolicyDraft,
      chatProviderKey: weaverChatProviderDraft,
      chatReadinessState:
        chatCategory?.state ?? weaverPolicyDraft.chatReadinessState,
      modelAliases: parseModelAliases(weaverModelsDraft),
      defaultModelAlias: weaverDefaultModelDraft,
      fallbackModelAliases: linesFromText(weaverFallbackModelsDraft),
      allowedTools: linesFromText(weaverToolsDraft),
      allowedSkills: linesFromText(weaverSkillsDraft),
      mcpServers: parseMcpServers(weaverMcpDraft),
    };
    return {
      ...draft,
      effectivePolicyPreview: buildEffectiveWeaverPreview(draft),
    };
  }, [
    chatCategory?.state,
    weaverChatProviderDraft,
    weaverDefaultModelDraft,
    weaverFallbackModelsDraft,
    weaverMcpDraft,
    weaverModelsDraft,
    weaverPolicyDraft,
    weaverSkillsDraft,
    weaverToolsDraft,
  ]);
  const weaverRegenerationBlockedReasons = [
    ...weaverEffectiveDraft.profileRegenerationBlockedReasons,
    ...(weaverPolicyConfirmed
      ? []
      : ["Effective Weaver policy preview confirmation"]),
  ];
  const weaverProfileRegenerationAllowed =
    canConfigure && weaverRegenerationBlockedReasons.length === 0;

  function resetApplyEvidence() {
    setProviderSelectionDryRun(null);
    setConsequenceConfirmed(false);
  }

  function changeCategory(categoryKey: string) {
    const category = controlPlane.providerCategories.find(
      (candidate) => candidate.key === categoryKey,
    );
    setSelectedCategory(categoryKey);
    setProviderDraft(defaultProviderKey(category));
    setChoiceModelDraft(
      category?.choiceModel === "not_selected"
        ? "recommended_self_hosted_default"
        : (category?.choiceModel ?? "recommended_self_hosted_default"),
    );
    setDryRunReport(null);
    resetApplyEvidence();
  }

  async function savePolicy() {
    if (!canConfigure) return;
    const allowedCapabilities = policyDraft
      .split("\n")
      .map((capability) => capability.trim())
      .filter(Boolean);
    const response = await api.updateWhitelistPolicy(allowedCapabilities);
    setControlPlane((current) => ({ ...current, whitelistPolicy: response }));
    setStatusMessage(
      `Whitelist policy saved with ${allowedCapabilities.length} requested capabilities.`,
    );
  }

  async function saveWeaverDistributionPolicy() {
    if (!canConfigure) return;
    const response =
      await api.updateWeaverDistributionPolicy(weaverEffectiveDraft);
    setControlPlane((current) => ({
      ...current,
      weaverDistributionPolicy: response,
    }));
    setWeaverPolicyDraft(response);
    setStatusMessage(
      `Weaver distribution policy saved for Chat provider ${response.chatProviderKey}; RuntimeProfile regeneration remains ${weaverProfileRegenerationAllowed ? "ready" : "blocked pending backend gates"}.`,
    );
  }

  async function revokeRuntimeProfile() {
    if (!canConfigure) return;
    const response = await api.revokeRuntimeProfile(
      weaverPolicyDraft.runtimeProfileHash,
    );
    setControlPlane((current) => ({
      ...current,
      weaverDistributionPolicy: response,
    }));
    setWeaverPolicyDraft(response);
    setStatusMessage(
      `RuntimeProfile ${weaverPolicyDraft.runtimeProfileHash} revocation requested; audit refs: ${response.auditRefs.join(", ")}.`,
    );
  }

  async function selectProvider(dryRun: boolean) {
    if (!canConfigure || !selectedCategoryDetails || !providerDraft) return;
    const result = await api.selectProvider(
      selectedCategoryDetails.key,
      providerDraft,
      choiceModelDraft,
      dryRun,
      dryRun ? undefined : providerSelectionDryRun?.evidenceRef,
    );
    if (dryRun) {
      setProviderSelectionDryRun({
        categoryKey: selectedCategoryDetails.key,
        providerKey: providerDraft,
        choiceModel: choiceModelDraft,
        completedAt: result.issuedAt ?? new Date().toISOString(),
        evidenceRef: result.evidenceRef,
        expiresAt: result.expiresAt,
        restartSurvivalEvidenceRef: result.restartSurvivalEvidenceRef,
        trustedBackendEvidence: Boolean(
          result.evidenceRef && result.supportSafe,
        ),
      });
      setConsequenceConfirmed(false);
      setStatusMessage(
        result.evidenceRef
          ? `Dry-run validated for ${selectedCategoryDetails.key}: ${providerDraft}. Review consequences and confirm before apply.`
          : `Dry-run response for ${selectedCategoryDetails.key}: ${providerDraft} did not include trusted backend evidence; apply remains blocked.`,
      );
      return;
    }
    const refreshed = await api.getControlPlane();
    setControlPlane(refreshed);
    setStatusMessage(
      `Provider selection applied for ${selectedCategoryDetails.key}: ${providerDraft}. Backend control plane refreshed as source of truth.`,
    );
    resetApplyEvidence();
  }

  async function dryRunReplacement() {
    if (!canConfigure || !selectedCategoryDetails || !providerDraft) return;
    const report = await api.dryRunProviderReplacement(
      selectedCategoryDetails,
      providerDraft,
      choiceModelDraft,
    );
    setDryRunReport(report);
    setStatusMessage(
      `${copy.replacementStatusSuccess} for ${report.category}: ${report.currentAdapter} → ${report.targetAdapter}.`,
    );
  }

  async function testReadiness(providerKey: string) {
    if (!canInspectReadiness || !providerKey) return;
    const result = await api.testProviderReadiness(providerKey);
    setStatusMessage(
      `Readiness test queued for ${result.providerKey}: ${readableState(result.state)}.`,
    );
  }

  return (
    <>
      <CssBaseline />
      <AppBar position="static" color="primary">
        <Toolbar>
          <Stack spacing={0.5} sx={{ py: 1 }}>
            <Typography
              variant="h1"
              component="h1"
              sx={{ fontSize: { xs: "1.35rem", md: "1.7rem" }, fontWeight: 700 }}
            >
              {copy.appTitle}
            </Typography>
            <Typography variant="body2">{copy.productSlogan}</Typography>
          </Stack>
        </Toolbar>
      </AppBar>
      <Container component="main" maxWidth="lg" sx={{ py: 4 }}>
        <Stack spacing={3}>
          <Alert
            severity={
              loadState === "loaded"
                ? "success"
                : loadState === "loading"
                  ? "info"
                  : "warning"
            }
            role="status"
          >
            {statusMessage}
          </Alert>
          {error ? <Alert severity="warning">{error}</Alert> : null}
          {loadState === "offline-sample" ? (
            <Alert severity="warning">{copy.offlineSampleWarning}</Alert>
          ) : null}

          <Card component="section" aria-labelledby="effective-policy-heading">
            <CardContent>
              <Typography
                id="effective-policy-heading"
                variant="h2"
                sx={{ fontSize: "1.35rem", mb: 1 }}
              >
                {copy.effectivePolicyHeading}
              </Typography>
              <Typography>{copy.effectivePolicySummary}</Typography>
              <Stack
                direction={{ xs: "column", md: "row" }}
                spacing={2}
                sx={{ mt: 2 }}
              >
                <Card variant="outlined" sx={{ flex: 1 }}>
                  <CardContent>
                    <Typography variant="h3" sx={{ fontSize: "1.05rem" }}>
                      {copy.ownerAdminRole}
                    </Typography>
                    <Typography>{copy.ownerAdminDescription}</Typography>
                  </CardContent>
                </Card>
                <Card variant="outlined" sx={{ flex: 1 }}>
                  <CardContent>
                    <Typography variant="h3" sx={{ fontSize: "1.05rem" }}>
                      {copy.operatorRole}
                    </Typography>
                    <Typography>{copy.operatorDescription}</Typography>
                  </CardContent>
                </Card>
                <Card variant="outlined" sx={{ flex: 1 }}>
                  <CardContent>
                    <Typography variant="h3" sx={{ fontSize: "1.05rem" }}>
                      {copy.memberRole}
                    </Typography>
                    <Typography>{copy.memberDescription}</Typography>
                  </CardContent>
                </Card>
              </Stack>
            </CardContent>
          </Card>

          {viewerRole !== "member" ? (
            <Card component="section" aria-labelledby="setup-assistant-heading">
              <CardContent>
                <Typography
                  id="setup-assistant-heading"
                  variant="h2"
                  sx={{ fontSize: "1.35rem", mb: 1 }}
                >
                  {copy.setupAssistantHeading}
                </Typography>
                <Alert severity="info" sx={{ mb: 2 }}>
                  {copy.setupAssistantDescription}
                </Alert>
                <List aria-label={copy.setupAssistantStepsLabel}>
                  {controlPlane.providerCategories.map((category) => (
                    <ListItem
                      key={`setup-${category.key}`}
                      alignItems="flex-start"
                    >
                      <ListItemText
                        primary={`${category.label}: ${setupStage(category.state)}`}
                        secondary={setupNextAction(category)}
                      />
                    </ListItem>
                  ))}
                </List>
              </CardContent>
            </Card>
          ) : null}

          <Card component="section" aria-labelledby="member-preview-heading">
            <CardContent>
              <Typography
                id="member-preview-heading"
                variant="h2"
                sx={{ fontSize: "1.35rem", mb: 1 }}
              >
                {copy.memberPreviewHeading}
              </Typography>
              <Alert severity="info" sx={{ mb: 2 }}>
                {copy.memberPreviewDescription}
              </Alert>
              <List aria-label={copy.memberCapabilityStatesLabel}>
                {controlPlane.providerCategories.map((category) => (
                  <ListItem key={`member-${category.key}`}>
                    <ListItemText
                      primary={category.label}
                      secondary={`${copy.memberStateLabel}: ${memberStableState(category.state)}. ${copy.memberStateDescription}`}
                    />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>

          {viewerRole !== "member" ? (
            <>
              <Card component="section" aria-labelledby="oidc-heading">
                <CardContent>
                  {/* Evidence marker: Admin Console calls only Weave backend admin APIs. */}
                  {/* Commercial adapter guard baseline: Microsoft Graph, Slack, and Teams are not direct Admin Console providers. */}
                  <Typography
                    id="oidc-heading"
                    variant="h2"
                    sx={{ fontSize: "1.35rem", mb: 1 }}
                  >
                    {copy.adminSignInHeading}
                  </Typography>
                  <Typography>
                    {copy.adminSignInDescriptionStart}{" "}
                    <strong>{adminConsoleConfig.oidcClientId}</strong>. This
                    {" "}{copy.adminSignInDescriptionEnd}
                  </Typography>
                  <Typography sx={{ mt: 1 }}>
                    {copy.adminSignInIssuerLabel}:{" "}
                    <code>{adminConsoleConfig.oidcIssuerUrl}</code>
                  </Typography>
                  <Button
                    variant="outlined"
                    sx={{ mt: 2 }}
                    href={`${adminConsoleConfig.oidcIssuerUrl}/protocol/openid-connect/auth`}
                  >
                    {copy.adminSignInOpenBrokerButton}
                  </Button>
                </CardContent>
              </Card>

              <Card component="section" aria-labelledby="org-heading">
                <CardContent>
                  <Typography
                    id="org-heading"
                    variant="h2"
                    sx={{ fontSize: "1.35rem", mb: 1 }}
                  >
                    {copy.organizationOverviewHeading}
                  </Typography>
                  <Stack spacing={1}>
                    <Typography>
                      <strong>{controlPlane.organization.displayName}</strong> (
                      {controlPlane.organization.id})
                    </Typography>
                    <Typography>
                      {copy.organizationProviderSourceLabel}:{" "}
                      <code>{controlPlane.providerConfigSource}</code>
                    </Typography>
                    <Typography>
                      {copy.organizationBootstrapDefaultsLabel}:{" "}
                      <strong>
                        {controlPlane.bootstrapDefaultsAreSuggestionsOnly
                          ? copy.yes
                          : copy.no}
                      </strong>
                    </Typography>
                    <Typography>
                      {copy.organizationViewerRoleLabel}:{" "}
                      <strong>{viewerRole}</strong>
                    </Typography>
                    <Typography>
                      {copy.organizationMemberProviderConfigLabel}:{" "}
                      <strong>{copy.no}</strong>
                    </Typography>
                  </Stack>
                </CardContent>
              </Card>

              <Card component="section" aria-labelledby="providers-heading">
                <CardContent>
                  <Typography
                    id="providers-heading"
                    variant="h2"
                    sx={{ fontSize: "1.35rem", mb: 2 }}
                  >
                    {copy.providerCategoriesHeading}
                  </Typography>
                  <Stack
                    direction={{ xs: "column", md: "row" }}
                    spacing={2}
                    sx={{ flexWrap: "wrap" }}
                    useFlexGap
                  >
                    {controlPlane.providerCategories.map((category) => (
                      <Card
                        key={category.key}
                        variant="outlined"
                        sx={{ flex: "1 1 260px" }}
                      >
                        <CardContent>
                          <Typography variant="h3" sx={{ fontSize: "1.05rem" }}>
                            {category.label}
                          </Typography>
                          <Chip
                            sx={{ mt: 1 }}
                            color={stateColor[category.state]}
                            label={`${copy.providerStatusLabel}: ${readableState(category.state)}`}
                            aria-label={`${category.label} status is ${readableState(category.state)}`}
                          />
                          <Typography sx={{ mt: 1 }}>
                            {category.summary}
                          </Typography>
                          <List
                            dense
                            aria-label={`${category.label} control-plane fields`}
                          >
                            <ListItem disableGutters>
                              <ListItemText
                                primary={copy.providerSelectedAdapterLabel}
                                secondary={category.selectedAdapter}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary={copy.providerRealityLevelLabel}
                                secondary={readableState(category.realityLevel)}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary={copy.providerEvidenceFreshnessLabel}
                                secondary={readableState(
                                  category.evidenceFreshness,
                                )}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary={copy.providerMemberImpactLabel}
                                secondary={category.memberImpact}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary={copy.providerRequiredNextActionLabel}
                                secondary={category.requiredNextAction}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary={copy.providerSafeNextActionLabel}
                                secondary={category.safeNextAction}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary={copy.providerSecretRefStatusLabel}
                                secondary={category.secretRefStatus}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary={copy.providerPolicyStateLabel}
                                secondary={category.policyState}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary={copy.providerMigrationStateLabel}
                                secondary={category.migrationState}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary={copy.providerEvidenceRefsLabel}
                                secondary={
                                  category.evidenceRefs.join(", ") ||
                                  copy.providerBackendEvidenceRequired
                                }
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary={copy.providerRestartEvidenceLabel}
                                secondary={
                                  category.restartSurvivalEvidenceRef ??
                                  copy.providerBackendRestartEvidenceRequired
                                }
                              />
                            </ListItem>
                          </List>
                          {canConfigure ? (
                            <Typography variant="body2" sx={{ mt: 1 }}>
                              {copy.providerCandidatesLabel}:{" "}
                              {category.providerCandidates.join(", ")}
                            </Typography>
                          ) : null}
                        </CardContent>
                      </Card>
                    ))}
                  </Stack>
                </CardContent>
              </Card>

              <Card
                component="section"
                aria-labelledby="readiness-dashboard-heading"
              >
                <CardContent>
                  <Typography
                    id="readiness-dashboard-heading"
                    variant="h2"
                    sx={{ fontSize: "1.35rem", mb: 2 }}
                  >
                    {copy.readinessDashboardHeading}
                  </Typography>
                  <Typography sx={{ mb: 2 }}>
                    {copy.readinessDashboardDescription}
                  </Typography>
                  <List aria-label={copy.readinessDashboardLabel}>
                    {controlPlane.providerCategories.map((category) => (
                      <ListItem
                        key={`readiness-${category.key}`}
                        alignItems="flex-start"
                      >
                        <ListItemText
                          primary={`${category.label}: ${readableState(category.state)}`}
                          secondary={`Member preview: ${memberStableState(
                            category.state,
                          )}. Next action: ${setupNextAction(category)}`}
                        />
                      </ListItem>
                    ))}
                  </List>
                </CardContent>
              </Card>

              <Card
                component="section"
                aria-labelledby="beta-readiness-preview-heading"
              >
                <CardContent>
                  <Typography
                    id="beta-readiness-preview-heading"
                    variant="h2"
                    sx={{ fontSize: "1.35rem", mb: 2 }}
                  >
                    {copy.betaReadinessHeading}
                  </Typography>
                  <Typography sx={{ mb: 2 }}>
                    {copy.betaReadinessDescription}
                  </Typography>
                  <List aria-label={copy.betaReadinessChecklistLabel}>
                    <ListItem alignItems="flex-start">
                      <ListItemText
                        primary={`IDM and RBAC posture: ${readableState(controlPlane.identityProviderReadiness.overallState)}`}
                        secondary={`Backend-owned identity facade: ${controlPlane.identityProviderReadiness.backendOwnedFacade ? "yes" : "no"}; member identity-provider setup controls: ${controlPlane.identityProviderReadiness.memberClientMayConfigureIdentityProvider ? "exposed" : "blocked"}.`}
                      />
                    </ListItem>
                    <ListItem alignItems="flex-start">
                      <ListItemText
                        primary={`Provider adapter posture: ${controlPlane.providerCategories.map((category) => `${category.label} ${readableState(category.state)}`).join("; ")}`}
                        secondary="Each provider category includes member impact, policy state, dry-run/apply gates, evidence freshness, and the next safe operator action."
                      />
                    </ListItem>
                    <ListItem alignItems="flex-start">
                      <ListItemText
                        primary={`Weaver availability: ${controlPlane.weaverEligibilityPreview.memberStateWhenEligible}`}
                        secondary={`Explicit policy enabled: ${controlPlane.weaverEligibilityPreview.policyEnabled ? "yes" : "no"}; required groups: ${controlPlane.weaverEligibilityPreview.requiredGroups.join(", ") || "none"}; fail-closed without group: ${controlPlane.weaverEligibilityPreview.memberStateWithoutGroup}.`}
                      />
                    </ListItem>
                    <ListItem alignItems="flex-start">
                      <ListItemText
                        primary={`Evidence posture: ${readableState(controlPlane.goLiveReadiness.state)}`}
                        secondary={`Support-safe go-live evidence: ${controlPlane.goLiveReadiness.supportSafe ? "yes" : "no"}; raw provider diagnostics exposed: ${controlPlane.goLiveReadiness.rawProviderDiagnosticsExposed ? "yes" : "no"}; blockers: ${controlPlane.goLiveReadiness.blockers.join(", ") || "none"}.`}
                      />
                    </ListItem>
                  </List>
                </CardContent>
              </Card>

              {canInspectReadiness ? (
                <Card component="section" aria-labelledby="go-live-heading">
                  <CardContent>
                    <Typography
                      id="go-live-heading"
                      variant="h2"
                      sx={{ fontSize: "1.35rem", mb: 2 }}
                    >
                    {copy.goLiveHeading}
                    </Typography>
                    <Alert
                      severity={
                        controlPlane.goLiveReadiness.state === "ready" &&
                        controlPlane.goLiveReadiness.supportSafe &&
                        !controlPlane.goLiveReadiness
                          .normalMembersMayAccessSetupControls &&
                        !controlPlane.goLiveReadiness.rawProviderDiagnosticsExposed
                          ? "success"
                          : "warning"
                      }
                      sx={{ mb: 2 }}
                    >
                      {copy.goLiveStateLabel}:{" "}
                      {readableState(controlPlane.goLiveReadiness.state)};
                      {copy.goLiveMemberPreviewLabel}:{" "}
                      {controlPlane.goLiveReadiness.memberPreviewState};
                      {copy.goLiveSetupControlsLabel}:{" "}
                      {controlPlane.goLiveReadiness
                        .normalMembersMayAccessSetupControls
                        ? copy.yes
                        : copy.no}
                      ; {copy.goLiveRawDiagnosticsLabel}:{" "}
                      {controlPlane.goLiveReadiness.rawProviderDiagnosticsExposed
                        ? copy.yes
                        : copy.no}
                      .
                    </Alert>
                    <Typography>
                      {copy.goLiveBlockersLabel}:{" "}
                      {controlPlane.goLiveReadiness.blockers.join(", ") ||
                        copy.none}
                    </Typography>
                    <Typography>
                      {copy.goLiveAdminActionsLabel}:{" "}
                      {controlPlane.goLiveReadiness.adminActions.join(" ")}
                    </Typography>
                    <Typography>
                      {copy.goLiveAuditRefsLabel}:{" "}
                      {controlPlane.goLiveReadiness.auditRefs.join(", ") ||
                        "backend audit required"}
                    </Typography>
                    <Divider sx={{ my: 2 }} />
                    <Typography
                      id="rc-claim-control-heading"
                      variant="h3"
                      sx={{ fontSize: "1.1rem", mb: 1 }}
                    >
                      {copy.rcClaimHeading}
                    </Typography>
                    <Alert
                      severity={
                        controlPlane.goLiveReadiness.releaseClaimControl
                          .unresolvedVetoes.length === 0 &&
                        controlPlane.goLiveReadiness.releaseClaimControl.gates.every(
                          (gate) => !gate.blocksReleaseClaim,
                        )
                          ? "success"
                          : "warning"
                      }
                      sx={{ mb: 2 }}
                    >
                      RC claim state: {" "}
                      {readableState(
                        controlPlane.goLiveReadiness.releaseClaimControl
                          .claimState,
                      )}
                      ; candidate: {" "}
                      {controlPlane.goLiveReadiness.releaseClaimControl
                        .candidateTag}
                      . Release claims stay blocked when evidence is missing,
                      stale, sample-only, or a Veto remains unresolved.
                    </Alert>
                    <List aria-label="RC go-live evidence and release claim gates">
                      <ListItem disableGutters>
                        <ListItemText
                          primary="Pinned specification corpus"
                          secondary={
                            controlPlane.goLiveReadiness.releaseClaimControl
                              .pinnedSpecCorpusRef
                          }
                        />
                      </ListItem>
                      <ListItem disableGutters>
                        <ListItemText
                          primary="Release notes source"
                          secondary={
                            controlPlane.goLiveReadiness.releaseClaimControl
                              .releaseNotesSource
                          }
                        />
                      </ListItem>
                      <ListItem disableGutters>
                        <ListItemText
                          primary="Support bundle"
                          secondary={
                            controlPlane.goLiveReadiness.releaseClaimControl
                              .supportBundleRef
                          }
                        />
                      </ListItem>
                      <ListItem disableGutters>
                        <ListItemText
                          primary="Accessibility evidence"
                          secondary={
                            controlPlane.goLiveReadiness.releaseClaimControl
                              .accessibilityEvidenceRef
                          }
                        />
                      </ListItem>
                      <ListItem disableGutters>
                        <ListItemText
                          primary="Unresolved Veto/blockers"
                          secondary={
                            controlPlane.goLiveReadiness.releaseClaimControl
                              .unresolvedVetoes.join(", ") || "none"
                          }
                        />
                      </ListItem>
                      {controlPlane.goLiveReadiness.releaseClaimControl.gates.map(
                        (gate) => (
                          <ListItem key={gate.key} disableGutters>
                            <ListItemText
                              primary={`${gate.label}: ${readableState(gate.state)}`}
                              secondary={`Freshness: ${readableState(gate.evidenceFreshness)}; blocks release claim: ${gate.blocksReleaseClaim ? "yes" : "no"}; evidence: ${gate.evidenceRefs.join(", ") || "backend evidence required"}; next action: ${gate.nextAction}`}
                            />
                          </ListItem>
                        ),
                      )}
                    </List>
                  </CardContent>
                </Card>
              ) : null}

              {canInspectReadiness ? (
                <Card component="section" aria-labelledby="suite-facades-heading">
                  <CardContent>
                    <Typography
                      id="suite-facades-heading"
                      variant="h2"
                      sx={{ fontSize: "1.35rem", mb: 2 }}
                    >
                      {copy.suiteFacadesHeading}
                    </Typography>
                    <Typography sx={{ mb: 2 }}>
                      {copy.suiteFacadesDescription}
                    </Typography>
                    <Stack direction={{ xs: "column", md: "row" }} spacing={2} useFlexGap sx={{ flexWrap: "wrap" }}>
                      {controlPlane.suiteDomainReadiness.map((domain) => (
                        <Card key={domain.domain} variant="outlined" sx={{ flex: "1 1 280px" }}>
                          <CardContent>
                            <Typography variant="h3" sx={{ fontSize: "1.05rem" }}>
                              {domain.label} suite facade
                            </Typography>
                            <Chip
                              sx={{ mt: 1 }}
                              color={stateColor[domain.adminReadiness]}
                              label={`Admin readiness: ${readableState(domain.adminReadiness)}`}
                            />
                            <List dense aria-label={`${domain.label} suite facade evidence`}>
                              <ListItem disableGutters>
                                <ListItemText primary="Member state" secondary={domain.memberState} />
                              </ListItem>
                              <ListItem disableGutters>
                                <ListItemText primary="Selected adapter posture" secondary={domain.selectedAdapterPosture} />
                              </ListItem>
                              <ListItem disableGutters>
                                <ListItemText primary="Source of truth" secondary={domain.sourceOfTruthMode} />
                              </ListItem>
                              <ListItem disableGutters>
                                <ListItemText primary="Canonical objects" secondary={domain.canonicalObjectKinds.join(", ") || "backend contract required"} />
                              </ListItem>
                              <ListItem disableGutters>
                                <ListItemText primary="Portability notes" secondary={domain.portabilityNotes.join("; ") || "none"} />
                              </ListItem>
                              <ListItem disableGutters>
                                <ListItemText primary="Next action" secondary={domain.nextAction} />
                              </ListItem>
                            </List>
                            <Typography variant="body2">
                              Facade owned by backend: {domain.backendOwnedFacade ? "yes" : "no"}; server-owned mapping: {domain.providerMappingOwnedByServer ? "yes" : "no"}; raw member config exposed: {domain.rawProviderConfigExposedToMembers ? "yes" : "no"}.
                            </Typography>
                          </CardContent>
                        </Card>
                      ))}
                    </Stack>
                  </CardContent>
                </Card>
              ) : null}

              {canInspectReadiness ? (
                <Card
                  component="section"
                  aria-labelledby="identity-readiness-heading"
                >
                  <CardContent>
                    <Typography
                      id="identity-readiness-heading"
                      variant="h2"
                      sx={{ fontSize: "1.35rem", mb: 2 }}
                    >
                      {copy.identityReadinessHeading}
                    </Typography>
                    <Alert severity="info" sx={{ mb: 2 }}>
                      {copy.identityReadinessDescription}
                    </Alert>
                    <Stack spacing={1}>
                      <Typography>
                        Contract:{" "}
                        <code>
                          {
                            controlPlane.identityProviderReadiness
                              .contractVersion
                          }
                        </code>
                        ; overall state:{" "}
                        <strong>
                          {readableState(
                            controlPlane.identityProviderReadiness.overallState,
                          )}
                        </strong>
                        ; backend-owned facade:{" "}
                        <strong>
                          {controlPlane.identityProviderReadiness
                            .backendOwnedFacade
                            ? "yes"
                            : "no"}
                        </strong>
                        ; member provider setup:{" "}
                        <strong>
                          {controlPlane.identityProviderReadiness
                            .memberClientMayConfigureIdentityProvider
                            ? "allowed"
                            : "blocked"}
                        </strong>
                        .
                      </Typography>
                      <Typography>
                        Stable states:{" "}
                        {controlPlane.identityProviderReadiness.stableStates
                          .map(readableState)
                          .join(", ")}
                      </Typography>
                    </Stack>
                    <Stack spacing={2} sx={{ mt: 2 }}>
                      {controlPlane.identityProviderReadiness.cards.map(
                        (card) => (
                          <Card key={card.key} variant="outlined">
                            <CardContent>
                              <Stack spacing={1}>
                                <Typography
                                  variant="h3"
                                  sx={{ fontSize: "1.05rem" }}
                                >
                                  {card.label}
                                </Typography>
                                <Chip
                                  color={stateColor[card.state]}
                                  label={`State: ${readableState(card.state)}`}
                                  aria-label={`${card.label} state is ${readableState(card.state)}`}
                                />
                                <Typography>{card.summary}</Typography>
                                <Typography>
                                  Member impact: {card.memberImpact}.
                                </Typography>
                                <Typography>
                                  Remediation: {card.remediation}
                                </Typography>
                                <Typography>
                                  Next actions:{" "}
                                  {card.nextActions.join("; ") ||
                                    "No action reported by backend."}
                                </Typography>
                                <Typography>
                                  Evidence refs:{" "}
                                  {card.evidenceRefs.join(", ") ||
                                    "support-safe backend evidence"}
                                </Typography>
                              </Stack>
                            </CardContent>
                          </Card>
                        ),
                      )}
                    </Stack>
                  </CardContent>
                </Card>
              ) : null}

              <Card
                component="section"
                aria-labelledby="provider-selection-heading"
              >
                <CardContent>
                  <Typography
                    id="provider-selection-heading"
                    variant="h2"
                    sx={{ fontSize: "1.35rem", mb: 2 }}
                  >
                    {copy.providerSelectionHeading}
                  </Typography>
                  <Alert severity="info" sx={{ mb: 2 }}>
                    {copy.providerSelectionDescription}
                  </Alert>
                  <Stack spacing={2}>
                    <FormControl fullWidth>
                      <InputLabel id="provider-category-select-label">
                        {copy.providerCategoryLabel}
                      </InputLabel>
                      <Select
                        labelId="provider-category-select-label"
                        id="provider-category-select"
                        value={selectedCategory}
                        label={copy.providerCategoryLabel}
                        onChange={(event) => changeCategory(event.target.value)}
                      >
                        {controlPlane.providerCategories.map((category) => (
                          <MenuItem key={category.key} value={category.key}>
                            {category.label}
                          </MenuItem>
                        ))}
                      </Select>
                      <FormHelperText>
                        {copy.providerCategoryHelper}
                      </FormHelperText>
                    </FormControl>

                    {selectedCategoryDetails ? (
                      <>
                        <FormControl fullWidth disabled={!canConfigure}>
                          <InputLabel id="provider-candidate-select-label">
                            {copy.selectedProviderAdapterLabel}
                          </InputLabel>
                          <Select
                            labelId="provider-candidate-select-label"
                            id="provider-candidate-select"
                            value={providerDraft}
                            label={copy.selectedProviderAdapterLabel}
                            onChange={(event) => {
                              setProviderDraft(event.target.value);
                              resetApplyEvidence();
                            }}
                          >
                            {selectedCategoryDetails.providerCandidates.map(
                              (candidate) => (
                                <MenuItem key={candidate} value={candidate}>
                                  {candidate}
                                </MenuItem>
                              ),
                            )}
                          </Select>
                        </FormControl>
                        <FormControl fullWidth disabled={!canConfigure}>
                          <InputLabel id="choice-model-select-label">
                            {copy.choiceModelLabel}
                          </InputLabel>
                          <Select
                            labelId="choice-model-select-label"
                            id="choice-model-select"
                            value={choiceModelDraft}
                            label={copy.choiceModelLabel}
                            onChange={(event) => {
                              setChoiceModelDraft(event.target.value);
                              resetApplyEvidence();
                            }}
                          >
                            <MenuItem value="recommended_self_hosted_default">
                              recommended self-hosted default
                            </MenuItem>
                            <MenuItem value="external_existing_provider">
                              external existing provider
                            </MenuItem>
                            <MenuItem value="managed_cloud_provider">
                              managed cloud provider
                            </MenuItem>
                          </Select>
                        </FormControl>
                        {canConfigure ? (
                          <Typography>
                            {copy.secretRefsLabel}:{" "}
                            {selectedCategoryDetails.secretRefs.join(", ") ||
                              `secretref://weave/provider/${providerDraft}`}
                          </Typography>
                        ) : null}
                        <Typography>
                          {copy.providerSecretWarning}
                        </Typography>
                        <Alert
                          severity={
                            selectedApplyAllowed ? "success" : "warning"
                          }
                        >
                          {copy.providerApplyPrefix}{" "}
                          {selectedApplyAllowed ? copy.enabled : copy.blocked}{" "}
                          {copy.providerApplySuffix}
                          {selectedApplyAllowed
                            ? ` ${copy.providerApplyAllGatesPassed}`
                            : ` ${copy.providerApplyMissingGates}: ${selectedApplyBlockedReasons.join(", ")}.`}
                        </Alert>
                        <Alert
                          severity={
                            hasFreshProviderSelectionDryRun
                              ? "success"
                              : "warning"
                          }
                        >
                          {copy.providerDryRunEvidencePrefix}{" "}
                          {hasFreshProviderSelectionDryRun
                            ? copy.providerDryRunFreshTrusted
                            : copy.providerDryRunMissing}
                          .
                          {providerSelectionDryRun
                            ? ` Last dry-run evidence ${providerSelectionDryRun.evidenceRef ?? "untrusted-client-only"} at ${providerSelectionDryRun.completedAt}${providerSelectionDryRun.expiresAt ? `, expires ${providerSelectionDryRun.expiresAt}` : ""}. Restart evidence: ${providerSelectionDryRun.restartSurvivalEvidenceRef ?? "not reported"}.`
                            : ` ${copy.providerDryRunPrompt}`}
                        </Alert>
                        {canConfigure ? (
                          <FormControlLabel
                            control={
                              <Checkbox
                                checked={consequenceConfirmed}
                                disabled={!hasFreshProviderSelectionDryRun}
                                onChange={(event) =>
                                  setConsequenceConfirmed(event.target.checked)
                                }
                              />
                            }
                            label={copy.providerConsequenceConfirmLabel}
                          />
                        ) : null}
                        <Stack
                          direction={{ xs: "column", sm: "row" }}
                          spacing={2}
                        >
                          {canConfigure ? (
                            <>
                              <Button
                                variant="outlined"
                                onClick={() => void selectProvider(true)}
                              >
                                {copy.dryRunProviderSelectionButton}
                              </Button>
                              <Button
                                variant="outlined"
                                color="secondary"
                                onClick={() => void dryRunReplacement()}
                              >
                                {copy.replacementButton}
                              </Button>
                              <Button
                                variant="contained"
                                disabled={!selectedApplyAllowed}
                                title={
                                  selectedApplyAllowed
                                    ? "Backend apply gates passed."
                                    : `Apply blocked: ${selectedApplyBlockedReasons.join(", ")}`
                                }
                                onClick={() => void selectProvider(false)}
                              >
                                {copy.applySelectedProviderButton}
                              </Button>
                            </>
                          ) : null}
                          <Button
                            variant="contained"
                            color="secondary"
                            onClick={() => void testReadiness(providerDraft)}
                          >
                            {copy.testReadinessButton}
                          </Button>
                        </Stack>
                      </>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>

              <Card
                component="section"
                aria-labelledby="weaver-projection-heading"
              >
                <CardContent>
                  <Typography
                    id="weaver-projection-heading"
                    variant="h2"
                    sx={{ fontSize: "1.35rem", mb: 1 }}
                  >
                    {copy.weaverProjectionHeading}
                  </Typography>
                  <Alert
                    severity={
                      controlPlane.weaverRuntimeProjection.supportSafe &&
                      controlPlane.weaverRuntimeProjection
                        .providerDiagnosticsRedacted &&
                      !controlPlane.weaverRuntimeProjection
                        .rawRuntimeInternalsExposed
                        ? "success"
                        : "warning"
                    }
                    sx={{ mb: 2 }}
                  >
                    {copy.weaverProjectionSummary}
                  </Alert>
                  <Stack spacing={1} sx={{ mb: 2 }}>
                    <Typography>
                      {copy.weaverProjectionEligibilityLabel}: policy enabled{" "}
                      {controlPlane.weaverEligibilityPreview.policyEnabled
                        ? copy.yes
                        : copy.no}
                      ; required group
                      {controlPlane.weaverEligibilityPreview.requiredGroups
                        .length === 1
                        ? ""
                        : "s"}
                      :{" "}
                      {controlPlane.weaverEligibilityPreview.requiredGroups.join(
                        ", ",
                      ) || copy.none}
                      ; eligible member preview:{" "}
                      {
                        controlPlane.weaverEligibilityPreview
                          .memberStateWhenEligible
                      }
                      .
                    </Typography>
                    <Typography>
                      {copy.weaverProjectionBlockedWithoutPolicyLabel}:{" "}
                      {
                        controlPlane.weaverEligibilityPreview
                          .memberStateWithoutPolicy
                      }
                      ; {copy.weaverProjectionBlockedWithoutGroupLabel}:{" "}
                      {
                        controlPlane.weaverEligibilityPreview
                          .memberStateWithoutGroup
                      }
                      .
                    </Typography>
                    <Typography>
                      {copy.weaverProjectionProfileVersionLabel}:{" "}
                      <code>
                        {controlPlane.weaverRuntimeProjection.profileVersion}
                      </code>
                      ; {copy.weaverProjectionRuntimeHashLabel}:{" "}
                      <code>
                        {controlPlane.weaverRuntimeProjection.runtimeProfileHash}
                      </code>
                      ; {copy.weaverProjectionExpiresLabel}:{" "}
                      {controlPlane.weaverRuntimeProjection.expiresAt}
                      .
                    </Typography>
                    <Typography>
                      {copy.weaverProjectionAuditRefsLabel}:{" "}
                      {controlPlane.weaverRuntimeProjection.auditReceiptRefs.join(
                        ", ",
                      ) || "backend audit receipt required"}
                    </Typography>
                    <Typography>
                      {copy.weaverProjectionRevocationRefsLabel}:{" "}
                      {controlPlane.weaverRuntimeProjection.pendingRevocationRefs.join(
                        ", ",
                      ) || "no pending revocation reported"}
                    </Typography>
                    <Typography>
                      {copy.weaverProjectionEligibilityBlockersLabel}:{" "}
                      {controlPlane.weaverEligibilityPreview.blockedReasons.join(
                        "; ",
                      ) || copy.none}
                    </Typography>
                  </Stack>
                  <Stack
                    direction={{ xs: "column", md: "row" }}
                    spacing={2}
                    sx={{ flexWrap: "wrap" }}
                    useFlexGap
                  >
                    {controlPlane.weaverRuntimeProjection.items.map((item) => (
                      <Card
                        key={item.id}
                        variant="outlined"
                        sx={{ flex: "1 1 280px" }}
                      >
                        <CardContent>
                          <Typography variant="h3" sx={{ fontSize: "1.05rem" }}>
                            {weaverProjectionCategoryLabels[item.category]}
                          </Typography>
                          <Typography sx={{ mt: 1 }}>
                            <strong>{item.label}</strong>
                          </Typography>
                          <Chip
                            sx={{ mt: 1 }}
                            color={stateColor[item.state]}
                            label={`State: ${readableState(item.state)}`}
                            aria-label={`${item.label} projection state is ${readableState(item.state)}`}
                          />
                          <List
                            dense
                            aria-label={`${item.label} support-safe projection details`}
                          >
                            <ListItem disableGutters>
                              <ListItemText
                                primary="Member impact"
                                secondary={item.memberImpact}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary="Policy impact preview"
                                secondary={item.policyImpact}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary="Readiness preview"
                                secondary={item.readinessSummary}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary="Receipt refs"
                                secondary={
                                  item.receiptRefs.join(", ") ||
                                  "backend receipt required before apply"
                                }
                              />
                            </ListItem>
                            {item.category === "model" ? (
                              <ListItem disableGutters>
                                <ListItemText
                                  primary="Model alias exposure"
                                  secondary={`User selectable: ${item.userSelectable ? "yes" : "no"}; default: ${item.defaultSelected ? "yes" : "no"}; fallback order: ${item.fallbackOrder ?? "not configured"}`}
                                />
                              </ListItem>
                            ) : null}
                          </List>
                        </CardContent>
                      </Card>
                    ))}
                  </Stack>
                </CardContent>
              </Card>

              <Card component="section" aria-labelledby="replacement-heading">
                <CardContent>
                  <Typography
                    id="replacement-heading"
                    variant="h2"
                    sx={{ fontSize: "1.35rem", mb: 1 }}
                  >
                    {copy.replacementHeading}
                  </Typography>
                  <Typography>{copy.replacementSummary}</Typography>
                  {dryRunReport ? (
                    <Stack spacing={1} sx={{ mt: 2 }}>
                      <Alert
                        severity={
                          dryRunReport.supportSafe &&
                          dryRunReport.providerDiagnosticsRedacted
                            ? "success"
                            : "warning"
                        }
                      >
                        {dryRunReport.status}; support-safe:{" "}
                        {dryRunReport.supportSafe ? "yes" : "no"}; diagnostics
                        redacted:{" "}
                        {dryRunReport.providerDiagnosticsRedacted
                          ? "yes"
                          : "no"}
                        .
                      </Alert>
                      <Typography>
                        {dryRunReport.category}: {dryRunReport.currentAdapter} →{" "}
                        {dryRunReport.targetAdapter}; readiness{" "}
                        {readableState(dryRunReport.readinessState)}.
                      </Typography>
                      <Typography>
                        Member impact states:{" "}
                        {dryRunReport.memberImpactStates.join(", ")}
                      </Typography>
                      <Typography>
                        Consequence counts: preserved {dryRunReport.consequencePreview.preservedCount}; lossy {dryRunReport.consequencePreview.lossyCount}; unsupported {dryRunReport.consequencePreview.unsupportedCount}; manual review {dryRunReport.consequencePreview.manualReviewCount}; archive only {dryRunReport.consequencePreview.archiveOnlyCount}.
                      </Typography>
                      {dryRunReport.crossDomainImpact.length > 0 ? (
                        <Box>
                          <Typography variant="subtitle2">
                            Cross-domain impact
                          </Typography>
                          <ul>
                            {dryRunReport.crossDomainImpact.map((impact) => (
                              <li
                                key={`${impact.domainKey}-${impact.canonicalObjectRef}`}
                              >
                                {impact.domainKey}: {impact.mappingClass};{" "}
                                {impact.canonicalObjectRef};{" "}
                                {impact.consequenceSummary} Evidence: {" "}
                                {impact.evidenceRefs.join(", ") ||
                                  "backend dry-run evidence required"}
                                . Blockers: {" "}
                                {impact.applyBlockers.join("; ") ||
                                  "none for this item"}
                                .
                              </li>
                            ))}
                          </ul>
                        </Box>
                      ) : null}
                      <Typography>
                        Member consequence copy: {dryRunReport.consequencePreview.memberImpactCopy.join(" ") || "backend dry-run reports member impact without provider internals"}
                      </Typography>
                      <Typography>
                        Source of truth:{" "}
                        {dryRunReport.lifecycleExpectations.sourceOfTruthPolicy}
                      </Typography>
                      <Typography>
                        What moves:{" "}
                        {dryRunReport.lossyMappingReport.canonicalObjects.join(
                          ", ",
                        ) || "reported by backend contract"}
                      </Typography>
                      <Typography>
                        What will not move:{" "}
                        {dryRunReport.portableExportImportContract.excludedAutomation.join(
                          "; ",
                        ) || "none reported by backend dry-run"}
                      </Typography>
                      <Typography>
                        Risks:{" "}
                        {dryRunReport.lossyMappingReport.contractRisks.join(
                          "; ",
                        ) || "none reported by backend dry-run"}
                      </Typography>
                      <Typography>
                        Conflicts:{" "}
                        {dryRunReport.lossyMappingReport.conflicts.join("; ") ||
                          "none reported by backend dry-run"}
                      </Typography>
                      <Typography>
                        Cutover gates:{" "}
                        {dryRunReport.cutoverGates.join("; ") ||
                          "backend migration dry-run before apply"}
                      </Typography>
                      <Typography>
                        {dryRunReport.lifecycleExpectations.exportExpectation}
                      </Typography>
                      <Typography>
                        {dryRunReport.lifecycleExpectations.deleteExpectation}
                      </Typography>
                      <Typography>
                        Rollback boundary:{" "}
                        {
                          dryRunReport.lifecycleExpectations
                            .rollbackSupportBoundary
                        }
                      </Typography>
                      <Typography>
                        Rollback limits: {dryRunReport.consequencePreview.rollbackLimits.join("; ") || "reported by backend dry-run"}
                      </Typography>
                      <Typography>
                        Apply blockers: {dryRunReport.consequencePreview.applyBlockers.join("; ") || "none reported by backend dry-run"}
                      </Typography>
                      <Typography>
                        No-unaccounted-data-loss report: supported {dryRunReport.noUnaccountedDataLossReport?.supportedCount ?? dryRunReport.consequencePreview.preservedCount}; known loss {dryRunReport.noUnaccountedDataLossReport?.lossyCount ?? dryRunReport.consequencePreview.lossyCount}; unsupported {dryRunReport.noUnaccountedDataLossReport?.unsupportedCount ?? dryRunReport.consequencePreview.unsupportedCount}; manual review {dryRunReport.noUnaccountedDataLossReport?.manualReviewCount ?? dryRunReport.consequencePreview.manualReviewCount}; archive only {dryRunReport.noUnaccountedDataLossReport?.archiveOnlyCount ?? dryRunReport.consequencePreview.archiveOnlyCount}; vendor locked {dryRunReport.noUnaccountedDataLossReport?.vendorLockedCount ?? 0}.
                      </Typography>
                      <Typography>
                        Known-loss and unsupported data: {[
                          ...(dryRunReport.noUnaccountedDataLossReport?.knownLosses ?? []),
                          ...(dryRunReport.noUnaccountedDataLossReport?.unsupportedData ?? []),
                        ].join("; ") || "none reported by backend dry-run"}
                      </Typography>
                      <Typography>
                        Bounded proof: {dryRunReport.boundedProof?.proofBoundary ?? "dry_run_only"}; limited apply {dryRunReport.boundedProof?.limitedApplyAllowed ? "allowed" : "blocked"}; production cutover {dryRunReport.boundedProof?.productionCutoverAllowed ? "allowed" : "blocked"}; rollback restore-smoke {dryRunReport.boundedProof?.rollbackRestoreSmokeRequired ?? true ? "required" : "not required"}.
                      </Typography>
                      <Typography>
                        Release claim boundaries: {dryRunReport.noUnaccountedDataLossReport?.releaseClaimBoundaries?.join("; ") || "bounded by backend evidence"}
                      </Typography>
                      <Typography>
                        Release blockers: {dryRunReport.boundedProof?.releaseBlockers?.join("; ") || "none reported by backend dry-run"}
                      </Typography>
                      <Typography>
                        Portable export/import:{" "}
                        {
                          dryRunReport.portableExportImportContract
                            .exportManifestRef
                        }{" "}
                        →{" "}
                        {
                          dryRunReport.portableExportImportContract
                            .importManifestRef
                        }
                        ; guarantee:{" "}
                        {
                          dryRunReport.portableExportImportContract
                            .portabilityGuarantee
                        }
                      </Typography>
                      <Typography>
                        Evidence refs:{" "}
                        {dryRunReport.portableExportImportContract.evidenceRefs.join(
                          ", ",
                        )}
                      </Typography>
                      <Typography>
                        Audit refs:{" "}
                        {dryRunReport.auditRefs.join(", ") ||
                          "backend audit ref required before apply"}
                      </Typography>
                      <Typography>
                        Switch plan: {dryRunReport.switchPlan.planRef};
                        preflight required:{" "}
                        {dryRunReport.switchPlan.preflightRequired
                          ? "yes"
                          : "no"}
                        ; cutover window required:{" "}
                        {dryRunReport.switchPlan.cutoverWindowRequired
                          ? "yes"
                          : "no"}
                        ; rollback required:{" "}
                        {dryRunReport.switchPlan.rollbackRequired
                          ? "yes"
                          : "no"}
                        ; member state during switch:{" "}
                        {dryRunReport.switchPlan.memberFacingStateDuringSwitch}.
                      </Typography>
                      <Typography>
                        Recovery actions:{" "}
                        {dryRunReport.switchPlan.recoveryActions.join("; ")}
                      </Typography>
                    </Stack>
                  ) : (
                    <Alert severity="info" sx={{ mt: 2 }}>
                      {copy.replacementEmpty}
                    </Alert>
                  )}
                </CardContent>
              </Card>

              {canConfigure ? (
                <Card
                  component="section"
                  aria-labelledby="weaver-distribution-policy-heading"
                >
                  <CardContent>
                    <Typography
                      id="weaver-distribution-policy-heading"
                      variant="h2"
                      sx={{ fontSize: "1.35rem", mb: 1 }}
                    >
                      {copy.weaverDistributionHeading}
                    </Typography>
                    <Alert severity="info" sx={{ mb: 2 }}>
                      {copy.weaverDistributionDescription}
                    </Alert>
                    <Stack spacing={2}>
                      <FormControl fullWidth>
                        <InputLabel id="weaver-chat-provider-label">
                          {copy.weaverChatProviderLabel}
                        </InputLabel>
                        <Select
                          labelId="weaver-chat-provider-label"
                          id="weaver-chat-provider"
                          value={weaverChatProviderDraft}
                          label={copy.weaverChatProviderLabel}
                          onChange={(event) => {
                            setWeaverChatProviderDraft(event.target.value);
                            setWeaverPolicyConfirmed(false);
                          }}
                        >
                          {(
                            chatCategory?.providerCandidates ?? [
                              weaverPolicyDraft.chatProviderKey,
                            ]
                          ).map((candidate) => (
                            <MenuItem key={candidate} value={candidate}>
                              {candidate}
                            </MenuItem>
                          ))}
                        </Select>
                        <FormHelperText>
                          {copy.weaverChatProviderHelper}
                        </FormHelperText>
                      </FormControl>
                      <Alert severity="warning">
                        Chat readiness:{" "}
                        {readableState(weaverEffectiveDraft.chatReadinessState)}
                        . Migration consequences before regeneration:{" "}
                        {weaverEffectiveDraft.chatMigrationConsequences.join(
                          " ",
                        )}
                      </Alert>
                      <TextField
                        label={copy.weaverModelAliasesLabel}
                        value={weaverModelsDraft}
                        onChange={(event) => {
                          setWeaverModelsDraft(event.target.value);
                          setWeaverPolicyConfirmed(false);
                        }}
                        fullWidth
                        multiline
                        minRows={4}
                      />
                      <TextField
                        label={copy.weaverDefaultModelLabel}
                        value={weaverDefaultModelDraft}
                        onChange={(event) => {
                          setWeaverDefaultModelDraft(event.target.value);
                          setWeaverPolicyConfirmed(false);
                        }}
                        fullWidth
                      />
                      <TextField
                        label={copy.weaverFallbackModelsLabel}
                        value={weaverFallbackModelsDraft}
                        onChange={(event) => {
                          setWeaverFallbackModelsDraft(event.target.value);
                          setWeaverPolicyConfirmed(false);
                        }}
                        fullWidth
                        multiline
                        minRows={2}
                      />
                      <TextField
                        label={copy.weaverAllowedToolsLabel}
                        value={weaverToolsDraft}
                        onChange={(event) => {
                          setWeaverToolsDraft(event.target.value);
                          setWeaverPolicyConfirmed(false);
                        }}
                        helperText={copy.weaverAllowedToolsHelper}
                        fullWidth
                        multiline
                        minRows={4}
                      />
                      <TextField
                        label={copy.weaverAllowedSkillsLabel}
                        value={weaverSkillsDraft}
                        onChange={(event) => {
                          setWeaverSkillsDraft(event.target.value);
                          setWeaverPolicyConfirmed(false);
                        }}
                        fullWidth
                        multiline
                        minRows={2}
                      />
                      <TextField
                        label={copy.weaverAllowedMcpLabel}
                        value={weaverMcpDraft}
                        onChange={(event) => {
                          setWeaverMcpDraft(event.target.value);
                          setWeaverPolicyConfirmed(false);
                        }}
                        fullWidth
                        multiline
                        minRows={3}
                      />
                      <Card variant="outlined">
                        <CardContent>
                          <Typography variant="h3" sx={{ fontSize: "1.05rem" }}>
                            {copy.weaverMcpRegistryHeading}
                          </Typography>
                          <Alert severity="info" sx={{ my: 1 }}>
                            {copy.weaverMcpRegistryDescription}
                          </Alert>
                          <List aria-label="Admin-bound MCP server registry">
                            {controlPlane.mcpServerBindings.map((binding) => (
                              <ListItem key={binding.serverKey} disableGutters>
                                <ListItemText
                                  primary={`${binding.displayName} (${binding.transport}) — ${readableState(binding.readinessState)}`}
                                  secondary={`Endpoint ref: ${binding.endpointRef}; enabled: ${binding.enabled ? "yes" : "no"}; tools: ${binding.allowedTools.join(", ")}; auth: ${binding.authRef}; raw endpoint exposed: ${binding.rawEndpointExposed ? "yes" : "no"}`}
                                />
                              </ListItem>
                            ))}
                          </List>
                        </CardContent>
                      </Card>
                      <Card variant="outlined">
                        <CardContent>
                          <Typography variant="h3" sx={{ fontSize: "1.05rem" }}>
                            {copy.weaverEffectivePolicyPreviewHeading}
                          </Typography>
                          <List aria-label="Effective Weaver RuntimeProfile policy preview">
                            {weaverEffectiveDraft.effectivePolicyPreview.map(
                              (line) => (
                                <ListItem key={line} disableGutters>
                                  <ListItemText primary={line} />
                                </ListItem>
                              ),
                            )}
                          </List>
                          <Typography>
                            Denied tools win globally:{" "}
                            {weaverEffectiveDraft.deniedTools.join(", ") ||
                              "none"}
                            .
                          </Typography>
                          <Typography>
                            Approval required for:{" "}
                            {weaverEffectiveDraft.approvalRequiredFor.join(
                              ", ",
                            )}
                            .
                          </Typography>
                        </CardContent>
                      </Card>
                      <Card variant="outlined">
                        <CardContent>
                          <Typography variant="h3" sx={{ fontSize: "1.05rem" }}>
                            RuntimeProfile revocation and audit
                          </Typography>
                          <Typography>
                            Active hash: {weaverPolicyDraft.runtimeProfileHash};
                            pending hash:{" "}
                            {weaverPolicyDraft.pendingRuntimeProfileHash ??
                              "none"}
                            ; rollback:{" "}
                            {weaverPolicyDraft.rollbackProfileHash ??
                              "not available"}
                            ; revocation state:{" "}
                            {readableState(weaverPolicyDraft.revocationState)}.
                          </Typography>
                          <Typography>
                            Audit refs:{" "}
                            {weaverPolicyDraft.auditRefs.join(", ") ||
                              "backend audit ref required"}
                            .
                          </Typography>
                          <List aria-label="RuntimeProfile change history">
                            {weaverPolicyDraft.changeHistory.map((change) => (
                              <ListItem
                                key={`${change.version}-${change.runtimeProfileHash}`}
                                disableGutters
                              >
                                <ListItemText
                                  primary={`${change.version}: ${readableState(change.status)} ${change.runtimeProfileHash}`}
                                  secondary={`${change.createdAt} — ${change.summary}`}
                                />
                              </ListItem>
                            ))}
                          </List>
                        </CardContent>
                      </Card>
                      <Alert
                        severity={
                          weaverProfileRegenerationAllowed
                            ? "success"
                            : "warning"
                        }
                      >
                        RuntimeProfile regeneration is{" "}
                        {weaverProfileRegenerationAllowed ? "ready" : "blocked"}
                        .
                        {weaverProfileRegenerationAllowed
                          ? " Effective policy was confirmed and backend blockers are clear."
                          : ` Blocked by: ${weaverRegenerationBlockedReasons.join(", ")}.`}
                      </Alert>
                      <FormControlLabel
                        control={
                          <Checkbox
                            checked={weaverPolicyConfirmed}
                            onChange={(event) =>
                              setWeaverPolicyConfirmed(event.target.checked)
                            }
                          />
                        }
                        label={copy.weaverPolicyConfirmLabel}
                      />
                      <Stack
                        direction={{ xs: "column", sm: "row" }}
                        spacing={2}
                      >
                        <Button
                          variant="contained"
                          disabled={!weaverPolicyConfirmed}
                          onClick={() => void saveWeaverDistributionPolicy()}
                        >
                          {copy.saveWeaverPolicyButton}
                        </Button>
                        <Button
                          variant="outlined"
                          color="error"
                          onClick={() => void revokeRuntimeProfile()}
                        >
                          {copy.revokeRuntimeProfileButton}
                        </Button>
                      </Stack>
                    </Stack>
                  </CardContent>
                </Card>
              ) : null}

              {canConfigure ? (
                <Card component="section" aria-labelledby="policy-heading">
                  <CardContent>
                    <Typography
                      id="policy-heading"
                      variant="h2"
                      sx={{ fontSize: "1.35rem", mb: 1 }}
                    >
                      {copy.policyWhitelistHeading}
                    </Typography>
                    <Alert severity="info" sx={{ mb: 2 }}>
                      {copy.policyWhitelistDescription}
                    </Alert>
                    <TextField
                      label={copy.allowedCapabilitiesLabel}
                      value={policyDraft}
                      onChange={(event) => setPolicyDraft(event.target.value)}
                      fullWidth
                      multiline
                      minRows={5}
                      helperText={copy.allowedCapabilitiesHelper}
                    />
                    <Button
                      sx={{ mt: 2 }}
                      variant="contained"
                      onClick={() => void savePolicy()}
                    >
                      {copy.saveWhitelistPolicyButton}
                    </Button>
                    <Divider sx={{ my: 2 }} />
                    <Typography>
                      {copy.blockedExamplesLabel}:{" "}
                      {controlPlane.whitelistPolicy.blockedCapabilities.join(
                        ", ",
                      )}
                    </Typography>
                  </CardContent>
                </Card>
              ) : null}

              {canConfigure ? (
                <Card component="section" aria-labelledby="secrets-heading">
                  <CardContent>
                    <Typography
                      id="secrets-heading"
                      variant="h2"
                      sx={{ fontSize: "1.35rem", mb: 1 }}
                    >
                      {copy.secretRefInventoryHeading}
                    </Typography>
                    <List aria-label={copy.secretRefInventoryLabel}>
                      {controlPlane.providerCategories
                        .flatMap((category) =>
                          category.secretRefs.map((secretRef) => ({
                            category,
                            secretRef,
                          })),
                        )
                        .map(({ category, secretRef }) => (
                          <ListItem
                            key={`${category.key}-${secretRef}`}
                            alignItems="flex-start"
                          >
                            <ListItemText
                              primary={`${category.label}: SecretRef handle`}
                              secondary={`${secretRef} — raw secret exposed: no`}
                            />
                          </ListItem>
                        ))}
                    </List>
                  </CardContent>
                </Card>
              ) : null}

              <Card component="section" aria-labelledby="audit-heading">
                <CardContent>
                  <Typography
                    id="audit-heading"
                    variant="h2"
                    sx={{ fontSize: "1.35rem", mb: 1 }}
                  >
                      {copy.auditTrailHeading}
                    </Typography>
                  <List aria-label={copy.auditTrailLabel}>
                    {controlPlane.auditEvents.map((event) => (
                      <ListItem key={event.id} alignItems="flex-start">
                        <ListItemText
                          primary={`${event.action} by ${event.actor}`}
                          secondary={`${event.createdAt} — ${event.summary}`}
                        />
                      </ListItem>
                    ))}
                  </List>
                </CardContent>
              </Card>
            </>
          ) : null}

          <Box component="footer">
            <Typography variant="body2">
              {copy.footerText}{" "}
              <Link href="/api/organization/manifest">
                {copy.organizationManifestLink}
              </Link>
            </Typography>
          </Box>
        </Stack>
      </Container>
    </>
  );
}

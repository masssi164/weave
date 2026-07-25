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
  AgentRuntimeLifecycleAction,
  AgentRuntimeProjection,
  CapabilityState,
  ControlPlaneResponse,
  OrganizationInvitation,
  OrganizationRole,
  ProviderCategory,
  ProviderReplacementDryRunReport,
  ProviderSwitchApplyGates,
  sampleControlPlane,
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
  const [agentRuntimePersonRef, setAgentRuntimePersonRef] = useState("");
  const [agentRuntimeReason, setAgentRuntimeReason] = useState("");
  const [agentRuntime, setAgentRuntime] =
    useState<AgentRuntimeProjection | null>(null);
  const [agentRuntimeBusy, setAgentRuntimeBusy] = useState(false);
  const [agentRuntimeError, setAgentRuntimeError] = useState<string | null>(null);
  const [runtimeStateDeleteConfirmed, setRuntimeStateDeleteConfirmed] =
    useState(false);
  const [invitations, setInvitations] = useState<OrganizationInvitation[]>([]);
  const [invitationEmail, setInvitationEmail] = useState("");
  const [invitationDisplayName, setInvitationDisplayName] = useState("");
  const [invitationRole, setInvitationRole] =
    useState<OrganizationRole>("member");
  const [invitationBusy, setInvitationBusy] = useState(false);
  const [invitationError, setInvitationError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    api
      .getControlPlane()
      .then((response) => {
        if (!alive) return;
        const firstCategory = response.providerCategories[0];
        setControlPlane(response);
        setPolicyDraft(response.whitelistPolicy.allowedCapabilities.join("\n"));
        setSelectedCategory(firstCategory?.key ?? "");
        setProviderDraft(defaultProviderKey(firstCategory));
        setChoiceModelDraft(
          firstCategory?.choiceModel === "not_selected"
            ? "recommended_self_hosted_default"
            : (firstCategory?.choiceModel ?? "recommended_self_hosted_default"),
        );
        setLoadState("loaded");
        setStatusMessage(copy.loadedStatus);
        if (canConfigure) {
          void api
            .listOrganizationInvitations(response.organization.id)
            .then((items) => {
              if (alive) setInvitations(items);
            })
            .catch((cause: unknown) => {
              if (alive) {
                setInvitationError(
                  cause instanceof Error
                    ? cause.message
                    : "Invitation lifecycle is unavailable.",
                );
              }
            });
        }
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
  }, [api, canConfigure, copy.loadedStatus, copy.offlineSampleStatus, copy.unavailableSampleError]);

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

  async function loadAgentRuntime() {
    if (!agentRuntimePersonRef) return;
    setAgentRuntimeBusy(true);
    setAgentRuntimeError(null);
    try {
      const response = await api.getAgentRuntime(agentRuntimePersonRef);
      setAgentRuntime(response);
      setStatusMessage(`Agent runtime loaded; audit ref ${response.auditRef}.`);
    } catch (cause: unknown) {
      setAgentRuntime(null);
      setAgentRuntimeError(
        cause instanceof Error ? cause.message : "Agent runtime is unavailable.",
      );
    } finally {
      setAgentRuntimeBusy(false);
    }
  }

  async function changeAgentRuntime(action: AgentRuntimeLifecycleAction) {
    if (!canConfigure || !agentRuntimePersonRef) return;
    if (action === "revoke" && !agentRuntime?.entitlementRevision) {
      setAgentRuntimeError(
        "Load the current runtime before revocation so its entitlement revision can be fenced.",
      );
      return;
    }
    if (action === "delete-runtime-state" && !runtimeStateDeleteConfirmed) return;
    setAgentRuntimeBusy(true);
    setAgentRuntimeError(null);
    try {
      const idempotencyKey = `admin-console-${Date.now()}-${action}`;
      const response = await api.changeAgentRuntime(
        agentRuntimePersonRef,
        action,
        idempotencyKey,
        {
          reason: agentRuntimeReason || undefined,
          entitlementRevision: agentRuntime?.entitlementRevision,
        },
      );
      setAgentRuntime(response);
      setRuntimeStateDeleteConfirmed(false);
      setStatusMessage(
        `Agent runtime ${action} accepted; desired ${response.desiredState}, observed ${response.observedState}, audit ref ${response.auditRef}.`,
      );
    } catch (cause: unknown) {
      setAgentRuntimeError(
        cause instanceof Error ? cause.message : "Agent runtime transition failed.",
      );
    } finally {
      setAgentRuntimeBusy(false);
    }
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

  async function refreshInvitations() {
    const items = await api.listOrganizationInvitations(
      controlPlane.organization.id,
    );
    setInvitations(items);
  }

  async function createInvitation() {
    if (!canConfigure || !invitationEmail.trim()) return;
    setInvitationBusy(true);
    setInvitationError(null);
    try {
      await api.createOrganizationInvitation(controlPlane.organization.id, {
        email: invitationEmail.trim(),
        displayName: invitationDisplayName.trim() || undefined,
        role: invitationRole,
      });
      await refreshInvitations();
      setInvitationEmail("");
      setInvitationDisplayName("");
      setStatusMessage(
        "Invitation created. Keycloak owns delivery, activation, expiry, and membership.",
      );
    } catch (cause) {
      setInvitationError(
        cause instanceof Error ? cause.message : "Invitation could not be created.",
      );
    } finally {
      setInvitationBusy(false);
    }
  }

  async function resendInvitation(providerInvitationId: string) {
    setInvitationBusy(true);
    setInvitationError(null);
    try {
      await api.resendOrganizationInvitation(
        controlPlane.organization.id,
        providerInvitationId,
      );
      await refreshInvitations();
      setStatusMessage("Keycloak invitation resent.");
    } catch (cause) {
      setInvitationError(
        cause instanceof Error ? cause.message : "Invitation could not be resent.",
      );
    } finally {
      setInvitationBusy(false);
    }
  }

  async function revokeInvitation(providerInvitationId: string) {
    setInvitationBusy(true);
    setInvitationError(null);
    try {
      await api.revokeOrganizationInvitation(
        controlPlane.organization.id,
        providerInvitationId,
      );
      await refreshInvitations();
      setStatusMessage("Keycloak invitation revoked.");
    } catch (cause) {
      setInvitationError(
        cause instanceof Error ? cause.message : "Invitation could not be revoked.",
      );
    } finally {
      setInvitationBusy(false);
    }
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

              {canConfigure ? (
                <Card component="section" aria-labelledby="invitations-heading">
                  <CardContent>
                    <Typography
                      id="invitations-heading"
                      variant="h2"
                      sx={{ fontSize: "1.35rem", mb: 1 }}
                    >
                      Member invitations
                    </Typography>
                    <Alert severity="info" sx={{ mb: 2 }}>
                      Keycloak owns email delivery, activation, expiry, and
                      organization membership. Weave records only temporary
                      role provisioning intent. The IAM adapter maps that role
                      to the native organization group.
                    </Alert>
                    {invitationError ? (
                      <Alert severity="error" sx={{ mb: 2 }}>
                        {invitationError}
                      </Alert>
                    ) : null}
                    <Stack spacing={2} component="form" onSubmit={(event) => {
                      event.preventDefault();
                      void createInvitation();
                    }}>
                      <TextField
                        required
                        type="email"
                        label="Member email"
                        value={invitationEmail}
                        onChange={(event) => setInvitationEmail(event.target.value)}
                      />
                      <TextField
                        label="Display name (optional)"
                        value={invitationDisplayName}
                        onChange={(event) =>
                          setInvitationDisplayName(event.target.value)
                        }
                      />
                      <FormControl>
                        <InputLabel id="invitation-role-label">Role</InputLabel>
                        <Select
                          labelId="invitation-role-label"
                          label="Role"
                          value={invitationRole}
                          onChange={(event) =>
                            setInvitationRole(event.target.value as OrganizationRole)
                          }
                        >
                          {(["owner", "admin", "member", "guest"] as const).map(
                            (role) => (
                              <MenuItem key={role} value={role}>
                                {role}
                              </MenuItem>
                            ),
                          )}
                        </Select>
                      </FormControl>
                      <Box>
                        <Button
                          type="submit"
                          variant="contained"
                          disabled={invitationBusy || !invitationEmail.trim()}
                        >
                          Invite member
                        </Button>
                      </Box>
                    </Stack>
                    <Divider sx={{ my: 3 }} />
                    <Typography variant="h3" sx={{ fontSize: "1.1rem" }}>
                      Current Keycloak invitations
                    </Typography>
                    {invitations.length === 0 ? (
                      <Typography sx={{ mt: 1 }}>
                        No active invitations were returned by Keycloak.
                      </Typography>
                    ) : (
                      <List aria-label="Current Keycloak invitations">
                        {invitations.map((invitation) => (
                          <ListItem
                            key={invitation.providerInvitationId}
                            alignItems="flex-start"
                            disableGutters
                            secondaryAction={
                              <Stack direction="row" spacing={1}>
                                <Button
                                  disabled={invitationBusy}
                                  onClick={() =>
                                    void resendInvitation(
                                      invitation.providerInvitationId,
                                    )
                                  }
                                >
                                  Resend
                                </Button>
                                <Button
                                  color="error"
                                  disabled={invitationBusy}
                                  onClick={() =>
                                    void revokeInvitation(
                                      invitation.providerInvitationId,
                                    )
                                  }
                                >
                                  Revoke
                                </Button>
                              </Stack>
                            }
                          >
                            <ListItemText
                              primary={invitation.displayName
                                ? `${invitation.displayName} — ${invitation.email}`
                                : invitation.email}
                              secondary={`Invitation: ${readableState(invitation.lifecycleStatus)} · Provisioning: ${readableState(invitation.provisioningStatus)} · Role: ${invitation.requestedRole}`}
                            />
                          </ListItem>
                        ))}
                      </List>
                    )}
                  </CardContent>
                </Card>
              ) : null}

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
                        primary={`Agent Runtime Control: ${readableState(controlPlane.providerCategories.find((category) => category.key === "agent-runtime-control")?.state ?? "disabled")}`}
                        secondary="Keycloak group entitlement is authoritative. Runtime cells, signed RuntimeProfile v2 projections, workload identities, and external state remain backend-operated and fail closed."
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
                    <Alert severity="info" sx={{ mb: 2 }}>
                      {copy.identityAuthorityNotice}
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
                aria-labelledby="agent-runtime-control-heading"
              >
                <CardContent>
                  <Typography
                    id="agent-runtime-control-heading"
                    variant="h2"
                    sx={{ fontSize: "1.35rem", mb: 1 }}
                  >
                    Agent Runtime Control
                  </Typography>
                  <Alert severity="info" sx={{ mb: 2 }}>
                    Operate one Keycloak-entitled cell through the real lifecycle
                    API. RuntimeProfile v2 is signed desired state, not an
                    authorization grant. Runtime-internal state is external and
                    encrypted; deleting it never deletes canonical Files content.
                  </Alert>
                  <Stack spacing={2}>
                    <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
                      <TextField
                        label="Opaque person reference"
                        value={agentRuntimePersonRef}
                        onChange={(event) => {
                          setAgentRuntimePersonRef(event.target.value.trim());
                          setAgentRuntime(null);
                          setAgentRuntimeError(null);
                          setRuntimeStateDeleteConfirmed(false);
                        }}
                        placeholder="acct_0123456789abcdef0123456789abcdef"
                        slotProps={{
                          htmlInput: {
                            pattern: "acct_[a-f0-9]{32}",
                            "aria-describedby": "agent-runtime-person-helper",
                          },
                        }}
                        fullWidth
                      />
                      <Button
                        variant="outlined"
                        disabled={!agentRuntimePersonRef || agentRuntimeBusy}
                        onClick={() => void loadAgentRuntime()}
                      >
                        Load runtime
                      </Button>
                    </Stack>
                    <FormHelperText id="agent-runtime-person-helper">
                      Use the opaque Weave personRef. Email and provider-native
                      user IDs are not runtime identity keys.
                    </FormHelperText>
                    {agentRuntimeError ? (
                      <Alert severity="error">{agentRuntimeError}</Alert>
                    ) : null}
                    {agentRuntime ? (
                      <Card variant="outlined">
                        <CardContent>
                          <Stack spacing={1}>
                            <Stack
                              direction={{ xs: "column", sm: "row" }}
                              spacing={1}
                            >
                              <Chip
                                color={stateColor[agentRuntime.capabilityState]}
                                label={`Capability: ${readableState(agentRuntime.capabilityState)}`}
                              />
                              <Chip
                                label={`Entitlement: ${readableState(agentRuntime.entitlementState)}`}
                              />
                              <Chip
                                label={`Desired: ${readableState(agentRuntime.desiredState)}`}
                              />
                              <Chip
                                label={`Observed: ${readableState(agentRuntime.observedState)}`}
                              />
                            </Stack>
                            <Typography>
                              Cell: <code>{agentRuntime.cellRef ?? "not provisioned"}</code>;
                              provider: {agentRuntime.runtimeProvider ?? "not selected"};
                              workspace revision: {agentRuntime.workspaceRevision ?? "none"}.
                            </Typography>
                            <Typography>
                              RuntimeProfile: <code>{agentRuntime.runtimeProfileRef ?? "none"}</code>;
                              conflicts: {agentRuntime.conflicts}; audit ref:{" "}
                              <code>{agentRuntime.auditRef}</code>.
                            </Typography>
                          </Stack>
                        </CardContent>
                      </Card>
                    ) : null}
                    {canConfigure ? (
                      <>
                        <TextField
                          label="Lifecycle reason"
                          value={agentRuntimeReason}
                          onChange={(event) =>
                            setAgentRuntimeReason(event.target.value)
                          }
                          helperText="Required context for suspend, revoke, and runtime-state deletion; kept support-safe in audit."
                          slotProps={{ htmlInput: { maxLength: 500 } }}
                          fullWidth
                        />
                        <Stack
                          direction={{ xs: "column", sm: "row" }}
                          spacing={1}
                          sx={{ flexWrap: "wrap" }}
                          useFlexGap
                        >
                          {(
                            [
                              "provision",
                              "start",
                              "stop",
                              "suspend",
                              "reconcile",
                              "revoke",
                            ] as AgentRuntimeLifecycleAction[]
                          ).map((action) => (
                            <Button
                              key={action}
                              variant={action === "revoke" ? "outlined" : "contained"}
                              color={action === "revoke" ? "error" : "primary"}
                              disabled={
                                agentRuntimeBusy ||
                                !agentRuntimePersonRef ||
                                ((action === "suspend" || action === "revoke") &&
                                  !agentRuntimeReason.trim())
                              }
                              onClick={() => void changeAgentRuntime(action)}
                            >
                              {action}
                            </Button>
                          ))}
                        </Stack>
                        <Divider />
                        <Alert severity="warning">
                          Runtime-state deletion revokes the per-cell workload
                          identity and removes encrypted runtime-internal state.
                          Canonical WebDAV/Files content is intentionally outside
                          this deletion boundary.
                        </Alert>
                        <FormControlLabel
                          control={
                            <Checkbox
                              checked={runtimeStateDeleteConfirmed}
                              onChange={(event) =>
                                setRuntimeStateDeleteConfirmed(event.target.checked)
                              }
                            />
                          }
                          label="I confirm DELETE_RUNTIME_STATE_ONLY"
                        />
                        <Button
                          variant="outlined"
                          color="error"
                          disabled={
                            agentRuntimeBusy ||
                            !agentRuntimePersonRef ||
                            !agentRuntimeReason.trim() ||
                            !runtimeStateDeleteConfirmed
                          }
                          onClick={() =>
                            void changeAgentRuntime("delete-runtime-state")
                          }
                        >
                          Delete runtime state only
                        </Button>
                      </>
                    ) : null}
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
                  aria-labelledby="mcp-workload-boundary-heading"
                >
                  <CardContent>
                    <Typography
                      id="mcp-workload-boundary-heading"
                      variant="h2"
                      sx={{ fontSize: "1.35rem", mb: 1 }}
                    >
                      MCP workload boundary
                    </Typography>
                    <Alert severity="info" sx={{ mb: 2 }}>
                      MCP is service-to-service only. Every admitted caller is a
                      current per-cell Keycloak service account, and its token is
                      exchanged to the backend audience before authorization.
                      Human bearer tokens and generic shared clients fail closed.
                    </Alert>
                    <Stack spacing={2}>
                      <Card variant="outlined">
                        <CardContent>
                          <Typography variant="h3" sx={{ fontSize: "1.05rem" }}>
                            Active server binding
                          </Typography>
                          <Alert severity="info" sx={{ my: 1 }}>
                            The current catalog is intentionally empty. Tool names
                            are never inferred from RuntimeProfile data or provider
                            configuration.
                          </Alert>
                          <List aria-label="Admin-bound MCP server registry">
                            {controlPlane.mcpServerBindings.map((binding) => (
                              <ListItem key={binding.serverKey} disableGutters>
                                <ListItemText
                                  primary={`${binding.displayName} (${binding.transport}) — ${readableState(binding.readinessState)}`}
                                  secondary={`Endpoint ref: ${binding.endpointRef}; enabled: ${binding.enabled ? "yes" : "no"}; tools: ${binding.allowedTools.join(", ") || "empty catalog"}; auth: ${binding.authRef}; raw endpoint exposed: ${binding.rawEndpointExposed ? "yes" : "no"}`}
                                />
                              </ListItem>
                            ))}
                          </List>
                        </CardContent>
                      </Card>
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

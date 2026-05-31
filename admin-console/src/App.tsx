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
  const [statusMessage, setStatusMessage] = useState(
    "Admin Console is loading backend control-plane data.",
  );

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
        setStatusMessage("Backend control-plane data loaded.");
      })
      .catch((cause: unknown) => {
        if (!alive) return;
        setLoadState("offline-sample");
        setError(
          cause instanceof Error
            ? cause.message
            : "Admin API is unavailable; showing the contract-backed sample state.",
        );
        setStatusMessage(
          "Admin API unavailable. Showing support-safe sample data only.",
        );
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
          <Typography
            variant="h1"
            component="h1"
            sx={{ fontSize: { xs: "1.35rem", md: "1.7rem" }, fontWeight: 700 }}
          >
            Weave Organization Admin Console
          </Typography>
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
            <Alert severity="warning">
              Offline/demo sample state — not live organization status. Do not
              use sample readiness as approval evidence.
            </Alert>
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
                  Guided setup assistant
                </Typography>
                <Alert severity="info" sx={{ mb: 2 }}>
                  Admins bind, unbind, validate, switch, or detach provider
                  adapters only through backend admin APIs. Every apply path
                  requires dry-run/preflight, member impact preview, clear
                  consequences, and recovery guidance before an irreversible
                  change.
                </Alert>
                <List aria-label="Admin setup assistant steps">
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
              <List aria-label="Member-visible capability states">
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
                  <Typography
                    id="oidc-heading"
                    variant="h2"
                    sx={{ fontSize: "1.35rem", mb: 1 }}
                  >
                    Admin sign-in contract
                  </Typography>
                  <Typography>
                    Sign in through OIDC/Keycloak client{" "}
                    <strong>{adminConsoleConfig.oidcClientId}</strong>. This
                    console calls only Weave backend admin APIs; it does not
                    call Keycloak, Nextcloud, Matrix, Microsoft Graph, Slack,
                    Teams, or other providers directly.
                  </Typography>
                  <Typography sx={{ mt: 1 }}>
                    Issuer: <code>{adminConsoleConfig.oidcIssuerUrl}</code>
                  </Typography>
                  <Button
                    variant="outlined"
                    sx={{ mt: 2 }}
                    href={`${adminConsoleConfig.oidcIssuerUrl}/protocol/openid-connect/auth`}
                  >
                    Open identity broker
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
                    Organization overview
                  </Typography>
                  <Stack spacing={1}>
                    <Typography>
                      <strong>{controlPlane.organization.displayName}</strong> (
                      {controlPlane.organization.id})
                    </Typography>
                    <Typography>
                      Provider source of truth:{" "}
                      <code>{controlPlane.providerConfigSource}</code>
                    </Typography>
                    <Typography>
                      Bootstrap defaults are suggestions only:{" "}
                      <strong>
                        {controlPlane.bootstrapDefaultsAreSuggestionsOnly
                          ? "yes"
                          : "no"}
                      </strong>
                    </Typography>
                    <Typography>
                      Current viewer role: <strong>{viewerRole}</strong>
                    </Typography>
                    <Typography>
                      Member clients may configure providers:{" "}
                      <strong>no</strong>
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
                    Provider categories
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
                            label={`Status: ${readableState(category.state)}`}
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
                                primary="Selected adapter"
                                secondary={category.selectedAdapter}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary="Reality level"
                                secondary={readableState(category.realityLevel)}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary="Evidence freshness"
                                secondary={readableState(
                                  category.evidenceFreshness,
                                )}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary="Member impact"
                                secondary={category.memberImpact}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary="Required next action"
                                secondary={category.requiredNextAction}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary="Safe next action"
                                secondary={category.safeNextAction}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary="SecretRef status"
                                secondary={category.secretRefStatus}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary="Policy state"
                                secondary={category.policyState}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary="Migration / dry-run state"
                                secondary={category.migrationState}
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary="Evidence refs"
                                secondary={
                                  category.evidenceRefs.join(", ") ||
                                  "backend evidence required"
                                }
                              />
                            </ListItem>
                            <ListItem disableGutters>
                              <ListItemText
                                primary="Restart survival evidence"
                                secondary={
                                  category.restartSurvivalEvidenceRef ??
                                  "backend restart evidence required before persistence claim"
                                }
                              />
                            </ListItem>
                          </List>
                          {canConfigure ? (
                            <Typography variant="body2" sx={{ mt: 1 }}>
                              Candidates:{" "}
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
                    Readiness dashboard
                  </Typography>
                  <Typography sx={{ mb: 2 }}>
                    Domain readiness is actionable for admins and operators but
                    support-safe by default: provider diagnostics are redacted,
                    SecretRef handles stay out of member contracts, and member
                    preview states remain provider-neutral.
                  </Typography>
                  <List aria-label="Domain readiness dashboard">
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
                      Identity provider readiness
                    </Typography>
                    <Alert severity="info" sx={{ mb: 2 }}>
                      Workspace Health reads identity readiness only from the
                      Weave backend facade. Member clients do not receive OIDC
                      URLs, client ids, realm internals, raw provider errors, or
                      credentials.
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
                    Provider selection and readiness
                  </Typography>
                  <Alert severity="info" sx={{ mb: 2 }}>
                    Admin Console-selected mappings are the source of truth.
                    Secrets stay as SecretRef handles; readiness tests run only
                    through backend admin APIs.
                  </Alert>
                  <Stack spacing={2}>
                    <FormControl fullWidth>
                      <InputLabel id="provider-category-select-label">
                        Provider category
                      </InputLabel>
                      <Select
                        labelId="provider-category-select-label"
                        id="provider-category-select"
                        value={selectedCategory}
                        label="Provider category"
                        onChange={(event) => changeCategory(event.target.value)}
                      >
                        {controlPlane.providerCategories.map((category) => (
                          <MenuItem key={category.key} value={category.key}>
                            {category.label}
                          </MenuItem>
                        ))}
                      </Select>
                      <FormHelperText>
                        Category-first canonical Weave contracts stay separate
                        from adapter choices.
                      </FormHelperText>
                    </FormControl>

                    {selectedCategoryDetails ? (
                      <>
                        <FormControl fullWidth disabled={!canConfigure}>
                          <InputLabel id="provider-candidate-select-label">
                            Selected provider adapter
                          </InputLabel>
                          <Select
                            labelId="provider-candidate-select-label"
                            id="provider-candidate-select"
                            value={providerDraft}
                            label="Selected provider adapter"
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
                            Choice model
                          </InputLabel>
                          <Select
                            labelId="choice-model-select-label"
                            id="choice-model-select"
                            value={choiceModelDraft}
                            label="Choice model"
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
                            SecretRefs:{" "}
                            {selectedCategoryDetails.secretRefs.join(", ") ||
                              `secretref://weave/provider/${providerDraft}`}
                          </Typography>
                        ) : null}
                        <Typography>
                          Never paste raw secrets, bearer tokens, provider URLs
                          with credentials, or downstream diagnostics.
                        </Typography>
                        <Alert
                          severity={
                            selectedApplyAllowed ? "success" : "warning"
                          }
                        >
                          Provider apply is{" "}
                          {selectedApplyAllowed ? "enabled" : "blocked"} by
                          backend gates, current-session dry-run evidence, and
                          explicit consequence confirmation.
                          {selectedApplyAllowed
                            ? " All required evidence gates passed."
                            : ` Missing gates: ${selectedApplyBlockedReasons.join(", ")}.`}
                        </Alert>
                        <Alert
                          severity={
                            hasFreshProviderSelectionDryRun
                              ? "success"
                              : "warning"
                          }
                        >
                          Current-session dry-run evidence is{" "}
                          {hasFreshProviderSelectionDryRun
                            ? "fresh and trusted"
                            : "missing, stale, or untrusted"}
                          .
                          {providerSelectionDryRun
                            ? ` Last dry-run evidence ${providerSelectionDryRun.evidenceRef ?? "untrusted-client-only"} at ${providerSelectionDryRun.completedAt}${providerSelectionDryRun.expiresAt ? `, expires ${providerSelectionDryRun.expiresAt}` : ""}. Restart evidence: ${providerSelectionDryRun.restartSurvivalEvidenceRef ?? "not reported"}.`
                            : " Run a dry-run for the selected category, adapter, and choice model before apply."}
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
                            label="I confirm I reviewed member impact, rollback evidence, and provider-switch consequences for this dry-run."
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
                                Dry-run provider selection
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
                                Apply selected provider
                              </Button>
                            </>
                          ) : null}
                          <Button
                            variant="contained"
                            color="secondary"
                            onClick={() => void testReadiness(providerDraft)}
                          >
                            Test readiness through backend
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
                      Profile version: {" "}
                      <code>
                        {controlPlane.weaverRuntimeProjection.profileVersion}
                      </code>
                      ; RuntimeProfile hash: {" "}
                      <code>
                        {controlPlane.weaverRuntimeProjection.runtimeProfileHash}
                      </code>
                      ; expires: {controlPlane.weaverRuntimeProjection.expiresAt}
                      .
                    </Typography>
                    <Typography>
                      Audit receipt refs: {" "}
                      {controlPlane.weaverRuntimeProjection.auditReceiptRefs.join(
                        ", ",
                      ) || "backend audit receipt required"}
                    </Typography>
                    <Typography>
                      Revocation refs: {" "}
                      {controlPlane.weaverRuntimeProjection.pendingRevocationRefs.join(
                        ", ",
                      ) || "no pending revocation reported"}
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
                <Card component="section" aria-labelledby="policy-heading">
                  <CardContent>
                    <Typography
                      id="policy-heading"
                      variant="h2"
                      sx={{ fontSize: "1.35rem", mb: 1 }}
                    >
                      Policy and whitelist
                    </Typography>
                    <Alert severity="info" sx={{ mb: 2 }}>
                      Policy is deny-by-default. Add one canonical Weave
                      capability per line only after the organization has
                      approved it.
                    </Alert>
                    <TextField
                      label="Allowed capabilities"
                      value={policyDraft}
                      onChange={(event) => setPolicyDraft(event.target.value)}
                      fullWidth
                      multiline
                      minRows={5}
                      helperText="Example: files.read. Do not paste secrets, provider tokens, raw diagnostics, or provider-specific payloads here."
                    />
                    <Button
                      sx={{ mt: 2 }}
                      variant="contained"
                      onClick={() => void savePolicy()}
                    >
                      Save whitelist policy
                    </Button>
                    <Divider sx={{ my: 2 }} />
                    <Typography>
                      Blocked examples:{" "}
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
                      SecretRef inventory
                    </Typography>
                    <List aria-label="Support-safe SecretRef handles">
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
                    Audit trail
                  </Typography>
                  <List aria-label="Recent admin audit events">
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
              Need member behavior? Use the provider-agnostic Weave Client.
              Admin/provider setup belongs here and in backend policy.{" "}
              <Link href="/api/organization/manifest">
                Organization manifest
              </Link>
            </Typography>
          </Box>
        </Stack>
      </Container>
    </>
  );
}

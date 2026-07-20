import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "./App";
import { AdminControlPlaneApi, sampleControlPlane } from "./api";
import { adminConsoleMessages } from "./copy";

function mockApi(
  overrides: Partial<AdminControlPlaneApi> = {},
): AdminControlPlaneApi {
  return {
    getControlPlane: vi.fn().mockResolvedValue(sampleControlPlane),
    updateWhitelistPolicy: vi.fn().mockResolvedValue({
      ...sampleControlPlane.whitelistPolicy,
      allowedCapabilities: ["files.read"],
    }),
    getAgentRuntime: vi.fn().mockResolvedValue({
      personRef: "acct_0123456789abcdef0123456789abcdef",
      cellRef: "cell_01",
      runtimeProvider: "weaver-openclaw",
      entitlementState: "entitled",
      entitlementRevision: "entitlement-rev-7",
      desiredState: "ready",
      observedState: "ready",
      runtimeProfileRef:
        "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      workspaceRevision: "workspace-rev-3",
      conflicts: 0,
      capabilityState: "ready",
      auditRef: "audit://agent-runtime-control/test",
    }),
    changeAgentRuntime: vi.fn().mockImplementation(
      async (_personRef, action) => ({
        personRef: "acct_0123456789abcdef0123456789abcdef",
        cellRef: "cell_01",
        runtimeProvider: "weaver-openclaw",
        entitlementState: action === "revoke" ? "revoked" : "entitled",
        entitlementRevision: "entitlement-rev-7",
        desiredState: action === "delete-runtime-state" ? "deleted" : "ready",
        observedState: action === "delete-runtime-state" ? "deleted" : "ready",
        runtimeProfileRef:
          "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        workspaceRevision: "workspace-rev-3",
        conflicts: 0,
        capabilityState: "ready",
        auditRef: "audit://agent-runtime-control/test",
      }),
    ),
    selectProvider: vi.fn(
      async (category, providerKey, choiceModel, dryRun) => ({
        category,
        providerKey,
        choiceModel,
        dryRun,
        evidenceRef: dryRun
          ? `${category}-${providerKey}-backend-dry-run`
          : undefined,
        issuedAt: "2099-01-01T00:00:00Z",
        expiresAt: "2099-01-02T00:00:00Z",
        restartSurvivalEvidenceRef: `${category}-${providerKey}-restart-survival`,
        supportSafe: true,
      }),
    ),
    dryRunProviderReplacement: vi.fn().mockResolvedValue({
      dryRunId: "chat-slack-dry-run",
      status: "dry_run_ready",
      category: "idm-rbac",
      currentAdapter: "keycloak-realm",
      targetAdapter: "keycloak-realm",
      readinessState: "ready",
      migrationDryRunRequired: true,
      memberImpactStates: ["available", "degraded", "disabled_by_policy"],
      supportSafe: true,
      providerDiagnosticsRedacted: true,
      cutoverGates: ["Run backend migration dry-run before apply"],
      auditRefs: ["provider-replacement-dry-run-idm-rbac"],
      consequencePreview: {
        preservedCount: 2,
        lossyCount: 1,
        unsupportedCount: 0,
        manualReviewCount: 1,
        archiveOnlyCount: 0,
        memberImpactCopy: ["Members keep provider-neutral access while admins review replacement consequences."],
        rollbackLimits: ["Rollback is bounded by provider export support."],
        applyBlockers: ["Group owner claim needs operator approval"],
      },
      lossyMappingReport: {
        canonicalObjects: ["IdentitySubject", "GroupMembership"],
        contractRisks: ["External claims need mapping review"],
        adminNotes: ["Support-safe dry-run"],
        conflicts: ["Group owner claim needs operator approval"],
        replacementRequirement: "Review before apply",
      },
      lifecycleExpectations: {
        sourceOfTruthPolicy:
          "Backend declares source of truth per IDM subject and group object",
        exportExpectation: "Export evidence is required before cutover.",
        deleteExpectation:
          "Delete/deprovision evidence is required after cutover.",
        deprovisionExpectation: "Deactivate old adapter after verification.",
        rollbackSupportBoundary: "Rollback bounded by provider export support.",
      },
      portableExportImportContract: {
        exportManifestRef: "idm-rbac-portable-export-manifest-v0.1",
        importManifestRef: "idm-rbac-portable-import-manifest-v0.1",
        portabilityGuarantee:
          "v0.1 guarantees portable export/import before automated migration.",
        excludedAutomation: ["full automated migration"],
        evidenceRefs: [
          "provider-switch-preflight",
          "portable-export-import-contract",
          "rollback-recovery-plan",
        ],
      },
      switchPlan: {
        planRef: "idm-rbac-switch-plan-v0.1",
        preflightRequired: true,
        cutoverWindowRequired: true,
        rollbackRequired: true,
        memberFacingStateDuringSwitch: "degraded",
        recoveryActions: [
          "keep current adapter active until evidence passes",
          "block apply until rollback evidence passes",
        ],
      },
      noUnaccountedDataLossReport: {
        supportedCount: 2,
        lossyCount: 1,
        unsupportedCount: 0,
        manualReviewCount: 1,
        archiveOnlyCount: 0,
        vendorLockedCount: 0,
        knownLosses: ["External claims need mapping review"],
        unsupportedData: [],
        rollbackLimits: ["Rollback is bounded by provider export support."],
        releaseClaimBoundaries: ["Provider replacement claims remain bounded by accepted dry-run evidence."],
      },
      boundedProof: {
        proofBoundary: "dry_run_only",
        limitedApplyAllowed: false,
        productionCutoverAllowed: false,
        rollbackRestoreSmokeRequired: true,
        requiredEvidenceRefs: ["provider-replacement-dry-run-idm-rbac"],
        releaseBlockers: ["bounded apply/cutover/rollback proof is not available for this dry-run"],
      },
      crossDomainImpact: [
        {
          domainKey: "files",
          canonicalObjectRef: "weave:files:attachment-ref/test",
          mappingClass: "archive_only",
          consequenceSummary: "Attachment refs require rollback-retention review.",
          evidenceRefs: ["impact:test:files"],
          applyBlockers: ["rollback retention decision required"],
        },
      ],
    }),
    testProviderReadiness: vi.fn().mockResolvedValue({
      providerKey: "keycloak-realm",
      state: "ready",
      summary: "Ready",
    }),
    listAuditEvents: vi.fn().mockResolvedValue(sampleControlPlane.auditEvents),
    listOrganizationInvitations: vi.fn().mockResolvedValue([]),
    createOrganizationInvitation: vi.fn().mockImplementation(
      async (organizationId, request) => ({
        providerInvitationId: "invite-123",
        organizationId,
        email: request.email,
        displayName: request.displayName,
        lifecycleStatus: "pending",
        provisioningStatus: "pending",
        requestedRole: request.role,
        organizationGroups: request.organizationGroups,
      }),
    ),
    resendOrganizationInvitation: vi.fn().mockResolvedValue({}),
    revokeOrganizationInvitation: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  } as unknown as AdminControlPlaneApi;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// V01_ADMIN_CONSOLE_MVP: admin console manages org/provider policy through backend APIs only.
describe("Admin Console MVP", () => {
  it("manages Keycloak invitation lifecycle separately from provisioning intent", async () => {
    const listOrganizationInvitations = vi.fn().mockResolvedValue([
      {
        providerInvitationId: "invite-existing",
        organizationId: "weave-dogfood",
        email: "existing@example.test",
        lifecycleStatus: "pending",
        provisioningStatus: "applied",
        requestedRole: "member",
        organizationGroups: ["engineering"],
      },
    ]);
    const createOrganizationInvitation = vi.fn().mockResolvedValue({});
    const api = mockApi({
      listOrganizationInvitations,
      createOrganizationInvitation,
    });
    const user = userEvent.setup();

    render(<App api={api} />);

    expect(
      await screen.findByRole("heading", { name: /member invitations/i }),
    ).toBeInTheDocument();
    expect(await screen.findByText("existing@example.test")).toBeInTheDocument();
    expect(screen.getByText(/Invitation: pending/)).toHaveTextContent(
      /Provisioning: applied/,
    );
    expect(screen.getByText(/Keycloak owns email delivery/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText(/member email/i), "new@example.test");
    await user.type(screen.getByLabelText(/display name/i), "New Member");
    await user.type(
      screen.getByLabelText(/organization groups/i),
      "engineering, reviewers",
    );
    await user.click(screen.getByRole("button", { name: /invite member/i }));

    await waitFor(() =>
      expect(createOrganizationInvitation).toHaveBeenCalledWith(
        "weave-dogfood",
        {
          email: "new@example.test",
          displayName: "New Member",
          role: "member",
          organizationGroups: ["engineering", "reviewers"],
        },
      ),
    );
    expect(document.body).not.toHaveTextContent(/activation token|client_secret/i);
  }, 15_000);

  it("renders organization, provider, policy, and audit sections from backend control-plane data", async () => {
    render(<App api={mockApi()} />);

    expect(
      await screen.findByRole("heading", {
        name: /weave organization admin console/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Weave – Collaboration Seamlessly Woven with Agentic AI, Activated on Your Terms.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /organization overview/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /provider categories/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /guided setup assistant/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /readiness dashboard/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /identity provider readiness/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", {
        name: /provider selection and readiness/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /effective policy explanation/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /member capability preview/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /agent runtime control/i, level: 2 }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", {
        name: /provider replacement dry-run results/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /mcp workload boundary/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /policy and whitelist/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /secretref inventory/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /audit trail/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/identity status is ready/i),
    ).toBeInTheDocument();
    for (const domain of [
      "People",
      "Spaces",
      "Chat",
      "Files",
      "Documents",
      "Calendar",
      "Boards",
      "Calls",
      "Decisions",
      "Notifications",
      "Health",
      "Agent Runtime Control",
    ]) {
      expect(
        screen.getByRole("heading", { name: domain, level: 3 }),
      ).toBeInTheDocument();
    }
    expect(screen.getAllByText(/Selected adapter/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Required next action/i).length).toBeGreaterThan(
      0,
    );
    expect(screen.getAllByText(/SecretRef status/i).length).toBeGreaterThan(0);
    expect(
      screen.getAllByText(/Migration \/ dry-run state/i).length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText(/Evidence refs/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Reality level/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Evidence freshness/i).length).toBeGreaterThan(
      0,
    );
    expect(
      screen.getAllByText(/Restart survival evidence/i).length,
    ).toBeGreaterThan(0);
    expect(screen.getByText(/provider apply is blocked/i)).toBeInTheDocument();
    expect(
      screen.getByText(/missing current-session dry-run evidence/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/provider source of truth/i)).toBeInTheDocument();
    expect(screen.getAllByText("configured").length).toBeGreaterThan(0);
    expect(screen.getByText(/backend-owned facade/i)).toBeInTheDocument();
    expect(screen.getByText(/Stable states:/i)).toHaveTextContent(
      /admin action required/i,
    );
    expect(
      screen.getAllByText(/policy is deny-by-default/i).length,
    ).toBeGreaterThan(0);
  });

  it("renders identity readiness as backend-owned support-safe Workspace Health cards", async () => {
    render(<App api={mockApi()} />);

    expect(
      await screen.findByRole("heading", {
        name: /identity provider readiness/i,
      }),
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
    expect(screen.getByText(/member provider setup:/i)).toHaveTextContent(
      /blocked/i,
    );
    expect(document.body).not.toHaveTextContent(/client_secret|access_token/i);
  });

  it("specifies guided setup and per-domain readiness before member go-live", async () => {
    render(<App api={mockApi()} />);

    expect(
      await screen.findByRole("heading", { name: /guided setup assistant/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/admin setup assistant steps/i),
    ).toHaveTextContent(/identity: ready for member go-live/i);
    expect(
      screen.getByLabelText(/admin setup assistant steps/i),
    ).toHaveTextContent(/calls: repair before inviting affected members/i);
    expect(
      screen.getByText(/requires dry-run\/preflight, member impact preview/i),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/domain readiness dashboard/i),
    ).toHaveTextContent(/next action: run a readiness test/i);
    expect(
      screen.getByLabelText(/domain readiness dashboard/i),
    ).toHaveTextContent(/member preview: degraded/i);
    expect(
      screen.getByRole("heading", {
        name: /beta setup and control readiness preview/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/beta setup and control readiness checklist/i),
    ).toHaveTextContent(/idm and rbac posture: ready/i);
    expect(
      screen.getByLabelText(/beta setup and control readiness checklist/i),
    ).toHaveTextContent(/agent runtime control: disabled/i);
    expect(
      screen.getByLabelText(/beta setup and control readiness checklist/i),
    ).toHaveTextContent(/raw provider diagnostics exposed: no/i);
    expect(screen.getByText(/rc claim control/i)).toBeInTheDocument();
    expect(
      screen.getByLabelText(/rc go-live evidence and release claim gates/i),
    ).toHaveTextContent(/pinned specification corpus/i);
    expect(
      screen.getByLabelText(/rc go-live evidence and release claim gates/i),
    ).toHaveTextContent(/accessibility evidence: degraded/i);
    expect(
      screen.getByLabelText(/rc go-live evidence and release claim gates/i),
    ).toHaveTextContent(/blocks release claim: yes/i);
    expect(
      screen.getByLabelText(/rc go-live evidence and release claim gates/i),
    ).toHaveTextContent(/unresolved veto\/blockers/i);
  });

  it("keeps admin/provider setup separate from the member client and direct providers", async () => {
    render(<App api={mockApi()} />);

    expect(
      await screen.findByText(/calls only Weave backend admin APIs/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /it does not call identity, chat, files, office, task, meeting, or other providers directly/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Secrets stay as SecretRef handles/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/use the provider-agnostic weave client/i),
    ).toBeInTheDocument();
  });

  // V01_AGENT_RUNTIME_CONTROL_POLICY: admins operate the real support-safe lifecycle API.
  it("loads an Agent Runtime Control projection without exposing runtime internals", async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    expect(
      await screen.findByRole("heading", {
        name: /agent runtime control/i,
        level: 2,
      }),
    ).toBeInTheDocument();
    const personField = screen.getByRole("textbox", {
      name: /opaque person reference/i,
    });
    await user.type(personField, "acct_0123456789abcdef0123456789abcdef");
    await user.click(screen.getByRole("button", { name: /load runtime/i }));

    await waitFor(() => expect(api.getAgentRuntime).toHaveBeenCalled());
    expect(screen.getByText("cell_01")).toBeInTheDocument();
    expect(
      screen.getAllByText(/audit:\/\/agent-runtime-control\/test/i).length,
    ).toBeGreaterThan(0);
    expect(screen.getByRole("heading", { name: /mcp workload boundary/i })).toBeInTheDocument();
    expect(screen.getByText(/empty catalog/i)).toBeInTheDocument();
    expect(
      screen.getByRole("region", { name: /agent runtime control/i }),
    ).not.toHaveTextContent(
      /client_secret\s*[:=]|access_token\s*[:=]|refresh_token\s*[:=]|Bearer\s+[A-Za-z0-9_-]{20}|openclaw\.json/i,
    );
  });

  it("saves whitelist policy through the backend API and announces the result", async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    const policyField = await screen.findByRole("textbox", {
      name: /allowed capabilities/i,
    });
    await user.clear(policyField);
    await user.type(policyField, "files.read");
    await user.click(
      screen.getByRole("button", { name: /save whitelist policy/i }),
    );

    await waitFor(() =>
      expect(api.updateWhitelistPolicy).toHaveBeenCalledWith(["files.read"]),
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      /whitelist policy saved/i,
    );
  }, 15_000);

  it("applies selected providers only after fresh dry-run evidence and consequence confirmation", async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    expect(
      await screen.findByRole("button", { name: /apply selected provider/i }),
    ).toBeDisabled();

    await user.click(
      screen.getByRole("button", { name: /dry-run provider selection/i }),
    );
    await waitFor(() =>
      expect(api.selectProvider).toHaveBeenCalledWith(
        "identity",
        "keycloak-realm",
        "recommended_self_hosted_default",
        true,
        undefined,
      ),
    );

    expect(
      screen.getByText(
        /current-session dry-run evidence is fresh and trusted/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /apply selected provider/i }),
    ).toBeDisabled();

    await user.click(
      screen.getByRole("checkbox", {
        name: /i confirm i reviewed member impact, rollback evidence, and provider-switch consequences/i,
      }),
    );

    await user.click(
      screen.getByRole("button", { name: /apply selected provider/i }),
    );

    await waitFor(() =>
      expect(api.selectProvider).toHaveBeenCalledWith(
        "identity",
        "keycloak-realm",
        "recommended_self_hosted_default",
        false,
        "identity-keycloak-realm-backend-dry-run",
      ),
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      /provider selection applied/i,
    );
  });

  it("blocks provider apply when dry-run evidence becomes stale after selection changes", async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.click(
      await screen.findByRole("button", {
        name: /dry-run provider selection/i,
      }),
    );
    await waitFor(() =>
      expect(
        screen.getByText(
          /current-session dry-run evidence is fresh and trusted/i,
        ),
      ).toBeInTheDocument(),
    );

    await user.click(screen.getByLabelText(/choice model/i));
    await user.click(
      screen.getByRole("option", { name: /managed cloud provider/i }),
    );

    expect(
      screen.getByText(
        /current-session dry-run evidence is missing, stale, or untrusted/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /missing gates: missing current-session dry-run evidence/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("checkbox", {
        name: /i confirm i reviewed member impact, rollback evidence, and provider-switch consequences/i,
      }),
    ).toBeDisabled();
    expect(
      screen.getByRole("button", { name: /apply selected provider/i }),
    ).toBeDisabled();
  });

  it("blocks missing dry-run evidence before apply endpoint calls", async () => {
    const api = mockApi({
      selectProvider: vi.fn(
        async (category, providerKey, choiceModel, dryRun) => ({
          category,
          providerKey,
          choiceModel,
          dryRun,
          supportSafe: true,
        }),
      ),
    });
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.click(
      await screen.findByRole("button", {
        name: /dry-run provider selection/i,
      }),
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      /did not include trusted backend evidence/i,
    );
    expect(
      screen.getByText(/missing trusted backend dry-run evidence/i),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /apply selected provider/i }),
    ).toBeDisabled();
    expect(api.selectProvider).toHaveBeenCalledTimes(1);
  });

  it("blocks untrusted dry-run evidence when supportSafe is omitted", async () => {
    const api = mockApi({
      selectProvider: vi.fn(
        async (category, providerKey, choiceModel, dryRun) => ({
          category,
          providerKey,
          choiceModel,
          dryRun,
          evidenceRef: `${category}-${providerKey}-backend-dry-run`,
          issuedAt: "2099-01-01T00:00:00Z",
          expiresAt: "2099-01-02T00:00:00Z",
          supportSafe: undefined as unknown as boolean,
        }),
      ),
    });
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.click(
      await screen.findByRole("button", {
        name: /dry-run provider selection/i,
      }),
    );

    expect(screen.getByText(/untrusted dry-run evidence/i)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /apply selected provider/i }),
    ).toBeDisabled();
    expect(api.selectProvider).toHaveBeenCalledTimes(1);
  });

  it("blocks expired backend dry-run evidence before apply endpoint calls", async () => {
    const api = mockApi({
      selectProvider: vi.fn(
        async (category, providerKey, choiceModel, dryRun) => ({
          category,
          providerKey,
          choiceModel,
          dryRun,
          evidenceRef: dryRun
            ? `${category}-${providerKey}-expired-dry-run`
            : undefined,
          issuedAt: "2000-01-01T00:00:00Z",
          expiresAt: "2000-01-02T00:00:00Z",
          supportSafe: true,
        }),
      ),
    });
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.click(
      await screen.findByRole("button", {
        name: /dry-run provider selection/i,
      }),
    );

    expect(screen.getByText(/stale dry-run evidence/i)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /apply selected provider/i }),
    ).toBeDisabled();
    expect(api.selectProvider).toHaveBeenCalledTimes(1);
  });

  it("blocks dry-run evidence when expiration is missing or unparseable", async () => {
    const api = mockApi({
      selectProvider: vi
        .fn()
        .mockResolvedValueOnce({
          category: "identity",
          providerKey: "keycloak-realm",
          choiceModel: "recommended_self_hosted_default",
          dryRun: true,
          evidenceRef: "identity-keycloak-realm-no-expiry",
          issuedAt: "2099-01-01T00:00:00Z",
          supportSafe: true,
        })
        .mockResolvedValueOnce({
          category: "identity",
          providerKey: "keycloak-realm",
          choiceModel: "recommended_self_hosted_default",
          dryRun: true,
          evidenceRef: "identity-keycloak-realm-invalid-expiry",
          issuedAt: "2099-01-01T00:00:00Z",
          expiresAt: "not-a-date",
          supportSafe: true,
        }),
    });
    const user = userEvent.setup();
    render(<App api={api} />);

    const dryRunButton = await screen.findByRole("button", {
      name: /dry-run provider selection/i,
    });
    await user.click(dryRunButton);

    expect(
      await screen.findByText(/missing dry-run evidence expiration/i),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /apply selected provider/i }),
    ).toBeDisabled();

    await user.click(dryRunButton);

    expect(
      await screen.findByText(/unparseable dry-run evidence expiration/i),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /apply selected provider/i }),
    ).toBeDisabled();
    expect(api.selectProvider).toHaveBeenCalledTimes(2);
  });

  it("fences Agent Runtime Control mutations and confirms runtime-state deletion", async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.type(
      await screen.findByRole("textbox", { name: /opaque person reference/i }),
      "acct_0123456789abcdef0123456789abcdef",
    );
    await user.click(screen.getByRole("button", { name: /load runtime/i }));
    await user.type(
      screen.getByRole("textbox", { name: /lifecycle reason/i }),
      "Member offboarding",
    );
    await user.click(screen.getByRole("button", { name: /^revoke$/i }));

    await waitFor(() =>
      expect(api.changeAgentRuntime).toHaveBeenCalledWith(
        "acct_0123456789abcdef0123456789abcdef",
        "revoke",
        expect.stringMatching(/^admin-console-/),
        {
          reason: "Member offboarding",
          entitlementRevision: "entitlement-rev-7",
        },
      ),
    );
    const deleteButton = screen.getByRole("button", {
      name: /delete runtime state only/i,
    });
    expect(deleteButton).toBeDisabled();
    await user.click(
      screen.getByRole("checkbox", {
        name: /i confirm delete_runtime_state_only/i,
      }),
    );
    await user.click(deleteButton);
    await waitFor(() =>
      expect(api.changeAgentRuntime).toHaveBeenCalledWith(
        "acct_0123456789abcdef0123456789abcdef",
        "delete-runtime-state",
        expect.stringMatching(/^admin-console-/),
        {
          reason: "Member offboarding",
          entitlementRevision: "entitlement-rev-7",
        },
      ),
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      /delete-runtime-state accepted/i,
    );
  });

  it("dry-runs selected providers through the backend API before applying", async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.click(
      await screen.findByRole("button", {
        name: /dry-run provider selection/i,
      }),
    );

    await waitFor(() =>
      expect(api.selectProvider).toHaveBeenCalledWith(
        "identity",
        "keycloak-realm",
        "recommended_self_hosted_default",
        true,
        undefined,
      ),
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      /dry-run validated/i,
    );
  });

  // V01_PROVIDER_SWITCH_PORTABILITY: admin provider switch requires preflight, portable export/import, cutover, rollback, recovery, and support-safe evidence.
  it("renders support-safe provider replacement dry-run evidence", async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.click(
      await screen.findByRole("button", {
        name: /dry-run replacement contract/i,
      }),
    );

    await waitFor(() =>
      expect(api.dryRunProviderReplacement).toHaveBeenCalledWith(
        expect.objectContaining({ key: "identity" }),
        "keycloak-realm",
        "recommended_self_hosted_default",
      ),
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      /replacement dry-run completed/i,
    );
    expect(screen.getByText(/support-safe: yes/i)).toBeInTheDocument();
    expect(screen.getByText(/diagnostics redacted: yes/i)).toBeInTheDocument();
    expect(
      screen.getByText(
        /member impact states: available, degraded, disabled_by_policy/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /consequence counts: preserved 2; lossy 1; unsupported 0; manual review 1; archive only 0/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/member consequence copy: members keep provider-neutral access/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/cross-domain impact/i)).toBeInTheDocument();
    expect(
      screen.getByText(/files: archive_only; weave:files:attachment-ref\/test/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/source of truth: backend declares source of truth/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/what moves: identitysubject, groupmembership/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/what will not move: full automated migration/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/risks: external claims/i)).toBeInTheDocument();
    expect(
      screen.getByText(/conflicts: group owner claim/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/portable export\/import: idm-rbac-portable-export/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/evidence refs: provider-switch-preflight/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/audit refs: provider-replacement-dry-run-idm-rbac/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/switch plan: idm-rbac-switch-plan-v0.1/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/cutover window required: yes/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/recovery actions: keep current adapter active/i),
    ).toBeInTheDocument();
  });

  it("shows distinct owner, operator, and member boundaries without provider-admin controls in member preview", async () => {
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
      screen.queryByRole("heading", {
        name: /identity provider readiness/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("heading", {
        name: /provider selection and readiness/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /apply selected provider/i }),
    ).not.toBeInTheDocument();
  });

  it("blocks provider apply when backend evidence gates are not complete", async () => {
    const blockedControlPlane = {
      ...sampleControlPlane,
      providerCategories: [
        {
          ...sampleControlPlane.providerCategories[0],
          applyGates: {
            ...sampleControlPlane.providerCategories[0].applyGates,
            preflightPassed: false,
            dryRunSuccessful: false,
            exportSnapshotExists: false,
          },
        },
        ...sampleControlPlane.providerCategories.slice(1),
      ],
    };
    render(
      <App
        api={mockApi({
          getControlPlane: vi.fn().mockResolvedValue(blockedControlPlane),
        })}
      />,
    );

    expect(
      await screen.findByText(/provider apply is blocked/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/missing gates: preflight passed/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/missing current-session dry-run evidence/i),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /apply selected provider/i }),
    ).toBeDisabled();
  });

  it("lets operators inspect diagnostics without mutating provider selection", async () => {
    render(<App api={mockApi()} viewerRole="operator" />);

    expect(
      await screen.findByRole("heading", {
        name: /identity provider readiness/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /test readiness through backend/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /apply selected provider/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /save whitelist policy/i }),
    ).not.toBeInTheDocument();
  });

  it("keeps new Admin Console copy in the localization catalog", () => {
    expect(Object.keys(adminConsoleMessages.de).sort()).toEqual(
      Object.keys(adminConsoleMessages.en).sort(),
    );
    expect(adminConsoleMessages.en.effectivePolicyHeading).toBe(
      "Effective policy explanation",
    );
    expect(adminConsoleMessages.en.replacementButton).toBe(
      "Dry-run replacement contract",
    );
    expect(adminConsoleMessages.en.memberPreviewDescription).toContain(
      "hides provider adapters",
    );
    expect(adminConsoleMessages.en.memberStateDescription).toContain(
      "stable capability state",
    );
    expect(adminConsoleMessages.de.memberPreviewDescription).toContain(
      "Provider-Adapter",
    );
    expect(adminConsoleMessages.de.providerSelectionHeading).toBe(
      "Provider-Auswahl und Bereitschaft",
    );
  });

  it("renders German admin chrome without exposing provider setup to members", async () => {
    render(<App api={mockApi()} viewerRole="member" locale="de" />);

    expect(
      await screen.findByRole("heading", {
        name: /weave organisations-admin-konsole/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /mitgliederfähigkeitsvorschau/i }),
    ).toBeInTheDocument();
    expect(screen.getAllByText(/Mitgliedszustand/i).length).toBeGreaterThan(0);
    expect(
      screen.queryByRole("heading", { name: /provider-auswahl/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /ausgewählten provider/i }),
    ).not.toBeInTheDocument();
  });

  it("queues provider readiness tests through the backend API", async () => {
    const api = mockApi();
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.click(
      await screen.findByRole("button", {
        name: /test readiness through backend/i,
      }),
    );

    await waitFor(() =>
      expect(api.testProviderReadiness).toHaveBeenCalledWith("keycloak-realm"),
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      /readiness test queued/i,
    );
  });
});

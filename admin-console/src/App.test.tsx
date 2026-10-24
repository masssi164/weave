import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "./App";
import { AdminControlPlaneApi, AdminApiError, adminConsoleConfig, sampleControlPlane } from "./api";
import { adminConsoleMessages } from "./copy";

const identityFor = (role: string) => ({ accountRef: "acct_" + "1".repeat(32), identityIssuer: adminConsoleConfig.oidcIssuerUrl, organizationId: sampleControlPlane.organization.id, roles: [role] });

function mockApi(
  overrides: Partial<AdminControlPlaneApi> = {},
): AdminControlPlaneApi {
  const actorRead = overrides.getCurrentActor ?? vi.fn().mockResolvedValue(identityFor("owner"));
  const controlPlaneRead = overrides.getControlPlane ?? vi.fn().mockResolvedValue(sampleControlPlane);
  return {
    configuration: adminConsoleConfig,
    getCurrentActor: actorRead,
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
      category: "chat",
      currentAdapter: "synapse-homeserver",
      targetAdapter: "slack",
      readinessState: "ready",
      migrationDryRunRequired: true,
      memberImpactStates: ["available", "degraded", "disabled_by_policy"],
      supportSafe: true,
      providerDiagnosticsRedacted: true,
      cutoverGates: ["Run backend migration dry-run before apply"],
      auditRefs: ["provider-replacement-dry-run-chat"],
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
        canonicalObjects: ["Conversation", "Message"],
        contractRisks: ["External thread metadata needs mapping review"],
        adminNotes: ["Support-safe dry-run"],
        conflicts: ["Thread retention needs operator approval"],
        replacementRequirement: "Review before apply",
      },
      lifecycleExpectations: {
        sourceOfTruthPolicy:
          "Backend declares source of truth per canonical Chat object",
        exportExpectation: "Export evidence is required before cutover.",
        deleteExpectation:
          "Delete/deprovision evidence is required after cutover.",
        deprovisionExpectation: "Deactivate old adapter after verification.",
        rollbackSupportBoundary: "Rollback bounded by provider export support.",
      },
      portableExportImportContract: {
        exportManifestRef: "chat-portable-export-manifest-v0.1",
        importManifestRef: "chat-portable-import-manifest-v0.1",
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
        planRef: "chat-switch-plan-v0.1",
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
        knownLosses: ["External thread metadata needs mapping review"],
        unsupportedData: [],
        rollbackLimits: ["Rollback is bounded by provider export support."],
        releaseClaimBoundaries: ["Provider replacement claims remain bounded by accepted dry-run evidence."],
      },
      boundedProof: {
        proofBoundary: "dry_run_only",
        limitedApplyAllowed: false,
        productionCutoverAllowed: false,
        rollbackRestoreSmokeRequired: true,
        requiredEvidenceRefs: ["provider-replacement-dry-run-chat"],
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
      providerKey: "synapse-homeserver",
      state: "ready",
      summary: "Ready",
    }),
    listAuditEvents: vi.fn().mockResolvedValue(sampleControlPlane.auditEvents),
    listOrganizationInvitations: vi.fn().mockResolvedValue([]),
    createOrganizationInvitation: vi.fn().mockImplementation(
      async (organizationId, request) => ({
        invitationHandle: "inv_handle-123",
        organizationId,
        email: request.email,
        displayName: request.displayName,
        lifecycleStatus: "pending",
        provisioningStatus: "pending",
        requestedRole: request.role,
      }),
    ),
    resendOrganizationInvitation: vi.fn().mockResolvedValue({}),
    revokeOrganizationInvitation: vi.fn().mockResolvedValue(undefined),
    ...overrides,
    getControlPlane: vi.fn(async () => ({ ...(await controlPlaneRead()), currentActor: await actorRead() })),
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
        invitationHandle: "inv_handle-existing",
        organizationId: "weave-dogfood",
        email: "existing@example.test",
        lifecycleStatus: "pending",
        provisioningStatus: "applied",
        requestedRole: "member",
      },
    ]);
    const createOrganizationInvitation = vi.fn().mockResolvedValue({});
    const resendOrganizationInvitation = vi.fn().mockResolvedValue({});
    const revokeOrganizationInvitation = vi.fn().mockResolvedValue(undefined);
    const api = mockApi({
      listOrganizationInvitations,
      createOrganizationInvitation,
      resendOrganizationInvitation,
      revokeOrganizationInvitation,
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
    expect(
      screen.queryByLabelText(/additional product capabilities/i),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /invite member/i }));

    await waitFor(() =>
      expect(createOrganizationInvitation).toHaveBeenCalledWith(
        "weave-dogfood",
        {
          email: "new@example.test",
          displayName: "New Member",
          role: "member",
        },
      ),
    );

    await user.click(
      within(
        screen.getByRole("list", { name: /current keycloak invitations/i }),
      ).getByRole("button", { name: /resend/i }),
    );
    await waitFor(() =>
      expect(resendOrganizationInvitation).toHaveBeenCalledWith(
        "weave-dogfood",
        "inv_handle-existing",
      ),
    );
    await user.click(
      within(
        screen.getByRole("list", { name: /current keycloak invitations/i }),
      ).getByRole("button", { name: /revoke/i }),
    );
    await waitFor(() =>
      expect(revokeOrganizationInvitation).toHaveBeenCalledWith(
        "weave-dogfood",
        "inv_handle-existing",
      ),
    );
    expect(document.body).not.toHaveTextContent(/activation token|client_secret/i);
  }, 15_000);

  it("renders organization, provider, policy, and audit sections from backend control-plane data", async () => {
    render(<App api={mockApi()} />);
    await screen.findByRole("heading", { name: /organization overview/i });

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
    expect(screen.getByRole("link", { name: /organization manifest/i }))
      .toHaveAttribute("href", "/api/platform/config");
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
      screen.getByRole("heading", { name: /platform identity readiness/i }),
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
      screen.queryByRole("heading", { name: /agent runtime control/i, level: 2 }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", {
        name: /provider replacement dry-run results/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /mcp workload boundary/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /role capabilities for your admin session/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /secretref inventory/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /audit trail/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/chat status is ready/i),
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
    expect(screen.getByText(adminConsoleMessages.en.policyWhitelistDescription)).toBeInTheDocument();
  });

  it("renders identity readiness as backend-owned support-safe Workspace Health cards", async () => {
    render(<App api={mockApi()} />);

    expect(
      await screen.findByRole("heading", {
        name: /platform identity readiness/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/realm import readiness state is ready/i),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/federation protocol readiness state is ready/i),
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
    expect(screen.getByText(/member platform-security setup:/i)).toHaveTextContent(
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
    ).toHaveTextContent(/chat: ready for member go-live/i);
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
    ).toHaveTextContent(/keycloak and rbac posture: ready/i);
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

  it("keeps deferred Agent Runtime controls out of the current Admin Console", async () => {
    const api = mockApi();
    render(<App api={api} />);

    expect(
      screen.queryByRole("heading", {
        name: /agent runtime control/i,
        level: 2,
      }),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /load runtime/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /delete runtime state only/i })).not.toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: /mcp workload boundary/i })).toBeInTheDocument();
    expect(screen.getByText(/empty catalog/i)).toBeInTheDocument();
  });

  it.each(["en", "de"] as const)("shows read-only current role capabilities without saving in %s while readiness remains usable", async (locale) => {
    const legacySave = vi.fn();
    const api = Object.assign(mockApi(), { updateWhitelistPolicy: legacySave });
    const user = userEvent.setup();
    const copy = adminConsoleMessages[locale];
    render(<App api={api} locale={locale} />);

    const policy = await screen.findByRole("region", {
      name: copy.policyWhitelistHeading,
    });
    expect(within(policy).getByText(copy.policyWhitelistDescription)).toBeInTheDocument();
    const capabilities = within(policy).getByRole("list", { name: copy.roleCapabilitiesLabel });
    expect(within(capabilities).getAllByRole("listitem").map((item) => item.textContent))
      .toEqual(["chat.read", "files.read"]);
    expect(within(policy).queryByRole("textbox")).not.toBeInTheDocument();
    expect(within(policy).queryByRole("button")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: copy.testReadinessButton }));
    await waitFor(() => expect(api.testProviderReadiness).toHaveBeenCalledWith("synapse-homeserver"));
    expect(legacySave).not.toHaveBeenCalled();
    expect(screen.getByRole("status")).not.toHaveTextContent(/whitelist.*saved|richtlinie.*gespeichert/i);
  });

  it.each([false, true])("distinguishes unavailable role projection from explicit empty grants (available=%s)", async (available) => {
    render(<App api={mockApi({
      getControlPlane: vi.fn().mockResolvedValue({
        ...sampleControlPlane,
        whitelistPolicy: { available, roleCapabilities: [] },
      }),
    })} />);
    const policy = await screen.findByRole("region", {
      name: adminConsoleMessages.en.policyWhitelistHeading,
    });
    expect(within(policy).getByText(available
      ? adminConsoleMessages.en.roleCapabilitiesEmpty
      : adminConsoleMessages.en.roleCapabilitiesUnavailable)).toBeInTheDocument();
    expect(within(policy).queryByText(available
      ? adminConsoleMessages.en.roleCapabilitiesUnavailable
      : adminConsoleMessages.en.roleCapabilitiesEmpty)).not.toBeInTheDocument();
    expect(within(policy).queryByRole("list")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: adminConsoleMessages.en.testReadinessButton })).toBeEnabled();
  });

  it("does not activate a provider from selection metadata even when a fixture claims fresh evidence", async () => {
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
        "chat",
        "synapse-homeserver",
        "recommended_self_hosted_default",
        true,
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

    expect(
      screen.getByRole("checkbox", {
        name: /i confirm i reviewed member impact, rollback evidence, and provider-switch consequences/i,
      }),
    ).toBeDisabled();
    expect(screen.getByText(/verified #1498 operation/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /apply selected provider/i })).toBeDisabled();
    expect(api.selectProvider).toHaveBeenCalledTimes(1);
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
      /active binding is unchanged/i,
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
          category: "people",
          providerKey: "weave-owned",
          choiceModel: "recommended_self_hosted_default",
          dryRun: true,
          evidenceRef: "people-weave-owned-no-expiry",
          issuedAt: "2099-01-01T00:00:00Z",
          supportSafe: true,
        })
        .mockResolvedValueOnce({
          category: "people",
          providerKey: "weave-owned",
          choiceModel: "recommended_self_hosted_default",
          dryRun: true,
          evidenceRef: "people-weave-owned-invalid-expiry",
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
        "chat",
        "synapse-homeserver",
        "recommended_self_hosted_default",
        true,
      ),
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      /active binding is unchanged/i,
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
        expect.objectContaining({ key: "chat" }),
        "synapse-homeserver",
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
      screen.getByText(/what moves: conversation, message/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/what will not move: full automated migration/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/risks: external thread metadata/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/conflicts: thread retention/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/portable export\/import: chat-portable-export/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/evidence refs: provider-switch-preflight/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/audit refs: provider-replacement-dry-run-chat/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/switch plan: chat-switch-plan-v0.1/i),
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

    render(<App api={mockApi({ getCurrentActor: vi.fn().mockResolvedValue(identityFor("member")) })} />);
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
        name: /platform identity readiness/i,
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

  it("does not treat an operator presentation label as a canonical current actor role", async () => {
    render(<App api={mockApi({ getCurrentActor: vi.fn().mockResolvedValue(identityFor("operator")) })} />);
    await screen.findByText(/current session does not permit/i);
    expect(screen.queryByRole("heading", { name: /platform identity readiness/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /apply selected provider|save whitelist policy/i })).not.toBeInTheDocument();
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
    render(<App api={mockApi({ getCurrentActor: vi.fn().mockResolvedValue(identityFor("member")) })} locale="de" />);

    await screen.findByRole("heading", { name: /mitgliederfähigkeitsvorschau/i });
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
      expect(api.testProviderReadiness).toHaveBeenCalledWith(
        "synapse-homeserver",
      ),
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      /readiness test queued/i,
    );
  });
});

describe("default Space setup access boundary", () => {
  it.each(["member", "operator"] as const)("hides provisioning for the %s presentation", async (viewerRole) => {
    render(<App api={mockApi({ getCurrentActor: vi.fn().mockResolvedValue(identityFor(viewerRole)) })} />);
    await screen.findByText(viewerRole === "operator" ? /current session does not permit/i : /backend control-plane data loaded/i);
    expect(screen.queryByRole("button", { name: /create or confirm default space/i })).not.toBeInTheDocument();
  });
  it("does not offer setup before a live control-plane response or after authentication failure", async () => {
    const api = mockApi({ getControlPlane: vi.fn().mockResolvedValue(sampleControlPlane) });
    render(<App api={api} authenticationError="Sign-in failed" />);
    await screen.findByText(/sign in with your organization account/i);
    expect(screen.queryByRole("button", { name: /create or confirm default space/i })).not.toBeInTheDocument();
  });
  it("shows an unverified explicit setup action after live owner load without provisioning", async () => {
    const provisionDefaultSpace = vi.fn();
    render(<App api={mockApi({ provisionDefaultSpace })} />);
    expect(await screen.findByRole("button", { name: /create or confirm default space/i })).toBeEnabled();
    expect(provisionDefaultSpace).not.toHaveBeenCalled();
  });
  it("never offers setup for offline sample state", async () => {
    render(<App api={mockApi({ getControlPlane: vi.fn().mockRejectedValue(new Error("Unavailable")) })} />);
    await screen.findByText(/could not verify current organization access/i);
    expect(screen.queryByRole("button", { name: /create or confirm default space/i })).not.toBeInTheDocument();
  });
});

describe("live administrator authentication boundary", () => {
  it.each([401, 403, 500])("shows truthful access state for HTTP %s without sample setup", async (status) => {
    const api = mockApi({ getCurrentActor: vi.fn().mockRejectedValue(new AdminApiError("private response", status)) });
    const signIn = vi.fn();
    render(<App api={api} onSignIn={signIn} />);
    expect(await screen.findByRole("button", { name: /sign in with organization identity/i })).toBeEnabled();
    expect(screen.queryByRole("heading", { name: /organization overview|guided setup|member invitations/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/sample state|Weave Dogfood|private response/)).not.toBeInTheDocument();
    expect(api.getControlPlane).toHaveBeenCalledOnce();
    await userEvent.click(screen.getByRole("button", { name: /sign in with organization identity/i }));
    expect(signIn).toHaveBeenCalledOnce();
  });
  it("does not mount setup while current identity or live organization is pending", async () => {
    const api = mockApi({ getControlPlane: vi.fn(() => new Promise<never>(() => {})) });
    render(<App api={api} />);
    await waitFor(() => expect(api.getControlPlane).toHaveBeenCalledOnce());
    expect(screen.getByRole("status")).toHaveTextContent(/loading/i);
    expect(screen.queryByRole("button", { name: /invite member|create or confirm/i })).not.toBeInTheDocument();
    expect(screen.queryByText("Weave Dogfood")).not.toBeInTheDocument();
  });
  it("fails closed on current organization mismatch rather than guessing owner authority", async () => {
    render(<App api={mockApi({ getCurrentActor: vi.fn().mockResolvedValue({ ...identityFor("owner"), organizationId: "other-organization" }) })} />);
    await screen.findByText(/current session does not permit/i);
    expect(screen.queryByRole("button", { name: /invite member|create or confirm/i })).not.toBeInTheDocument();
  });
});


describe("live preparation initial-state regressions", () => {
  it.each(["external_existing_provider", "managed_cloud_provider", "not_selected"])("preserves the backend choice model %s before any change", async (choiceModel) => {
    const data = { ...sampleControlPlane, providerCategories: [{ ...sampleControlPlane.providerCategories[0], choiceModel }, ...sampleControlPlane.providerCategories.slice(1)] };
    render(<App api={mockApi({ getControlPlane: vi.fn().mockResolvedValue(data) })} />);
    const input = await screen.findByLabelText(/choice model/i);
    expect(input).toHaveTextContent(choiceModel === "not_selected" ? /recommended self-hosted default/i
      : choiceModel === "managed_cloud_provider" ? /managed cloud provider/i : /external/i);
  });
  it("shows an invitation failure and retries the read without claiming loading", async () => {
    const list = vi.fn().mockRejectedValueOnce(new Error("private failure")).mockResolvedValueOnce([]);
    render(<App api={mockApi({ listOrganizationInvitations: list })} />);
    expect(await screen.findByText(/could not be loaded. Retry the invitation list/i)).toBeInTheDocument();
    expect(screen.queryByText("private failure")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /retry invitation list/i }));
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2));
    expect(await screen.findByText(/no active invitations were returned by Keycloak/i)).toBeInTheDocument();
  });
  it("rejects the wrong live actor issuer before mounting mutation controls", async () => {
    const api = mockApi({ getCurrentActor: vi.fn().mockResolvedValue({ ...identityFor("owner"), identityIssuer: "https://other.example.invalid/realms/weave" }) });
    render(<App api={api} />);
    await screen.findByText(/current session does not permit/i);
    expect(api.getControlPlane).toHaveBeenCalledOnce();
    expect(screen.queryByRole("button", { name: /invite member|create or confirm/i })).not.toBeInTheDocument();
  });
  it("uses the API configuration issuer when no explicit App configuration is supplied", async () => {
    const configuration = { ...adminConsoleConfig, oidcIssuerUrl: "https://identity.example.invalid/realms/current" };
    const api = mockApi({ configuration, getCurrentActor: vi.fn().mockResolvedValue({ ...identityFor("owner"), identityIssuer: configuration.oidcIssuerUrl }) });
    render(<App api={api} />);
    expect(await screen.findByRole("heading", { name: /organization overview/i })).toBeInTheDocument();
  });
});


describe("packaged Admin current actor transport", () => {
  it("mounts first-owner setup from one generated Admin response without calling User me", async () => {
    const config = { apiBaseUrl: "https://api.example.invalid/api", oidcIssuerUrl: "https://identity.example.invalid/realms/weave", oidcClientId: "weave-admin-console" };
    const paths: string[] = [];
    const fetcher = vi.fn(async (input: RequestInfo | URL) => {
      const path = new URL(String(input)).pathname; paths.push(path);
      if (path === "/api/me") throw new Error("Admin bearer must not reach User me");
      if (path === "/api/admin/control-plane") return new Response(JSON.stringify({ organizationId: "live-organization", currentActor: { accountRef: "acct_" + "1".repeat(32), organizationId: "live-organization", identityIssuer: config.oidcIssuerUrl, roles: ["owner"] }, categories: [] }), { status: 200 });
      if (path === "/api/admin/audit/events" || path === "/api/admin/organizations/live-organization/invitations") return new Response("[]", { status: 200 });
      throw new Error("Unexpected Admin request");
    });
    const api = new AdminControlPlaneApi(config, fetcher, () => "fixture-admin-bearer");
    render(<App api={api} />);
    expect(await screen.findByRole("button", { name: /create or confirm default Space/i })).toBeEnabled();
    expect(paths.filter((path) => path === "/api/admin/control-plane")).toHaveLength(1);
    expect(paths).not.toContain("/api/me");
  });
});


it.each([["owner", "member"], ["owner", "unknown"], ["owner", "owner"], ["unknown"]])("does not select owner from inconsistent current roles %j", async (...roles) => {
  render(<App api={mockApi({ getCurrentActor: vi.fn().mockResolvedValue({ ...identityFor("owner"), roles }) })} />);
  await screen.findByText(/current session does not permit/i);
  expect(screen.queryByRole("button", { name: /create or confirm default space/i })).not.toBeInTheDocument();
});

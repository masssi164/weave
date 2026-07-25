import { describe, expect, it, vi } from "vitest";
import { AdminControlPlaneApi, sampleControlPlane } from "./api";
import apiSource from "./api.ts?raw";
import generatedOpenApiSource from "./generated/openapi.ts?raw";

// V01_ADMIN_CONSOLE_MVP: Admin Console may call only Weave backend admin APIs, not optional provider APIs.
describe("AdminControlPlaneApi provider boundary", () => {
  it("uses only Weave admin APIs for Keycloak invitation lifecycle", async () => {
    const calls: Array<{
      url: string;
      method: string;
      body?: string;
      idempotencyKey?: string | null;
    }> = [];
    const invitation = {
      providerInvitationId: "invite-123",
      organizationId: "acme",
      email: "member@example.test",
      lifecycleStatus: "pending",
      provisioningStatus: "pending" as const,
      requestedRole: "member" as const,
    };
    const fetchImpl = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({
        url: String(input),
        method: init?.method ?? "GET",
        body: init?.body as string | undefined,
        idempotencyKey: new Headers(init?.headers).get("Idempotency-Key"),
      });
      if (init?.method === "DELETE") return new Response(null, { status: 204 });
      return new Response(
        JSON.stringify(init?.method === "GET" || !init?.method ? [invitation] : invitation),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    });
    const api = new AdminControlPlaneApi(
      {
        apiBaseUrl: "https://api.example.invalid/api",
        oidcIssuerUrl: "https://auth.example.invalid",
        oidcClientId: "weave-admin-console",
      },
      fetchImpl as typeof fetch,
    );

    await api.listOrganizationInvitations("acme");
    await api.createOrganizationInvitation("acme", {
      email: "member@example.test",
      role: "member",
    });
    await api.resendOrganizationInvitation("acme", "invite-123");
    await api.revokeOrganizationInvitation("acme", "invite-123");

    expect(calls.map(({ url, method }) => `${method} ${url}`)).toEqual([
      "GET https://api.example.invalid/api/admin/organizations/acme/invitations",
      "POST https://api.example.invalid/api/admin/organizations/acme/invitations",
      "POST https://api.example.invalid/api/admin/organizations/acme/invitations/invite-123/resend",
      "DELETE https://api.example.invalid/api/admin/organizations/acme/invitations/invite-123",
    ]);
    expect(calls[1]?.body).toBe(
      JSON.stringify({
        email: "member@example.test",
        role: "member",
      }),
    );
    expect(calls.slice(1).map(({ idempotencyKey }) => idempotencyKey)).toEqual([
      expect.stringMatching(/^admin-console-invitation-create-/),
      expect.stringMatching(/^admin-console-invitation-resend-/),
      expect.stringMatching(/^admin-console-invitation-revoke-/),
    ]);
    expect(calls.map(({ url }) => url).join("\n")).not.toMatch(
      /auth\.example|keycloak|activation/i,
    );
  });

  it("uses generated OpenAPI invitation contracts without parallel DTOs", () => {
    expect(generatedOpenApiSource).toContain(
      "export type GeneratedMemberInvitationRequest",
    );
    expect(generatedOpenApiSource).toContain(
      "export type GeneratedMemberInvitationResponse",
    );
    expect(apiSource).toContain("GeneratedMemberInvitationRequest");
    expect(apiSource).toContain("GeneratedMemberInvitationResponse");
    expect(apiSource).not.toMatch(
      /interface (CreateOrganizationInvitationRequest|OrganizationInvitation)/,
    );
  });

  it("uses backend admin endpoints for provider selection, readiness, and replacement dry-runs", async () => {
    const calls: string[] = [];
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      calls.push(String(input));
      const path = String(input);
      const body = path.includes("/identity/readiness")
        ? {
            contractVersion: "identity-provider-readiness-v1",
            category: "idm-rbac",
            providerKey: "keycloak-realm",
            overallState: "admin-action-required",
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
              "coming_later",
              "disabled",
            ],
            cards: [
              {
                key: "realm-import",
                label: "Realm import readiness",
                state: "admin-action-required",
                summary: "Select an identity provider mapping.",
                memberImpact: "degraded",
                remediation: "Run profile-specific Identity Ops plan and verify tasks.",
                nextActions: ["Run Identity Ops plan and verify"],
                evidenceRefs: ["keycloak-identity-ops-plan", "keycloak-identity-ops-verify"],
              },
              {
                key: "provisioning-source-readiness",
                label: "SCIM, LDAP, and AD provisioning readiness",
                state: "admin-action-required",
                summary: "Fixture-backed provisioning source posture.",
                memberImpact: "degraded",
                remediation: "Prove immutable anchors before member go-live.",
                nextActions: ["Record source of truth"],
                evidenceRefs: ["scim-lifecycle-contract"],
                diagnostics: {
                  scimConceptCovered: true,
                  liveLdapAdConnectorClaimed: false,
                },
              },
            ],
            nextActions: ["Resolve admin-action-required cards"],
          }
        : path.includes("/replacements/dry-run")
          ? {
              status: "dry_run_ready",
              category: "chat-channels",
              currentAdapter: "synapse-homeserver",
              targetAdapter: "slack",
              readinessState: "degraded",
              memberImpactStates: [
                "usable",
                "disabled",
                "degraded",
                "policy-blocked",
              ],
              supportSafe: true,
              providerDiagnosticsRedacted: true,
              lossyMappingReport: {
                canonicalObjects: ["Conversation"],
                contractRisks: [],
                adminNotes: [],
                conflicts: [],
              },
              lifecycleExpectations: {
                exportExpectation: "export first",
                deleteExpectation: "delete after cutover",
              },
              crossDomainImpact: [
                {
                  domainKey: "calendar",
                  canonicalObjectRef: "weave:calendar:event-link/test",
                  mappingClass: "lossy",
                  consequenceSummary: "Meeting links lose provider-specific room metadata.",
                  evidenceRefs: ["impact:test:calendar"],
                  applyBlockers: ["lossy calendar decision required"],
                },
              ],
            }
          : path.includes("/readiness-tests")
            ? { providerKey: "slack", state: "ready", readiness: "ready" }
            : {};
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });
    const api = new AdminControlPlaneApi(
      {
        apiBaseUrl: "https://api.example.invalid/api",
        oidcIssuerUrl: "https://auth.example.invalid",
        oidcClientId: "weave-admin-console",
      },
      fetchImpl as typeof fetch,
    );
    const category = {
      ...sampleControlPlane.providerCategories[1],
      selectedAdapter: "synapse-homeserver",
    };

    await api.selectProvider("chat-channels", "slack");
    const identityReadiness = await api.getIdentityProviderReadiness();
    await api.testProviderReadiness("slack");
    const report = await api.dryRunProviderReplacement(category, "slack");

    expect(identityReadiness.overallState).toBe("admin-action-required");
    expect(identityReadiness.memberClientMayConfigureIdentityProvider).toBe(
      false,
    );
    expect(identityReadiness.cards[0]?.memberImpact).toBe("degraded");
    expect(identityReadiness.stableStates).toContain("coming_later");
    expect(identityReadiness.cards[1]?.diagnostics).toMatchObject({
      scimConceptCovered: true,
      liveLdapAdConnectorClaimed: false,
    });
    expect(report.supportSafe).toBe(true);
    expect(report.memberImpactStates).toEqual([
      "available",
      "disabled_by_policy",
      "degraded",
    ]);
    expect(report.crossDomainImpact[0]).toMatchObject({
      domainKey: "calendar",
      mappingClass: "lossy",
      evidenceRefs: ["impact:test:calendar"],
      applyBlockers: ["lossy calendar decision required"],
    });
    expect(calls).toEqual([
      "https://api.example.invalid/api/admin/providers/selections",
      "https://api.example.invalid/api/admin/identity/readiness",
      "https://api.example.invalid/api/admin/providers/readiness-tests",
      "https://api.example.invalid/api/admin/providers/replacements/dry-run",
    ]);
    expect(calls.join("\n")).not.toMatch(
      /slack\.com|graph\.microsoft\.com|nextcloud|matrix|livekit/i,
    );
  });


  it("normalizes RC go-live release claim gates as support-safe blockers", async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const body = String(input).includes("/audit/events")
        ? []
        : {
            categories: [],
            goLiveReadiness: {
              state: "admin-action-required",
              memberPreviewState: "degraded",
              supportSafe: true,
              rawProviderDiagnosticsExposed: false,
              normalMembersMayAccessSetupControls: false,
              releaseClaimControl: {
                claimState: "admin-action-required",
                candidateTag: "v0.1.0-rc.test",
                pinnedSpecCorpusRef: "specs/weave-specs.lock.json#test",
                releaseNotesSource: "merged PR metadata",
                supportBundleRef: "support-bundle://redacted",
                accessibilityEvidenceRef: "docs/evidence/admin-a11y.md",
                unresolvedVetoes: ["release-blocker-open"],
                gates: [
                  {
                    key: "acceptance",
                    label: "Acceptance evidence",
                    state: "degraded",
                    evidenceFreshness: "stale",
                    evidenceRefs: ["./gradlew acceptanceContract"],
                    nextAction: "rerun on candidate head",
                    blocksReleaseClaim: true,
                  },
                ],
              },
            },
          };
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });
    const api = new AdminControlPlaneApi(
      {
        apiBaseUrl: "https://api.example.invalid/api",
        oidcIssuerUrl: "https://auth.example.invalid",
        oidcClientId: "weave-admin-console",
      },
      fetchImpl as typeof fetch,
    );

    const controlPlane = await api.getControlPlane();

    expect(controlPlane.goLiveReadiness.releaseClaimControl.candidateTag).toBe(
      "v0.1.0-rc.test",
    );
    expect(
      controlPlane.goLiveReadiness.releaseClaimControl.unresolvedVetoes,
    ).toEqual(["release-blocker-open"]);
    expect(controlPlane.goLiveReadiness.releaseClaimControl.gates[0]).toMatchObject({
      key: "acceptance",
      state: "degraded",
      evidenceFreshness: "stale",
      blocksReleaseClaim: true,
    });
    expect(JSON.stringify(controlPlane.goLiveReadiness)).not.toMatch(
      /client_secret|access_token|bearer/i,
    );
  });

  it("does not read the removed v1 Weaver policy or projection fields", async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      const body = path.includes("/audit/events")
        ? []
        : {
            organizationId: "weave-dogfood",
            categories: [],
            whitelist: { denyByDefault: true },
            weaverRuntimeProjection: { unsafe: "obsolete" },
            weaverEligibilityPreview: { unsafe: "obsolete" },
            weaverDistributionPolicy: { unsafe: "obsolete" },
          };
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });
    const api = new AdminControlPlaneApi(
      {
        apiBaseUrl: "https://api.example.invalid/api",
        oidcIssuerUrl: "https://auth.example.invalid",
        oidcClientId: "weave-admin-console",
      },
      fetchImpl as typeof fetch,
    );

    const controlPlane = await api.getControlPlane();

    expect(JSON.stringify(controlPlane)).not.toMatch(
      /weaverRuntimeProjection|weaverEligibilityPreview|weaverDistributionPolicy|obsolete/,
    );
  });

  it("fails closed when older backends omit the optional identity readiness contract", async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      const body = path.includes("/audit/events")
        ? []
        : {
            organizationId: "weave-dogfood",
            categories: [],
            whitelist: { denyByDefault: true },
          };
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });
    const api = new AdminControlPlaneApi(
      {
        apiBaseUrl: "https://api.example.invalid/api",
        oidcIssuerUrl: "https://auth.example.invalid",
        oidcClientId: "weave-admin-console",
      },
      fetchImpl as typeof fetch,
    );

    const controlPlane = await api.getControlPlane();

    expect(controlPlane.identityProviderReadiness.overallState).toBe(
      "admin-action-required",
    );
    expect(controlPlane.identityProviderReadiness.cards[0]).toEqual(
      expect.objectContaining({
        key: "identity-readiness-contract-missing",
        state: "admin-action-required",
        memberImpact: "degraded",
      }),
    );
    expect(controlPlane.identityProviderReadiness.nextActions).toContain(
      "Treat missing identity readiness as admin-action-required and fail closed.",
    );
  });

  it("fails closed when dry-run evidence omits supportSafe or expiresAt", async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      const body = path.includes("/providers/selections")
        ? {
            category: "identity",
            providerKey: "keycloak-realm",
            choiceModel: "recommended_self_hosted_default",
            dryRun: true,
            evidenceRef: "identity-keycloak-ops-plan",
          }
        : {};
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });
    const api = new AdminControlPlaneApi(
      {
        apiBaseUrl: "https://api.example.invalid/api",
        oidcIssuerUrl: "https://auth.example.invalid",
        oidcClientId: "weave-admin-console",
      },
      fetchImpl as typeof fetch,
    );

    const result = await api.selectProvider(
      "identity",
      "keycloak-realm",
      "recommended_self_hosted_default",
      true,
    );

    expect(result.supportSafe).toBe(false);
    expect(result.expiresAt).toBeUndefined();
  });

  it("does not infer dry-run freshness from control-plane generatedAt", async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      const body = path.includes("/audit/events")
        ? []
        : {
            organizationId: "weave-dogfood",
            generatedAt: "2099-01-01T00:00:00Z",
            categories: [
              {
                category: "identity",
                readiness: "ready",
                selectedProviderKey: "keycloak-realm",
              },
              {
                category: "chat-channels",
                readiness: "ready",
                selectedProviderKey: "synapse-homeserver",
                dryRunEvidenceExpiresAt: "not-a-date",
              },
            ],
            whitelist: { denyByDefault: true },
          };
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });
    const api = new AdminControlPlaneApi(
      {
        apiBaseUrl: "https://api.example.invalid/api",
        oidcIssuerUrl: "https://auth.example.invalid",
        oidcClientId: "weave-admin-console",
      },
      fetchImpl as typeof fetch,
    );

    const controlPlane = await api.getControlPlane();

    expect(controlPlane.providerCategories[0]?.lastCheckedAt).toBe(
      "2099-01-01T00:00:00Z",
    );
    expect(controlPlane.providerCategories[0]?.evidenceFreshness).toBe(
      "missing",
    );
    expect(controlPlane.providerCategories[1]?.evidenceFreshness).toBe(
      "missing",
    );
    expect(controlPlane.providerCategories[0]?.supportSafe).toBe(false);
  });

  it("uses the exact Agent Runtime Control lifecycle endpoints", async () => {
    const calls: Array<{
      path: string;
      method: string;
      idempotencyKey?: string;
      body?: Record<string, unknown>;
    }> = [];
    const fetchImpl = vi.fn(
      async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        calls.push({
          path,
          method: init?.method ?? "GET",
          idempotencyKey: new Headers(init?.headers).get("Idempotency-Key") ?? undefined,
          body: init?.body ? JSON.parse(String(init.body)) : undefined,
        });
        const body = {
          personRef: "acct_0123456789abcdef0123456789abcdef",
          cellRef: "cell_01",
          runtimeProvider: "weaver-openclaw",
          entitlementState: path.endsWith("/revoke") ? "revoked" : "entitled",
          entitlementRevision: "entitlement-rev-7",
          desiredState: path.endsWith("/runtime-state") ? "deleted" : "ready",
          observedState: path.endsWith("/runtime-state") ? "deleted" : "ready",
          runtimeProfileRef: "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          workspaceRevision: "workspace-rev-3",
          conflicts: 0,
          capabilityState: "ready",
          auditRef: "audit://agent-runtime-control/test",
        };
        return new Response(JSON.stringify(body), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      },
    );
    const api = new AdminControlPlaneApi(
      {
        apiBaseUrl: "https://api.example.invalid/api",
        oidcIssuerUrl: "https://auth.example.invalid",
        oidcClientId: "weave-admin-console",
      },
      fetchImpl as typeof fetch,
    );

    const personRef = "acct_0123456789abcdef0123456789abcdef";
    const loaded = await api.getAgentRuntime(personRef);
    await api.changeAgentRuntime(personRef, "provision", "idem-provision-0001");
    const revoked = await api.changeAgentRuntime(
      personRef,
      "revoke",
      "idem-revocation-0001",
      { reason: "Entitlement removed", entitlementRevision: "entitlement-rev-7" },
    );
    await api.changeAgentRuntime(
      personRef,
      "delete-runtime-state",
      "idem-delete-state-0001",
      { reason: "Member requested runtime-state deletion" },
    );

    expect(loaded.runtimeProvider).toBe("weaver-openclaw");
    expect(revoked.entitlementState).toBe("revoked");
    expect(calls.map((call) => call.path)).toEqual([
      `https://api.example.invalid/api/admin/agent-runtimes/${personRef}`,
      `https://api.example.invalid/api/admin/agent-runtimes/${personRef}/provision`,
      `https://api.example.invalid/api/admin/agent-runtimes/${personRef}/revoke`,
      `https://api.example.invalid/api/admin/agent-runtimes/${personRef}/runtime-state`,
    ]);
    expect(calls[1]).toMatchObject({ method: "POST", idempotencyKey: "idem-provision-0001" });
    expect(calls[2]?.body).toEqual({
      reason: "Entitlement removed",
      entitlementRevision: "entitlement-rev-7",
    });
    expect(calls[3]?.body).toEqual({
      reason: "Member requested runtime-state deletion",
      confirmation: "DELETE_RUNTIME_STATE_ONLY",
    });
    expect(calls.map((call) => call.path).join("\n")).not.toMatch(
      /slack\.com|graph\.microsoft\.com|matrix|openclaw\.json/i,
    );
  });
});

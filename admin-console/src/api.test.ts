import { describe, expect, it, vi } from "vitest";
import { AdminControlPlaneApi, sampleControlPlane } from "./api";

// V01_ADMIN_CONSOLE_MVP: Admin Console may call only Weave backend admin APIs, not optional provider APIs.
describe("AdminControlPlaneApi provider boundary", () => {
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
              "disabled",
            ],
            cards: [
              {
                key: "realm-import",
                label: "Realm import readiness",
                state: "admin-action-required",
                summary: "Select an identity provider mapping.",
                memberImpact: "degraded",
                remediation: "Run backend realm dry-run.",
                nextActions: ["Run dry-run"],
                evidenceRefs: ["identity-realm-dry-run"],
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

  it("normalizes Weaver projection labels without exposing unsafe runtime details", async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      const body = path.includes("/audit/events")
        ? []
        : {
            organizationId: "weave-dogfood",
            categories: [],
            whitelist: { denyByDefault: true },
            weaverRuntimeProjection: {
              profileVersion: "weaver-runtime-profile-v1",
              runtimeProfileHash: "sha256:test-profile",
              expiresAt: "2099-01-02T00:00:00Z",
              supportSafe: true,
              providerDiagnosticsRedacted: true,
              rawRuntimeInternalsExposed: false,
              auditReceiptRefs: ["receipt://weaver/runtime/test-audit"],
              pendingRevocationRefs: ["receipt://weaver/runtime/test-revoke"],
              items: [
                {
                  id: "chat-route",
                  category: "chat",
                  label: "Weave Chat domain route",
                  state: "ready",
                  memberImpact: "available",
                  policyImpact: "Stable chat projection is preserved.",
                  readinessSummary: "Backend dry-run passed.",
                  receiptRefs: ["receipt://weaver/chat-route"],
                },
                {
                  id: "unsafe",
                  category: "tool",
                  label: "openclaw.json bearer token",
                  state: "ready",
                },
              ],
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

    expect(controlPlane.weaverRuntimeProjection.supportSafe).toBe(true);
    expect(controlPlane.weaverRuntimeProjection.rawRuntimeInternalsExposed).toBe(
      false,
    );
    expect(controlPlane.weaverRuntimeProjection.items).toHaveLength(1);
    expect(controlPlane.weaverRuntimeProjection.items[0]).toEqual(
      expect.objectContaining({
        category: "chat",
        label: "Weave Chat domain route",
        receiptRefs: ["receipt://weaver/chat-route"],
      }),
    );
    expect(JSON.stringify(controlPlane.weaverRuntimeProjection)).not.toMatch(
      /openclaw\.json|bearer|token/i,
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
            evidenceRef: "identity-keycloak-realm-dry-run",
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

  it("uses backend admin endpoints for Weaver distribution policy and RuntimeProfile revocation", async () => {
    const calls: Array<{ path: string; body?: Record<string, unknown> }> = [];
    const fetchImpl = vi.fn(
      async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        calls.push({
          path,
          body: init?.body ? JSON.parse(String(init.body)) : undefined,
        });
        const body = path.includes("/weaver/runtime-profiles/revocations")
          ? {
              runtimeProfileHash: "wrp_active_hash",
              revocationState: "revocation_pending",
              auditRefs: ["audit://weaver/revocation/requested"],
            }
          : {
              chatProviderKey: "slack",
              chatReadinessState: "ready",
              modelAliases: [
                {
                  alias: "general-assistant",
                  provider: "weave-approved-openai",
                  model: "gpt-4.1-mini",
                  userSelectable: true,
                },
              ],
              defaultModelAlias: "general-assistant",
              fallbackModelAliases: ["general-assistant"],
              allowedTools: ["chat.search_messages"],
              allowedSkills: ["weave-user-help"],
              mcpServers: [
                {
                  serverKey: "weave-facade-mcp",
                  tools: ["chat.search_messages"],
                  approvalRequired: false,
                },
              ],
              runtimeProfileHash: "wrp_active_hash",
              auditRefs: ["audit://weaver/policy-preview"],
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

    const saved = await api.updateWeaverDistributionPolicy({
      ...sampleControlPlane.weaverDistributionPolicy,
      chatProviderKey: "slack",
      allowedTools: ["chat.search_messages"],
    });
    const revoked = await api.revokeRuntimeProfile("wrp_active_hash");

    expect(saved.chatProviderKey).toBe("slack");
    expect(saved.effectivePolicyPreview.join("\n")).toContain(
      "chat.provider=slack",
    );
    expect(revoked.revocationState).toBe("revocation_pending");
    expect(calls.map((call) => call.path)).toEqual([
      "https://api.example.invalid/api/admin/weaver/distribution-policy",
      "https://api.example.invalid/api/admin/weaver/runtime-profiles/revocations",
    ]);
    expect(calls[0]?.body).toEqual(
      expect.objectContaining({
        chatProviderKey: "slack",
        reason:
          "Updated Weaver distribution policy through Organization/Admin Console",
      }),
    );
    expect(calls[1]?.body).toEqual(
      expect.objectContaining({
        runtimeProfileHash: "wrp_active_hash",
        reason: "Revoked through Organization/Admin Console",
      }),
    );
    expect(calls.map((call) => call.path).join("\n")).not.toMatch(
      /slack\.com|graph\.microsoft\.com|matrix|openclaw\.json/i,
    );
  });
});

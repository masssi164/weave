import { describe, expect, it, vi } from "vitest";
import { AdminControlPlaneApi, sampleControlPlane } from "./api";

const actorFor = (organizationId: string, identityIssuer = "https://auth.example.invalid") => ({ accountRef: "acct_" + "1".repeat(32), organizationId, identityIssuer, roles: ["owner"] });

// V01_ADMIN_CONSOLE_MVP: Admin Console may call only Weave backend admin APIs, not optional provider APIs.
describe("AdminControlPlaneApi provider boundary", () => {
  it("projects only current role grants without unioning other capability profiles", async () => {
    const fetcher = vi.fn(async (input: RequestInfo | URL) => new Response(JSON.stringify(
      String(input).includes("/audit/events") ? [] : {
        organizationId: "fixture-organization",
        currentActor: actorFor("fixture-organization"),
        whitelist: {
          effectiveCapabilities: ["files.read", "files.read"],
          profileCapabilities: {
            "workspace-admin": ["admin.policy.edit"],
            "group:/capabilities/weaver": ["agent-runtime.entitled"],
          },
        },
      },
    ), { status: 200, headers: { "Content-Type": "application/json" } }));
    const api = new AdminControlPlaneApi({ apiBaseUrl: "https://api.example.invalid/api", oidcIssuerUrl: "https://auth.example.invalid", oidcClientId: "weave-admin-console" }, fetcher);

    expect((await api.getControlPlane()).whitelistPolicy).toEqual({
      available: true,
      roleCapabilities: ["files.read"],
    });
  });

  it.each([
    { name: "missing whitelist", whitelist: undefined, available: false },
    { name: "missing current grants", whitelist: { profileCapabilities: { "workspace-admin": ["admin.policy.edit"] } }, available: false },
    { name: "invalid current grants", whitelist: { effectiveCapabilities: "files.read" }, available: false },
    { name: "explicit empty grants", whitelist: { effectiveCapabilities: [] }, available: true },
  ])("distinguishes $name when reading the current role projection", async ({ whitelist, available }) => {
    const fetcher = vi.fn(async (input: RequestInfo | URL) => new Response(JSON.stringify(
      String(input).includes("/audit/events") ? [] : {
        organizationId: "fixture-organization",
        currentActor: actorFor("fixture-organization"),
        whitelist,
      },
    ), { status: 200, headers: { "Content-Type": "application/json" } }));
    const api = new AdminControlPlaneApi({ apiBaseUrl: "https://api.example.invalid/api", oidcIssuerUrl: "https://auth.example.invalid", oidcClientId: "weave-admin-console" }, fetcher);

    expect((await api.getControlPlane()).whitelistPolicy).toEqual({
      available,
      roleCapabilities: [],
    });
  });

  it("uses the generated Admin current actor with its own bearer and retains unauthorized status", async () => {
    const received: Array<{url: string; bearer: string | null}> = [];
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      received.push({ url: String(input), bearer: new Headers(init?.headers).get("Authorization") });
      return new Response(null, { status: 401 });
    });
    const api = new AdminControlPlaneApi({ apiBaseUrl: "https://api.example.invalid/api", oidcIssuerUrl: "https://identity.example.invalid", oidcClientId: "weave-admin-console" }, fetcher, () => "current-private-bearer");
    await expect(api.getCurrentActor()).rejects.toMatchObject({ status: 401 });
    expect(received).toEqual([{ url: "https://api.example.invalid/api/admin/control-plane", bearer: "Bearer current-private-bearer" }]);
  });
  it("never substitutes a Dogfood organization when the live response lacks identity", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ categories: [] }), { status: 200 }));
    const api = new AdminControlPlaneApi({ apiBaseUrl: "https://api.example.invalid/api", oidcIssuerUrl: "https://identity.example.invalid", oidcClientId: "weave-admin-console" }, fetcher);
    await expect(api.getControlPlane()).rejects.toMatchObject({ status: 502 });
  });

  it("uses the generated Admin selection model without inventing cutover evidence", async () => {
    let requestBody: Record<string, unknown> | undefined;
    const fetchImpl = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      requestBody = JSON.parse(String(init?.body)) as Record<string, unknown>;
      return new Response(
        JSON.stringify({
          category: "chat",
          providerKey: "weave-native",
          choiceModel: "recommended_self_hosted_default",
          dryRun: true,
          supportSafe: true,
          applied: false,
        }),
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

    const result = await api.selectProvider("chat", "weave-native", undefined, true);

    expect(requestBody).toEqual({
      category: "chat",
      providerKey: "weave-native",
      choiceModel: "recommended_self_hosted_default",
      dryRun: true,
      secretRef: "secretref://weave/provider/weave-native",
      reason: "Dry-run of provider-selection metadata through Organization/Admin Console",
    });
    expect(result.evidenceRef).toBeUndefined();
    expect(result.dryRun).toBe(true);
    expect(result.supportSafe).toBe(true);
  });

  it("does not render an invalid generated audit timestamp", async () => {
    const fetchImpl = vi.fn(async () =>
      new Response(
        JSON.stringify([{ action: "provider-check", occurredAt: "not-a-date" }]),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    const api = new AdminControlPlaneApi(
      {
        apiBaseUrl: "https://api.example.invalid/api",
        oidcIssuerUrl: "https://auth.example.invalid",
        oidcClientId: "weave-admin-console",
      },
      fetchImpl as typeof fetch,
    );

    const events = await api.listAuditEvents();
    expect(events[0]?.createdAt).toBe("");
    expect(events[0]?.id).toBe("provider-check-unknown");
  });

  it("reads the current Admin bearer for each generated request and preserves HTTP denial", async () => {
    let token = "admin-session-one";
    const received: Array<string | null> = [];
    const fetchImpl = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const bearer = new Headers(init?.headers).get("Authorization");
      received.push(bearer);
      return bearer === "Bearer member-session"
        ? new Response(null, { status: 403 })
        : new Response("[]", {
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
      () => token,
    );

    await api.listOrganizationInvitations("acme");
    token = "admin-session-two";
    await api.listOrganizationInvitations("acme");
    token = "member-session";
    await expect(api.listOrganizationInvitations("acme")).rejects.toMatchObject({
      status: 403,
    });
    expect(received).toEqual([
      "Bearer admin-session-one",
      "Bearer admin-session-two",
      "Bearer member-session",
    ]);
  });

  it("uses only Weave admin APIs for Keycloak invitation lifecycle", async () => {
    const calls: Array<{
      url: string;
      method: string;
      body?: string;
      idempotencyKey: string | null;
    }> = [];
    const invitation = {
      invitationHandle: "inv_handle-123",
      organizationId: "acme",
      email: "member@example.test",
      lifecycleStatus: "pending",
      provisioningStatus: "pending" as const,
      requestedRole: "member" as const,
    };
    const fetchImpl = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const headers = new Headers(init?.headers);
      calls.push({
        url: String(input),
        method: init?.method ?? "GET",
        body: init?.body as string | undefined,
        idempotencyKey: headers.get("Idempotency-Key"),
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
    await api.resendOrganizationInvitation("acme", "inv_handle/123");
    await api.revokeOrganizationInvitation("acme", "inv_handle/123");

    expect(calls.map(({ url, method }) => `${method} ${url}`)).toEqual([
      "GET https://api.example.invalid/api/admin/organizations/acme/invitations",
      "POST https://api.example.invalid/api/admin/organizations/acme/invitations",
      "POST https://api.example.invalid/api/admin/organizations/acme/invitations/inv_handle%2F123/resend",
      "DELETE https://api.example.invalid/api/admin/organizations/acme/invitations/inv_handle%2F123",
    ]);
    expect(calls[1]?.body).toBe(
      JSON.stringify({
        email: "member@example.test",
        role: "member",
      }),
    );
    expect(calls[0]?.idempotencyKey).toBeNull();
    expect(calls[1]?.idempotencyKey).toMatch(
      /^admin-invitation-create-[0-9a-f]{32}$/,
    );
    expect(calls[2]?.idempotencyKey).toMatch(
      /^admin-invitation-resend-[0-9a-f]{32}$/,
    );
    expect(calls[3]?.idempotencyKey).toMatch(
      /^admin-invitation-revoke-[0-9a-f]{32}$/,
    );
    expect(
      new Set(calls.slice(1).map(({ idempotencyKey }) => idempotencyKey)).size,
    ).toBe(3);
    expect(calls.map(({ url }) => url).join("\n")).not.toMatch(
      /auth\.example|keycloak|activation/i,
    );
  });

  it("uses backend admin endpoints for provider selection, readiness, and replacement dry-runs", async () => {
    const calls: string[] = [];
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      calls.push(String(input));
      const path = String(input);
      const body = path.includes("/platform/identity/readiness")
        ? {
            contractVersion: "platform-identity-readiness-v1",
            platformAuthority: "keycloak",
            overallState: "admin-action-required",
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
              "coming_later",
              "disabled",
            ],
            cards: [
              {
                key: "realm-import",
                label: "Realm import readiness",
                state: "admin-action-required",
                summary: "Keycloak Desired State is not yet current.",
                memberImpact: "degraded",
                remediation: "Run backend realm dry-run.",
                nextActions: ["Run dry-run"],
                evidenceRefs: ["identity-realm-dry-run"],
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
    const identityReadiness = await api.getPlatformIdentityReadiness();
    await api.testProviderReadiness("slack");
    const report = await api.dryRunProviderReplacement(category, "slack");

    expect(identityReadiness.overallState).toBe("admin-action-required");
    expect(identityReadiness.memberClientMayConfigurePlatformSecurity).toBe(
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
      "https://api.example.invalid/api/admin/platform/identity/readiness",
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
            organizationId: "fixture-organization", currentActor: actorFor("fixture-organization"),
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
            organizationId: "weave-dogfood", currentActor: actorFor("weave-dogfood"),
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

    expect(controlPlane.organization.manifestUrl).toBe("/api/platform/config");
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
            organizationId: "weave-dogfood", currentActor: actorFor("weave-dogfood"),
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

    expect(controlPlane.platformIdentityReadiness.overallState).toBe(
      "admin-action-required",
    );
    expect(controlPlane.platformIdentityReadiness.cards[0]).toEqual(
      expect.objectContaining({
        key: "platform-identity-readiness-contract-missing",
        state: "admin-action-required",
        memberImpact: "degraded",
      }),
    );
    expect(controlPlane.platformIdentityReadiness.nextActions).toContain(
      "Treat missing platform-identity readiness as admin-action-required and fail closed.",
    );
  });

  it("fails closed when dry-run evidence omits supportSafe or expiresAt", async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      const body = path.includes("/providers/selections")
        ? {
            category: "chat",
            providerKey: "synapse-homeserver",
            choiceModel: "recommended_self_hosted_default",
            dryRun: true,
            evidenceRef: "chat-synapse-homeserver-dry-run",
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
      "chat",
      "synapse-homeserver",
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
            organizationId: "weave-dogfood", currentActor: actorFor("weave-dogfood"),
            generatedAt: "2099-01-01T00:00:00Z",
            categories: [
              {
                category: "chat-channels",
                readiness: "ready",
                providerRealityLevel: "live_read",
                policyState: "allowed",
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
      "2099-01-01T00:00:00.000Z",
    );
    expect(controlPlane.providerCategories[0]?.evidenceFreshness).toBe(
      "missing",
    );
    expect(controlPlane.providerCategories[0]?.realityLevel).toBe("live_read");
    expect(controlPlane.providerCategories[0]?.policyState).toBe("allowed");
    expect(controlPlane.providerCategories[0]?.applyGates.applySupported).toBe(false);
    expect(controlPlane.providerCategories).toHaveLength(1);
    expect(controlPlane.providerCategories[0]?.supportSafe).toBe(false);
  });


});

describe("default Space provisioning boundary", () => {
  const config = { apiBaseUrl: "https://api.example.invalid/api", oidcIssuerUrl: "https://auth.example.invalid", oidcClientId: "weave-admin-console" };
  it("requires an authenticated session before any request", async () => {
    const fetchImpl = vi.fn();
    await expect(new AdminControlPlaneApi(config, fetchImpl).provisionDefaultSpace()).rejects.toMatchObject({ status: 401 });
    expect(fetchImpl).not.toHaveBeenCalled();
  });
  it("uses the generated Admin operation with only the acting owner and preserves conflicts", async () => {
    const fetchImpl = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response(null, { status: 409 }));
    const api = new AdminControlPlaneApi(config, fetchImpl, () => "current-admin-session");
    await expect(api.provisionDefaultSpace()).rejects.toMatchObject({ status: 409 });
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    const [url, request] = fetchImpl.mock.calls[0];
    expect(url).toBe("https://api.example.invalid/api/admin/spaces");
    expect(request?.method).toBe("POST");
    expect(JSON.parse(String(request?.body))).toEqual({ spaceRef: "workspace-default", displayName: "Organization workspace", memberAccountRefs: [] });
    expect(new Headers(request?.headers).get("Authorization")).toBe("Bearer current-admin-session");
  });
});

describe("conditional preparation API boundaries", () => {
  const config = { apiBaseUrl: "https://api.example.invalid/api", oidcIssuerUrl: "https://identity.example.invalid/realms/weave", oidcClientId: "weave-admin-console" };
  it("exhausts current member pages and rejects repeated cursors", async () => {
    const calls: string[] = [];
    const fetcher = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input)); calls.push(url.searchParams.get("cursor") ?? "first");
      return new Response(JSON.stringify({ items: [{ accountRef: calls.length === 1 ? "owner" : "member", capabilities: [], enabled: true }], nextCursor: calls.length === 1 ? "next" : null }), { status: 200 });
    });
    const api = new AdminControlPlaneApi(config, fetcher);
    expect((await api.listAllOrganizationMembers("organization")).map((member) => member.accountRef)).toEqual(["owner", "member"]);
    expect(calls).toEqual(["first", "next"]);
    const looping = new AdminControlPlaneApi(config, vi.fn().mockImplementation(async () => new Response(JSON.stringify({ items: [], nextCursor: "same" }), { status: 200 })));
    await expect(looping.listAllOrganizationMembers("organization")).rejects.toMatchObject({ status: 502 });
  });
  it("distinguishes missing membership from revoked or missing Space and uses only conditional EDIT creation", async () => {
    const responses = [new Response(JSON.stringify({ code: "space-membership-absent" }), { status: 404 }),
      new Response(JSON.stringify({ code: "space-membership-revoked" }), { status: 410 }),
      new Response(JSON.stringify({ code: "space-not-found" }), { status: 404 }),
      new Response(JSON.stringify({ accountRef: "account", permissions: ["VIEW", "EDIT"] }), { status: 200, headers: { ETag: '"grant-1"' } }),
      new Response(JSON.stringify({ accountRef: "account", permissions: ["VIEW", "EDIT"] }), { status: 200 })];
    const requests: RequestInit[] = [];
    const api = new AdminControlPlaneApi(config, vi.fn(async (_input, init) => { requests.push(init ?? {}); return responses.shift()!; }), () => "private-session");
    expect(await api.readPreparationGrant("workspace-default", "account")).toBeNull();
    await expect(api.readPreparationGrant("workspace-default", "account")).rejects.toMatchObject({ status: 410 });
    await expect(api.readPreparationGrant("workspace-default", "account")).rejects.toMatchObject({ status: 404 });
    expect((await api.readPreparationGrant("workspace-default", "account"))?.version).toBe('"grant-1"');
    await api.createPreparationGrant("workspace-default", "account");
    const sent = requests.at(-1)!;
    expect(new Headers(sent.headers).get("If-None-Match")).toBe("*");
    expect(new Headers(sent.headers).get("If-Match")).toBeNull();
    expect(JSON.parse(String(sent.body))).toEqual({ permissionLevel: "EDIT" });
  });
});


describe("generated Admin current actor conformance", () => {
  const config = { apiBaseUrl: "https://api.example.invalid/api", oidcIssuerUrl: "https://identity.example.invalid/realms/weave", oidcClientId: "weave-admin-console" };
  it("never sends the Admin bearer to User me and revalidates fresh actor data on every read", async () => {
    let accountRef = "acct_" + "2".repeat(32);
    const paths: string[] = [];
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = new URL(String(input)).pathname; paths.push(path);
      if (path === "/api/me") throw new Error("User endpoint must never receive the Admin bearer");
      expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer current-admin-session");
      if (path === "/api/admin/audit/events") return new Response("[]", { status: 200 });
      expect(path).toBe("/api/admin/control-plane");
      return new Response(JSON.stringify({ organizationId: "current-organization", currentActor: { accountRef, organizationId: "current-organization", identityIssuer: config.oidcIssuerUrl, roles: ["admin"] }, categories: [] }), { status: 200 });
    });
    const api = new AdminControlPlaneApi(config, fetcher, () => "current-admin-session");
    expect((await api.getControlPlane()).currentActor.accountRef).toBe("acct_" + "2".repeat(32));
    accountRef = "acct_" + "3".repeat(32);
    expect((await api.getCurrentActor()).accountRef).toBe("acct_" + "3".repeat(32));
    expect(paths.filter((path) => path === "/api/admin/control-plane")).toHaveLength(2);
    expect(paths).not.toContain("/api/me");
  });
  it.each(["missing", "account", "malformed-account", "organization", "issuer", "roles", "mixed-roles", "unknown-role", "duplicate-roles"])("fails closed for %s actor data from the real generated transport", async (invalid) => {
    const currentActor = { ...actorFor("current-organization", config.oidcIssuerUrl),
      ...(invalid === "account" ? { accountRef: "" } : invalid === "malformed-account" ? { accountRef: "raw-provider-id" } : invalid === "organization" ? { organizationId: "another-organization" }
        : invalid === "issuer" ? { identityIssuer: "https://another.example.invalid" } : invalid === "roles" ? { roles: [] } : invalid === "mixed-roles" ? { roles: ["owner", "member"] }
          : invalid === "unknown-role" ? { roles: ["owner", "unknown"] } : invalid === "duplicate-roles" ? { roles: ["owner", "owner"] } : {}) };
    const fetcher = vi.fn(async (input: RequestInfo | URL) => {
      if (new URL(String(input)).pathname === "/api/me") throw new Error("Forbidden User identity operation");
      return new Response(JSON.stringify({ organizationId: "current-organization", ...(invalid === "missing" ? {} : { currentActor }) }), { status: 200 });
    });
    const api = new AdminControlPlaneApi(config, fetcher, () => "admin-session");
    await expect(api.getCurrentActor()).rejects.toMatchObject({ status: 403 });
    await expect(api.getControlPlane()).rejects.toMatchObject({ status: 403 });
  });
});

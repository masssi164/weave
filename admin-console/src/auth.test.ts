import { beforeEach, describe, expect, it, vi } from "vitest";
import { resolveAdminConsoleConfig } from "./api";
import { BrowserOidcPkceClient, type BrowserOidcDependencies } from "./auth";

const config = {
  apiBaseUrl: "https://admin.example.invalid/api",
  oidcIssuerUrl: "https://auth.example.invalid/realms/weave",
  oidcClientId: "weave-admin-console",
};

function browser(fetchImpl: typeof fetch = vi.fn() as unknown as typeof fetch) {
  const location = {
    origin: "https://admin.example.invalid",
    search: "",
    assign: vi.fn(),
  };
  const history = { replaceState: vi.fn() };
  const dependencies: BrowserOidcDependencies = {
    crypto: globalThis.crypto,
    fetch: fetchImpl,
    storage: sessionStorage,
    location,
    history,
    applicationBasePath: "/admin-console/",
  };
  return { dependencies, location, history };
}

beforeEach(() => sessionStorage.clear());

describe("BrowserOidcPkceClient", () => {
  it("creates only an Authorization Code + PKCE S256 browser request", async () => {
    const surface = browser();
    const client = new BrowserOidcPkceClient(config, surface.dependencies);

    const authorizationUrl = new URL(await client.createAuthorizationUrl());

    expect(authorizationUrl.origin).toBe("https://auth.example.invalid");
    expect(authorizationUrl.pathname).toBe(
      "/realms/weave/protocol/openid-connect/auth",
    );
    expect(authorizationUrl.searchParams.get("response_type")).toBe("code");
    expect(authorizationUrl.searchParams.get("client_id")).toBe(
      "weave-admin-console",
    );
    expect(authorizationUrl.searchParams.get("redirect_uri")).toBe(
      "https://admin.example.invalid/admin-console/",
    );
    expect(authorizationUrl.searchParams.get("code_challenge_method")).toBe(
      "S256",
    );
    expect(authorizationUrl.searchParams.get("code_challenge")).toMatch(
      /^[A-Za-z0-9_-]{43}$/,
    );
    expect(authorizationUrl.searchParams.get("state")).toMatch(
      /^[A-Za-z0-9_-]{43}$/,
    );
    expect(authorizationUrl.toString()).not.toMatch(
      /code_verifier|client_secret|\/admin\/realms\//i,
    );
  });

  it("exchanges the callback code without a client secret or admin REST call", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    const fetchImpl = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ url: String(input), init });
      return new Response(
        JSON.stringify({ access_token: "short-lived-browser-token", token_type: "Bearer" }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    });
    const surface = browser(fetchImpl as typeof fetch);
    const client = new BrowserOidcPkceClient(config, surface.dependencies);
    const authorizationUrl = new URL(await client.createAuthorizationUrl());
    surface.location.search = new URLSearchParams({
      code: "one-time-code",
      state: authorizationUrl.searchParams.get("state")!,
    }).toString();

    await expect(client.completeAuthorizationCallback()).resolves.toBe(true);

    expect(calls).toHaveLength(1);
    expect(calls[0]?.url).toBe(
      "https://auth.example.invalid/realms/weave/protocol/openid-connect/token",
    );
    const body = calls[0]?.init?.body as URLSearchParams;
    expect(body.get("grant_type")).toBe("authorization_code");
    expect(body.get("code")).toBe("one-time-code");
    expect(body.get("code_verifier")).toMatch(/^[A-Za-z0-9_-]{80,}$/);
    expect(body.get("client_secret")).toBeNull();
    expect(client.token()).toBe("short-lived-browser-token");
    expect(surface.history.replaceState).toHaveBeenCalledWith(
      null,
      "",
      "https://admin.example.invalid/admin-console/",
    );
    expect(JSON.stringify(calls)).not.toMatch(/\/admin\/realms\//i);
  });

  it("fails closed before token exchange when callback state does not match", async () => {
    const fetchImpl = vi.fn();
    const surface = browser(fetchImpl as unknown as typeof fetch);
    const client = new BrowserOidcPkceClient(config, surface.dependencies);
    await client.createAuthorizationUrl();
    surface.location.search = "?code=one-time-code&state=attacker-state";

    await expect(client.completeAuthorizationCallback()).rejects.toThrow(
      /state or PKCE verifier is missing or invalid/i,
    );
    expect(fetchImpl).not.toHaveBeenCalled();
    expect(client.token()).toBeUndefined();
  });

  it("rejects a Keycloak administration URL as a browser issuer", () => {
    expect(
      () =>
        new BrowserOidcPkceClient(
          { ...config, oidcIssuerUrl: "https://auth.example.invalid/admin/realms/weave" },
          browser().dependencies,
        ),
    ).toThrow(/must not be a Keycloak administration URL/i);
  });
});

describe("production runtime configuration", () => {
  it("discovers the public issuer while keeping packaged API calls same-origin", async () => {
    const fetchImpl = vi.fn(async () =>
      new Response(
        JSON.stringify({
          oidc: { issuer: "https://auth.runtime.invalid/realms/weave" },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );

    const resolved = await resolveAdminConsoleConfig(
      fetchImpl as typeof fetch,
      "https://admin.runtime.invalid",
    );

    expect(fetchImpl).toHaveBeenCalledWith(
      "https://admin.runtime.invalid/api/platform/config",
      { headers: { Accept: "application/json" } },
    );
    expect(resolved).toEqual({
      apiBaseUrl: "https://admin.runtime.invalid/api",
      oidcIssuerUrl: "https://auth.runtime.invalid/realms/weave",
      oidcClientId: "weave-admin-console",
    });
  });
});

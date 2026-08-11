import type { AdminConsoleConfig } from "./api";

const STATE_KEY = "weave.admin.oidc.state";
const VERIFIER_KEY = "weave.admin.oidc.pkce-verifier";

interface BrowserLocation {
  origin: string;
  search: string;
  assign(url: string): void;
}

interface BrowserHistory {
  replaceState(data: unknown, unused: string, url?: string | URL | null): void;
}

export interface BrowserOidcDependencies {
  crypto: Crypto;
  fetch: typeof fetch;
  storage: Storage;
  location: BrowserLocation;
  history: BrowserHistory;
  applicationBasePath: string;
}

interface TokenResponse {
  access_token?: string;
  token_type?: string;
}

export class BrowserOidcPkceClient {
  private accessToken: string | undefined;

  constructor(
    private readonly config: AdminConsoleConfig,
    private readonly browser: BrowserOidcDependencies = {
      crypto: globalThis.crypto,
      fetch: globalThis.fetch,
      storage: globalThis.sessionStorage,
      location: globalThis.location,
      history: globalThis.history,
      applicationBasePath: import.meta.env.BASE_URL,
    },
  ) {
    assertBrowserIssuer(config.oidcIssuerUrl);
  }

  token(): string | undefined {
    return this.accessToken;
  }

  async createAuthorizationUrl(): Promise<string> {
    const state = randomBase64Url(this.browser.crypto, 32);
    const verifier = randomBase64Url(this.browser.crypto, 64);
    const challenge = await sha256Base64Url(this.browser.crypto, verifier);
    this.browser.storage.setItem(STATE_KEY, state);
    this.browser.storage.setItem(VERIFIER_KEY, verifier);

    const authorizationUrl = oidcEndpoint(this.config.oidcIssuerUrl, "auth");
    authorizationUrl.searchParams.set("response_type", "code");
    authorizationUrl.searchParams.set("client_id", this.config.oidcClientId);
    authorizationUrl.searchParams.set("redirect_uri", this.redirectUri());
    authorizationUrl.searchParams.set("scope", "openid agent-runtime.admin");
    authorizationUrl.searchParams.set("state", state);
    authorizationUrl.searchParams.set("code_challenge", challenge);
    authorizationUrl.searchParams.set("code_challenge_method", "S256");
    return authorizationUrl.toString();
  }

  async beginAuthorization(): Promise<void> {
    this.browser.location.assign(await this.createAuthorizationUrl());
  }

  async completeAuthorizationCallback(): Promise<boolean> {
    const query = new URLSearchParams(this.browser.location.search);
    const authorizationError = query.get("error");
    if (authorizationError) {
      this.clearPendingAuthorization();
      this.clearCallbackUrl();
      throw new Error("OIDC authorization was not completed");
    }
    const code = query.get("code");
    if (!code) return false;

    const expectedState = this.browser.storage.getItem(STATE_KEY);
    const verifier = this.browser.storage.getItem(VERIFIER_KEY);
    this.browser.storage.removeItem(STATE_KEY);
    this.browser.storage.removeItem(VERIFIER_KEY);
    if (!expectedState || !verifier || query.get("state") !== expectedState) {
      this.clearCallbackUrl();
      throw new Error("OIDC callback state or PKCE verifier is missing or invalid");
    }
    this.clearCallbackUrl();

    const body = new URLSearchParams({
      grant_type: "authorization_code",
      client_id: this.config.oidcClientId,
      code,
      redirect_uri: this.redirectUri(),
      code_verifier: verifier,
    });
    const tokenUrl = oidcEndpoint(this.config.oidcIssuerUrl, "token");
    const response = await this.browser.fetch(tokenUrl, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body,
    });
    if (!response.ok) {
      throw new Error(`OIDC token exchange failed with HTTP ${response.status}`);
    }
    const payload = (await response.json()) as TokenResponse;
    if (!payload.access_token || payload.token_type?.toLowerCase() !== "bearer") {
      throw new Error("OIDC token response did not contain a Bearer access token");
    }
    this.accessToken = payload.access_token;
    return true;
  }

  private redirectUri(): string {
    return new URL(this.browser.applicationBasePath, this.browser.location.origin).toString();
  }

  private clearPendingAuthorization(): void {
    this.browser.storage.removeItem(STATE_KEY);
    this.browser.storage.removeItem(VERIFIER_KEY);
  }

  private clearCallbackUrl(): void {
    this.browser.history.replaceState(null, "", this.redirectUri());
  }
}

function oidcEndpoint(issuer: string, endpoint: "auth" | "token"): URL {
  const url = new URL(`${issuer.replace(/\/$/, "")}/protocol/openid-connect/${endpoint}`);
  if (isKeycloakAdminRestPath(url)) {
    throw new Error("Browser OIDC must never use the Keycloak Admin REST API");
  }
  return url;
}

function assertBrowserIssuer(issuer: string): void {
  const url = new URL(issuer);
  const loopback = url.hostname === "localhost" || url.hostname === "127.0.0.1";
  if (url.protocol !== "https:" && !(loopback && url.protocol === "http:")) {
    throw new Error("Browser OIDC issuer must use HTTPS outside loopback development");
  }
  if (isKeycloakAdminRestPath(url)) {
    throw new Error("Browser OIDC issuer must not be a Keycloak administration URL");
  }
}

function isKeycloakAdminRestPath(url: URL): boolean {
  const segments = url.pathname.split("/").filter(Boolean);
  return segments.some(
    (segment, index) =>
      segment.toLowerCase() === "admin" &&
      segments[index + 1]?.toLowerCase() === "realms",
  );
}

function randomBase64Url(crypto: Crypto, byteLength: number): string {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

async function sha256Base64Url(crypto: Crypto, value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return base64Url(new Uint8Array(digest));
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

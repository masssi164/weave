// @vitest-environment-options {"url":"https://organization.example.invalid"}
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import LocalTrustSetup, { validateLocalTrustDescriptor } from "./LocalTrustSetup";

const origin = "https://organization.example.invalid";
const descriptor = {
  schemaVersion: "weave.dogfood-local-trust/v1", environment: "dogfood",
  organizationOrigin: origin, apiOrigin: "https://api.example.invalid", issuerOrigin: "https://identity.example.invalid",
  certificateUrl: `${origin}/admin-console/setup/ca.pem`, caSha256Fingerprint: Array(32).fill("AB").join(":"),
  supportSafe: true, containsSecretValues: false,
};
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
describe("local trust descriptor boundary", () => {
  it("accepts only the closed canonical public Dogfood descriptor", () => {
    expect(validateLocalTrustDescriptor(descriptor, origin)).toEqual(descriptor);
    for (const change of [
      { environment: "production" }, { schemaVersion: "other" }, { extra: "private" },
      { organizationOrigin: "https://other.example.invalid" }, { apiOrigin: "http://api.example.invalid" },
      { issuerOrigin: "https://user:secret@identity.example.invalid" }, { issuerOrigin: "https://identity.example.invalid/path" },
      { organizationOrigin: `${origin}/` }, { certificateUrl: "https://other.example.invalid/ca.pem" },
      { certificateUrl: `${origin}/admin-console/setup/ca.pem?secret=private` },
      { caSha256Fingerprint: descriptor.caSha256Fingerprint.toLowerCase() },
      { caSha256Fingerprint: descriptor.caSha256Fingerprint + "\n" }, { containsSecretValues: true }, { supportSafe: false },
    ]) expect(validateLocalTrustDescriptor({ ...descriptor, ...change }, origin)).toBeNull();
    expect(validateLocalTrustDescriptor(descriptor, "http://localhost")).toBeNull();
  });
  it("hides production 404 and never offers a certificate on failed or malformed data", async () => {
    for (const status of [404, 403, 500, 200]) {
      const fetcher = vi.fn().mockResolvedValue(new Response(status === 200 ? JSON.stringify({ ...descriptor, containsSecretValues: true }) : null, { status }));
      vi.stubGlobal("fetch", fetcher);
      const view = render(<LocalTrustSetup locale="en" />);
      await vi.waitFor(() => expect(fetcher).toHaveBeenCalledOnce());
      if (status !== 404) await screen.findByText(/could not be verified/i);
      expect(screen.queryByRole("link")).not.toBeInTheDocument();
      if (status === 404) expect(screen.queryByRole("heading")).not.toBeInTheDocument();
      expect(fetcher).toHaveBeenCalledWith("/admin-console/setup/trust.json", { credentials: "omit", redirect: "error", cache: "no-store" });
      view.unmount();
    }
  });
  it("shows only a validated certificate and explicit independent trust instructions", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(descriptor), { status: 200 })));
    render(<LocalTrustSetup locale="en" />);
    expect(await screen.findByRole("link", { name: /download the public local CA/i })).toHaveAttribute("href", descriptor.certificateUrl);
    expect(screen.getByText(/Downloading does not establish trust/)).toBeInTheDocument();
    expect(screen.getByText(new RegExp(descriptor.caSha256Fingerprint))).toBeInTheDocument();
  });
});

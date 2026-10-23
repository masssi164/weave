import { useEffect, useState } from "react";
import { Alert, Box, Link, Typography } from "@mui/material";
import { adminCopy, type AdminConsoleLocale } from "./copy";

export interface LocalTrustDescriptor {
  schemaVersion: "weave.dogfood-local-trust/v1";
  environment: "dogfood";
  organizationOrigin: string;
  apiOrigin: string;
  issuerOrigin: string;
  certificateUrl: string;
  caSha256Fingerprint: string;
  supportSafe: true;
  containsSecretValues: false;
}

export function validateLocalTrustDescriptor(value: unknown, browserOrigin: string): LocalTrustDescriptor | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const item = value as Record<string, unknown>;
  const fields = ["schemaVersion", "environment", "organizationOrigin", "apiOrigin", "issuerOrigin", "certificateUrl", "caSha256Fingerprint", "supportSafe", "containsSecretValues"];
  if (Object.keys(item).length !== fields.length || fields.some((field) => !(field in item))) return null;
  const canonical = (origin: unknown): origin is string => {
    if (typeof origin !== "string") return false;
    try { const url = new URL(origin); return url.protocol === "https:" && url.origin === origin; }
    catch { return false; }
  };
  if (!canonical(browserOrigin) || item.schemaVersion !== "weave.dogfood-local-trust/v1" || item.environment !== "dogfood"
    || !canonical(item.organizationOrigin) || item.organizationOrigin !== browserOrigin
    || !canonical(item.apiOrigin) || !canonical(item.issuerOrigin)
    || item.certificateUrl !== `${browserOrigin}/admin-console/setup/ca.pem`
    || typeof item.caSha256Fingerprint !== "string" || item.caSha256Fingerprint.length !== 95 || !/^(?:[0-9A-F]{2}:){31}[0-9A-F]{2}$/.test(item.caSha256Fingerprint)
    || item.supportSafe !== true || item.containsSecretValues !== false) return null;
  return item as unknown as LocalTrustDescriptor;
}

export default function LocalTrustSetup({ locale }: { locale: AdminConsoleLocale }) {
  const copy = adminCopy(locale);
  const [state, setState] = useState<"hidden" | "unavailable" | LocalTrustDescriptor>("hidden");
  useEffect(() => {
    let alive = true;
    void (async () => {
      try {
        const response = await fetch("/admin-console/setup/trust.json", { credentials: "omit", redirect: "error", cache: "no-store" });
        if (!alive) return;
        if (response.status === 404) { setState("hidden"); return; }
        if (!response.ok) { setState("unavailable"); return; }
        const descriptor = validateLocalTrustDescriptor(await response.json(), window.location.origin);
        if (alive) setState(descriptor ?? "unavailable");
      } catch { if (alive) setState("unavailable"); }
    })();
    return () => { alive = false; };
  }, []);
  if (state === "hidden") return null;
  return <Box component="section" aria-labelledby="local-trust-heading" sx={{ mx: "auto", my: 3, px: 3, maxWidth: 720 }}>
    <Typography id="local-trust-heading" variant="h2" sx={{ fontSize: "1.2rem", mb: 1 }}>{copy.localTrustHeading}</Typography>
    {state === "unavailable" ? <Alert severity="warning">{copy.localTrustUnavailable}</Alert> : <>
      <Typography>{copy.localTrustInstructions}</Typography>
      <Typography sx={{ overflowWrap: "anywhere", my: 1 }}>{copy.localTrustFingerprint}: {state.caSha256Fingerprint}</Typography>
      <Typography>{copy.localTrustOrganization}: {state.organizationOrigin}</Typography>
      <Typography>{copy.localTrustApi}: {state.apiOrigin}</Typography>
      <Typography>{copy.localTrustIssuer}: {state.issuerOrigin}</Typography>
      <Link href={state.certificateUrl} download sx={{ display: "inline-block", py: 2 }}>{copy.localTrustDownload}</Link>
    </>}
  </Box>;
}

import { useState } from "react";
import { Alert, Box, Button, Typography } from "@mui/material";
import { AdminApiError, AdminControlPlaneApi } from "./api";
import { adminCopy, type AdminConsoleLocale } from "./copy";

export default function DefaultSpaceSetup({ api, locale }: {
  api: AdminControlPlaneApi;
  locale: AdminConsoleLocale;
}) {
  const copy = adminCopy(locale);
  const [state, setState] = useState<"unverified" | "busy" | "ready" | "conflict" | "denied" | "failed">("unverified");
  async function provision() {
    setState("busy");
    try {
      const result = await api.provisionDefaultSpace();
      setState(result.spaceRef === "workspace-default" &&
        (result.result === "CREATED" || result.result === "UNCHANGED") ? "ready" : "failed");
    } catch (error: unknown) {
      setState(error instanceof AdminApiError && error.status === 409 ? "conflict"
        : error instanceof AdminApiError && (error.status === 401 || error.status === 403)
          ? "denied" : "failed");
    }
  }
  const message = {
    unverified: copy.defaultSpaceUnverified, busy: copy.defaultSpaceBusy,
    ready: copy.defaultSpaceReady, conflict: copy.defaultSpaceConflict,
    denied: copy.defaultSpaceDenied, failed: copy.defaultSpaceFailed,
  }[state];
  return <Box component="section" aria-labelledby="default-space-heading" sx={{ mb: 3 }}>
    <Typography id="default-space-heading" variant="h3" sx={{ fontSize: "1.1rem" }}>{copy.defaultSpaceHeading}</Typography>
    <Typography sx={{ my: 1 }}>{copy.defaultSpaceDescription}</Typography>
    <Alert role="region" aria-label={copy.defaultSpaceHeading} aria-live="polite" severity={state === "ready" ? "success" :
      state === "conflict" || state === "denied" || state === "failed" ? "warning" : "info"}>{message}</Alert>
    <Button sx={{ mt: 1 }} variant="contained" onClick={() => void provision()}
      disabled={state === "busy" || state === "ready" || state === "denied" || state === "conflict"}>
      {copy.defaultSpaceButton}
    </Button>
  </Box>;
}

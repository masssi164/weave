import { useState } from "react";
import { Alert, Box, Button, FormControl, InputLabel, MenuItem, Select, Stack, TextField, Typography } from "@mui/material";
import { AdminApiError, type AdminControlPlaneApi } from "./api";
import type { AdminConsoleLocale } from "./copy";
import { PreparationFailure, PreparationSession, intendedOwners, parsePreparationPlan, parsePreparationProof, type PreparationInventoryMember, type PreparationReceipt } from "./preparation";

const messages = {
  en: {
    heading: "Prepare two test members", intro: "Review the exact organization, test members and workspace grants before approving. Your personal account, credentials and session are preserved.",
    plan: "Preparation plan JSON", review: "Review preparation plan", owner: "Intended iPhone owner", noOwner: "Select a current enabled owner before approval. If no owner is available, preparation is blocked.",
    approve: "Approve preparation", permission: "Member role; default workspace: view and edit", proof: "Independent member proof JSON", grants: "Verify member proof and grants",
    pending: "Checking current administrator authority and organization state…", reviewed: "Review this plan. No access changes have been approved yet.",
    approved: "Invitations are verified. Continue independent test-member activation and sign-in before importing proof.", ready: "Current test-member grants are verified. This is preparation evidence, not qualification or human acceptance.",
    failed: "Preparation could not verify the current state. No further action was taken. Review current access and the private preparation record before retrying.",
    authorityLost: "The administrator session is no longer authorized. Sign in again and review the preparation plan.",
    refresh: "Refresh preparation status", nonce: "Preparation operation nonce", ownerAccess: "The intended owner’s existing workspace administrator access could not be verified. Owner workspace preparation is required; no owner access was added or restored.",
    receipt: "Dogfood preparation receipt", details: "Preparation receipt details",
  },
  de: {
    heading: "Zwei Testmitglieder vorbereiten", intro: "Prüfen Sie Organisation, Testmitglieder und Arbeitsbereichsrechte vor Ihrer Zustimmung. Ihr persönliches Konto, Ihre Zugangsdaten und Ihre Sitzung bleiben erhalten.",
    plan: "Vorbereitungsplan als JSON", review: "Vorbereitungsplan prüfen", owner: "Vorgesehene iPhone-Eigentümerperson", noOwner: "Wählen Sie vor der Zustimmung eine aktuell aktive Eigentümerperson. Ohne passende Person ist die Vorbereitung gesperrt.",
    approve: "Vorbereitung genehmigen", permission: "Mitgliedsrolle; Standardarbeitsbereich: lesen und bearbeiten", proof: "Unabhängiger Mitgliedsnachweis als JSON", grants: "Mitgliedsnachweis und Rechte prüfen",
    pending: "Aktuelle Administrationsrechte und Organisationszustand werden geprüft…", reviewed: "Prüfen Sie diesen Plan. Es wurden noch keine Zugriffsänderungen genehmigt.",
    approved: "Die Einladungen sind geprüft. Aktivieren Sie die unabhängigen Testmitglieder und melden Sie diese an, bevor Sie Nachweise importieren.", ready: "Die aktuellen Testmitgliedsrechte sind geprüft. Dies ist ein Vorbereitungsnachweis, keine Qualifikation oder menschliche Abnahme.",
    failed: "Der aktuelle Zustand konnte nicht geprüft werden. Es wurde keine weitere Aktion ausgeführt. Prüfen Sie den aktuellen Zugriff und das private Vorbereitungsprotokoll vor einem neuen Versuch.",
    authorityLost: "Die Administrationssitzung ist nicht mehr berechtigt. Melden Sie sich erneut an und prüfen Sie den Vorbereitungsplan.",
    refresh: "Vorbereitungsstatus aktualisieren", nonce: "Nonce des Vorbereitungsschritts", ownerAccess: "Die bestehenden Administrationsrechte der vorgesehenen Eigentümerperson im Arbeitsbereich konnten nicht geprüft werden. Eine gesonderte Vorbereitung ist erforderlich. Eigentümerrechte wurden weder hinzugefügt noch wiederhergestellt.",
    receipt: "Dogfood-Vorbereitungsbeleg", details: "Details zum Vorbereitungsbeleg",
  },
};
export default function TestMemberPreparation({ api, locale }: { api: AdminControlPlaneApi; locale: AdminConsoleLocale }) {
  const copy = messages[locale];
  const [input, setInput] = useState("");
  const [proof, setProof] = useState("");
  const [nonce, setNonce] = useState("");
  const [ownerAccessFailed, setOwnerAccessFailed] = useState(false);
  const [authorityLost, setAuthorityLost] = useState(false);
  const [session, setSession] = useState<PreparationSession | null>(null);
  const [owners, setOwners] = useState<PreparationInventoryMember[]>([]);
  const [owner, setOwner] = useState("");
  const [approved, setApproved] = useState(false);
  const [receipt, setReceipt] = useState<PreparationReceipt | null>(null);
  const [status, setStatus] = useState<"idle" | "pending" | "reviewed" | "approved" | "ready" | "failed">("idle");
  const busy = status === "pending";
  function failed(failure: unknown) {
    setOwnerAccessFailed(failure instanceof PreparationFailure && failure.stage === "owner-workspace-access-required");
    setAuthorityLost((failure instanceof PreparationFailure && failure.stage === "administrator-session-unavailable")
      || (failure instanceof AdminApiError && [401, 403].includes(failure.status)));
    setApproved(false); setReceipt(null); setStatus("failed");
  }
  async function review() {
    setSession(null); setReceipt(null); setApproved(false); setOwners([]); setOwner(""); setStatus("pending");
    try {
      const plan = parsePreparationPlan(input);
      const identity = await api.getCurrentIdentity();
      if (!identity.accountId || !identity.organizationId) throw new Error("Unavailable identity");
      const next = new PreparationSession(api, plan, identity.accountId, identity.organizationId);
      const snapshot = await next.preflight();
      const choices = intendedOwners(snapshot.members);
      setSession(next); setOwners(choices); setOwner(choices.length === 1 ? choices[0].accountRef : ""); setStatus("reviewed");
    } catch (failure) { failed(failure); }
  }
  async function approve() {
    if (!session || !owner || busy) return;
    setStatus("pending"); setOwnerAccessFailed(false);
    try { setReceipt(await session.invitations(owner)); setApproved(true); setStatus("approved"); }
    catch (failure) { failed(failure); }
  }
  async function grant() {
    if (!session || !receipt || !approved || busy) return;
    setStatus("pending");
    try {
      const result = await session.grants(parsePreparationProof(proof, session.plan), receipt);
      setReceipt(result); setStatus("ready");
    } catch (failure) { failed(failure); }
  }
  async function refresh() {
    if (!session || !receipt || !approved || busy) return;
    const previous = receipt;
    setReceipt(null); setStatus("pending");
    try { setReceipt(await session.refresh(previous, nonce)); setStatus(previous.stage === "grants-ready" ? "ready" : "approved"); }
    catch (failure) { failed(failure); }
  }
  return <Box component="section" aria-labelledby="test-member-preparation-heading" sx={{ my: 3 }}>
    <Stack spacing={2}>
      <Typography id="test-member-preparation-heading" variant="h2" sx={{ fontSize: "1.3rem" }}>{copy.heading}</Typography>
      <Typography>{copy.intro}</Typography>
      <TextField id="dogfood-preparation-plan" label={copy.plan} multiline minRows={3} value={input} disabled={busy || approved}
        onChange={(event) => { setInput(event.target.value); setSession(null); setReceipt(null); setApproved(false); setStatus("idle"); }} slotProps={{ htmlInput: { maxLength: 16384, spellCheck: false } }} />
      <Button variant="outlined" disabled={busy || approved} onClick={() => void review()}>{copy.review}</Button>
      {status !== "idle" && <Alert aria-live="polite" severity={status === "failed" ? "warning" : status === "ready" ? "success" : "info"}>{authorityLost && status === "failed" ? copy.authorityLost : ownerAccessFailed && status === "failed" ? copy.ownerAccess : copy[status]}</Alert>}
      {session && <>
        <Typography>{session.plan.organizationOrigin}</Typography>
        <Typography>{session.plan.apiOrigin}</Typography>
        <Typography>{session.plan.issuerUrl}</Typography>
        {session.plan.members.map((member) => <Box key={member.slot}><Typography>{member.displayName} — {member.email}</Typography><Typography>{copy.permission}</Typography></Box>)}
        <FormControl fullWidth><InputLabel id="preparation-owner-label">{copy.owner}</InputLabel>
          <Select id="dogfood-preparation-owner" labelId="preparation-owner-label" label={copy.owner} value={owner} disabled={busy || approved}
            onChange={(event) => setOwner(event.target.value)}>
            {owners.map((member) => <MenuItem key={member.accountRef} value={member.accountRef}>{member.displayName}</MenuItem>)}
          </Select>
        </FormControl>
        {!owner && <Alert severity="warning">{copy.noOwner}</Alert>}
        {!approved && <Button id="dogfood-preparation-approve" variant="contained" disabled={busy || !owner} onClick={() => void approve()}>{copy.approve}</Button>}
      </>}
      {approved && <>
        <TextField id="dogfood-preparation-nonce" label={copy.nonce} value={nonce} disabled={busy} onChange={(event) => setNonce(event.target.value)} slotProps={{ htmlInput: { maxLength: 32 } }} />
        <Button disabled={busy || !receipt} onClick={() => void refresh()}>{copy.refresh}</Button>
        <TextField id="dogfood-preparation-proof" label={copy.proof} multiline minRows={3} value={proof} disabled={busy} onChange={(event) => setProof(event.target.value)} slotProps={{ htmlInput: { maxLength: 16384, spellCheck: false } }} />
        <Button id="dogfood-preparation-grants" disabled={busy} onClick={() => void grant()}>{copy.grants}</Button>
      </>}
      {receipt && status !== "pending" && <Box component="details"><Box component="summary">{copy.details}</Box>
        <TextField id="dogfood-preparation-receipt" label={copy.receipt} multiline minRows={5} fullWidth value={JSON.stringify(receipt)} slotProps={{ input: { readOnly: true } }} />
      </Box>}
    </Stack>
  </Box>;
}

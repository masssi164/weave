import type { AdminControlPlaneApi, OrganizationInvitation } from "./api";

export interface PreparationMember {
  slot: "A" | "B"; email: string; displayName: string; role: "member";
  spaceRole: "EDIT"; invitationIdempotencyKey: string;
}
export interface PreparationPlan {
  schemaVersion: "weave.dogfood-admin-preparation-plan/v1";
  planId: string; nonce: string; organizationOrigin: string; apiOrigin: string;
  issuerUrl: string; spaceRef: "workspace-default"; members: PreparationMember[];
}
export interface PreparationProof {
  schemaVersion: "weave.dogfood-admin-preparation-proof/v1"; planId: string; nonce: string;
  members: { slot: "A" | "B"; accountDigest: string; organizationDigest: string }[];
}
export interface PreparationReceipt {
  schemaVersion: "weave.dogfood-admin-preparation-receipt/v1";
  planId: string; nonce: string; stage: "invitations-ready" | "grants-ready";
  organizationId: string; administratorAccountRef: string;
  intendedOwnerAccountRef: string; ownerDisplayName: string;
  members: { slot: "A" | "B"; email: string; displayName: string;
    memberHandle?: string; invitationHandle?: string; accountRef?: string; grantVersion?: string }[];
  supportSafe: true; containsSecretValues: false;
}
export interface PreparationInventoryMember {
  accountRef: string; memberHandle?: string; email?: string; displayName?: string;
  enabled: boolean; role?: string; version?: string;
}
export class PreparationFailure extends Error {
  constructor(readonly stage: "state-unverified" | "owner-workspace-access-required" | "administrator-session-unavailable" = "state-unverified") { super(stage); }
}
const fail = (): never => { throw new PreparationFailure(); };
const record = (value: unknown): Record<string, unknown> => value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : fail();
const closed = (value: unknown, keys: string[]): Record<string, unknown> => {
  const item = record(value);
  if (Object.keys(item).length !== keys.length || keys.some((key) => !(key in item))) fail();
  return item;
};
const text = (value: unknown, max = 256): value is string => typeof value === "string" && value.length > 0 && value.length <= max && !/[\u0000-\u001f\u007f]/.test(value);
const hex = (value: unknown, length: number): boolean => typeof value === "string" && value.length === length && /^[0-9a-f]+$/.test(value);
const origin = (value: unknown): boolean => {
  if (!text(value)) return false;
  try { const url = new URL(value); return url.protocol === "https:" && url.origin === value; } catch { return false; }
};
const parse = (input: string): unknown => { if (new TextEncoder().encode(input).length > 16384) fail(); try { return JSON.parse(input); } catch { return fail(); } };
export function parsePreparationPlan(input: string): PreparationPlan {
  const item = closed(parse(input), ["schemaVersion", "planId", "nonce", "organizationOrigin", "apiOrigin", "issuerUrl", "spaceRef", "members"]);
  if (item.schemaVersion !== "weave.dogfood-admin-preparation-plan/v1" || !hex(item.planId, 32) || !hex(item.nonce, 32)
    || !origin(item.organizationOrigin) || !origin(item.apiOrigin) || item.spaceRef !== "workspace-default" || !text(item.issuerUrl)) fail();
  const issuer = new URL(item.issuerUrl as string);
  if (issuer.protocol !== "https:" || issuer.username || issuer.password || issuer.search || issuer.hash || issuer.href !== item.issuerUrl) fail();
  if (!Array.isArray(item.members) || item.members.length !== 2) fail();
  const members = (item.members as unknown[]).map((value) => {
    const member = closed(value, ["slot", "email", "displayName", "role", "spaceRole", "invitationIdempotencyKey"]);
    if (!["A", "B"].includes(member.slot as string) || !text(member.email) || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(member.email as string)
      || !text(member.displayName, 160) || member.displayName !== (member.displayName as string).trim()
      || member.role !== "member" || member.spaceRole !== "EDIT" || !text(member.invitationIdempotencyKey, 128)) fail();
    return member as unknown as PreparationMember;
  });
  if (new Set(members.map((m) => m.slot)).size !== 2 || new Set(members.map((m) => m.email.toLowerCase())).size !== 2
    || new Set(members.map((m) => m.invitationIdempotencyKey)).size !== 2) fail();
  return { ...item, members } as unknown as PreparationPlan;
}
export function parsePreparationProof(input: string, plan: PreparationPlan): PreparationProof {
  const item = closed(parse(input), ["schemaVersion", "planId", "nonce", "members"]);
  if (item.schemaVersion !== "weave.dogfood-admin-preparation-proof/v1" || item.planId !== plan.planId || !hex(item.nonce, 32)
    || !Array.isArray(item.members) || item.members.length !== 2) fail();
  const members = (item.members as unknown[]).map((value) => {
    const member = closed(value, ["slot", "accountDigest", "organizationDigest"]);
    if (!["A", "B"].includes(member.slot as string) || !hex(member.accountDigest, 64) || !hex(member.organizationDigest, 64)) fail();
    return member;
  });
  if (new Set(members.map((m) => m.slot)).size !== 2) fail();
  return item as unknown as PreparationProof;
}
export async function identityDigest(kind: "weave-account" | "weave-organization", issuer: string, value: string): Promise<string> {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(JSON.stringify([kind, issuer, value])));
  return Array.from(new Uint8Array(bytes), (byte) => byte.toString(16).padStart(2, "0")).join("");
}
export function intendedOwners(members: PreparationInventoryMember[]): PreparationInventoryMember[] {
  return members.filter((member) => member.enabled && member.role?.toUpperCase() === "OWNER" && text(member.accountRef) && text(member.displayName, 160));
}
function matchingMember(members: PreparationInventoryMember[], plan: PreparationMember): PreparationInventoryMember | undefined {
  const matches = members.filter((member) => member.email?.toLowerCase() === plan.email.toLowerCase());
  if (matches.length > 1) fail();
  const found = matches[0];
  if (found && (!found.enabled || found.role?.toUpperCase() !== "MEMBER" || found.displayName !== plan.displayName || !text(found.accountRef) || !text(found.memberHandle) || !text(found.version))) fail();
  return found;
}
function matchingInvitation(invitations: OrganizationInvitation[], member: PreparationMember): OrganizationInvitation | undefined {
  const matches = invitations.filter((invitation) => invitation.email.toLowerCase() === member.email.toLowerCase());
  if (matches.length > 1) fail();
  const found = matches[0];
  if (found && (found.displayName !== member.displayName || found.requestedRole !== "member"
    || !["pending", "applied"].includes(found.provisioningStatus)
    || found.lifecycleStatus !== "pending" || !found.expiresAt || !Number.isFinite(Date.parse(String(found.expiresAt))) || Date.parse(String(found.expiresAt)) <= Date.now() || !text(found.invitationHandle))) fail();
  return found;
}
export class PreparationSession {
  constructor(private readonly api: AdminControlPlaneApi, readonly plan: PreparationPlan,
    private readonly administratorAccountRef: string, private readonly organizationId: string) {}
  async current() {
    const identity = await this.api.getCurrentActor();
    const config = this.api.configuration;
    if (identity.accountRef !== this.administratorAccountRef || identity.organizationId !== this.organizationId
      || identity.identityIssuer !== this.plan.issuerUrl || config.oidcIssuerUrl !== this.plan.issuerUrl
      || new URL(config.apiBaseUrl).origin !== this.plan.apiOrigin || window.location.origin !== this.plan.organizationOrigin
      || !(identity.roles?.length === 1 && ["owner", "admin"].includes(identity.roles[0]))) throw new PreparationFailure("administrator-session-unavailable");
    const members = await this.api.listAllOrganizationMembers(this.organizationId);
    const invitations = await this.api.listOrganizationInvitations(this.organizationId);
    // Detect stale authority again after paginated readback before any write.
    const after = await this.api.getCurrentActor();
    if (after.accountRef !== identity.accountRef || after.organizationId !== identity.organizationId || after.identityIssuer !== identity.identityIssuer
      || !(after.roles?.length === 1 && ["owner", "admin"].includes(after.roles[0]))) throw new PreparationFailure("administrator-session-unavailable");
    return { members, invitations };
  }
  async preflight() {
    const snapshot = await this.current();
    for (const member of this.plan.members) {
      matchingMember(snapshot.members, member);
      matchingInvitation(snapshot.invitations, member);
    }
    return snapshot;
  }
  private async ownerAccess(members: PreparationInventoryMember[], ownerRef: string) {
    const owner = intendedOwners(members).find((value) => value.accountRef === ownerRef);
    if (!owner) throw new PreparationFailure("owner-workspace-access-required");
    const grant = await this.api.readPreparationGrant(this.plan.spaceRef, owner.accountRef);
    if (!grant || grant.accountRef !== owner.accountRef || grant.permissions.length !== 3
      || !["VIEW", "EDIT", "ADMIN"].every((permission) => grant.permissions.includes(permission))) {
      throw new PreparationFailure("owner-workspace-access-required");
    }
    return owner;
  }
  async invitations(ownerRef: string): Promise<PreparationReceipt> {
    let snapshot = await this.preflight();
    const owner = await this.ownerAccess(snapshot.members, ownerRef);
    const results: PreparationReceipt["members"] = [];
    for (const member of this.plan.members) {
      snapshot = await this.preflight();
      await this.ownerAccess(snapshot.members, ownerRef);
      const existingMember = matchingMember(snapshot.members, member);
      const existing = matchingInvitation(snapshot.invitations, member);
      if (existingMember) {
        results.push({ slot: member.slot, email: member.email, displayName: member.displayName,
          memberHandle: existingMember.memberHandle, accountRef: existingMember.accountRef, invitationHandle: existing?.invitationHandle });
        continue;
      }
      const invitation = existing ?? await this.api.createOrganizationInvitation(this.organizationId,
        { email: member.email, displayName: member.displayName, role: "member" }, member.invitationIdempotencyKey);
      const readback = await this.preflight();
      const confirmed = matchingInvitation(readback.invitations, member) ?? fail();
      if (confirmed.invitationHandle !== invitation.invitationHandle) fail();
      results.push({ slot: member.slot, email: member.email, displayName: member.displayName, invitationHandle: confirmed.invitationHandle });
    }
    return { schemaVersion: "weave.dogfood-admin-preparation-receipt/v1", planId: this.plan.planId, nonce: this.plan.nonce,
      stage: "invitations-ready", organizationId: this.organizationId, administratorAccountRef: this.administratorAccountRef,
      intendedOwnerAccountRef: owner.accountRef, ownerDisplayName: owner.displayName!, members: results, supportSafe: true, containsSecretValues: false };
  }
  async refresh(receipt: PreparationReceipt, nonce: string): Promise<PreparationReceipt> {
    if (!hex(nonce, 32) || nonce === receipt.nonce || receipt.planId !== this.plan.planId) fail();
    const snapshot = await this.preflight();
    const owner = await this.ownerAccess(snapshot.members, receipt.intendedOwnerAccountRef);
    for (const planned of this.plan.members) {
      const previous = receipt.members.find((member) => member.slot === planned.slot) ?? fail();
      const member = matchingMember(snapshot.members, planned);
      const invitation = matchingInvitation(snapshot.invitations, planned);
      if (member) {
        if (previous.accountRef && previous.accountRef !== member.accountRef) fail();
      } else if (!invitation || invitation.invitationHandle !== previous.invitationHandle) fail();
      if (receipt.stage === "grants-ready") {
        const currentMember = member ?? fail();
        if (previous.accountRef !== currentMember.accountRef) fail();
        const grant = await this.api.readPreparationGrant(this.plan.spaceRef, currentMember.accountRef);
        if (!grant || grant.version !== previous.grantVersion || grant.permissions.length !== 2
          || !grant.permissions.includes("VIEW") || !grant.permissions.includes("EDIT")) fail();
      }
    }
    const final = await this.current();
    await this.ownerAccess(final.members, receipt.intendedOwnerAccountRef);
    return { ...receipt, nonce, ownerDisplayName: owner.displayName! };
  }
  async grants(proof: PreparationProof, invitationReceipt: PreparationReceipt): Promise<PreparationReceipt> {
    if (proof.planId !== this.plan.planId || invitationReceipt.planId !== this.plan.planId) fail();
    const initial = await this.preflight();
    await this.ownerAccess(initial.members, invitationReceipt.intendedOwnerAccountRef);
    for (const planned of this.plan.members) {
      const member = matchingMember(initial.members, planned) ?? fail();
      const observed = proof.members.find((value) => value.slot === planned.slot) ?? fail();
      if (observed.accountDigest !== await identityDigest("weave-account", this.plan.issuerUrl, member.accountRef)
        || observed.organizationDigest !== await identityDigest("weave-organization", this.plan.issuerUrl, this.organizationId)) fail();
    }
    const members: PreparationReceipt["members"] = [];
    for (const planned of this.plan.members) {
      const snapshot = await this.preflight();
      await this.ownerAccess(snapshot.members, invitationReceipt.intendedOwnerAccountRef);
      const member = matchingMember(snapshot.members, planned) ?? fail();
      const observed = proof.members.find((value) => value.slot === planned.slot) ?? fail();
      if (observed.accountDigest !== await identityDigest("weave-account", this.plan.issuerUrl, member.accountRef)
        || observed.organizationDigest !== await identityDigest("weave-organization", this.plan.issuerUrl, this.organizationId)) fail();
      const previous = invitationReceipt.members.find((value) => value.slot === planned.slot) ?? fail();
      if (previous.accountRef && previous.accountRef !== member.accountRef) fail();
      let grant = await this.api.readPreparationGrant(this.plan.spaceRef, member.accountRef);
      if (!grant) {
        if (previous.grantVersion) fail();
        // Conditional creation never replaces an existing/revoked grant.
        const beforeWrite = await this.current();
        const currentMember = matchingMember(beforeWrite.members, planned) ?? fail();
        if (currentMember.accountRef !== member.accountRef || currentMember.version !== member.version) fail();
        await this.ownerAccess(beforeWrite.members, invitationReceipt.intendedOwnerAccountRef);
        await this.api.createPreparationGrant(this.plan.spaceRef, member.accountRef);
        grant = await this.api.readPreparationGrant(this.plan.spaceRef, member.accountRef);
      }
      const confirmedGrant = grant ?? fail();
      if (confirmedGrant.accountRef !== member.accountRef || !confirmedGrant.version || confirmedGrant.permissions.length !== 2
        || !confirmedGrant.permissions.includes("VIEW") || !confirmedGrant.permissions.includes("EDIT")) fail();
      members.push({ slot: planned.slot, email: planned.email, displayName: planned.displayName,
        accountRef: member.accountRef, memberHandle: member.memberHandle, invitationHandle: previous.invitationHandle, grantVersion: confirmedGrant.version });
    }
    const final = await this.preflight();
    await this.ownerAccess(final.members, invitationReceipt.intendedOwnerAccountRef);
    for (const result of members) {
      const planned = this.plan.members.find((value) => value.slot === result.slot) ?? fail();
      const member = matchingMember(final.members, planned) ?? fail();
      if (member.accountRef !== result.accountRef) fail();
      const grant = await this.api.readPreparationGrant(this.plan.spaceRef, member.accountRef);
      if (!grant || grant.version !== result.grantVersion || grant.permissions.length !== 2
        || !grant.permissions.includes("VIEW") || !grant.permissions.includes("EDIT")) fail();
    }
    return { ...invitationReceipt, nonce: proof.nonce, stage: "grants-ready", members };
  }
}

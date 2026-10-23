// @vitest-environment-options {"url":"https://organization.example.invalid"}
import { describe, expect, it, vi } from "vitest";
import type { AdminControlPlaneApi, OrganizationInvitation } from "./api";
import { PreparationSession, identityDigest, parsePreparationPlan, parsePreparationProof, type PreparationInventoryMember, type PreparationPlan } from "./preparation";
export const plan: PreparationPlan = {
  schemaVersion: "weave.dogfood-admin-preparation-plan/v1", planId: "a".repeat(32), nonce: "b".repeat(32),
  organizationOrigin: "https://organization.example.invalid", apiOrigin: "https://api.example.invalid", issuerUrl: "https://identity.example.invalid/realms/weave", spaceRef: "workspace-default",
  members: ["A", "B"].map((slot) => ({ slot, email: `test-${slot.toLowerCase()}@example.invalid`, displayName: `Approved Test ${slot}`, role: "member", spaceRole: "EDIT", invitationIdempotencyKey: `approved-invitation-${slot}` })) as PreparationPlan["members"],
};
export function fixture() {
  const owner: PreparationInventoryMember = { accountRef: "intended-owner", memberHandle: "owner-handle", email: "owner@example.invalid", displayName: "Current Phone Owner", enabled: true, role: "OWNER", version: '"owner-1"' };
  const inventory: PreparationInventoryMember[] = [owner];
  const invitations: OrganizationInvitation[] = [];
  const grants = new Map<string, { accountRef: string; permissions: string[]; version: string }>([[owner.accountRef, { accountRef: owner.accountRef, permissions: ["VIEW", "EDIT", "ADMIN"], version: '"owner-grant"' }]]);
  const api = {
    configuration: { apiBaseUrl: `${plan.apiOrigin}/api`, oidcIssuerUrl: plan.issuerUrl, oidcClientId: "weave-admin-console" },
    getCurrentIdentity: vi.fn().mockResolvedValue({ accountId: "current-administrator", organizationId: "current-organization", identityIssuer: plan.issuerUrl, roles: ["admin"] }),
    listAllOrganizationMembers: vi.fn(async () => [...inventory]), listOrganizationInvitations: vi.fn(async () => [...invitations]),
    createOrganizationInvitation: vi.fn(async (_org, member, key) => {
      const result = { ...member, requestedRole: "member", invitationHandle: key, organizationId: "current-organization", lifecycleStatus: "pending", provisioningStatus: "pending", expiresAt: new Date("2099-01-01") } as OrganizationInvitation;
      invitations.push(result); return result;
    }),
    readPreparationGrant: vi.fn(async (_space, account) => grants.get(account) ?? null),
    createPreparationGrant: vi.fn(async (_space, account) => { grants.set(account, { accountRef: account, permissions: ["VIEW", "EDIT"], version: '"grant-1"' }); }),
  };
  const session = new PreparationSession(api as unknown as AdminControlPlaneApi, plan, "current-administrator", "current-organization");
  async function activate() {
    for (const member of plan.members) inventory.push({ accountRef: `account-${member.slot}`, memberHandle: `member-${member.slot}`, email: member.email, displayName: member.displayName, role: "MEMBER", enabled: true, version: '"member-1"' });
    invitations.splice(0);
    return { schemaVersion: "weave.dogfood-admin-preparation-proof/v1" as const, planId: plan.planId, nonce: "c".repeat(32), members: await Promise.all(plan.members.map(async (member) => ({ slot: member.slot, accountDigest: await identityDigest("weave-account", plan.issuerUrl, `account-${member.slot}`), organizationDigest: await identityDigest("weave-organization", plan.issuerUrl, "current-organization") }))) };
  }
  return { api, owner, inventory, invitations, grants, session, activate };
}
describe("closed preparation inputs", () => {
  it("accepts only two exact approved members and rejects secrets, unsafe coordinates and ambiguity", () => {
    expect(parsePreparationPlan(JSON.stringify(plan))).toEqual(plan);
    for (const change of [{ token: "forbidden" }, { spaceRef: "another-space" }, { organizationOrigin: "http://organization.example.invalid" }, { nonce: "bad" }, { members: [plan.members[0], plan.members[0]] }, { members: [{ ...plan.members[0], password: "forbidden" }, plan.members[1]] }]) {
      expect(() => parsePreparationPlan(JSON.stringify({ ...plan, ...change }))).toThrow();
    }
    expect(() => parsePreparationPlan(" ".repeat(16385))).toThrow();
  });
  it("binds proof to the plan with exact independent identity digests", async () => {
    const { activate } = fixture(); const proof = await activate();
    expect(parsePreparationProof(JSON.stringify(proof), plan)).toEqual(proof);
    for (const changed of [{ ...proof, password: "forbidden" }, { ...proof, planId: "d".repeat(32) }, { ...proof, members: [{ ...proof.members[0], accountDigest: "sha256:" + proof.members[0].accountDigest }, proof.members[1]] }]) {
      expect(() => parsePreparationProof(JSON.stringify(changed), plan)).toThrow();
    }
  });
});
describe("current administrator preparation", () => {
  it("preflights without writes and binds receipts to the intended owner rather than the administrator", async () => {
    const f = fixture(); await f.session.preflight();
    expect(f.api.createOrganizationInvitation).not.toHaveBeenCalled();
    const receipt = await f.session.invitations(f.owner.accountRef);
    expect(receipt.administratorAccountRef).toBe("current-administrator");
    expect(receipt.intendedOwnerAccountRef).toBe(f.owner.accountRef);
    expect(receipt.ownerDisplayName).toBe(f.owner.displayName);
    expect(f.api.createOrganizationInvitation.mock.calls.map((call) => call[2])).toEqual(plan.members.map((member) => member.invitationIdempotencyKey));
    expect(JSON.stringify(receipt)).not.toMatch(/password|access_token|activationUrl|issuerUrl/);
  });
  it("reuses matching activated members without creating duplicate invitations", async () => {
    const f = fixture(); await f.activate(); await f.session.invitations(f.owner.accountRef);
    expect(f.api.createOrganizationInvitation).not.toHaveBeenCalled();
  });
  it.each(["suspended", "ambiguous", "expired", "owner-access", "wrong-issuer"])("blocks %s before invitation mutations", async (state) => {
    const f = fixture();
    if (state === "suspended" || state === "ambiguous") { await f.activate(); if (state === "suspended") f.inventory[1].enabled = false; else f.inventory.push({ ...f.inventory[1] }); }
    if (state === "expired") f.invitations.push({ invitationHandle: "old", organizationId: "current-organization", email: plan.members[0].email, displayName: plan.members[0].displayName, requestedRole: "member", lifecycleStatus: "expired", provisioningStatus: "pending", expiresAt: new Date("2000-01-01") });
    if (state === "owner-access") f.grants.delete(f.owner.accountRef);
    if (state === "wrong-issuer") f.api.getCurrentIdentity.mockResolvedValue({ accountId: "current-administrator", organizationId: "current-organization", identityIssuer: "https://wrong.example.invalid", roles: ["admin"] });
    await expect(f.session.invitations(f.owner.accountRef)).rejects.toThrow();
    expect(f.api.createOrganizationInvitation).not.toHaveBeenCalled();
  });
  it("validates both independent proofs before conditional test grants and verifies exact final readback", async () => {
    const f = fixture(); const receipt = await f.session.invitations(f.owner.accountRef); const proof = await f.activate();
    const wrong = { ...proof, members: [proof.members[0], { ...proof.members[1], accountDigest: "d".repeat(64) }] };
    await expect(f.session.grants(wrong, receipt)).rejects.toThrow();
    expect(f.api.createPreparationGrant).not.toHaveBeenCalled();
    const result = await f.session.grants(proof, receipt);
    expect(result.stage).toBe("grants-ready"); expect(result.nonce).toBe(proof.nonce);
    expect(f.api.createPreparationGrant).toHaveBeenCalledTimes(2);
    await f.session.grants(proof, result); expect(f.api.createPreparationGrant).toHaveBeenCalledTimes(2);
    const fresh = await f.session.refresh(result, "e".repeat(32)); expect(fresh.nonce).toBe("e".repeat(32));
    await expect(f.session.refresh(fresh, fresh.nonce)).rejects.toThrow();
    f.grants.delete("account-A"); await expect(f.session.refresh(fresh, "f".repeat(32))).rejects.toThrow();
    expect(f.api.createPreparationGrant).toHaveBeenCalledTimes(2);
  });
  it("stops revoked grants and changed administrator authority without restoration", async () => {
    const f = fixture(); const receipt = await f.session.invitations(f.owner.accountRef); const proof = await f.activate();
    f.api.readPreparationGrant.mockImplementation(async (_space, account) => { if (account === "account-A") throw new Error("revoked"); return f.grants.get(account) ?? null; });
    await expect(f.session.grants(proof, receipt)).rejects.toThrow(); expect(f.api.createPreparationGrant).not.toHaveBeenCalled();
    f.api.getCurrentIdentity.mockResolvedValue({ accountId: "other-administrator", organizationId: "current-organization", identityIssuer: plan.issuerUrl, roles: ["admin"] });
    await expect(f.session.preflight()).rejects.toThrow();
  });
});

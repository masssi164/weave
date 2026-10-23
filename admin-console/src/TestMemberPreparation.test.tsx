// @vitest-environment-options {"url":"https://organization.example.invalid"}
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { AdminControlPlaneApi } from "./api";
import TestMemberPreparation from "./TestMemberPreparation";
import type { PreparationPlan } from "./preparation";
const plan: PreparationPlan = { schemaVersion: "weave.dogfood-admin-preparation-plan/v1", planId: "a".repeat(32), nonce: "b".repeat(32), organizationOrigin: window.location.origin, apiOrigin: "https://api.example.invalid", issuerUrl: "https://identity.example.invalid/realms/weave", spaceRef: "workspace-default", members: ["A", "B"].map((slot) => ({ slot, email: `test-${slot.toLowerCase()}@example.invalid`, displayName: `Approved Test ${slot}`, role: "member", spaceRole: "EDIT", invitationIdempotencyKey: `approved-${slot}` })) as PreparationPlan["members"] };
function apiFixture(ownerCount = 1) {
  const invitations: unknown[] = [];
  const api = {
    configuration: { apiBaseUrl: `${plan.apiOrigin}/api`, oidcIssuerUrl: plan.issuerUrl },
    getCurrentIdentity: vi.fn().mockResolvedValue({ accountId: "administrator", organizationId: "organization", identityIssuer: plan.issuerUrl, roles: ["admin"] }),
    listAllOrganizationMembers: vi.fn().mockResolvedValue(Array.from({ length: ownerCount }, (_, index) => ({ accountRef: `owner-${index}`, displayName: `Phone Owner ${index}`, enabled: true, role: "OWNER" }))),
    listOrganizationInvitations: vi.fn(async () => [...invitations]),
    readPreparationGrant: vi.fn(async (_space, accountRef) => ({ accountRef, permissions: ["VIEW", "EDIT", "ADMIN"], version: '"owner-1"' })),
    createOrganizationInvitation: vi.fn(async (_org, member, key) => { const result = { ...member, invitationHandle: key, requestedRole: "member", lifecycleStatus: "pending", provisioningStatus: "pending", expiresAt: new Date("2099-01-01") }; invitations.push(result); return result; }),
  };
  return api;
}
afterEach(cleanup);
describe("ordinary operator preparation controls", () => {
  it("requires actual approval after read-only review and exposes only a bounded read-only receipt", async () => {
    const api = apiFixture();
    render(<TestMemberPreparation api={api as unknown as AdminControlPlaneApi} locale="en" />);
    fireEvent.change(screen.getByLabelText("Preparation plan JSON"), { target: { value: JSON.stringify(plan) } });
    await userEvent.click(screen.getByRole("button", { name: "Review preparation plan" }));
    const approve = await screen.findByRole("button", { name: "Approve preparation" });
    expect(approve).toBeEnabled(); expect(api.createOrganizationInvitation).not.toHaveBeenCalled();
    expect(screen.getByText("Approved Test A — test-a@example.invalid")).toBeInTheDocument();
    await userEvent.click(approve);
    await screen.findByText(/Invitations are verified/);
    expect(api.createOrganizationInvitation).toHaveBeenCalledTimes(2);
    fireEvent.click(screen.getByText("Preparation receipt details"));
    const receipt = screen.getByLabelText("Dogfood preparation receipt") as HTMLTextAreaElement;
    expect(receipt).toHaveAttribute("readonly");
    expect(JSON.parse(receipt.value)).toMatchObject({ administratorAccountRef: "administrator", intendedOwnerAccountRef: "owner-0", ownerDisplayName: "Phone Owner 0", stage: "invitations-ready" });
    expect(receipt.value).not.toMatch(/password|token|activationUrl/);
    const before = api.getCurrentIdentity.mock.calls.length;
    fireEvent.change(screen.getByLabelText("Preparation operation nonce"), { target: { value: "c".repeat(32) } });
    await userEvent.click(screen.getByRole("button", { name: "Refresh preparation status" }));
    await waitFor(() => expect((screen.getByLabelText("Dogfood preparation receipt") as HTMLTextAreaElement).value).toContain('"nonce":"' + "c".repeat(32)));
    expect(api.getCurrentIdentity.mock.calls.length).toBeGreaterThan(before);
    expect(api.createOrganizationInvitation).toHaveBeenCalledTimes(2);
  });
  it.each([0, 2])("does not assume which owner to prepare when %s enabled owners exist", async (ownerCount) => {
    const api = apiFixture(ownerCount);
    render(<TestMemberPreparation api={api as unknown as AdminControlPlaneApi} locale="en" />);
    fireEvent.change(screen.getByLabelText("Preparation plan JSON"), { target: { value: JSON.stringify(plan) } });
    await userEvent.click(screen.getByRole("button", { name: "Review preparation plan" }));
    expect(await screen.findByRole("button", { name: "Approve preparation" })).toBeDisabled();
    expect(api.createOrganizationInvitation).not.toHaveBeenCalled();
  });
});

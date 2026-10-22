import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import DefaultSpaceSetup from "./DefaultSpaceSetup";
import { AdminApiError, AdminControlPlaneApi } from "./api";

afterEach(cleanup);
describe("explicit default Space setup", () => {
  it.each(["CREATED", "UNCHANGED"])("confirms %s only after an explicit action", async (result) => {
    const provisionDefaultSpace = vi.fn().mockResolvedValue({ spaceRef: "workspace-default", result });
    render(<DefaultSpaceSetup api={{ provisionDefaultSpace } as unknown as AdminControlPlaneApi} locale="en" />);
    expect(provisionDefaultSpace).not.toHaveBeenCalled();
    expect(screen.getByText(/has not been verified in this session/i)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /create or confirm/i }));
    expect(await screen.findByText(/setup is confirmed for your account/i)).toBeInTheDocument();
    expect(provisionDefaultSpace).toHaveBeenCalledTimes(1);
  });
  it.each([401, 403, 409, 500])("does not claim readiness or retry automatically after HTTP %s", async (status) => {
    const provisionDefaultSpace = vi.fn().mockRejectedValue(new AdminApiError("raw diagnostic must stay hidden", status));
    render(<DefaultSpaceSetup api={{ provisionDefaultSpace } as unknown as AdminControlPlaneApi} locale="en" />);
    await userEvent.click(screen.getByRole("button", { name: /create or confirm/i }));
    expect(await screen.findByText(status === 409 ? /different membership settings/i : status === 500 ? /could not be confirmed/i : /current owner or administrator permission/i)).toBeInTheDocument();
    expect(screen.queryByText(/setup is confirmed for your account/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/raw diagnostic/i)).not.toBeInTheDocument();
    expect(provisionDefaultSpace).toHaveBeenCalledTimes(1);
    if (status !== 500) expect(screen.getByRole("button")).toBeDisabled();
  });
});

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SettingsPanel from "./SettingsPanel";
import { changePassword, deleteAccount, getOrders } from "../lib/api";
import { downloadCsv } from "../lib/csv";
import { showToast } from "../lib/toast";
import { axeViolations } from "../test/axe";

vi.mock("../lib/api", () => ({
  changePassword: vi.fn(),
  deleteAccount: vi.fn(),
  getOrders: vi.fn(),
  getTransactions: vi.fn(),
}));
vi.mock("../lib/toast", () => ({ showToast: vi.fn() }));
vi.mock("../lib/csv", async (actual) => ({ ...(await actual()), downloadCsv: vi.fn() }));

const SESSION = { email: "me@example.com", emailVerified: true };

beforeEach(() => vi.clearAllMocks());

describe("SettingsPanel", () => {
  it("has no accessibility violations", async () => {
    const { container } = render(<SettingsPanel session={SESSION} onLogout={() => {}} onDeleted={() => {}} />);
    expect(await axeViolations(container)).toEqual([]);
  });

  it("changes the password and clears the form", async () => {
    changePassword.mockResolvedValue({ message: "Your password has been changed." });
    render(<SettingsPanel session={SESSION} onLogout={() => {}} onDeleted={() => {}} />);

    await userEvent.type(screen.getByLabelText("Current password"), "old-password");
    await userEvent.type(screen.getByLabelText("New password"), "new-password-1");
    await userEvent.type(screen.getByLabelText("Confirm new password"), "new-password-1");
    await userEvent.click(screen.getByRole("button", { name: "Change password" }));

    expect(changePassword).toHaveBeenCalledWith("old-password", "new-password-1");
    expect(showToast).toHaveBeenCalledWith("success", "Your password has been changed.");
    expect(screen.getByLabelText("Current password")).toHaveValue("");
  });

  it("refuses mismatched new passwords without calling the server", async () => {
    render(<SettingsPanel session={SESSION} onLogout={() => {}} onDeleted={() => {}} />);
    await userEvent.type(screen.getByLabelText("Current password"), "old-password");
    await userEvent.type(screen.getByLabelText("New password"), "new-password-1");
    await userEvent.type(screen.getByLabelText("Confirm new password"), "new-password-2");
    await userEvent.click(screen.getByRole("button", { name: "Change password" }));

    expect(changePassword).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent("don't match");
  });

  it("shows the server's error, e.g. a wrong current password", async () => {
    changePassword.mockRejectedValue(new Error("Your current password is incorrect"));
    render(<SettingsPanel session={SESSION} onLogout={() => {}} onDeleted={() => {}} />);
    await userEvent.type(screen.getByLabelText("Current password"), "nope");
    await userEvent.type(screen.getByLabelText("New password"), "new-password-1");
    await userEvent.type(screen.getByLabelText("Confirm new password"), "new-password-1");
    await userEvent.click(screen.getByRole("button", { name: "Change password" }));

    expect(screen.getByRole("alert")).toHaveTextContent("Your current password is incorrect");
  });

  it("exports orders as CSV", async () => {
    getOrders.mockResolvedValue([{ id: 7, symbol: "AAPL", type: "BUY", kind: "MARKET", status: "FILLED", quantity: 2, price: 190.5 }]);
    render(<SettingsPanel session={SESSION} onLogout={() => {}} onDeleted={() => {}} />);

    await userEvent.click(screen.getByRole("button", { name: /Orders/ }));

    expect(downloadCsv).toHaveBeenCalledTimes(1);
    const [filename, csv] = downloadCsv.mock.calls[0];
    expect(filename).toMatch(/^meridian-orders-\d{4}-\d{2}-\d{2}\.csv$/);
    expect(csv).toContain("Order ID,Created");
    expect(csv).toContain("7,,,AAPL,BUY,MARKET,FILLED,2,190.5");
    expect(showToast).toHaveBeenCalledWith("success", "Exported 1 order");
  });

  it("deletes the account only with the password and DELETE typed", async () => {
    deleteAccount.mockResolvedValue(null);
    const onDeleted = vi.fn();
    render(<SettingsPanel session={SESSION} onLogout={() => {}} onDeleted={onDeleted} />);

    await userEvent.click(screen.getByRole("button", { name: "Delete account…" }));
    const dialog = screen.getByRole("dialog", { name: "Delete account" });
    const submit = screen.getByRole("button", { name: "Delete me@example.com" });

    await userEvent.type(screen.getByLabelText("Password"), "my-password");
    expect(submit).toBeDisabled();
    await userEvent.type(screen.getByLabelText(/to confirm/), "DELETE");
    expect(submit).toBeEnabled();
    await userEvent.click(submit);

    expect(dialog).toBeInTheDocument();
    expect(deleteAccount).toHaveBeenCalledWith("my-password");
    expect(onDeleted).toHaveBeenCalledTimes(1);
  });
});

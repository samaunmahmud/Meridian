import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AlertsPanel from "./AlertsPanel";
import { createAlert, getAlerts } from "../lib/api";
import { showToast } from "../lib/toast";

vi.mock("../lib/api", () => ({
  getTickers: vi.fn(() => Promise.resolve([{ symbol: "AAPL", name: "Apple" }])),
  getAlerts: vi.fn(),
  createAlert: vi.fn(),
  deleteAlert: vi.fn(),
}));
vi.mock("../lib/toast", () => ({ showToast: vi.fn() }));

beforeEach(() => {
  vi.clearAllMocks();
  getAlerts.mockResolvedValue([
    { id: 1, symbol: "AAPL", direction: "BELOW", targetPrice: 190, triggered: false, movePercent: 5, referencePrice: 200 },
  ]);
});

describe("AlertsPanel", () => {
  it("shows a percentage alert as the move and the price it was measured from", async () => {
    render(<AlertsPanel />);
    expect(await screen.findByRole("button", { name: "Remove alert: AAPL down 5% from $200.00 ($190.00)" })).toBeInTheDocument();
  });

  it("creates an alert for a percentage move", async () => {
    createAlert.mockResolvedValue({ id: 2, symbol: "AAPL", direction: "ABOVE", targetPrice: 210, movePercent: 5, referencePrice: 200 });
    render(<AlertsPanel />);
    await screen.findByRole("option", { name: /AAPL/ });

    await userEvent.click(screen.getByRole("button", { name: "On a % move" }));
    await userEvent.click(screen.getByRole("button", { name: "Rises" }));
    await userEvent.type(screen.getByLabelText("Move (%) from the current price"), "5");
    await userEvent.click(screen.getByRole("button", { name: "Create alert" }));

    expect(createAlert).toHaveBeenCalledWith("AAPL", "ABOVE", null, 5);
    expect(showToast).toHaveBeenCalledWith("success", "Alert set: AAPL up 5% from $200.00 ($210.00)");
  });
});

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import RecurringOrdersPanel from "./RecurringOrdersPanel";
import { createRecurringOrder } from "../lib/api";
import { showToast } from "../lib/toast";

vi.mock("../lib/api", () => ({
  getTickers: vi.fn(() => Promise.resolve([{ symbol: "IBM", name: "IBM" }, { symbol: "NVDA", name: "NVIDIA" }])),
  getRecurringOrders: vi.fn(() =>
    Promise.resolve([
      { id: 1, symbol: "IBM", amount: 50, settlementCurrency: "EUR", frequency: "WEEKLY" },
      { id: 2, symbol: "NVDA", amount: 200, settlementCurrency: "USD", frequency: "MONTHLY" },
    ])
  ),
  getWallets: vi.fn(() =>
    Promise.resolve([
      { currency: "USD", balance: 10000, reserved: 0, available: 10000 },
      { currency: "EUR", balance: 1000, reserved: 0, available: 1000 },
    ])
  ),
  createRecurringOrder: vi.fn(() => Promise.resolve({})),
  deleteRecurringOrder: vi.fn(),
}));
vi.mock("../lib/toast", () => ({ showToast: vi.fn() }));

beforeEach(() => vi.clearAllMocks());

describe("RecurringOrdersPanel", () => {
  it("lists each recurring buy in its own currency", async () => {
    render(<RecurringOrdersPanel refreshKey={0} />);

    expect(await screen.findByText("€50.00 weekly")).toBeInTheDocument();
    expect(screen.getByText("$200.00 monthly")).toBeInTheDocument();
  });

  it("explains what a euro amount means once EUR is chosen", async () => {
    render(<RecurringOrdersPanel refreshKey={0} />);
    await screen.findByText("€50.00 weekly");
    const [, wallet] = screen.getAllByRole("combobox");

    expect(screen.getByPlaceholderText("Amount ($)")).toBeInTheDocument();
    await userEvent.selectOptions(wallet, "EUR");

    expect(screen.getByPlaceholderText("Amount (€)")).toBeInTheDocument();
    expect(screen.getByText(/worth of shares each time; commission and the 0\.5% conversion spread are charged on top/)).toBeInTheDocument();
  });

  it("creates the recurring buy against the chosen wallet", async () => {
    render(<RecurringOrdersPanel refreshKey={0} />);
    await screen.findByText("€50.00 weekly");
    const [, wallet] = screen.getAllByRole("combobox");

    await userEvent.selectOptions(wallet, "EUR");
    await userEvent.type(screen.getByPlaceholderText("Amount (€)"), "75");
    await userEvent.click(screen.getByRole("button", { name: "Set up recurring buy" }));

    expect(createRecurringOrder).toHaveBeenCalledWith("IBM", 75, "WEEKLY", "EUR");
    expect(showToast).toHaveBeenCalledWith("success", "Recurring buy set: €75.00 of IBM weekly");
  });

  it("sends no currency for USD", async () => {
    render(<RecurringOrdersPanel refreshKey={0} />);
    await screen.findByText("€50.00 weekly");

    await userEvent.type(screen.getByPlaceholderText("Amount ($)"), "100");
    await userEvent.click(screen.getByRole("button", { name: "Set up recurring buy" }));

    expect(createRecurringOrder).toHaveBeenCalledWith("IBM", 100, "WEEKLY", null);
  });
});

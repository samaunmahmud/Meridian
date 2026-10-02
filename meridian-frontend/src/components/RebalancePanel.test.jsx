import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import RebalancePanel from "./RebalancePanel";
import { getRebalancePlan, getTickers, setTargets } from "../lib/api";
import { showToast } from "../lib/toast";
import { axeViolations } from "../test/axe";

vi.mock("../lib/api", () => ({ getRebalancePlan: vi.fn(), getTickers: vi.fn(), setTargets: vi.fn() }));
vi.mock("../lib/toast", () => ({ showToast: vi.fn() }));

const PLAN = {
  hasTargets: true,
  totalValue: 9992.5,
  toleranceBand: 1,
  cash: { value: 6992.5, currentPercent: 69.98, targetPercent: 50 },
  rows: [
    { symbol: "AAPL", name: "Apple", price: 100, shares: 30, value: 3000, currentPercent: 30.02, targetPercent: 20,
      trade: { type: "SELL", quantity: 10.015, estimatedValue: 1001.5 } },
    { symbol: "MSFT", name: "Microsoft", price: 50, shares: 0, value: 0, currentPercent: 0, targetPercent: 30,
      trade: { type: "BUY", quantity: 59.8051, estimatedValue: 2990.26 } },
    { symbol: "NVDA", name: "NVIDIA", price: 10, shares: 1, value: 10, currentPercent: 0.1, targetPercent: 0.5, trade: null },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  getTickers.mockResolvedValue([{ symbol: "AAPL" }, { symbol: "MSFT" }, { symbol: "NVDA" }, { symbol: "SPY" }]);
});

describe("RebalancePanel", () => {
  it("shows drift and suggested trades that open the trade form filled in", async () => {
    getRebalancePlan.mockResolvedValue(PLAN);
    const onTrade = vi.fn();
    const { container } = render(<RebalancePanel refreshKey={0} onTrade={onTrade} />);

    expect(await screen.findByText("2 trades would bring you back to target: sell first, so the buys have the cash.")).toBeInTheDocument();
    expect(screen.getByText("30.02% now · target 20%")).toBeInTheDocument();
    expect(screen.getByText("On target")).toBeInTheDocument();
    expect(screen.getByText("69.98% now · target 50%")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Sell 10.015 AAPL, about $1,001.50" }));
    expect(onTrade).toHaveBeenCalledWith("AAPL", "SELL", 10.015);
    await userEvent.click(screen.getByRole("button", { name: "Buy 59.8051 MSFT, about $2,990.26" }));
    expect(onTrade).toHaveBeenLastCalledWith("MSFT", "BUY", 59.8051);
    expect(await axeViolations(container)).toEqual([]);
  });

  it("without targets invites setting them, starting from today's weights", async () => {
    getRebalancePlan.mockResolvedValue({
      ...PLAN,
      hasTargets: false,
      cash: { ...PLAN.cash, targetPercent: 100 },
      rows: PLAN.rows.map((r) => ({ ...r, targetPercent: 0, trade: null })),
    });
    render(<RebalancePanel refreshKey={0} />);

    await userEvent.click(await screen.findByRole("button", { name: "Set targets" }));
    expect(screen.getByLabelText("AAPL target (%)")).toHaveValue(30);
    expect(screen.getByLabelText("MSFT target (%)")).toHaveValue(0);
    expect(screen.getByRole("status")).toHaveTextContent("Stocks 30% · Cash 70%");
  });

  it("edits, adds a stock and saves every target at once", async () => {
    getRebalancePlan.mockResolvedValue(PLAN);
    setTargets.mockResolvedValue({ ...PLAN, rows: PLAN.rows.slice(0, 1) });
    render(<RebalancePanel refreshKey={0} />);
    await userEvent.click(await screen.findByRole("button", { name: "Edit targets" }));

    const aapl = screen.getByLabelText("AAPL target (%)");
    await userEvent.clear(aapl);
    await userEvent.type(aapl, "25");
    await screen.findByRole("option", { name: "SPY" });
    await userEvent.click(screen.getByRole("button", { name: "Add stock" }));
    await userEvent.type(screen.getByLabelText("SPY target (%)"), "40"); // "0" + "40" = "040"
    expect(screen.getByRole("status")).toHaveTextContent("Stocks 95.5% · Cash 4.5%");
    await userEvent.click(screen.getByRole("button", { name: "Save targets" }));

    expect(setTargets).toHaveBeenCalledWith([
      { symbol: "AAPL", percent: 25 },
      { symbol: "MSFT", percent: 30 },
      { symbol: "NVDA", percent: 0.5 },
      { symbol: "SPY", percent: 40 },
    ]);
    expect(showToast).toHaveBeenCalledWith("success", "Targets saved");
    expect(screen.queryByRole("button", { name: "Save targets" })).not.toBeInTheDocument();
  });

  it("won't save targets over 100%, and shows the server's refusal", async () => {
    getRebalancePlan.mockResolvedValue(PLAN);
    setTargets.mockRejectedValue(new Error("SPY is not a tracked stock"));
    render(<RebalancePanel refreshKey={0} />);
    await userEvent.click(await screen.findByRole("button", { name: "Edit targets" }));

    const msft = screen.getByLabelText("MSFT target (%)");
    await userEvent.clear(msft);
    await userEvent.type(msft, "90");
    expect(screen.getByRole("status")).toHaveTextContent("targets can add up to at most 100%");
    expect(screen.getByRole("button", { name: "Save targets" })).toBeDisabled();

    await userEvent.clear(msft);
    await userEvent.type(msft, "10");
    await userEvent.click(screen.getByRole("button", { name: "Save targets" }));
    expect(showToast).toHaveBeenCalledWith("error", "SPY is not a tracked stock");
    expect(within(screen.getByRole("region", { name: "Target allocation" })).getByRole("button", { name: "Save targets" })).toBeInTheDocument();
  });
});

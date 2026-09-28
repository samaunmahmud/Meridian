import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import PerformancePanel from "./PerformancePanel";
import { getPerformance } from "../lib/api";
import { axeViolations } from "../test/axe";

vi.mock("../lib/api", () => ({ getPerformance: vi.fn() }));

const DATA = {
  realizedPnL: 40,
  unrealizedPnL: -30,
  totalPnL: 10,
  feesPaid: 6,
  filledOrders: 6,
  closedTrades: 3,
  winningTrades: 2,
  winRate: 66.7,
  bestTrade: { orderId: 6, symbol: "NVDA", quantity: 4, realizedPnL: 40, executedAt: "2026-09-28T10:00:00Z" },
  worstTrade: { orderId: 4, symbol: "AAPL", quantity: 1, realizedPnL: -15, executedAt: "2026-09-28T09:00:00Z" },
  bySymbol: [
    { symbol: "NVDA", name: "NVIDIA", realizedPnL: 40, unrealizedPnL: 0, totalPnL: 40, feesPaid: 2, trades: 2 },
    { symbol: "AAPL", name: "Apple", realizedPnL: 0, unrealizedPnL: -30, totalPnL: -30, feesPaid: 4, trades: 4 },
  ],
};

beforeEach(() => vi.clearAllMocks());

describe("PerformancePanel", () => {
  it("shows realized, unrealized and total P&L with signs, and the win rate", async () => {
    getPerformance.mockResolvedValue(DATA);
    const { container } = render(<PerformancePanel refreshKey={0} />);

    expect(await screen.findByText("66.7%")).toBeInTheDocument();
    expect(screen.getByText("2 of 3 sells in profit")).toBeInTheDocument();
    expect(screen.getByText("Total P&L").nextSibling).toHaveTextContent("+$10.00");
    expect(screen.getByText("Unrealized").nextSibling).toHaveTextContent("−$30.00");
    expect(screen.getByText("$6.00")).toBeInTheDocument();
    expect(await axeViolations(container)).toEqual([]);
  });

  it("opens a stock from the breakdown", async () => {
    getPerformance.mockResolvedValue(DATA);
    const onSelect = vi.fn();
    render(<PerformancePanel refreshKey={0} onSelect={onSelect} />);

    await userEvent.click(await screen.findByRole("button", { name: /AAPL/ }));
    expect(onSelect).toHaveBeenCalledWith({ symbol: "AAPL", name: "Apple" });
  });

  it("explains an empty account instead of showing zeros as results", async () => {
    getPerformance.mockResolvedValue({
      ...DATA, realizedPnL: 0, unrealizedPnL: 0, totalPnL: 0, feesPaid: 0, filledOrders: 0, closedTrades: 0,
      winningTrades: 0, winRate: null, bestTrade: null, worstTrade: null, bySymbol: [],
    });
    render(<PerformancePanel refreshKey={0} />);

    expect(await screen.findByText("Sell to see it")).toBeInTheDocument();
    expect(screen.getAllByText("No sells yet")).toHaveLength(2);
    expect(screen.queryByText("By stock")).not.toBeInTheDocument();
  });
});

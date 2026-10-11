import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import OrderHistory from "./OrderHistory";
import { getOrders } from "../lib/api";

vi.mock("../lib/api", () => ({ getOrders: vi.fn(), cancelOrder: vi.fn() }));

const today = new Date();
today.setHours(15, 0, 0, 0);
const orders = [
  { id: 1, type: "BUY", symbol: "NVDA", status: "FILLED", kind: "MARKET", quantity: 2, price: 120, feeAmount: 1.25, realizedPnL: null, executedAt: "2026-10-09T15:00:00Z", note: "Earnings" },
  { id: 2, type: "SELL", symbol: "AAPL", status: "PENDING", kind: "LIMIT", quantity: 3, limitPrice: 245.1, createdAt: today.toISOString() },
  { id: 3, type: "SELL", symbol: "BTC", status: "FILLED", kind: "MARKET", quantity: 0.5, price: 60000, feeAmount: 7.4, realizedPnL: -12.5, executedAt: "2026-10-09T16:00:00Z" },
];

beforeEach(() => getOrders.mockResolvedValue(orders));

describe("OrderHistory", () => {
  it("groups orders by day, newest first, like a transaction list", async () => {
    render(<OrderHistory refreshKey={0} />);

    const days = await screen.findAllByRole("heading", { level: 3 });
    expect(days[0]).toHaveTextContent("Today");
    expect(days[1]).toHaveTextContent(new Date("2026-10-09T15:00:00Z").toLocaleDateString([], { day: "numeric", month: "short" }));
    const [btc, nvda] = within(screen.getByRole("list", { name: /^Orders, (?!Today)/ })).getAllByRole("listitem");
    expect(btc).toHaveTextContent("Sell BTC");
    expect(nvda).toHaveTextContent("Buy NVDA");
  });

  it("shows the money each order moved, its status, fee, note and realized P/L", async () => {
    render(<OrderHistory refreshKey={0} />);

    const today = await screen.findByRole("list", { name: "Orders, Today" });
    const aapl = within(today).getByRole("listitem");
    expect(aapl).toHaveTextContent("Pending");
    expect(aapl).toHaveTextContent("@ 245.10");
    expect(within(aapl).getByRole("button", { name: "Edit pending sell order: AAPL" })).toBeInTheDocument();

    const [btc, nvda] = within(screen.getByRole("list", { name: /^Orders, (?!Today)/ })).getAllByRole("listitem");
    expect(nvda).toHaveTextContent("−$240.00");
    expect(nvda).toHaveTextContent("Filled · fee 1.25");
    expect(nvda).toHaveTextContent("Earnings");
    expect(btc).toHaveTextContent("+$30,000.00");
    expect(btc).toHaveTextContent("P/L -12.50");
  });

  it("shows the latest ten and the rest behind See all", async () => {
    const many = Array.from({ length: 13 }, (_, i) => ({
      id: 100 + i, type: "BUY", symbol: "KO", status: "FILLED", kind: "MARKET", quantity: 1, price: 80, feeAmount: 1,
      executedAt: new Date(Date.UTC(2026, 8, 1 + i, 15)).toISOString(),
    }));
    getOrders.mockResolvedValue(many);
    render(<OrderHistory refreshKey={0} />);

    const button = await screen.findByRole("button", { name: "See all 13 orders" });
    expect(screen.getAllByRole("listitem")).toHaveLength(10);
    button.click();
    expect(await screen.findByRole("button", { name: "Show fewer" })).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(13);
  });
});

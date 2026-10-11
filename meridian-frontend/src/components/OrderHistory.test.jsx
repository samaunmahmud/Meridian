import { render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import OrderHistory from "./OrderHistory";
import { getOrders } from "../lib/api";

vi.mock("../lib/api", () => ({ getOrders: vi.fn(), cancelOrder: vi.fn() }));

const orders = [
  { id: 1, type: "BUY", symbol: "NVDA", status: "FILLED", kind: "MARKET", quantity: 2, price: 120, feeAmount: 1.25, realizedPnL: null, executedAt: "2026-10-09T15:00:00Z", note: "Earnings" },
  { id: 2, type: "SELL", symbol: "AAPL", status: "PENDING", kind: "LIMIT", quantity: 3, limitPrice: 245.1, createdAt: "2026-10-09T15:00:00Z" },
  { id: 3, type: "SELL", symbol: "BTC", status: "FILLED", kind: "MARKET", quantity: 0.5, price: 60000, feeAmount: 7.4, realizedPnL: -12.5, executedAt: "2026-10-09T15:00:00Z" },
];

beforeEach(() => getOrders.mockResolvedValue(orders));
afterEach(() => vi.restoreAllMocks());

describe("OrderHistory", () => {
  it("shows a table when it fits its card", async () => {
    render(<OrderHistory refreshKey={0} />);

    const table = await screen.findByRole("table");
    expect(within(table).getAllByRole("row")).toHaveLength(4);
    expect(screen.getByRole("button", { name: "Cancel pending sell order: AAPL" })).toBeInTheDocument();
  });

  it("stacks each order's details when the table would be wider than the card", async () => {
    vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(300);
    vi.spyOn(HTMLElement.prototype, "scrollWidth", "get").mockImplementation(function () {
      return this.tagName === "TABLE" ? 700 : 300;
    });
    render(<OrderHistory refreshKey={0} />);

    const list = await screen.findByRole("list", { name: "Orders" });
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    const [nvda, aapl, btc] = within(list).getAllByRole("listitem");
    expect(nvda).toHaveTextContent("Buy NVDA");
    expect(nvda).toHaveTextContent("Filled · fee 1.25");
    expect(nvda).toHaveTextContent("Earnings");
    expect(aapl).toHaveTextContent("Pending");
    expect(aapl).toHaveTextContent("@ 245.10");
    expect(within(aapl).getByRole("button", { name: "Edit pending sell order: AAPL" })).toBeInTheDocument();
    expect(btc).toHaveTextContent("P/L -12.50");
  });
});

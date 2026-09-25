import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { axeViolations } from "../test/axe";
import StockDetails from "./StockDetails";
import { getAlerts, getChanges, getOrders, getPortfolio } from "../lib/api";

vi.mock("../lib/api", () => ({ getChanges: vi.fn(), getPortfolio: vi.fn(), getOrders: vi.fn(), getAlerts: vi.fn() }));

const nvda = { symbol: "NVDA", name: "NVIDIA Corporation", exchange: "NASDAQ", assetType: "STOCK" };
const btc = { symbol: "BTC", name: "Bitcoin", exchange: "CRYPTO", assetType: "CRYPTO" };

const holding = {
  symbol: "NVDA", quantity: 10, reservedQuantity: 2, availableQuantity: 8, avgCost: 100,
  currentPrice: 110, marketValue: 1100, gainLoss: 100, gainLossPct: 10,
};

function stat(label) {
  return screen.getByText(label).nextElementSibling;
}

beforeEach(() => {
  vi.clearAllMocks();
  getChanges.mockResolvedValue([
    { symbol: "NVDA", price: 110, referencePrice: 104 },
    { symbol: "BTC", price: 60000, referencePrice: 62000 },
  ]);
  getPortfolio.mockResolvedValue({ holdings: [holding] });
  getOrders.mockResolvedValue([
    { id: 1, symbol: "NVDA", type: "SELL", kind: "LIMIT", status: "PENDING", quantity: 2, limitPrice: 130 },
    { id: 2, symbol: "NVDA", type: "BUY", kind: "MARKET", status: "FILLED", quantity: 10, price: 100 },
    { id: 3, symbol: "AAPL", type: "BUY", kind: "LIMIT", status: "PENDING", quantity: 1, limitPrice: 200 },
  ]);
  getAlerts.mockResolvedValue([
    { id: 5, symbol: "NVDA", direction: "BELOW", targetPrice: 95, triggered: false },
    { id: 6, symbol: "NVDA", direction: "ABOVE", targetPrice: 105, triggered: true },
  ]);
});

describe("StockDetails", () => {
  it("shows the day's move from the previous close and the position's value and return", async () => {
    render(<StockDetails ticker={nvda} />);
    await screen.findByRole("heading", { name: "NVDA today and your position" });

    expect(stat("Previous close")).toHaveTextContent("$104.00");
    expect(stat("Change today")).toHaveTextContent("+$6.00 (+5.77%)");
    expect(stat("You own")).toHaveTextContent("10");
    expect(stat("Average cost")).toHaveTextContent("$100.00");
    expect(stat("Market value")).toHaveTextContent("$1,100.00");
    expect(stat("Total return")).toHaveTextContent("+$100.00 (+10.00%)");
    expect(stat("Held for sell orders")).toHaveTextContent("2");
  });

  it("lists only this stock's open orders and untriggered alerts", async () => {
    render(<StockDetails ticker={nvda} />);

    const orders = (await screen.findByRole("heading", { name: "Open orders" })).parentElement;
    expect(within(orders).getAllByRole("listitem").map((li) => li.textContent)).toEqual(["Sell 2 · Limit at $130.00"]);
    const alerts = screen.getByRole("heading", { name: "Price alerts" }).parentElement;
    expect(within(alerts).getAllByRole("listitem").map((li) => li.textContent)).toEqual([
      "Tell me when it goes below $95.00",
    ]);
  });

  it("follows live prices for the day's change and the position's value", async () => {
    const { rerender } = render(<StockDetails ticker={nvda} />);
    await screen.findByText("Market value");

    rerender(<StockDetails ticker={nvda} liveUpdate={{ kind: "PRICE_UPDATE", symbol: "NVDA", price: 99 }} />);

    expect(stat("Change today")).toHaveTextContent("-$5.00 (-4.81%)");
    expect(stat("Market value")).toHaveTextContent("$990.00");
    expect(stat("Total return")).toHaveTextContent("-$10.00 (-1.00%)");
  });

  it("uses 24-hour wording for crypto and says when nothing is owned", async () => {
    render(<StockDetails ticker={btc} />);

    expect(await screen.findByText("You don’t own any BTC yet.")).toBeInTheDocument();
    expect(stat("Price 24 h ago")).toHaveTextContent("$62,000.00");
    expect(stat("Change (24 h)")).toHaveTextContent("-$2,000.00 (-3.23%)");
    expect(screen.queryByText("Open orders")).not.toBeInTheDocument();
  });

  it("leaves out what failed to load instead of claiming there is no position", async () => {
    getPortfolio.mockRejectedValue(new Error("down"));
    render(<StockDetails ticker={nvda} />);
    await screen.findByText("Change today");

    expect(screen.queryByText(/don’t own/)).not.toBeInTheDocument();
    expect(screen.queryByText("Market value")).not.toBeInTheDocument();
  });

  it("passes axe", async () => {
    const { container } = render(<StockDetails ticker={nvda} />);
    await screen.findByText("Open orders");

    expect(await axeViolations(container)).toEqual([]);
  });
});

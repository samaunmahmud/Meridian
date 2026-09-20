import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MarketBadge from "./MarketBadge";
import TradePanel from "./TradePanel";
import { getMarketStatus, placeOrder } from "../lib/api";
import { showToast } from "../lib/toast";

vi.mock("../lib/api", () => ({
  getMarketStatus: vi.fn(),
  getTickers: vi.fn(() =>
    Promise.resolve([
      { symbol: "NVDA", name: "NVIDIA Corporation", assetType: "STOCK" },
      { symbol: "BTC", name: "Bitcoin", assetType: "CRYPTO" },
    ])
  ),
  getPrices: vi.fn(() => Promise.resolve([{ price: 120 }])),
  getWallets: vi.fn(() => Promise.resolve([{ currency: "USD", balance: 10000, reserved: 0, available: 10000 }])),
  getFxRates: vi.fn(() => Promise.resolve([])),
  placeOrder: vi.fn(),
}));
vi.mock("../lib/toast", () => ({ showToast: vi.fn() }));

const inTwoDays = new Date(Date.now() + 2 * 86400000).toISOString();
const closed = { stocks: { open: false, nextOpen: inTwoDays, nextClose: null }, crypto: { open: true } };
const open = { stocks: { open: true, nextOpen: null, nextClose: inTwoDays }, crypto: { open: true } };

beforeEach(() => vi.clearAllMocks());

async function renderPanel() {
  render(<TradePanel onOrderPlaced={vi.fn()} refreshKey={0} />);
  await screen.findByRole("option", { name: /NVDA/ });
}

describe("MarketBadge", () => {
  it("says the market is closed and when it opens", async () => {
    getMarketStatus.mockResolvedValue(closed);
    render(<MarketBadge assetType="STOCK" />);
    expect(await screen.findByText(/Market closed · opens/)).toBeInTheDocument();
  });

  it("says the market is open", async () => {
    getMarketStatus.mockResolvedValue(open);
    render(<MarketBadge assetType="STOCK" />);
    expect(await screen.findByText(/Market open · closes/)).toBeInTheDocument();
  });

  it("shows crypto as always open even while stocks are closed", async () => {
    getMarketStatus.mockResolvedValue(closed);
    render(<MarketBadge assetType="CRYPTO" />);
    expect(await screen.findByText("Crypto trades 24/7")).toBeInTheDocument();
  });
});

describe("TradePanel while the stock market is closed", () => {
  it("explains that a market order is queued, and the button says so", async () => {
    getMarketStatus.mockResolvedValue(closed);
    await renderPanel();

    expect(await screen.findByText(/The market is closed \(opens/)).toBeInTheDocument();
    expect(screen.getByText(/queued and fills at the first price after the open/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Queue buy NVDA" })).toBeInTheDocument();
  });

  it("says limit orders only fill while the market is open, and keeps the normal button", async () => {
    getMarketStatus.mockResolvedValue(closed);
    await renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "Limit" }));

    expect(await screen.findByText(/only fill while the market is open/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Buy NVDA" })).toBeInTheDocument();
  });

  it("confirms with a toast that the order is queued", async () => {
    getMarketStatus.mockResolvedValue(closed);
    placeOrder.mockResolvedValue({ status: "PENDING", kind: "MARKET", type: "BUY", quantity: 1, symbol: "NVDA" });
    await renderPanel();
    await screen.findByText(/The market is closed/);

    await userEvent.click(screen.getByRole("button", { name: "Queue buy NVDA" }));

    expect(showToast).toHaveBeenCalledWith("success", expect.stringContaining("Market order queued for the open"));
  });

  it("does not warn about a crypto order, which trades around the clock", async () => {
    getMarketStatus.mockResolvedValue(closed);
    await renderPanel();
    await userEvent.selectOptions(screen.getByLabelText("Symbol"), "BTC");

    expect(screen.queryByText(/The market is closed/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Buy BTC" })).toBeInTheDocument();
  });

  it("shows no notice while the market is open", async () => {
    getMarketStatus.mockResolvedValue(open);
    await renderPanel();

    expect(screen.queryByText(/The market is closed/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Buy NVDA" })).toBeInTheDocument();
  });
});

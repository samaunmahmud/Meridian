import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import StockHero from "./StockHero";
import { getPrices } from "../lib/api";

vi.mock("../lib/api", () => ({
  getMarketStatus: vi.fn(() => Promise.resolve({ stocks: { open: true, nextClose: null }, crypto: { open: true } })), getPrices: vi.fn() }));

const ticker = { symbol: "AAPL", name: "Apple Inc.", exchange: "NASDAQ" };
const minutesAgo = (m) => new Date(Date.now() - m * 60000).toISOString();

// The API returns newest first.
const pricesRecorded = (...ages) => ages.map((m, i) => ({ price: 230 - i, recordedAt: minutesAgo(m) }));

beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal("ResizeObserver", class { observe() {} unobserve() {} disconnect() {} });
});
afterEach(() => vi.unstubAllGlobals());

describe("StockHero: how old is this price?", () => {
  it("shows the time of the last update for a live price", async () => {
    getPrices.mockResolvedValue(pricesRecorded(1, 21, 41));
    render(<StockHero ticker={ticker} liveUpdate={null} onTrade={vi.fn()} />);

    expect(await screen.findByText(/^Updated /)).toBeInTheDocument();
    expect(screen.queryByText(/Prices delayed/)).not.toBeInTheDocument();
  });

  it("says plainly that prices are delayed, and by how much, once the feed has fallen behind", async () => {
    getPrices.mockResolvedValue(pricesRecorded(180, 200, 220)); // newest is 3 hours old
    render(<StockHero ticker={ticker} liveUpdate={null} onTrade={vi.fn()} />);

    const notice = await screen.findByRole("status");
    expect(notice).toHaveTextContent("Prices delayed — last update 3 h ago");
    expect(screen.queryByText(/^Updated /)).not.toBeInTheDocument();
  });

  it("asks the server for the chosen time window, thinned, and keeps the range buttons in place", async () => {
    getPrices.mockResolvedValue(pricesRecorded(1, 21, 41));
    render(<StockHero ticker={ticker} liveUpdate={null} onTrade={vi.fn()} />);
    await screen.findByText(/^Updated /);
    expect(getPrices).toHaveBeenLastCalledWith("AAPL", { range: "1D", points: 500 });
    expect(screen.getByRole("button", { name: "1D" })).toHaveAttribute("aria-pressed", "true");

    await userEvent.click(screen.getByRole("button", { name: "1W" }));

    expect(getPrices).toHaveBeenLastCalledWith("AAPL", { range: "1W", points: 500 });
    expect(screen.getByRole("button", { name: "1W" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "All" })).toBeInTheDocument();
  });
});

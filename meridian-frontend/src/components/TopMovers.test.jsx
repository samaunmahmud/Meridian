import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { axeViolations } from "../test/axe";
import TopMovers from "./TopMovers";
import { getMovers } from "../lib/api";

vi.mock("../lib/api", () => ({ getMovers: vi.fn() }));

const nvda = { symbol: "NVDA", name: "NVIDIA Corporation", exchange: "NASDAQ", assetType: "STOCK", price: 132.5, changePercent: 4.12 };
const btc = { symbol: "BTC", name: "Bitcoin", exchange: "CRYPTO", assetType: "CRYPTO", price: 61000, changePercent: -2.5 };

beforeEach(() => vi.clearAllMocks());

describe("TopMovers", () => {
  it("lists gainers and losers with their daily change", async () => {
    getMovers.mockResolvedValue({ gainers: [nvda], losers: [btc] });
    render(<TopMovers onSelect={vi.fn()} />);

    const gainers = (await screen.findByRole("heading", { name: "Gainers" })).parentElement;
    const losers = screen.getByRole("heading", { name: "Losers" }).parentElement;
    expect(within(gainers).getByText("NVDA")).toBeInTheDocument();
    expect(within(gainers).getByText("+4.12%")).toBeInTheDocument();
    expect(within(losers).getByText("BTC")).toBeInTheDocument();
    expect(within(losers).getByText("-2.50%")).toBeInTheDocument();
  });

  it("passes axe", async () => {
    getMovers.mockResolvedValue({ gainers: [nvda], losers: [btc] });
    const { container } = render(<TopMovers onSelect={vi.fn()} />);
    await screen.findByText("NVDA");

    expect(await axeViolations(container)).toEqual([]);
  });

  it("says so when nothing has moved", async () => {
    getMovers.mockResolvedValue({ gainers: [], losers: [] });
    render(<TopMovers onSelect={vi.fn()} />);

    expect(await screen.findByText("Nothing is up yet today.")).toBeInTheDocument();
    expect(screen.getByText("Nothing is down yet today.")).toBeInTheDocument();
  });

  it("picking a mover selects it", async () => {
    getMovers.mockResolvedValue({ gainers: [nvda], losers: [] });
    const onSelect = vi.fn();
    render(<TopMovers onSelect={onSelect} />);

    await userEvent.click(await screen.findByRole("button", { name: /NVDA/ }));

    expect(onSelect).toHaveBeenCalledWith(nvda);
  });

  it("asks again after a live price update", async () => {
    vi.useFakeTimers();
    try {
      getMovers.mockResolvedValue({ gainers: [], losers: [] });
      const { rerender } = render(<TopMovers onSelect={vi.fn()} liveUpdate={null} />);
      expect(getMovers).toHaveBeenCalledTimes(1);

      rerender(<TopMovers onSelect={vi.fn()} liveUpdate={{ kind: "PRICE_UPDATE", symbol: "NVDA", price: 1 }} />);
      await act(async () => vi.advanceTimersByTime(2500));

      expect(getMovers).toHaveBeenCalledTimes(2);
    } finally {
      vi.useRealTimers();
    }
  });

  it("shows a message when the request fails", async () => {
    getMovers.mockRejectedValue(new Error("down"));
    render(<TopMovers onSelect={vi.fn()} />);

    expect(await screen.findByText("Top movers could not be loaded.")).toBeInTheDocument();
  });
});

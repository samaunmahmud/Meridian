import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { axeViolations } from "../test/axe";
import Watchlist from "./Watchlist";
import { getChanges, getPrices, getTickers } from "../lib/api";

vi.mock("../lib/api", () => ({ getTickers: vi.fn(), getPrices: vi.fn(), getChanges: vi.fn() }));

const nvda = { symbol: "NVDA", name: "NVIDIA Corporation" };
const btc = { symbol: "BTC", name: "Bitcoin" };

// Newest first, as the server sends them. The last two polls moved NVDA 110 -> 104 (down), but
// against yesterday's close of 100 it is up 4% today.
const history = {
  NVDA: [{ price: 104, recordedAt: "2026-09-21T15:00:00Z" }, { price: 110, recordedAt: "2026-09-21T14:59:40Z" }],
  BTC: [{ price: 61000, recordedAt: "2026-09-21T15:00:00Z" }],
};

function rowFor(symbol) {
  return screen.getByRole("button", { name: new RegExp(`^${symbol}`) });
}

beforeEach(() => {
  vi.clearAllMocks();
  getTickers.mockResolvedValue([nvda, btc]);
  getPrices.mockImplementation((symbol) => Promise.resolve(history[symbol]));
  getChanges.mockResolvedValue([{ symbol: "NVDA", referencePrice: 100, changePercent: 4 }]);
});

describe("Watchlist", () => {
  it("shows today's change against the previous close, not the change since the last poll", async () => {
    render(<Watchlist onSelect={vi.fn()} />);

    expect(await screen.findByText("+4.00%")).toBeInTheDocument();
    expect(screen.queryByText(/-5\.45%/)).not.toBeInTheDocument();
    expect(rowFor("NVDA")).toHaveAccessibleName(/\+4\.00%\s*today/);
  });

  it("shows no change for a ticker with no reference price yet", async () => {
    render(<Watchlist onSelect={vi.fn()} />);
    await screen.findByText("+4.00%");

    expect(rowFor("BTC").textContent).not.toMatch(/%/);
  });

  it("measures a live price against the same reference", async () => {
    const { rerender } = render(<Watchlist onSelect={vi.fn()} />);
    await screen.findByText("+4.00%");

    rerender(
      <Watchlist
        onSelect={vi.fn()}
        liveUpdate={{ kind: "PRICE_UPDATE", symbol: "NVDA", price: 97, recordedAt: "2026-09-21T15:00:20Z" }}
      />
    );

    expect(await screen.findByText("-3.00%")).toBeInTheDocument();
  });

  it("still lists prices when the changes cannot be loaded", async () => {
    getChanges.mockRejectedValue(new Error("down"));
    render(<Watchlist onSelect={vi.fn()} />);

    expect(await screen.findByText("$104.00")).toBeInTheDocument();
    expect(rowFor("NVDA").textContent).not.toMatch(/%/);
  });

  it("passes axe", async () => {
    const { container } = render(<Watchlist onSelect={vi.fn()} />);
    await screen.findByText("+4.00%");

    expect(await axeViolations(container)).toEqual([]);
  });
});

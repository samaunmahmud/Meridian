import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import StockDividends from "./StockDividends";
import { getDividends, getPrices } from "../lib/api";
import { axeViolations } from "../test/axe";

vi.mock("../lib/api", () => ({ getDividends: vi.fn(), getPrices: vi.fn() }));

const IBM = { symbol: "IBM", name: "IBM", assetType: "STOCK" };

// A calendar day `days` from today, as the server sends it.
function iso(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function label(isoDay) {
  const [y, m, d] = isoDay.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString("en-US", { month: "short", day: "numeric", timeZone: "UTC" });
}

beforeEach(() => {
  vi.clearAllMocks();
  getPrices.mockResolvedValue([{ price: 250, recordedAt: new Date().toISOString() }]);
});

describe("StockDividends", () => {
  it("shows the next dividend, the yield, what you've received and the history", async () => {
    const next = { exDate: iso(20), payDate: iso(30), amount: 1.69 };
    getDividends.mockResolvedValue({
      symbol: "IBM",
      dividends: [next, { exDate: iso(-70), payDate: iso(-40), amount: 1.68 }, { exDate: iso(-160), payDate: iso(-130), amount: 1.67 }],
      trailingYearPerShare: 6.7,
      received: 33.6,
    });
    const { container } = render(<StockDividends ticker={IBM} refreshKey={0} />);

    expect(await screen.findByText("Next dividend")).toBeInTheDocument();
    expect(getDividends).toHaveBeenCalledWith("IBM");
    expect(screen.getByText("$1.69 / share")).toBeInTheDocument();
    expect(screen.getByText(`Ex ${label(next.exDate)} · paid ${label(next.payDate)}`)).toBeInTheDocument();
    expect(screen.getByText("$6.70 / share")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("2.68%")).toBeInTheDocument()); // 6.70 / 250
    expect(screen.getByText("$33.60")).toBeInTheDocument();
    expect(screen.getByRole("table", { name: "Recent dividends of IBM" })).toBeInTheDocument();
    expect(screen.getAllByRole("row")).toHaveLength(4); // header + 3
    expect(await axeViolations(container)).toEqual([]);
  });

  it("shows the last dividend when none is announced", async () => {
    getDividends.mockResolvedValue({
      symbol: "IBM",
      dividends: [{ exDate: iso(-70), payDate: iso(-40), amount: 1.68 }],
      trailingYearPerShare: 1.68,
      received: 0,
    });
    render(<StockDividends ticker={IBM} refreshKey={0} />);
    expect(await screen.findByText("Last dividend")).toBeInTheDocument();
  });

  it("an ex-date that has passed with the payment still to come is the next dividend", async () => {
    getDividends.mockResolvedValue({
      symbol: "IBM",
      dividends: [{ exDate: iso(-3), payDate: iso(5), amount: 1.69 }],
      trailingYearPerShare: 1.69,
      received: 0,
    });
    render(<StockDividends ticker={IBM} refreshKey={0} />);
    expect(await screen.findByText("Next dividend")).toBeInTheDocument();
  });

  it("is left out for stocks that pay nothing, on errors, and for crypto", async () => {
    getDividends.mockResolvedValueOnce({ symbol: "TSLA", dividends: [], trailingYearPerShare: 0, received: 0 });
    const { container, unmount } = render(<StockDividends ticker={{ symbol: "TSLA", assetType: "STOCK" }} refreshKey={0} />);
    await waitFor(() => expect(getDividends).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
    unmount();

    getDividends.mockRejectedValueOnce(new Error("down"));
    const failed = render(<StockDividends ticker={IBM} refreshKey={0} />);
    await waitFor(() => expect(getDividends).toHaveBeenCalledTimes(2));
    expect(failed.container).toBeEmptyDOMElement();
    failed.unmount();

    const coin = render(<StockDividends ticker={{ symbol: "BTC", assetType: "CRYPTO" }} refreshKey={0} />);
    expect(coin.container).toBeEmptyDOMElement();
    expect(getDividends).toHaveBeenCalledTimes(2);
  });
});

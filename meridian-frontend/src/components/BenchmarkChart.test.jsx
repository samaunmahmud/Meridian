import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import BenchmarkChart from "./BenchmarkChart";
import { getBenchmark, getTickers } from "../lib/api";
import { axeViolations } from "../test/axe";

vi.mock("../lib/api", () => ({ getBenchmark: vi.fn(), getTickers: vi.fn() }));

const TICKERS = [
  { symbol: "BTC", name: "Bitcoin", exchange: "CRYPTO", assetType: "CRYPTO" },
  { symbol: "AAPL", name: "Apple", exchange: "NASDAQ", assetType: "STOCK" },
  { symbol: "SPY", name: "SPDR S&P 500", exchange: "NYSE", assetType: "STOCK" },
];

function result(symbol, portfolioReturn, benchmarkReturn, n = 3) {
  return {
    symbol,
    name: TICKERS.find((t) => t.symbol === symbol)?.name,
    portfolioReturn,
    benchmarkReturn,
    points: Array.from({ length: n }, (_, i) => ({
      at: `2026-09-${10 + i}T12:00:00Z`,
      portfolio: i === n - 1 ? portfolioReturn : 0,
      benchmark: i === n - 1 ? benchmarkReturn : 0,
    })),
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  getTickers.mockResolvedValue(TICKERS);
});
// For the whole file, not per test: a chart can finish rendering just after a test ends.
beforeAll(() => vi.stubGlobal("ResizeObserver", class { observe() {} unobserve() {} disconnect() {} }));
afterAll(() => vi.unstubAllGlobals());

describe("BenchmarkChart", () => {
  it("compares against an index fund when one is tracked, with both returns and a text summary", async () => {
    getBenchmark.mockResolvedValue(result("SPY", 13.44, 20));
    const { container } = render(<BenchmarkChart refreshKey={0} />);

    expect(await screen.findByText("+13.44%")).toBeInTheDocument();
    expect(getBenchmark).toHaveBeenCalledWith("SPY", "1M");
    expect(screen.getByText("+20.00%")).toBeInTheDocument();
    expect(screen.getByLabelText("Compare with")).toHaveValue("SPY");
    expect(screen.getByRole("img")).toHaveAccessibleName(
      "Your portfolio +13.44% against SPY +20.00%: 6.56 points behind it; 3 data points"
    );
    expect(await axeViolations(container)).toEqual([]);
  });

  it("changes the ticker and window, and remembers the ticker", async () => {
    getBenchmark.mockImplementation((symbol) => Promise.resolve(result(symbol, -2, symbol === "AAPL" ? -5 : 1)));
    render(<BenchmarkChart refreshKey={0} />);
    await screen.findByText("−2.00%");

    await userEvent.selectOptions(screen.getByLabelText("Compare with"), "AAPL");
    expect(await screen.findByText("−5.00%")).toBeInTheDocument();
    expect(localStorage.getItem("meridian_benchmark")).toBe("AAPL");

    await userEvent.click(screen.getByRole("button", { name: "3M" }));
    await waitFor(() => expect(getBenchmark).toHaveBeenLastCalledWith("AAPL", "3M"));
    expect(screen.getByRole("button", { name: "3M" })).toHaveAttribute("aria-pressed", "true");
  });

  it("uses the remembered ticker, and falls back to a stock rather than crypto", async () => {
    localStorage.setItem("meridian_benchmark", "AAPL");
    getBenchmark.mockResolvedValue(result("AAPL", 1, 1));
    const { unmount } = render(<BenchmarkChart refreshKey={0} />);
    await waitFor(() => expect(getBenchmark).toHaveBeenCalledWith("AAPL", "1M"));
    unmount();

    localStorage.clear();
    getTickers.mockResolvedValue(TICKERS.filter((t) => t.symbol !== "SPY"));
    render(<BenchmarkChart refreshKey={0} />);
    await waitFor(() => expect(getBenchmark).toHaveBeenLastCalledWith("AAPL", "1M"));
  });

  it("explains when there is not enough history yet", async () => {
    getBenchmark.mockResolvedValue({ symbol: "SPY", name: "SPDR S&P 500", portfolioReturn: null, benchmarkReturn: null, points: [] });
    render(<BenchmarkChart refreshKey={0} />);
    expect(await screen.findByText(/Not enough history in this window yet/)).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("shows nothing when there are no tickers to compare with", async () => {
    getTickers.mockResolvedValue([]);
    const { container } = render(<BenchmarkChart refreshKey={0} />);
    await waitFor(() => expect(getTickers).toHaveBeenCalled());
    await waitFor(() => expect(container).toBeEmptyDOMElement());
    expect(getBenchmark).not.toHaveBeenCalled();
  });
});

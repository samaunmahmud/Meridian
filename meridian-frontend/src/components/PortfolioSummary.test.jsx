import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import PortfolioSummary from "./PortfolioSummary";
import { getPortfolio, getPortfolioHistory } from "../lib/api";

vi.mock("../lib/api", () => ({ getPortfolio: vi.fn(), getPortfolioHistory: vi.fn() }));

const portfolio = { totalValue: 16500, holdingsValue: 12000, cashBalance: 4500, reservedCash: 0, holdings: [], amountPutIn: 15000 };

beforeEach(() => {
  vi.stubGlobal("ResizeObserver", class { observe() {} unobserve() {} disconnect() {} });
  getPortfolio.mockResolvedValue(portfolio);
});
afterEach(() => vi.unstubAllGlobals());

describe("PortfolioSummary", () => {
  it("leaves money put in during the window out of the change", async () => {
    // $10,000 a month ago, $5,000 deposited since, $16,500 now: a $1,500 gain, not $6,500.
    getPortfolioHistory.mockResolvedValue([
      { totalValue: 10000, recordedAt: "2026-09-11T20:00:00Z", amountPutIn: 10000 },
      { totalValue: 16500, recordedAt: "2026-10-11T16:00:00Z", amountPutIn: 15000 },
    ]);
    render(<PortfolioSummary refreshKey={0} />);

    expect(await screen.findByText(/\+\$1,500\.00 \(\+10\.00%\)/)).toBeInTheDocument();
  });

  it("falls back to the plain change when the history has no amounts put in", async () => {
    getPortfolioHistory.mockResolvedValue([
      { totalValue: 15000, recordedAt: "2026-09-11T20:00:00Z" },
      { totalValue: 16500, recordedAt: "2026-10-11T16:00:00Z" },
    ]);
    render(<PortfolioSummary refreshKey={0} />);

    expect(await screen.findByText(/\+\$1,500\.00 \(\+10\.00%\)/)).toBeInTheDocument();
  });
});

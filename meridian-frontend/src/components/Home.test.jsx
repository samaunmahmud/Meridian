import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { axeViolations } from "../test/axe";
import AddMoneyModal from "./AddMoneyModal";
import BalanceHero from "./BalanceHero";
import InvestmentsList from "./InvestmentsList";
import { depositToWallet, getPortfolio, getPortfolioHistory } from "../lib/api";

vi.mock("../lib/api", () => ({
  getPortfolio: vi.fn(),
  getPortfolioHistory: vi.fn(),
  depositToWallet: vi.fn(),
}));
vi.mock("../lib/toast", () => ({ showToast: vi.fn() }));
// The balance counts up to its value; show the final figure straight away in tests.
vi.mock("../lib/useAnimatedNumber", () => ({ useAnimatedNumber: (v) => v }));

const nvda = { symbol: "NVDA", name: "NVIDIA Corporation", quantity: 10, marketValue: 1280.76, gainLoss: 30, gainLossPct: 2.4 };
const btc = { symbol: "BTC", name: "Bitcoin", quantity: 0.5, marketValue: 30000, gainLoss: -500, gainLossPct: -1.64 };

beforeEach(() => {
  vi.clearAllMocks();
  getPortfolio.mockResolvedValue({ totalValue: 10250, holdingsValue: 1280.76, cashBalance: 8969.24, holdings: [nvda, btc] });
  getPortfolioHistory.mockResolvedValue([{ totalValue: 10000 }, { totalValue: 10250 }]);
});

describe("BalanceHero", () => {
  it("shows the total balance and the all-time change", async () => {
    render(<BalanceHero />);

    expect(await screen.findByText("$10,250.00")).toBeInTheDocument();
    expect(screen.getByText(/\+\$250\.00 \(\+2\.50%\)/)).toBeInTheDocument();
  });

  it("each round action button does its job", async () => {
    const handlers = { onBuy: vi.fn(), onSell: vi.fn(), onExchange: vi.fn(), onAddMoney: vi.fn() };
    render(<BalanceHero {...handlers} />);
    await screen.findByText("$10,250.00");

    await userEvent.click(screen.getByRole("button", { name: "Buy" }));
    await userEvent.click(screen.getByRole("button", { name: "Sell" }));
    await userEvent.click(screen.getByRole("button", { name: "Exchange" }));
    await userEvent.click(screen.getByRole("button", { name: "Add money" }));

    for (const fn of Object.values(handlers)) expect(fn).toHaveBeenCalledTimes(1);
  });

  it("passes axe", async () => {
    const { container } = render(<BalanceHero />);
    await screen.findByText("$10,250.00");
    expect(await axeViolations(container)).toEqual([]);
  });
});

describe("InvestmentsList", () => {
  it("lists each position with its shares, value and return, and opens it when picked", async () => {
    const onSelect = vi.fn();
    render(<InvestmentsList onSelect={onSelect} />);

    const row = await screen.findByRole("button", { name: /NVIDIA Corporation/ });
    expect(row).toHaveTextContent("10 shares");
    expect(row).toHaveTextContent("$1,280.76");
    expect(row).toHaveTextContent("+2.40%");
    expect(screen.getByRole("button", { name: /Bitcoin/ })).toHaveTextContent("−1.64%");

    await userEvent.click(row);
    expect(onSelect).toHaveBeenCalledWith(nvda);
  });

  it("offers a first buy when nothing is owned", async () => {
    getPortfolio.mockResolvedValue({ holdings: [] });
    const onStart = vi.fn();
    render(<InvestmentsList onSelect={vi.fn()} onStart={onStart} />);

    await userEvent.click(await screen.findByRole("button", { name: "Buy your first stock" }));
    expect(onStart).toHaveBeenCalled();
  });

  it("passes axe", async () => {
    const { container } = render(<InvestmentsList onSelect={vi.fn()} />);
    await screen.findByText("NVIDIA Corporation");
    expect(await axeViolations(container)).toEqual([]);
  });
});

describe("AddMoneyModal", () => {
  it("adds the chosen amount to the chosen account", async () => {
    depositToWallet.mockResolvedValue({});
    const onAdded = vi.fn();
    const onClose = vi.fn();
    render(<AddMoneyModal onAdded={onAdded} onClose={onClose} />);

    await userEvent.click(screen.getByRole("button", { name: "EUR" }));
    await userEvent.click(screen.getByRole("button", { name: "€500" }));
    await userEvent.click(screen.getByRole("button", { name: "Add money" }));

    expect(depositToWallet).toHaveBeenCalledWith("EUR", 500);
    expect(onAdded).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it("will not submit without an amount", () => {
    render(<AddMoneyModal onAdded={vi.fn()} onClose={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Add money" })).toBeDisabled();
  });

  it("passes axe", async () => {
    render(<AddMoneyModal onAdded={vi.fn()} onClose={vi.fn()} />);
    expect(await axeViolations(document.body)).toEqual([]);
  });
});

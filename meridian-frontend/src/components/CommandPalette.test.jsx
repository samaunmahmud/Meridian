import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import CommandPalette, { matchTickers } from "./CommandPalette";
import { getTickers } from "../lib/api";
import { axeViolations } from "../test/axe";

vi.mock("../lib/api", () => ({ getTickers: vi.fn() }));

const TICKERS = [
  { symbol: "AAPL", name: "Apple Inc.", exchange: "NASDAQ", assetType: "STOCK" },
  { symbol: "MSFT", name: "Microsoft Corp.", exchange: "NASDAQ", assetType: "STOCK" },
  { symbol: "PYPL", name: "PayPal Holdings", exchange: "NASDAQ", assetType: "STOCK" },
  { symbol: "BTC", name: "Bitcoin", exchange: "CRYPTO", assetType: "CRYPTO" },
];

beforeEach(() => {
  vi.clearAllMocks();
  getTickers.mockResolvedValue(TICKERS);
});

describe("matchTickers", () => {
  it("puts symbol matches before name matches", () => {
    expect(matchTickers(TICKERS, "p").map((t) => t.symbol)).toEqual(["PYPL", "AAPL", "MSFT"]); // "Corp." has a p
    expect(matchTickers(TICKERS, "micro").map((t) => t.symbol)).toEqual(["MSFT"]);
    expect(matchTickers(TICKERS, "zzz")).toEqual([]);
  });
});

describe("CommandPalette", () => {
  function setup() {
    const commands = [
      { id: "tab-portfolio", group: "Go to", label: "Portfolio", icon: "pie", run: vi.fn() },
      { id: "add", group: "Actions", label: "Add money", icon: "plus", keywords: ["deposit"], run: vi.fn() },
    ];
    const onOpenStock = vi.fn();
    const onClose = vi.fn();
    const view = render(<CommandPalette commands={commands} onOpenStock={onOpenStock} onClose={onClose} />);
    return { commands, onOpenStock, onClose, ...view };
  }

  it("is an accessible combobox with no violations", async () => {
    setup();
    await screen.findByRole("option", { name: /AAPL/ });
    const box = screen.getByRole("combobox", { name: "Search stocks, pages and actions" });
    expect(box).toHaveFocus();
    expect(await axeViolations(screen.getByRole("dialog"))).toEqual([]);
  });

  it("opens the highlighted stock with the keyboard", async () => {
    const { onOpenStock, onClose } = setup();
    await screen.findByRole("option", { name: /AAPL/ });

    await userEvent.type(screen.getByRole("combobox"), "bit");
    expect(screen.getByRole("option", { name: /BTC/ })).toHaveAttribute("aria-selected", "true");
    await userEvent.keyboard("{Enter}");

    expect(onClose).toHaveBeenCalled();
    expect(onOpenStock).toHaveBeenCalledWith(TICKERS[3]);
  });

  it("finds actions by keyword and runs them", async () => {
    const { commands } = setup();
    await screen.findByRole("option", { name: /AAPL/ });

    await userEvent.type(screen.getByRole("combobox"), "deposit");
    expect(screen.getAllByRole("option")).toHaveLength(1);
    await userEvent.keyboard("{Enter}");

    expect(commands[1].run).toHaveBeenCalledTimes(1);
  });

  it("moves through results with the arrow keys and wraps around", async () => {
    setup();
    await screen.findByRole("option", { name: /AAPL/ });
    const options = () => screen.getAllByRole("option");

    await userEvent.keyboard("{ArrowDown}");
    expect(options()[1]).toHaveAttribute("aria-selected", "true");
    await userEvent.keyboard("{ArrowUp}{ArrowUp}");
    expect(options()[options().length - 1]).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("combobox")).toHaveAttribute("aria-activedescendant", options()[options().length - 1].id);
  });

  it("says when nothing matches", async () => {
    setup();
    await screen.findByRole("option", { name: /AAPL/ });
    await userEvent.type(screen.getByRole("combobox"), "qqqq");
    expect(screen.queryAllByRole("option")).toHaveLength(0);
    expect(screen.getByText(/Nothing matches/)).toBeInTheDocument();
  });
});

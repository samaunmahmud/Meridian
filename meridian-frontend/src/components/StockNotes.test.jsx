import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import StockNotes from "./StockNotes";
import { getWatchlist, saveWatchlistNote } from "../lib/api";
import { showToast } from "../lib/toast";
import { axeViolations } from "../test/axe";

vi.mock("../lib/api", () => ({ getWatchlist: vi.fn(), saveWatchlistNote: vi.fn() }));
vi.mock("../lib/toast", () => ({ showToast: vi.fn() }));

const AAPL = { symbol: "AAPL", name: "Apple Inc." };

beforeEach(() => vi.clearAllMocks());

describe("StockNotes", () => {
  it("shows your note and how far the price is from your target", async () => {
    getWatchlist.mockResolvedValue([{ symbol: "AAPL", currentPrice: 231.4, note: "Buy the dip after earnings.", targetPrice: 220 }]);
    const { container } = render(<StockNotes ticker={AAPL} />);

    expect(await screen.findByText("Buy the dip after earnings.")).toBeInTheDocument();
    expect(screen.getByText("$220.00")).toBeInTheDocument();
    expect(screen.getByText("5.2% above your target")).toBeInTheDocument();
    expect(await axeViolations(container)).toEqual([]);
  });

  it("follows live prices, and says when the target is reached", async () => {
    getWatchlist.mockResolvedValue([{ symbol: "AAPL", currentPrice: 231.4, note: null, targetPrice: 220 }]);
    const { rerender } = render(<StockNotes ticker={AAPL} />);
    await screen.findByText("5.2% above your target");

    rerender(<StockNotes ticker={AAPL} liveUpdate={{ kind: "PRICE_UPDATE", symbol: "AAPL", price: 219.5 }} />);
    expect(await screen.findByText("At or below your target")).toBeInTheDocument();
  });

  it("adds a note and a target price", async () => {
    getWatchlist.mockResolvedValue([]);
    saveWatchlistNote.mockResolvedValue({ symbol: "AAPL", currentPrice: 231.4, note: "Strong services growth", targetPrice: 210 });
    render(<StockNotes ticker={AAPL} />);

    await userEvent.click(await screen.findByRole("button", { name: "Add a note" }));
    await userEvent.type(screen.getByLabelText("Why you're watching AAPL"), "  Strong services growth ");
    await userEvent.type(screen.getByLabelText("Price you'd like to buy at (optional)"), "210");
    expect(screen.getByText("25 / 500")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(saveWatchlistNote).toHaveBeenCalledWith("AAPL", "Strong services growth", 210);
    expect(await screen.findByText("Strong services growth")).toBeInTheDocument();
    expect(showToast).toHaveBeenCalledWith("success", "Note saved");
  });

  it("clears both, and shows the server's refusal", async () => {
    getWatchlist.mockResolvedValue([{ symbol: "AAPL", currentPrice: 231.4, note: "Old", targetPrice: 200 }]);
    saveWatchlistNote.mockRejectedValueOnce(new Error("The target price must be above 0, with at most 4 decimals"));
    render(<StockNotes ticker={AAPL} />);
    await userEvent.click(await screen.findByRole("button", { name: "Edit" }));

    await userEvent.clear(screen.getByLabelText("Why you're watching AAPL"));
    await userEvent.clear(screen.getByLabelText("Price you'd like to buy at (optional)"));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(saveWatchlistNote).toHaveBeenCalledWith("AAPL", null, null);
    expect(showToast).toHaveBeenCalledWith("error", "The target price must be above 0, with at most 4 decimals");
    expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
  });
});

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import StockNotes from "./StockNotes";
import { createAlert, deleteAlert, getAlerts, getWatchlist, saveWatchlistNote } from "../lib/api";
import { showToast } from "../lib/toast";
import { axeViolations } from "../test/axe";

vi.mock("../lib/api", () => ({
  getWatchlist: vi.fn(),
  saveWatchlistNote: vi.fn(),
  getAlerts: vi.fn(),
  createAlert: vi.fn(),
  deleteAlert: vi.fn(),
}));
vi.mock("../lib/toast", () => ({ showToast: vi.fn() }));

const AAPL = { symbol: "AAPL", name: "Apple Inc." };

beforeEach(() => {
  vi.clearAllMocks();
  getAlerts.mockResolvedValue([]);
  createAlert.mockImplementation((symbol, direction, targetPrice) =>
    Promise.resolve({ id: 99, symbol, direction, targetPrice, triggered: false, movePercent: null })
  );
  deleteAlert.mockResolvedValue(null);
});

const ALERT_200 = { id: 7, symbol: "AAPL", direction: "BELOW", targetPrice: 200, triggered: false, movePercent: null };

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
    expect(createAlert).toHaveBeenCalledWith("AAPL", "BELOW", 210);
    expect(showToast).toHaveBeenCalledWith("success", "Note saved. We'll tell you when AAPL falls to $210.00");
    expect(screen.getByText("We'll tell you when it falls to your target.")).toBeInTheDocument();
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

  it("sets no alert when the box is unticked, or when the price is already at the target", async () => {
    getWatchlist.mockResolvedValue([]);
    saveWatchlistNote.mockResolvedValue({ symbol: "AAPL", currentPrice: 231.4, note: null, targetPrice: 210 });
    render(<StockNotes ticker={AAPL} />);
    await userEvent.click(await screen.findByRole("button", { name: "Add a note" }));
    await userEvent.type(screen.getByLabelText("Price you'd like to buy at (optional)"), "210");
    await userEvent.click(screen.getByRole("checkbox", { name: "Tell me when it gets there" }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(showToast).toHaveBeenCalledWith("success", "Note saved");
    expect(createAlert).not.toHaveBeenCalled();
  });

  it("does not set an alert for a target the price has already reached", async () => {
    getWatchlist.mockResolvedValue([{ symbol: "AAPL", currentPrice: 231.4, note: null, targetPrice: null }]);
    saveWatchlistNote.mockResolvedValue({ symbol: "AAPL", currentPrice: 231.4, note: null, targetPrice: 240 });
    render(<StockNotes ticker={AAPL} />);
    await userEvent.click(await screen.findByRole("button", { name: "Add a note" }));
    await userEvent.type(screen.getByLabelText("Price you'd like to buy at (optional)"), "240");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByText("At or below your target")).toBeInTheDocument();
    expect(createAlert).not.toHaveBeenCalled();
  });

  it("moves the alert when the target changes and removes it when the target is cleared", async () => {
    getWatchlist.mockResolvedValue([{ symbol: "AAPL", currentPrice: 231.4, note: null, targetPrice: 200 }]);
    getAlerts.mockResolvedValue([ALERT_200]);
    saveWatchlistNote
      .mockResolvedValueOnce({ symbol: "AAPL", currentPrice: 231.4, note: null, targetPrice: 210 })
      .mockResolvedValueOnce({ symbol: "AAPL", currentPrice: 231.4, note: null, targetPrice: null });
    render(<StockNotes ticker={AAPL} />);
    expect(await screen.findByText("We'll tell you when it falls to your target.")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    expect(screen.getByRole("checkbox", { name: "Tell me when it gets there" })).toBeChecked();
    const target = screen.getByLabelText("Price you'd like to buy at (optional)");
    await userEvent.clear(target);
    await userEvent.type(target, "210");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    await screen.findByText("Target");
    expect(deleteAlert).toHaveBeenCalledWith(7);
    expect(createAlert).toHaveBeenCalledWith("AAPL", "BELOW", 210);

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.clear(screen.getByLabelText("Price you'd like to buy at (optional)"));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    await screen.findByRole("button", { name: "Add a note" });
    expect(deleteAlert).toHaveBeenLastCalledWith(99);
    expect(createAlert).toHaveBeenCalledTimes(1);
  });

  it("offers an alert for a saved target that has none", async () => {
    getWatchlist.mockResolvedValue([{ symbol: "AAPL", currentPrice: 231.4, note: null, targetPrice: 220 }]);
    render(<StockNotes ticker={AAPL} />);

    await userEvent.click(await screen.findByRole("button", { name: "Tell me when it gets there" }));

    expect(createAlert).toHaveBeenCalledWith("AAPL", "BELOW", 220);
    expect(showToast).toHaveBeenCalledWith("success", "We'll tell you when AAPL falls to $220.00");
    expect(await screen.findByText("We'll tell you when it falls to your target.")).toBeInTheDocument();
  });
});

import { render, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import HoldingsList from "./HoldingsList";

const holdings = [
  { symbol: "NVDA", name: "NVIDIA Corporation", quantity: 12.5, avgCost: 118.4, currentPrice: 131.22, marketValue: 1640.25, gainLoss: 160.25, gainLossPct: 10.82 },
  { symbol: "BTC", name: "Bitcoin", quantity: 0.0523, avgCost: 64210.5, currentPrice: 61180.75, marketValue: 3199.61, gainLoss: -158.46, gainLossPct: -4.72 },
];

// Both views are always in the page; CSS shows the cards on phones (< 640px)
// and the table from there up.
describe("HoldingsList", () => {
  it("renders one card per position for phones", () => {
    const { container } = render(<HoldingsList holdings={holdings} />);
    const cards = within(container.querySelector("ul")).getAllByRole("listitem");

    expect(cards).toHaveLength(2);
    expect(within(cards[0]).getByText("NVDA")).toBeInTheDocument();
    expect(within(cards[0]).getByText("NVIDIA Corporation")).toBeInTheDocument();
    expect(within(cards[0]).getByText("1,640.25")).toBeInTheDocument(); // market value
    expect(within(cards[0]).getByText("12.5")).toBeInTheDocument(); // quantity
    expect(within(cards[0]).getByText("118.40")).toBeInTheDocument(); // average cost
    expect(within(cards[0]).getByText("131.22")).toBeInTheDocument(); // price
  });

  it("renders the full table for larger screens", () => {
    const { container } = render(<HoldingsList holdings={holdings} />);
    const table = within(container.querySelector("table"));

    ["Asset", "Qty", "Avg cost", "Price", "Value"].forEach((h) => expect(table.getByText(h)).toBeInTheDocument());
    expect(table.getAllByRole("row")).toHaveLength(3); // header + 2 positions
    expect(table.getByText("61,180.75")).toBeInTheDocument();
  });

  it("shows gains in the gain colour with a plus sign, and losses in the loss colour", () => {
    const { container } = render(<HoldingsList holdings={holdings} />);
    const [gainCard, lossCard] = within(container.querySelector("ul")).getAllByRole("listitem");

    const gain = within(gainCard).getByText("+160.25");
    expect(gain.closest("div.text-gain")).not.toBeNull();
    expect(within(gainCard).getByText("+10.82%")).toBeInTheDocument();

    const loss = within(lossCard).getByText("-158.46");
    expect(loss.closest("div.text-loss")).not.toBeNull();
    expect(within(lossCard).getByText("-4.72%")).toBeInTheDocument();
  });
});

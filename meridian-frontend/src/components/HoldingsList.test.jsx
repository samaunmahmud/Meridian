import { render, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import HoldingsList from "./HoldingsList";

const holdings = [
  { symbol: "NVDA", name: "NVIDIA Corporation", quantity: 12.5, avgCost: 118.4, currentPrice: 131.22, marketValue: 1640.25, gainLoss: 160.25, gainLossPct: 10.82 },
  { symbol: "BTC", name: "Bitcoin", quantity: 0.0523, avgCost: 64210.5, currentPrice: 61180.75, marketValue: 3199.61, gainLoss: -158.46, gainLossPct: -4.72 },
];

describe("HoldingsList", () => {
  it("renders one row per position with its value, size, average cost and price", () => {
    const { container } = render(<HoldingsList holdings={holdings} />);
    const cards = within(container.querySelector("ul")).getAllByRole("listitem");

    expect(cards).toHaveLength(2);
    expect(within(cards[0]).getByText("NVDA")).toBeInTheDocument();
    expect(within(cards[0]).getByText("NVIDIA Corporation")).toBeInTheDocument();
    expect(within(cards[0]).getByText("$1,640.25")).toBeInTheDocument(); // market value
    expect(within(cards[0]).getByText("12.5")).toBeInTheDocument(); // quantity
    expect(within(cards[0]).getByText("118.40")).toBeInTheDocument(); // average cost
    expect(within(cards[0]).getByText("131.22")).toBeInTheDocument(); // price
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

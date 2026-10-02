import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import CompanyNews from "./CompanyNews";
import { getNews } from "../lib/api";
import { axeViolations } from "../test/axe";

vi.mock("../lib/api", () => ({ getNews: vi.fn() }));

const AAPL = { symbol: "AAPL", name: "Apple Inc." };

function article(i, extra = {}) {
  return {
    headline: `Story ${i}`,
    summary: `Summary ${i}`,
    source: "Example Wire",
    url: `https://news.example/${i}`,
    imageUrl: null,
    publishedAt: new Date(Date.now() - (i + 2) * 3600_000).toISOString(),
    sentiment: null,
    ...extra,
  };
}

beforeEach(() => vi.clearAllMocks());

describe("CompanyNews", () => {
  it("lists articles as links that open in a new tab, with source, age and sentiment", async () => {
    getNews.mockResolvedValue([
      article(0, { sentiment: "Bullish", imageUrl: "https://img.example/a.jpg" }),
      article(1, { sentiment: "Bearish", summary: null, source: null }),
    ]);
    const { container } = render(<CompanyNews ticker={AAPL} />);

    const link = await screen.findByRole("link", { name: /Story 0/ });
    expect(getNews).toHaveBeenCalledWith("AAPL");
    expect(link).toHaveAttribute("href", "https://news.example/0");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
    expect(link).toHaveAccessibleName(/opens in a new tab/);
    expect(screen.getByText("Example Wire · 2 h ago")).toBeInTheDocument();
    expect(screen.getByText("Bullish")).toBeInTheDocument();
    expect(screen.getByText("Bearish")).toBeInTheDocument();
    expect(screen.getByText("Summary 0")).toBeInTheDocument();
    expect(screen.getByText("3 h ago")).toBeInTheDocument();
    expect(container.querySelector("img")).toHaveAttribute("src", "https://img.example/a.jpg");
    expect(await axeViolations(container)).toEqual([]);
  });

  it("shows five at first and the rest on request", async () => {
    getNews.mockResolvedValue([0, 1, 2, 3, 4, 5, 6].map((i) => article(i)));
    render(<CompanyNews ticker={AAPL} />);

    await screen.findByText("Story 0");
    expect(screen.getAllByRole("link")).toHaveLength(5);
    await userEvent.click(screen.getByRole("button", { name: "Show 2 more" }));
    expect(screen.getAllByRole("link")).toHaveLength(7);
    expect(screen.getByRole("button", { name: "Show less" })).toHaveAttribute("aria-expanded", "true");
  });

  it("says so when there is no news, or when it can't be loaded", async () => {
    getNews.mockResolvedValueOnce([]);
    const { unmount } = render(<CompanyNews ticker={AAPL} />);
    expect(await screen.findByText("No recent news about AAPL.")).toBeInTheDocument();
    unmount();

    getNews.mockRejectedValueOnce(Object.assign(new Error("paused"), { status: 503 }));
    render(<CompanyNews ticker={AAPL} />);
    expect(await screen.findByText("News isn't available right now. Try again later.")).toBeInTheDocument();
  });

  it("loads the new stock's news when the stock changes", async () => {
    getNews.mockImplementation((symbol) => Promise.resolve([article(0, { headline: `${symbol} story` })]));
    const { rerender } = render(<CompanyNews ticker={AAPL} />);
    await screen.findByText("AAPL story");

    rerender(<CompanyNews ticker={{ symbol: "MSFT", name: "Microsoft" }} />);
    expect(await screen.findByText("MSFT story")).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText("AAPL story")).not.toBeInTheDocument());
  });
});

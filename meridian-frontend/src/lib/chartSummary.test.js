import { describe, expect, it } from "vitest";
import { summarizeSeries } from "./chartSummary";

describe("summarizeSeries", () => {
  it("describes a rising series", () => {
    expect(summarizeSeries("AAPL price", [100, 90, 125])).toBe(
      "AAPL price: up 25.00% from $100.00 to $125.00; low $90.00, high $125.00; 3 data points"
    );
  });

  it("describes a falling series and a flat one", () => {
    expect(summarizeSeries("Portfolio value", [200, 150])).toContain("down 25.00% from $200.00 to $150.00");
    expect(summarizeSeries("X", [5, 6, 5])).toContain("unchanged at $5.00");
  });

  it("says so when there is nothing to draw", () => {
    expect(summarizeSeries("X", [1])).toBe("X: not enough data yet");
    expect(summarizeSeries("X", [])).toBe("X: not enough data yet");
  });
});

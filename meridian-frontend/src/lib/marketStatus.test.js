import { describe, expect, it } from "vitest";
import { describeMarket, formatWhen } from "./marketStatus";

// The clock and the time zone are the viewer's, so these check the wording, not exact hours.
const now = new Date(2026, 8, 19, 12, 0); // Saturday, local time
const at = (dayOffset, hour, minute = 0) => new Date(2026, 8, 19 + dayOffset, hour, minute).toISOString();

describe("formatWhen", () => {
  it("says today, tomorrow or the weekday", () => {
    expect(formatWhen(at(0, 18), now)).toMatch(/^today \d{1,2}:\d{2}/);
    expect(formatWhen(at(1, 9, 30), now)).toMatch(/^tomorrow \d{1,2}:\d{2}/);
    expect(formatWhen(at(2, 9, 30), now)).toMatch(/^Mon \d{1,2}:\d{2}/);
  });

  it("includes a time zone so nobody has to guess whose 9:30 it is", () => {
    expect(formatWhen(at(2, 9, 30), now)).toMatch(/[A-Z]{2,5}|GMT/);
  });
});

describe("describeMarket", () => {
  it("describes an open stock market and when it closes", () => {
    const { open, label } = describeMarket({ open: true, nextClose: at(0, 16) }, "STOCK", now);
    expect(open).toBe(true);
    expect(label).toMatch(/^Market open · closes today/);
  });

  it("describes a closed stock market and when it opens", () => {
    const { open, label } = describeMarket({ open: false, nextOpen: at(2, 9, 30) }, "STOCK", now);
    expect(open).toBe(false);
    expect(label).toMatch(/^Market closed · opens Mon/);
  });

  it("says crypto never closes, whatever the stock market is doing", () => {
    expect(describeMarket({ open: false, nextOpen: at(2, 9) }, "CRYPTO", now)).toEqual({ open: true, label: "Crypto trades 24/7" });
  });

  it("says nothing while the status is not known yet", () => {
    expect(describeMarket(null, "STOCK", now)).toEqual({ open: null, label: "" });
  });
});

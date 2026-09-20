import { describe, expect, it } from "vitest";
import { formatAge, isDelayed } from "./priceAge";

const now = Date.parse("2026-09-20T12:00:00Z");
const ago = (ms) => new Date(now - ms).toISOString();
const MIN = 60 * 1000;

describe("price age", () => {
  it("is not delayed for a normal live price", () => {
    expect(isDelayed(ago(30 * 1000), now)).toBe(false);
    expect(isDelayed(ago(14 * MIN), now)).toBe(false);
  });

  it("is delayed once the newest price is more than 15 minutes old", () => {
    expect(isDelayed(ago(16 * MIN), now)).toBe(true);
    expect(isDelayed(ago(6 * 60 * MIN), now)).toBe(true);
  });

  it("says how old in words a person can read", () => {
    expect(formatAge(ago(10 * 1000), now)).toBe("just now");
    expect(formatAge(ago(25 * MIN), now)).toBe("25 min ago");
    expect(formatAge(ago(3 * 60 * MIN), now)).toBe("3 h ago");
    expect(formatAge(ago(3 * 24 * 60 * MIN), now)).toBe("3 days ago");
  });

  it("never shows a negative age when the browser clock is slightly behind the server", () => {
    expect(formatAge(ago(-5 * 1000), now)).toBe("just now");
  });
});

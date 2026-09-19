import { describe, expect, it } from "vitest";
import { currencySymbol, formatMoney, formatNumber } from "./formatMoney";

describe("formatMoney", () => {
  it("formats in each supported currency", () => {
    expect(formatMoney(1234.5, "USD")).toBe("$1,234.50");
    expect(formatMoney(1234.5, "EUR")).toBe("€1,234.50");
    expect(formatMoney(1234.5, "GBP")).toBe("£1,234.50");
  });

  it("defaults to USD and shows a dash for missing values", () => {
    expect(formatMoney(5)).toBe("$5.00");
    expect(formatMoney(null)).toBe("—");
    expect(formatMoney(undefined)).toBe("—");
    expect(formatMoney(NaN)).toBe("—");
  });

  it("falls back to a plain number for an unknown currency code", () => {
    expect(formatMoney(5, "not-a-currency")).toBe("5.00 not-a-currency");
  });
});

describe("formatNumber and currencySymbol", () => {
  it("groups thousands and fixes the decimals", () => {
    expect(formatNumber(1234567.891)).toBe("1,234,567.89");
    expect(formatNumber(2, 4)).toBe("2.0000");
    expect(formatNumber(null)).toBe("—");
  });

  it("knows the symbols and falls back to the code", () => {
    expect(currencySymbol("EUR")).toBe("€");
    expect(currencySymbol("GBP")).toBe("£");
    expect(currencySymbol("CHF")).toBe("CHF ");
  });
});

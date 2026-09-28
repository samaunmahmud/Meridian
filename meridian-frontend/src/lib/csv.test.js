import { describe, expect, it } from "vitest";
import { toCsv } from "./csv";

const COLUMNS = [
  ["Symbol", (r) => r.symbol],
  ["Note", (r) => r.note],
  ["Amount", (r) => r.amount],
];

describe("toCsv", () => {
  it("writes a header row and one line per row", () => {
    expect(toCsv([{ symbol: "AAPL", note: "Bought", amount: 12.5 }], COLUMNS)).toBe(
      "Symbol,Note,Amount\r\nAAPL,Bought,12.5\r\n"
    );
  });

  it("quotes commas, quotes and line breaks, and leaves empty values blank", () => {
    expect(toCsv([{ symbol: "A,B", note: 'say "hi"\nthere', amount: null }], COLUMNS)).toBe(
      'Symbol,Note,Amount\r\n"A,B","say ""hi""\nthere",\r\n'
    );
  });

  it("stops text from being read as a spreadsheet formula but keeps negative numbers", () => {
    const csv = toCsv([{ symbol: "=HYPERLINK(1)", note: "@x", amount: -3.2 }], COLUMNS);
    expect(csv.split("\r\n")[1]).toBe("'=HYPERLINK(1),'@x,-3.2");
  });
});

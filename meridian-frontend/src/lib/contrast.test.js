import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

// `npm test` runs from the meridian-frontend folder.
const css = readFileSync(resolve(process.cwd(), "src/index.css"), "utf8");

// WCAG 2.1 AA: text needs 4.5:1, the border of a form control needs 3:1 (1.4.11).
// This reads the real theme tokens in index.css, so changing a colour there cannot
// quietly bring low-contrast text back.

function block(selector) {
  const start = css.indexOf(selector);
  expect(start, `${selector} block not found`).toBeGreaterThan(-1);
  const body = css.slice(css.indexOf("{", start) + 1, css.indexOf("}", start));
  return Object.fromEntries([...body.matchAll(/--c-([\w-]+):\s*(#[0-9a-fA-F]{6})/g)].map((m) => [m[1], m[2]]));
}

function luminance(hex) {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function ratio(a, b) {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const THEMES = {
  dark: block(':root,\n:root[data-theme="dark"]'),
  light: block(':root[data-theme="light"]'),
};
const SURFACES = ["bg", "surface", "surface-2", "accent-soft", "gain-soft", "loss-soft"];
const TEXT = ["text", "muted", "dim", "accent", "gain", "loss"];

describe.each(Object.entries(THEMES))("%s theme colours", (_name, t) => {
  it("reads every token it checks", () => {
    for (const k of [...SURFACES, ...TEXT, "accent-ink", "on-loss", "control"]) expect(t[k], `--c-${k}`).toBeDefined();
  });

  for (const fg of TEXT) {
    it(`text colour ${fg} is at least 4.5:1 on every surface`, () => {
      for (const bg of SURFACES) {
        expect(ratio(t[fg], t[bg]), `--c-${fg} on --c-${bg}`).toBeGreaterThanOrEqual(4.5);
      }
    });
  }

  it("button text is at least 4.5:1 on its button", () => {
    expect(ratio(t["accent-ink"], t.accent)).toBeGreaterThanOrEqual(4.5);
    expect(ratio(t["on-loss"], t.loss)).toBeGreaterThanOrEqual(4.5);
  });

  it("form control borders are at least 3:1 against the surface and the field fill", () => {
    expect(ratio(t.control, t.surface)).toBeGreaterThanOrEqual(3);
    expect(ratio(t.control, t["surface-2"])).toBeGreaterThanOrEqual(3);
  });
});

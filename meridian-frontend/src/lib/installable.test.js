import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

// `npm test` runs from the meridian-frontend folder.
const root = process.cwd();
const manifest = JSON.parse(readFileSync(resolve(root, "public/manifest.webmanifest"), "utf8"));
const html = readFileSync(resolve(root, "index.html"), "utf8");

// PNG width/height sit in the IHDR chunk, bytes 16-23.
function pngSize(file) {
  const buf = readFileSync(resolve(root, "public", file));
  expect(buf.subarray(1, 4).toString(), `${file} is a PNG`).toBe("PNG");
  return [buf.readUInt32BE(16), buf.readUInt32BE(20)];
}

describe("the app can be installed on a phone", () => {
  it("has a manifest a browser accepts as installable", () => {
    expect(manifest.name).toBe("Meridian");
    expect(manifest.start_url).toBe("/");
    expect(manifest.display).toBe("standalone");
    expect(manifest.background_color).toMatch(/^#[0-9a-f]{6}$/i);
  });

  it("declares 192 and 512 pixel icons, and each file really has that size", () => {
    const declared = manifest.icons.filter((i) => i.purpose === "any").map((i) => i.sizes);
    expect(declared).toEqual(expect.arrayContaining(["192x192", "512x512"]));
    for (const icon of manifest.icons) {
      const [w, h] = icon.sizes.split("x").map(Number);
      expect(pngSize(icon.src.replace(/^\//, "")), icon.src).toEqual([w, h]);
    }
  });

  it("has a maskable icon so Android does not crop the globe", () => {
    expect(manifest.icons.some((i) => i.purpose === "maskable")).toBe(true);
  });

  it("is linked from the page, with an iPhone icon and theme colours for both themes", () => {
    expect(html).toContain('rel="manifest" href="/manifest.webmanifest"');
    expect(html).toContain('rel="apple-touch-icon" href="/apple-touch-icon.png"');
    expect(pngSize("apple-touch-icon.png")).toEqual([180, 180]);
    expect(html).toMatch(/theme-color[^>]*prefers-color-scheme: dark/);
    expect(html).toMatch(/theme-color[^>]*prefers-color-scheme: light/);
  });
});

import axe from "axe-core";

// Runs axe-core on a rendered DOM and returns the violations as readable text.
// Colour contrast is left out on purpose: jsdom has no layout, so axe cannot measure it
// (lib/contrast.test.js checks the theme colours instead). `region` is off for single
// components, which are rendered outside the page's landmarks.
export async function axeViolations(container, { page = false } = {}) {
  const results = await axe.run(container, {
    rules: { "color-contrast": { enabled: false }, region: { enabled: page } },
  });
  return results.violations.map((v) => `${v.id}: ${v.help} -> ${v.nodes.map((n) => n.html.slice(0, 80)).join(" | ")}`);
}

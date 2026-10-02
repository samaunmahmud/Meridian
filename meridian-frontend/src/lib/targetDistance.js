// How far a price is from the price you'd like to buy at, in words: `text` for where there is room,
// `short` for a list row. Null when either is unknown.
export function targetDistance(price, target) {
  if (price == null || !target) return null;
  const pct = ((price - target) / target) * 100;
  if (pct <= 0) return { reached: true, text: "At or below your target", short: "Target reached" };
  const shown = pct.toFixed(pct < 10 ? 1 : 0);
  return { reached: false, text: `${shown}% above your target`, short: `${shown}% to target` };
}

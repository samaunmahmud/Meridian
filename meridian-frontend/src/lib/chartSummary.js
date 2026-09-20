import { formatNumber } from "./formatMoney";

// A chart is a picture, so it needs a text alternative. This turns a series of values into
// one sentence with the same facts a sighted user reads off the line: direction, start and
// end, low and high, and how many points there are.
export function summarizeSeries(label, values) {
  const nums = values.filter((v) => typeof v === "number" && !Number.isNaN(v));
  if (nums.length < 2) return `${label}: not enough data yet`;
  const first = nums[0];
  const last = nums[nums.length - 1];
  const pct = first ? ((last - first) / first) * 100 : 0;
  const trend =
    last === first
      ? `unchanged at $${formatNumber(last)}`
      : `${last > first ? "up" : "down"} ${Math.abs(pct).toFixed(2)}% from $${formatNumber(first)} to $${formatNumber(last)}`;
  return `${label}: ${trend}; low $${formatNumber(Math.min(...nums))}, high $${formatNumber(Math.max(...nums))}; ${nums.length} data points`;
}

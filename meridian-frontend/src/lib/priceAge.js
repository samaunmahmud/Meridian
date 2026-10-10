// Prices come from a feed that can fall behind (provider outage, rate limit). Anything older
// than this is shown as "delayed" so nobody mistakes an old number for the current price.
// (The server decides what is too old to TRADE on; this is only what the page says.)
export const DELAYED_AFTER_MS = 15 * 60 * 1000;
// While the market is closed no new prices come, so the last one is the closing price, not a delayed
// one, until it is older than the longest closure (a long weekend: Friday's close to Tuesday's open).
export const CLOSED_DELAYED_AFTER_MS = 4 * 24 * 60 * 60 * 1000;

export function isDelayed(recordedAt, now = Date.now(), marketOpen = true) {
  const limit = marketOpen ? DELAYED_AFTER_MS : CLOSED_DELAYED_AFTER_MS;
  return now - new Date(recordedAt).getTime() > limit;
}

export function formatAge(recordedAt, now = Date.now()) {
  const minutes = Math.max(0, Math.round((now - new Date(recordedAt).getTime()) / 60000));
  if (minutes < 1) return "just now";
  if (minutes < 90) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 48) return `${hours} h ago`;
  return `${Math.round(hours / 24)} days ago`;
}

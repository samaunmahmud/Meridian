// Turns the server's market status into words. Times are shown in the viewer's own time zone
// ("opens Mon 2:30 PM EDT"), because that is what a person planning their day needs.

const timeFormat = { hour: "numeric", minute: "2-digit", timeZoneName: "short" };

function sameDay(a, b) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

/** "today 2:30 PM EDT", "tomorrow 2:30 PM EDT" or "Mon 2:30 PM EDT". */
export function formatWhen(iso, now = new Date()) {
  const when = new Date(iso);
  const time = when.toLocaleTimeString([], timeFormat);
  const tomorrow = new Date(now);
  tomorrow.setDate(now.getDate() + 1);
  if (sameDay(when, now)) return `today ${time}`;
  if (sameDay(when, tomorrow)) return `tomorrow ${time}`;
  return `${when.toLocaleDateString([], { weekday: "short" })} ${time}`;
}

/** { open, label } for one market (`status` is stocks or crypto from GET /market/status). */
export function describeMarket(status, assetType, now = new Date()) {
  if (assetType === "CRYPTO") return { open: true, label: "Crypto trades 24/7" };
  if (!status) return { open: null, label: "" };
  if (status.open) {
    return { open: true, label: status.nextClose ? `Market open · closes ${formatWhen(status.nextClose, now)}` : "Market open" };
  }
  return { open: false, label: `Market closed · opens ${formatWhen(status.nextOpen, now)}` };
}

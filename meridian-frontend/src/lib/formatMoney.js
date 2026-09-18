const SYMBOLS = { USD: "$", EUR: "€", GBP: "£" };

// Used only where a non-USD amount can actually appear (wallets, convert,
// activity feed) — stock/crypto trading stays USD-only, so those screens
// keep their existing plain "$" formatting.
export function formatMoney(amount, currency = "USD") {
  if (amount == null || Number.isNaN(amount)) return "—";
  try {
    return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency}`;
  }
}

export function currencySymbol(currency = "USD") {
  return SYMBOLS[currency] ?? `${currency} `;
}

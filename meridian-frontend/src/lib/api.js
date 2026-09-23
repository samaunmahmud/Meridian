import { API_BASE as BASE_URL } from "./config";

// Sessions live in an HttpOnly cookie that the server sets on login. Page
// scripts can't read it (so an XSS bug can't steal it), and this file never
// touches a token: every request just asks the browser to include cookies.
// Older versions kept a token in localStorage — clear any that's left over.
try {
  localStorage.removeItem("meridian_token");
  localStorage.removeItem("meridian_email");
} catch {
  // storage unavailable (private mode etc.) — nothing to clean up
}

// Errors carry the HTTP status as `status`; 0 means the server could not be reached at all.
function apiError(message, status) {
  const err = new Error(message);
  err.status = status;
  return err;
}

/** True when an error means the server is down or unreachable, not that the request was refused. */
export function isServerUnavailable(err) {
  return err?.status === 0 || err?.status >= 500;
}

async function apiFetch(path, options = {}) {
  let res;
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      ...options,
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        // Custom header the server requires on cookie-authenticated requests
        // that change data: another website can't add it, so it can't forge them.
        "X-Requested-With": "meridian",
        ...options.headers,
      },
    });
  } catch {
    throw apiError("Cannot reach Meridian. Check your connection and try again.", 0);
  }

  // A 401 on a normal call means the session ended. On the auth calls it just
  // means "wrong password" / "not signed in", which the caller handles itself.
  if (res.status === 401 && !path.startsWith("/auth/")) {
    window.dispatchEvent(new Event("meridian:session-expired"));
    throw apiError("Your session has expired. Please log in again.", 401);
  }

  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw apiError(body?.message || "Request failed", res.status);
  }

  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

/** Who is signed in? Rejects if nobody is. Used once on page load. */
export function getMe() {
  return apiFetch("/auth/me");
}

export function logout() {
  return apiFetch("/auth/logout", { method: "POST" });
}

export function register(email, password) {
  return apiFetch("/auth/register", { method: "POST", body: JSON.stringify({ email, password }) });
}

export function login(email, password) {
  return apiFetch("/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
}

export function forgotPassword(email) {
  return apiFetch("/auth/forgot-password", { method: "POST", body: JSON.stringify({ email }) });
}

export function resetPassword(token, password) {
  return apiFetch("/auth/reset-password", { method: "POST", body: JSON.stringify({ token, password }) });
}

export function verifyEmail(token) {
  return apiFetch("/auth/verify-email", { method: "POST", body: JSON.stringify({ token }) });
}

export function resendVerification() {
  return apiFetch("/auth/resend-verification", { method: "POST" });
}

/** Whether stocks and crypto can be traded right now: { stocks: {open, nextOpen, nextClose}, crypto: {...} }. */
export function getMarketStatus() {
  return apiFetch("/market/status");
}

/**
 * Biggest moves today: { gainers: [...], losers: [...] }, each row { symbol, name, exchange, assetType, price,
 * referencePrice, change, changePercent, recordedAt, referenceAt }. Stocks move from the previous close,
 * crypto over 24 hours.
 */
export function getMovers(limit = 5) {
  return apiFetch(`/market/movers?limit=${limit}`);
}

export function getTickers() {
  return apiFetch("/tickers");
}

export function searchTickers(query) {
  return apiFetch(`/tickers/search?q=${encodeURIComponent(query)}`);
}

export function addTicker(symbol, name, exchange) {
  return apiFetch("/tickers", { method: "POST", body: JSON.stringify({ symbol, name, exchange }) });
}

/**
 * Prices for a stock, newest first. `range` is "1D" | "1W" | "1M" | "3M" | "1Y" | "ALL", `points` caps how many
 * come back (evenly thinned), `limit` asks for just the newest N.
 */
export function getPrices(symbol, { range, points, limit } = {}) {
  const query = new URLSearchParams();
  if (range) query.set("range", range);
  if (points) query.set("points", points);
  if (limit) query.set("limit", limit);
  const qs = query.toString();
  return apiFetch(`/prices/${encodeURIComponent(symbol)}${qs ? `?${qs}` : ""}`);
}

export function getPortfolio() {
  return apiFetch("/portfolio");
}

export function getPortfolioHistory() {
  return apiFetch("/portfolio/history");
}

export function getOrders() {
  return apiFetch("/orders");
}

// settlementCurrency: which wallet a MARKET order pays from / is paid into
// (null = USD). Limit and stop-loss orders always settle in USD.
export function placeOrder(symbol, type, quantity, kind = "MARKET", limitPrice = null, stopPrice = null, settlementCurrency = null) {
  return apiFetch("/orders", {
    method: "POST",
    body: JSON.stringify({ symbol, type, kind, quantity, limitPrice, stopPrice, settlementCurrency }),
  });
}

export function cancelOrder(id) {
  return apiFetch(`/orders/${id}`, { method: "DELETE" });
}

export function getTransactions() {
  return apiFetch("/portfolio/transactions");
}

export function getWallets() {
  return apiFetch("/wallets");
}

export function convertCurrency(fromCurrency, toCurrency, amount) {
  return apiFetch("/wallets/convert", {
    method: "POST",
    body: JSON.stringify({ fromCurrency, toCurrency, amount }),
  });
}

export function depositToWallet(currency, amount) {
  return apiFetch("/wallets/deposit", { method: "POST", body: JSON.stringify({ currency, amount }) });
}

export function withdrawFromWallet(currency, amount) {
  return apiFetch("/wallets/withdraw", { method: "POST", body: JSON.stringify({ currency, amount }) });
}

export function getFxRates() {
  return apiFetch("/fx-rates");
}

export function getRecurringOrders() {
  return apiFetch("/recurring-orders");
}

export function createRecurringOrder(symbol, amount, frequency, settlementCurrency = null) {
  return apiFetch("/recurring-orders", {
    method: "POST",
    body: JSON.stringify({ symbol, amount, frequency, settlementCurrency }),
  });
}

export function deleteRecurringOrder(id) {
  return apiFetch(`/recurring-orders/${id}`, { method: "DELETE" });
}

export function getAlerts() {
  return apiFetch("/alerts");
}

export function createAlert(symbol, direction, targetPrice) {
  return apiFetch("/alerts", { method: "POST", body: JSON.stringify({ symbol, direction, targetPrice }) });
}

export function deleteAlert(id) {
  return apiFetch(`/alerts/${id}`, { method: "DELETE" });
}

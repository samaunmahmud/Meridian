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

/** Needs the current password. Other sessions end; this one gets a new cookie. */
export function changePassword(currentPassword, newPassword) {
  return apiFetch("/auth/change-password", {
    method: "POST",
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}

/** Deletes the account and everything in it, then signs out. */
export function deleteAccount(password) {
  return apiFetch("/auth/account", { method: "DELETE", body: JSON.stringify({ password }) });
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

/** Today's move for every ticker that has one (same rows and measure as getMovers), in no order. */
export function getChanges() {
  return apiFetch("/market/changes");
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

/**
 * Recent news about a tracked ticker, newest first (at most 10): [{ headline, summary, source, url, imageUrl,
 * publishedAt, sentiment }]. `summary`, `source`, `imageUrl`, `publishedAt` and `sentiment` ("Bullish" | "Bearish" |
 * "Neutral") may be null.
 */
export function getNews(symbol) {
  return apiFetch(`/news/${encodeURIComponent(symbol)}`);
}

/**
 * A stock's cash dividends in USD: { symbol, dividends: [{ exDate, payDate, amount }] (newest ex-date first,
 * announced ones included), trailingYearPerShare, received (what this account has been paid by it) }.
 */
export function getDividends(symbol) {
  return apiFetch(`/dividends/${encodeURIComponent(symbol)}`);
}

/**
 * The portfolio next to its target allocation: { hasTargets, totalValue, toleranceBand, cash: { value,
 * currentPercent, targetPercent }, rows: [{ symbol, name, price, shares, value, currentPercent, targetPercent,
 * trade: { type, quantity, estimatedValue } | null }] }.
 */
export function getRebalancePlan() {
  return apiFetch("/portfolio/rebalance");
}

/** Replaces every target ([{ symbol, percent }]; the rest is cash) and returns the new plan. */
export function setTargets(targets) {
  return apiFetch("/portfolio/targets", { method: "PUT", body: JSON.stringify({ targets }) });
}

/** Your watchlist entries: [{ symbol, name, currentPrice, addedAt, note, targetPrice }]. */
export function getWatchlist() {
  return apiFetch("/watchlist");
}

/** Saves your note and target price on a stock (null or blank clears them); adds it to your watchlist. */
export function saveWatchlistNote(symbol, note, targetPrice) {
  return apiFetch(`/watchlist/${encodeURIComponent(symbol)}`, {
    method: "PUT",
    body: JSON.stringify({ note, targetPrice }),
  });
}

export function getPortfolio() {
  return apiFetch("/portfolio");
}

export function getPortfolioHistory() {
  return apiFetch("/portfolio/history");
}

/**
 * Profit and loss in USD: { realizedPnL, unrealizedPnL, totalPnL, feesPaid, filledOrders, closedTrades,
 * winningTrades, winRate (null before any sell), bestTrade, worstTrade, bySymbol: [...] }.
 */
export function getPerformance() {
  return apiFetch("/portfolio/performance");
}

/**
 * The portfolio's time-weighted return next to `symbol`'s move over `range` ("1D" | "1W" | "1M" | "3M" | "1Y" | "ALL"):
 * { symbol, name, portfolioReturn, benchmarkReturn, points: [{ at, portfolio, benchmark }] }, all in % since the
 * window's first point. `benchmark` is null before the ticker had a price; the totals are null with too little history.
 */
export function getBenchmark(symbol, range) {
  const query = new URLSearchParams({ symbol });
  if (range) query.set("range", range);
  return apiFetch(`/portfolio/benchmark?${query}`);
}

export function getOrders() {
  return apiFetch("/orders");
}

// settlementCurrency: which wallet the order pays from / is paid into (null = USD).
// trailPercent: TRAILING_STOP only — the stop follows the highest price this many % below it.
export function placeOrder(symbol, type, quantity, kind = "MARKET", limitPrice = null, stopPrice = null, settlementCurrency = null, trailPercent = null) {
  return apiFetch("/orders", {
    method: "POST",
    body: JSON.stringify({ symbol, type, kind, quantity, limitPrice, stopPrice, settlementCurrency, trailPercent }),
  });
}

/** Replaces a pending order with new terms ({ quantity, limitPrice, stopPrice, trailPercent }); returns the new order. */
export function replaceOrder(id, terms) {
  return apiFetch(`/orders/${id}`, { method: "PUT", body: JSON.stringify(terms) });
}

/** Sets the user's note on an order; an empty note removes it. Returns the order. */
export function setOrderNote(id, note) {
  return apiFetch(`/orders/${id}/note`, { method: "PUT", body: JSON.stringify({ note }) });
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

/** Set `targetPrice`, or leave it null and give `movePercent` for "tell me if it moves this % from now". */
export function createAlert(symbol, direction, targetPrice, movePercent = null) {
  return apiFetch("/alerts", { method: "POST", body: JSON.stringify({ symbol, direction, targetPrice, movePercent }) });
}

export function deleteAlert(id) {
  return apiFetch(`/alerts/${id}`, { method: "DELETE" });
}

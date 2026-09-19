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

async function apiFetch(path, options = {}) {
  const res = await fetch(`${BASE_URL}${path}`, {
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

  // A 401 on a normal call means the session ended. On the auth calls it just
  // means "wrong password" / "not signed in", which the caller handles itself.
  if (res.status === 401 && !path.startsWith("/auth/")) {
    window.dispatchEvent(new Event("meridian:session-expired"));
    throw new Error("Your session has expired. Please log in again.");
  }

  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message || "Request failed");
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

export function getTickers() {
  return apiFetch("/tickers");
}

export function searchTickers(query) {
  return apiFetch(`/tickers/search?q=${encodeURIComponent(query)}`);
}

export function addTicker(symbol, name, exchange) {
  return apiFetch("/tickers", { method: "POST", body: JSON.stringify({ symbol, name, exchange }) });
}

export function getPrices(symbol) {
  return apiFetch(`/prices/${symbol}`);
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

const BASE_URL = "http://localhost:8080/api";

export function getToken() {
  return localStorage.getItem("meridian_token");
}

export function setSession(token, email) {
  localStorage.setItem("meridian_token", token);
  localStorage.setItem("meridian_email", email);
}

export function getSession() {
  const token = localStorage.getItem("meridian_token");
  const email = localStorage.getItem("meridian_email");
  return token && email ? { token, email } : null;
}

export function clearSession() {
  localStorage.removeItem("meridian_token");
  localStorage.removeItem("meridian_email");
}

async function apiFetch(path, options = {}) {
  const token = getToken();
  const headers = {
    "Content-Type": "application/json",
    ...options.headers,
  };
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`${BASE_URL}${path}`, { ...options, headers });

  if (res.status === 401) {
    clearSession();
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

export function placeOrder(symbol, type, quantity) {
  return apiFetch("/orders", { method: "POST", body: JSON.stringify({ symbol, type, quantity }) });
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

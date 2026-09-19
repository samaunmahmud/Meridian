// Where the backend lives. Local development talks to the backend directly;
// the Docker image is built with VITE_API_BASE=/api so the site and the API
// share one address (nginx proxies /api and /ws), which also keeps the
// session cookie same-origin.
export const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080/api";

// The live-price websocket sits next to the API, on ws:// or wss:// to match.
export function socketUrl() {
  const api = new URL(API_BASE, window.location.href);
  return `${api.protocol === "https:" ? "wss:" : "ws:"}//${api.host}/ws/prices`;
}

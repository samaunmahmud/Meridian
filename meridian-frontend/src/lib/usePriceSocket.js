import { useEffect, useState } from "react";
import { getToken } from "./api";

// Returns the most recent WebSocket message, whatever kind it is
// ({ kind: "PRICE_UPDATE", ... } or { kind: "ALERT_TRIGGERED", ... }).
// Consumers check .kind before acting on it.
export function usePriceSocket() {
  const [latestMessage, setLatestMessage] = useState(null);

  useEffect(() => {
    const token = getToken();
    // Browsers can't send custom headers on a WebSocket handshake, so the
    // JWT goes as a query param instead — the backend's handshake
    // interceptor reads it from there.
    const url = token
      ? `ws://localhost:8080/ws/prices?token=${encodeURIComponent(token)}`
      : "ws://localhost:8080/ws/prices";

    const ws = new WebSocket(url);

    ws.onmessage = (event) => {
      try {
        setLatestMessage(JSON.parse(event.data));
      } catch (e) {
        console.error("Failed to parse WebSocket message", e);
      }
    };

    ws.onerror = (e) => console.error("WebSocket error", e);
    return () => ws.close();
  }, []);

  return latestMessage;
}

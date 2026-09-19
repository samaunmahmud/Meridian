import { useEffect, useState } from "react";
import { socketUrl } from "./config";

// Returns the most recent WebSocket message, whatever kind it is
// ({ kind: "PRICE_UPDATE", ... } or { kind: "ALERT_TRIGGERED", ... }).
// Consumers check .kind before acting on it.
//
// `userKey` identifies who is signed in (their email, or null). The session
// cookie authenticates the connection at the moment it is opened, so the
// socket is reconnected whenever that changes — otherwise a connection opened
// before login would stay anonymous and never receive personal messages
// (order fills, alerts). It also reconnects by itself, with a growing delay,
// if the connection drops.
export function usePriceSocket(userKey) {
  const [latestMessage, setLatestMessage] = useState(null);

  useEffect(() => {
    let ws;
    let retryTimer;
    let attempts = 0;
    let stopped = false;

    function connect() {
      // Browsers send the session cookie with the handshake automatically —
      // no token in the URL (URLs end up in logs and browser history).
      ws = new WebSocket(socketUrl());

      ws.onopen = () => {
        attempts = 0;
      };

      ws.onmessage = (event) => {
        try {
          setLatestMessage(JSON.parse(event.data));
        } catch (e) {
          console.error("Failed to parse WebSocket message", e);
        }
      };

      ws.onerror = () => ws.close(); // onclose below schedules the retry

      ws.onclose = () => {
        if (stopped) return;
        const delay = Math.min(30000, 1000 * 2 ** attempts);
        attempts += 1;
        retryTimer = setTimeout(connect, delay);
      };
    }

    connect();

    return () => {
      stopped = true;
      clearTimeout(retryTimer);
      ws?.close();
    };
  }, [userKey]);

  return latestMessage;
}

import { useEffect, useState } from "react";
import { getMarketStatus } from "./api";

/** The stocks/crypto market status from the server, refreshed every minute. */
export function useMarketStatus() {
  const [status, setStatus] = useState(null);

  useEffect(() => {
    let cancelled = false;
    const load = () =>
      getMarketStatus()
        .then((s) => !cancelled && setStatus(s))
        .catch(() => {}); // unknown is fine: the page just shows no market notice
    load();
    const timer = setInterval(load, 60000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, []);

  return status;
}

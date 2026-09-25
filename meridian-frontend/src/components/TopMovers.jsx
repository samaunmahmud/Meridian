import { useEffect, useRef, useState } from "react";
import { getMovers } from "../lib/api";
import { formatNumber } from "../lib/formatMoney";
import Skeleton from "./Skeleton";
import TickerAvatar from "./TickerAvatar";

// A price update arrives at most every 20 s; re-asking the server on each one is plenty cheap, but a
// burst (several tickers in a row) only needs one refresh.
const REFRESH_DEBOUNCE_MS = 2000;

function MoverList({ title, rows, positive, onSelect, empty }) {
  return (
    <div className="min-w-0">
      <h3 className="text-[13px] font-semibold text-muted mb-1 px-2">{title}</h3>
      {rows.length === 0 ? (
        <p className="text-sm text-muted px-2 py-3">{empty}</p>
      ) : (
        <ul>
          {rows.map((m) => (
            <li key={m.symbol}>
              <button
                type="button"
                onClick={() => onSelect(m)}
                className="w-full flex items-center gap-3 h-16 px-2 rounded-2xl text-left hover:bg-panel-2 transition-colors"
              >
                <TickerAvatar symbol={m.symbol} size={40} />
                <div className="flex-1 min-w-0">
                  <div className="text-[15px] font-semibold truncate">{m.symbol}</div>
                  <div className="text-[13px] text-muted truncate">{m.name}</div>
                </div>
                <div className="text-right shrink-0">
                  <div className="text-[15px] font-mono font-semibold">${formatNumber(m.price)}</div>
                  <div className={`text-[13px] font-mono font-medium ${positive ? "text-gain" : "text-loss"}`}>
                    {positive ? "+" : ""}
                    {Number(m.changePercent).toFixed(2)}%
                  </div>
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// Today's biggest rises and falls among the tracked tickers. Stocks are measured from the previous
// close (so at the weekend this is Friday's move), crypto over 24 hours. Picking one shows it above.
export default function TopMovers({ onSelect, liveUpdate }) {
  const [movers, setMovers] = useState(null);
  const [failed, setFailed] = useState(false);
  const timer = useRef(null);

  function load() {
    getMovers(5)
      .then((m) => {
        setMovers(m);
        setFailed(false);
      })
      .catch(() => setFailed(true));
  }

  useEffect(() => {
    load();
    return () => clearTimeout(timer.current);
  }, []);

  useEffect(() => {
    if (!liveUpdate || liveUpdate.kind !== "PRICE_UPDATE") return;
    clearTimeout(timer.current);
    timer.current = setTimeout(load, REFRESH_DEBOUNCE_MS);
  }, [liveUpdate]);

  return (
    <section aria-labelledby="top-movers-heading" className="bg-panel rounded-[28px] p-4 sm:p-5 fade-in">
      <div className="flex items-baseline justify-between gap-3 mb-3 px-2">
        <h2 id="top-movers-heading" className="text-lg font-bold">
          Top movers
        </h2>
        <span className="text-xs text-muted">Stocks since the last close · crypto 24h</span>
      </div>

      {!movers && !failed && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
          {[1, 2].map((i) => (
            <div key={i} className="space-y-2">
              <Skeleton className="h-11 w-full" />
              <Skeleton className="h-11 w-full" />
            </div>
          ))}
        </div>
      )}

      {failed && !movers && <p className="text-xs text-dim px-1.5 py-3">Top movers could not be loaded.</p>}

      {movers && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4">
          <MoverList title="Gainers" rows={movers.gainers} positive onSelect={onSelect} empty="Nothing is up yet today." />
          <MoverList title="Losers" rows={movers.losers} positive={false} onSelect={onSelect} empty="Nothing is down yet today." />
        </div>
      )}
    </section>
  );
}

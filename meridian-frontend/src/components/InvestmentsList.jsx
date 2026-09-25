import { useEffect, useRef, useState } from "react";
import { getPortfolio } from "../lib/api";
import { formatMoney } from "../lib/formatMoney";
import Skeleton from "./Skeleton";
import TickerAvatar from "./TickerAvatar";

// Values come from the server; after a burst of price updates, re-read once.
const REFRESH_DEBOUNCE_MS = 2000;

function shares(q) {
  const n = Number(q);
  return `${n.toLocaleString("en-US", { maximumFractionDigits: 8 })} ${n === 1 ? "share" : "shares"}`;
}

// Home's list of what you own: one row per position with its value and return. Picking a row
// opens that stock.
export default function InvestmentsList({ refreshKey, liveUpdate, onSelect, onStart }) {
  const [holdings, setHoldings] = useState(null);
  const timer = useRef(null);

  function load() {
    getPortfolio()
      .then((p) => setHoldings(p.holdings))
      .catch(() => setHoldings((h) => h ?? []));
  }

  useEffect(() => {
    load();
  }, [refreshKey]);

  useEffect(() => () => clearTimeout(timer.current), []);

  useEffect(() => {
    if (liveUpdate?.kind !== "PRICE_UPDATE") return;
    if (!holdings?.some((h) => h.symbol === liveUpdate.symbol)) return;
    clearTimeout(timer.current);
    timer.current = setTimeout(load, REFRESH_DEBOUNCE_MS);
  }, [liveUpdate]);

  return (
    <section aria-labelledby="investments-heading" className="bg-panel rounded-[28px] p-4 sm:p-5 fade-in">
      <div className="flex items-baseline justify-between px-2 mb-1">
        <h2 id="investments-heading" className="text-lg font-bold">
          Your investments
        </h2>
        {holdings?.length > 0 && <span className="text-[13px] text-muted">{holdings.length}</span>}
      </div>

      {!holdings && (
        <div className="space-y-2 mt-3 px-2">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      )}

      {holdings?.length === 0 && (
        <div className="px-2 py-4">
          <p className="text-sm text-muted">You don&rsquo;t own anything yet.</p>
          {onStart && (
            <button
              type="button"
              onClick={onStart}
              className="mt-3 h-10 px-5 rounded-full bg-accent text-accent-ink text-sm font-semibold hover:brightness-110 transition-all"
            >
              Buy your first stock
            </button>
          )}
        </div>
      )}

      {holdings?.length > 0 && (
        <ul>
          {holdings.map((h) => {
            const up = h.gainLoss >= 0;
            return (
              <li key={h.symbol}>
                <button
                  type="button"
                  onClick={() => onSelect(h)}
                  className="w-full flex items-center gap-3 h-16 px-2 rounded-2xl text-left hover:bg-panel-2 transition-colors"
                >
                  <TickerAvatar symbol={h.symbol} size={40} />
                  <div className="flex-1 min-w-0">
                    <div className="text-[15px] font-semibold truncate">{h.name}</div>
                    <div className="text-[13px] text-muted truncate">{shares(h.quantity)}</div>
                  </div>
                  <div className="text-right shrink-0">
                    <div className="text-[15px] font-mono font-semibold">{formatMoney(h.marketValue)}</div>
                    <div className={`text-[13px] font-mono font-medium ${up ? "text-gain" : "text-loss"}`}>
                      {up ? "+" : "−"}
                      {Math.abs(h.gainLossPct).toFixed(2)}%
                    </div>
                  </div>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

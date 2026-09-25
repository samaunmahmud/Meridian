import { useEffect, useRef, useState } from "react";
import { getChanges, getTickers, getPrices } from "../lib/api";
import Sparkline from "./Sparkline";
import TickerAvatar from "./TickerAvatar";
import Skeleton from "./Skeleton";
import Icon from "./Icon";
import AddTickerModal from "./AddTickerModal";
import { formatNumber } from "../lib/formatMoney";

// The % under each price is today's change: a stock since the previous close, crypto over 24 hours (the
// server's measure, as in Top movers). A live price is compared with that same reference; the references
// themselves are re-read shortly after updates, so they roll over when a new session starts.
const CHANGES_REFRESH_MS = 2000;

function dayChange(price, reference) {
  if (price == null || !reference) return null;
  return ((price - reference) / reference) * 100;
}

export default function Watchlist({ selectedSymbol, onSelect, liveUpdate }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [showAddModal, setShowAddModal] = useState(false);
  const [references, setReferences] = useState({}); // symbol -> price today's change is measured from
  const refreshTimer = useRef(null);

  function loadChanges() {
    return getChanges()
      .then((changes) => setReferences(Object.fromEntries(changes.map((c) => [c.symbol, c.referencePrice]))))
      .catch(() => {}); // no % shown rather than a wrong one
  }

  async function load() {
    setLoading(true);
    const [tickers] = await Promise.all([getTickers(), loadChanges()]);
    const withPrices = await Promise.all(
      tickers.map(async (t) => {
        const history = await getPrices(t.symbol);
        const chronological = [...history].reverse();
        return {
          ...t,
          latest: history[0],
          sparkline: chronological.map((p) => p.price),
        };
      })
    );
    setRows(withPrices);
    setLoading(false);
    if (!selectedSymbol && withPrices.length > 0) {
      onSelect(withPrices[0]);
    }
  }

  useEffect(() => {
    load();
    return () => clearTimeout(refreshTimer.current);
  }, []);

  useEffect(() => {
    if (!liveUpdate || liveUpdate.kind !== "PRICE_UPDATE") return;
    setRows((prev) =>
      prev.map((row) => {
        if (row.symbol !== liveUpdate.symbol) return row;
        return {
          ...row,
          latest: { price: liveUpdate.price, recordedAt: liveUpdate.recordedAt },
          sparkline: [...row.sparkline, liveUpdate.price],
        };
      })
    );
    clearTimeout(refreshTimer.current);
    refreshTimer.current = setTimeout(loadChanges, CHANGES_REFRESH_MS);
  }, [liveUpdate]);

  const filtered = rows.filter(
    (r) =>
      r.symbol.toLowerCase().includes(query.toLowerCase()) ||
      r.name.toLowerCase().includes(query.toLowerCase())
  );

  return (
    <>
      <div className="bg-panel border border-line rounded-[20px] p-4 sm:p-5 fade-in h-fit">
        <div className="flex items-center justify-between mb-1 px-1.5">
          <h2 className="text-base font-semibold">Watchlist</h2>
          <button
            onClick={() => setShowAddModal(true)}
            aria-label="Add a stock to the watchlist"
            className="flex items-center gap-1 h-8 px-2 -mr-2 text-[13px] text-accent hover:brightness-110 transition-all font-medium"
          >
            <Icon name="plus" size={14} strokeWidth={2.2} />
            Add
          </button>
        </div>

        {loading ? (
          <div className="space-y-3 mt-2">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
        ) : (
          <>
            <input
              aria-label="Filter watchlist"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Filter tickers..."
              className="w-full bg-panel-2 border border-control rounded-lg px-3 py-1.5 text-xs my-2 outline-none focus:border-accent transition-colors"
            />

            {filtered.length === 0 && (
              <div className="text-xs text-dim px-1 py-3">No tickers match "{query}"</div>
            )}

            {filtered.map((row, i) => {
              const pct = dayChange(row.latest?.price, references[row.symbol]);
              const isUp = (pct ?? 0) >= 0;
              const isSelected = row.symbol === selectedSymbol;
              return (
                <button
                  key={row.symbol}
                  onClick={() => onSelect(row)}
                  aria-pressed={isSelected}
                  className={`w-full flex items-center gap-3 h-[58px] px-2.5 rounded-[14px] text-left transition-colors ${
                    isSelected ? "bg-panel-2" : "hover:bg-panel-2/60"
                  }`}
                >
                  <TickerAvatar symbol={row.symbol} size={36} />

                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-semibold truncate">{row.symbol}</div>
                    <div className="text-xs text-muted truncate">{row.name}</div>
                  </div>

                  <Sparkline values={row.sparkline} positive={isUp} width={52} height={22} />

                  <div className="text-right w-[84px] shrink-0">
                    <div className="text-[13px] font-mono font-medium">
                      {row.latest ? `$${formatNumber(row.latest.price)}` : "\u2014"}
                    </div>
                    <div className={`text-xs font-mono ${pct == null ? "text-dim" : isUp ? "text-gain" : "text-loss"}`}>
                      {pct == null ? "" : `${isUp ? "+" : ""}${pct.toFixed(2)}%`}
                      {pct != null && <span className="sr-only"> today</span>}
                    </div>
                  </div>
                </button>
              );
            })}
          </>
        )}
      </div>

      {showAddModal && (
        <AddTickerModal onClose={() => setShowAddModal(false)} onAdded={load} />
      )}
    </>
  );
}

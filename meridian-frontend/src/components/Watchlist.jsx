import { useEffect, useState } from "react";
import { getTickers, getPrices } from "../lib/api";
import Sparkline from "./Sparkline";
import TickerAvatar from "./TickerAvatar";
import Skeleton from "./Skeleton";
import Icon from "./Icon";
import AddTickerModal from "./AddTickerModal";
import { formatNumber } from "../lib/formatMoney";

export default function Watchlist({ selectedSymbol, onSelect, liveUpdate }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [showAddModal, setShowAddModal] = useState(false);

  async function load() {
    setLoading(true);
    const tickers = await getTickers();
    const withPrices = await Promise.all(
      tickers.map(async (t) => {
        const history = await getPrices(t.symbol);
        const chronological = [...history].reverse();
        const latest = history[0];
        const previous = history[1];
        const delta = latest && previous ? latest.price - previous.price : 0;
        return {
          ...t,
          latest,
          delta,
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
  }, []);

  useEffect(() => {
    if (!liveUpdate || liveUpdate.kind !== "PRICE_UPDATE") return;
    setRows((prev) =>
      prev.map((row) => {
        if (row.symbol !== liveUpdate.symbol) return row;
        const previousPrice = row.latest?.price ?? liveUpdate.price;
        return {
          ...row,
          latest: { price: liveUpdate.price, recordedAt: liveUpdate.recordedAt },
          delta: liveUpdate.price - previousPrice,
          sparkline: [...row.sparkline, liveUpdate.price],
        };
      })
    );
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
          <div className="text-base font-semibold">Watchlist</div>
          <button
            onClick={() => setShowAddModal(true)}
            className="flex items-center gap-1 text-[13px] text-accent hover:brightness-110 transition-all font-medium"
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
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Filter tickers..."
              className="w-full bg-panel-2 border border-line rounded-lg px-3 py-1.5 text-xs my-2 outline-none focus:border-accent transition-colors"
            />

            {filtered.length === 0 && (
              <div className="text-xs text-dim px-1 py-3">No tickers match "{query}"</div>
            )}

            {filtered.map((row, i) => {
              const isUp = row.delta >= 0;
              const isSelected = row.symbol === selectedSymbol;
              const previous = row.latest ? row.latest.price - row.delta : 0;
              const pct = previous ? (row.delta / previous) * 100 : 0;
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
                    <div className={`text-xs font-mono ${isUp ? "text-gain" : "text-loss"}`}>
                      {row.latest ? `${isUp ? "+" : ""}${pct.toFixed(2)}%` : ""}
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

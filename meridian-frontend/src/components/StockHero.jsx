import { useEffect, useState } from "react";
import { getPrices } from "../lib/api";
import CandlestickChart from "./CandlestickChart";
import TickerAvatar from "./TickerAvatar";
import Skeleton from "./Skeleton";
import { useAnimatedNumber } from "../lib/useAnimatedNumber";

const RANGES = [
  { key: "20", label: "20" },
  { key: "50", label: "50" },
  { key: "all", label: "All" },
];

export default function StockHero({ ticker, liveUpdate, onTrade }) {
  const [prices, setPrices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [range, setRange] = useState("all");

  useEffect(() => {
    if (!ticker) return;
    setLoading(true);
    getPrices(ticker.symbol)
      .then((data) => setPrices([...data].reverse()))
      .finally(() => setLoading(false));
  }, [ticker]);

  useEffect(() => {
    if (!liveUpdate || liveUpdate.kind !== "PRICE_UPDATE") return;
    if (!ticker || liveUpdate.symbol !== ticker.symbol) return;
    setPrices((prev) => {
      const last = prev[prev.length - 1];
      if (last && last.recordedAt === liveUpdate.recordedAt) return prev;
      return [...prev, { price: liveUpdate.price, recordedAt: liveUpdate.recordedAt }];
    });
  }, [liveUpdate, ticker]);

  const visible = range === "all" ? prices : prices.slice(-Number(range));
  const latest = visible[visible.length - 1];
  const first = visible[0];
  const delta = latest && first ? latest.price - first.price : 0;
  const deltaPct = first && first.price ? ((delta / first.price) * 100).toFixed(2) : "0.00";
  const isUp = delta >= 0;

  const animatedPrice = useAnimatedNumber(latest?.price ?? 0);

  if (!ticker) return null;

  if (loading) {
    return (
      <section className="bg-panel border border-line rounded-2xl p-7 space-y-4">
        <div className="flex items-center gap-3">
          <Skeleton className="h-10 w-10 rounded-full" />
          <div className="space-y-1.5">
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-3 w-24" />
          </div>
        </div>
        <Skeleton className="h-9 w-48" />
        <Skeleton className="h-56 w-full" />
      </section>
    );
  }

  return (
    <section className="bg-panel border border-line rounded-2xl p-7 fade-in transition-shadow hover:shadow-lg hover:shadow-black/20">
      <div className="flex justify-between items-start mb-1">
        <div className="flex items-center gap-3">
          <TickerAvatar symbol={ticker.symbol} size={40} />
          <div>
            <div className="text-[17px] font-semibold leading-tight">{ticker.name}</div>
            <div className="text-[13px] text-muted">
              {ticker.symbol} &middot; {ticker.exchange}
            </div>
          </div>
        </div>
        <div className="flex gap-1 bg-panel-2 rounded-lg p-1 text-xs">
          {RANGES.map((r) => (
            <button
              key={r.key}
              onClick={() => setRange(r.key)}
              className={`px-2.5 py-1 rounded-md transition-colors ${
                range === r.key ? "bg-line text-bone" : "text-dim"
              }`}
            >
              {r.label}
            </button>
          ))}
        </div>
      </div>

      {latest ? (
        <>
          <div className="flex items-end justify-between mt-4 mb-2">
            <div>
              <div className="text-[38px] font-mono font-medium leading-none">
                {animatedPrice.toFixed(2)}
              </div>
              <div
                className={`text-sm font-mono mt-2 inline-block px-2 py-0.5 rounded-md ${
                  isUp ? "text-gain bg-gain-dim" : "text-loss bg-loss-dim"
                }`}
              >
                {isUp ? "+" : ""}{delta.toFixed(2)} ({isUp ? "+" : ""}{deltaPct}%)
              </div>
            </div>

            <div className="flex gap-2">
              <button
                onClick={() => onTrade(ticker.symbol, "BUY")}
                className="px-5 py-2.5 rounded-lg bg-gain text-ink text-sm font-medium hover:brightness-110 active:scale-[0.97] transition-all"
              >
                Buy
              </button>
              <button
                onClick={() => onTrade(ticker.symbol, "SELL")}
                className="px-5 py-2.5 rounded-lg bg-panel-2 border border-line text-bone text-sm font-medium hover:bg-line active:scale-[0.97] transition-all"
              >
                Sell
              </button>
            </div>
          </div>
          <div className="text-xs text-dim mb-2">
            Last updated: {new Date(latest.recordedAt).toLocaleString()}
          </div>

          <CandlestickChart points={visible} />
        </>
      ) : (
        <div className="text-sm text-dim mt-8">No price data yet — the scheduler hasn't polled this ticker.</div>
      )}
    </section>
  );
}

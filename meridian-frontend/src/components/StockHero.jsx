import { useEffect, useRef, useState } from "react";
import { getPrices } from "../lib/api";
import { formatNumber } from "../lib/formatMoney";
import CandlestickChart from "./CandlestickChart";
import Amount from "./Amount";
import Icon from "./Icon";
import MarketBadge from "./MarketBadge";
import PriceChart from "./PriceChart";
import Segmented from "./Segmented";
import TickerAvatar from "./TickerAvatar";
import Skeleton from "./Skeleton";
import { useAnimatedNumber } from "../lib/useAnimatedNumber";
import { formatAge, isDelayed } from "../lib/priceAge";

// Time windows, answered by the server (which thins a long history to at most CHART_POINTS points).
// History only goes back to when the stock was first tracked, so a young stock shows the same on all.
const RANGES = [
  { key: "1D", label: "1D", words: "today" },
  { key: "1W", label: "1W", words: "past week" },
  { key: "1M", label: "1M", words: "past month" },
  { key: "3M", label: "3M", words: "past 3 months" },
  { key: "1Y", label: "1Y", words: "past year" },
  { key: "ALL", label: "All", words: "all time" },
];
const CHART_POINTS = 500;

const CHART_TYPES = [
  { key: "line", label: "Line" },
  { key: "candles", label: "Candles" },
];

// `tradeBesideChart`: the page has its own trade form (wide screens) and Buy / Sell bar (narrower
// ones), so this leaves its Buy / Sell buttons out.
export default function StockHero({ ticker, liveUpdate, onTrade, tradeBesideChart = false }) {
  const [prices, setPrices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [range, setRange] = useState("1D");
  const [chartType, setChartType] = useState("line");

  // Only a NEW stock shows the loading skeleton; changing the range updates the chart in place, so the
  // range buttons stay where they are. A stale answer (a slower earlier request) is ignored.
  const shownSymbol = useRef(null);
  useEffect(() => {
    if (!ticker) return;
    let cancelled = false;
    if (shownSymbol.current !== ticker.symbol) setLoading(true);
    getPrices(ticker.symbol, { range, points: CHART_POINTS })
      .then((data) => {
        if (cancelled) return;
        shownSymbol.current = ticker.symbol;
        setPrices([...data].reverse());
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [ticker, range]);

  useEffect(() => {
    if (!liveUpdate || liveUpdate.kind !== "PRICE_UPDATE") return;
    if (!ticker || liveUpdate.symbol !== ticker.symbol) return;
    setPrices((prev) => {
      const last = prev[prev.length - 1];
      if (last && last.recordedAt === liveUpdate.recordedAt) return prev;
      return [...prev, { price: liveUpdate.price, recordedAt: liveUpdate.recordedAt }];
    });
  }, [liveUpdate, ticker]);

  const visible = prices;
  const latest = visible[visible.length - 1];
  const first = visible[0];
  const delta = latest && first ? latest.price - first.price : 0;
  const deltaPct = first && first.price ? ((delta / first.price) * 100).toFixed(2) : "0.00";
  const isUp = delta >= 0;

  const animatedPrice = useAnimatedNumber(latest?.price ?? 0);

  // Re-check once a minute, so a quiet feed turns into "delayed" without a reload.
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60000);
    return () => clearInterval(timer);
  }, []);

  if (!ticker) return null;

  if (loading) {
    return (
      <section className="bg-panel rounded-[28px] p-5 sm:p-7 space-y-4">
        <div className="flex items-center gap-3">
          <Skeleton className="h-11 w-11 rounded-full" />
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

  const windowStats = latest
    ? [
        { label: "High", value: `$${formatNumber(Math.max(...visible.map((p) => p.price)))}` },
        { label: "Low", value: `$${formatNumber(Math.min(...visible.map((p) => p.price)))}` },
        { label: "Start", value: `$${formatNumber(first.price)}` },
        { label: "Price updates", value: String(visible.length) },
      ]
    : [];

  const rangeWords = RANGES.find((r) => r.key === range)?.words;

  return (
    <section aria-label={`${ticker.name} price`} className="sm:bg-panel sm:rounded-[28px] sm:p-7 fade-in">
      <div className="flex items-center gap-3">
        <TickerAvatar symbol={ticker.symbol} size={44} />
        <div className="min-w-0">
          <h2 className="text-lg font-semibold leading-tight truncate">{ticker.name}</h2>
          <div className="flex items-center gap-2 text-[13px] text-muted">
            <span>
              {ticker.symbol} &middot; {ticker.exchange}
            </span>
            <MarketBadge assetType={ticker.assetType} />
          </div>
        </div>
      </div>

      {latest ? (
        <>
          <div className="mt-5">
            <Amount value={animatedPrice} className="block font-display text-[44px] sm:text-[52px] leading-none" />
            <div className={`flex items-center gap-1 text-sm font-semibold mt-2 ${isUp ? "text-gain" : "text-loss"}`}>
              <Icon name={isUp ? "up" : "down"} size={14} strokeWidth={2.4} />
              {isUp ? "+" : "−"}${formatNumber(Math.abs(delta))} ({isUp ? "+" : "−"}
              {Math.abs(Number(deltaPct)).toFixed(2)}%)
              <span className="text-muted font-medium ml-1">{rangeWords}</span>
            </div>
          </div>

          <div className="mt-5 -mx-1">
            {chartType === "line" ? (
              <PriceChart points={visible} positive={isUp} name={ticker.symbol} />
            ) : (
              <CandlestickChart points={visible} name={ticker.symbol} />
            )}
          </div>

          <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2 mt-3">
            <Segmented options={RANGES} value={range} onChange={setRange} label="Price window" />
            <Segmented options={CHART_TYPES} value={chartType} onChange={setChartType} label="Chart type" />
          </div>
          {isDelayed(latest.recordedAt, now) ? (
            // The words carry the meaning (the amber dot is decoration).
            <div role="status" className="flex items-center gap-2 text-xs text-bone mt-3">
              <span className="w-2 h-2 rounded-full shrink-0" style={{ background: "var(--c-s2)" }} aria-hidden="true" />
              Prices delayed — last update {formatAge(latest.recordedAt, now)}
            </div>
          ) : (
            <div className="flex items-center gap-2 text-xs text-muted mt-3">
              <span className="w-2 h-2 rounded-full bg-gain shrink-0" aria-hidden="true" />
              Updated {new Date(latest.recordedAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}
            </div>
          )}

          <h3 className="text-[15px] font-semibold mt-6 mb-1">Key stats</h3>
          <dl className="grid grid-cols-2 gap-x-6">
            {windowStats.map((s) => (
              <div key={s.label} className="flex items-center justify-between py-2.5 border-b border-line">
                <dt className="text-sm text-muted">{s.label}</dt>
                <dd className="font-mono font-medium text-sm">{s.value}</dd>
              </div>
            ))}
          </dl>

          <div className={`grid grid-cols-2 gap-3 mt-5 ${tradeBesideChart ? "hidden" : ""}`}>
            <button
              onClick={() => onTrade(ticker.symbol, "BUY")}
              className="h-12 rounded-full bg-accent text-accent-ink text-[15px] font-semibold flex items-center justify-center gap-2 hover:brightness-110 active:scale-[0.98] transition-all"
            >
              <Icon name="up" size={16} strokeWidth={2.2} />
              Buy {ticker.symbol}
            </button>
            <button
              onClick={() => onTrade(ticker.symbol, "SELL")}
              className="h-12 rounded-full bg-loss-dim text-loss text-[15px] font-semibold flex items-center justify-center gap-2 hover:brightness-110 active:scale-[0.98] transition-all"
            >
              <Icon name="down" size={16} strokeWidth={2.2} />
              Sell
            </button>
          </div>
        </>
      ) : (
        <div className="text-sm text-dim mt-8">No price data yet — the scheduler hasn't polled this ticker.</div>
      )}
    </section>
  );
}

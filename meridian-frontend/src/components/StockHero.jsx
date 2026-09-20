import { useEffect, useRef, useState } from "react";
import { getPrices } from "../lib/api";
import { formatNumber } from "../lib/formatMoney";
import CandlestickChart from "./CandlestickChart";
import Icon from "./Icon";
import MarketBadge from "./MarketBadge";
import PriceChart from "./PriceChart";
import TickerAvatar from "./TickerAvatar";
import Skeleton from "./Skeleton";
import { useAnimatedNumber } from "../lib/useAnimatedNumber";
import { formatAge, isDelayed } from "../lib/priceAge";

// Time windows, answered by the server (which thins a long history to at most CHART_POINTS points).
// History only goes back to when the stock was first tracked, so a young stock shows the same on all.
const RANGES = [
  { key: "1D", label: "1D" },
  { key: "1W", label: "1W" },
  { key: "1M", label: "1M" },
  { key: "ALL", label: "All" },
];
const CHART_POINTS = 500;

const CHART_TYPES = [
  { key: "line", label: "Line" },
  { key: "candles", label: "Candles" },
];

function Segmented({ options, value, onChange, label }) {
  return (
    <div role="group" aria-label={label} className="flex gap-0.5 bg-panel-2 rounded-xl p-1 text-[13px]">
      {options.map((o) => (
        <button
          key={o.key}
          type="button"
          aria-pressed={value === o.key}
          onClick={() => onChange(o.key)}
          className={`px-3.5 py-1.5 rounded-[9px] transition-colors ${
            value === o.key ? "bg-accent-dim text-accent font-semibold" : "text-muted font-medium hover:text-bone"
          }`}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

export default function StockHero({ ticker, liveUpdate, onTrade }) {
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
      <section className="bg-panel border border-line rounded-[20px] p-5 sm:p-7 space-y-4">
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
        { label: "Window high", value: `$${formatNumber(Math.max(...visible.map((p) => p.price)))}` },
        { label: "Window low", value: `$${formatNumber(Math.min(...visible.map((p) => p.price)))}` },
        { label: "Window start", value: `$${formatNumber(first.price)}` },
        { label: "Price updates", value: String(visible.length) },
      ]
    : [];

  return (
    <section className="bg-panel border border-line rounded-[20px] p-5 sm:p-7 fade-in">
      <div className="flex flex-wrap justify-between items-start gap-4 mb-5">
        <div className="flex items-center gap-3.5">
          <TickerAvatar symbol={ticker.symbol} size={48} />
          <div>
            <div className="text-lg font-semibold leading-tight">{ticker.name}</div>
            <div className="text-[13px] text-muted">
              {ticker.symbol} &middot; {ticker.exchange}
            </div>
            <div className="mt-1">
              <MarketBadge assetType={ticker.assetType} />
            </div>
          </div>
        </div>

        {latest && (
          <div className="sm:text-right">
            <div className="text-[34px] font-mono font-medium leading-none" style={{ letterSpacing: "-0.04em" }}>
              ${formatNumber(animatedPrice)}
            </div>
            <div
              className={`inline-flex items-center gap-1 text-xs font-mono font-medium mt-2 px-2.5 py-1 rounded-full ${
                isUp ? "text-gain bg-gain-dim" : "text-loss bg-loss-dim"
              }`}
            >
              <Icon name={isUp ? "up" : "down"} size={12} strokeWidth={2.2} />
              {isUp ? "+" : ""}
              {formatNumber(delta)} ({isUp ? "+" : ""}
              {deltaPct}%)
            </div>
          </div>
        )}
      </div>

      {latest ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
            <div className="flex flex-wrap gap-2">
              <Segmented options={RANGES} value={range} onChange={setRange} label="Price window" />
              <Segmented options={CHART_TYPES} value={chartType} onChange={setChartType} label="Chart type" />
            </div>
            {isDelayed(latest.recordedAt, now) ? (
              // The words carry the meaning (the amber dot is decoration).
              <div role="status" className="flex items-center gap-2 text-xs text-bone">
                <span className="w-2 h-2 rounded-full shrink-0" style={{ background: "var(--c-s2)" }} aria-hidden="true" />
                Prices delayed — last update {formatAge(latest.recordedAt, now)}
              </div>
            ) : (
              <div className="flex items-center gap-2 text-xs text-muted">
                <span className="w-2 h-2 rounded-full bg-gain shrink-0" aria-hidden="true" />
                Updated {new Date(latest.recordedAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}
              </div>
            )}
          </div>

          {chartType === "line" ? (
            <PriceChart points={visible} positive={isUp} name={ticker.symbol} />
          ) : (
            <CandlestickChart points={visible} name={ticker.symbol} />
          )}

          <dl className="grid grid-cols-2 sm:grid-cols-4 gap-x-4 gap-y-4 bg-panel-2 rounded-[20px] px-5 py-4 mt-5">
            {windowStats.map((s) => (
              <div key={s.label}>
                <dt className="text-xs font-medium text-muted mb-1">{s.label}</dt>
                <dd className="font-mono font-medium text-sm">{s.value}</dd>
              </div>
            ))}
          </dl>

          <div className="grid grid-cols-2 gap-3 mt-5">
            <button
              onClick={() => onTrade(ticker.symbol, "BUY")}
              className="h-12 rounded-[14px] bg-accent text-accent-ink text-[15px] font-semibold flex items-center justify-center gap-2 hover:brightness-110 active:scale-[0.98] transition-all"
            >
              <Icon name="up" size={16} strokeWidth={2.2} />
              Buy {ticker.symbol}
            </button>
            <button
              onClick={() => onTrade(ticker.symbol, "SELL")}
              className="h-12 rounded-[14px] bg-loss-dim text-loss text-[15px] font-semibold flex items-center justify-center gap-2 hover:brightness-110 active:scale-[0.98] transition-all"
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

import { useEffect, useState } from "react";
import { LineChart, Line, ResponsiveContainer, YAxis, Tooltip, ReferenceLine } from "recharts";
import { getBenchmark, getTickers } from "../lib/api";
import Segmented from "./Segmented";
import Skeleton from "./Skeleton";

const RANGES = [
  { key: "1W", label: "1W" },
  { key: "1M", label: "1M" },
  { key: "3M", label: "3M" },
  { key: "ALL", label: "All" },
];
const STORAGE_KEY = "meridian_benchmark";
// Index funds people usually measure themselves against, best first, when one is tracked.
const PREFERRED = ["SPY", "VOO", "IVV", "QQQ", "VTI"];

function pct(value) {
  if (value == null) return "—";
  return `${value > 0 ? "+" : value < 0 ? "−" : ""}${Math.abs(value).toFixed(2)}%`;
}

function tone(value) {
  return value > 0 ? "text-gain" : value < 0 ? "text-loss" : "text-bone";
}

function savedSymbol() {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function pickDefault(tickers) {
  const symbols = tickers.map((t) => t.symbol);
  const saved = savedSymbol();
  if (saved && symbols.includes(saved)) return saved;
  return PREFERRED.find((s) => symbols.includes(s)) ?? tickers.find((t) => t.assetType !== "CRYPTO")?.symbol ?? symbols[0];
}

function ChartTooltip({ active, payload, symbol }) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  return (
    <div className="bg-panel-2 border border-line rounded-xl px-3 py-2 text-xs shadow-xl font-mono">
      <div>
        <span className="text-muted">You </span>
        <span className={tone(point.portfolio)}>{pct(point.portfolio)}</span>
      </div>
      <div>
        <span className="text-muted">{symbol} </span>
        <span className={tone(point.benchmark)}>{pct(point.benchmark)}</span>
      </div>
      <div className="text-dim mt-0.5">{new Date(point.at).toLocaleString()}</div>
    </div>
  );
}

function Legend({ color, label, value }) {
  return (
    <div className="min-w-0">
      <div className="flex items-center gap-2 text-xs font-medium text-muted">
        <span aria-hidden="true" className="inline-block w-3 h-0.5 rounded-full" style={{ background: color }} />
        <span className="truncate">{label}</span>
      </div>
      <div className={`font-mono text-xl mt-1 ${tone(value)}`}>{pct(value)}</div>
    </div>
  );
}

function summary(data) {
  if (data.portfolioReturn == null) return "Not enough history to compare yet";
  const yours = `Your portfolio ${pct(data.portfolioReturn)}`;
  if (data.benchmarkReturn == null) return `${yours}; ${data.symbol} has no prices in this window`;
  const gap = data.portfolioReturn - data.benchmarkReturn;
  const verdict = gap === 0 ? "level with" : `${Math.abs(gap).toFixed(2)} points ${gap > 0 ? "ahead of" : "behind"}`;
  return `${yours} against ${data.symbol} ${pct(data.benchmarkReturn)}: ${verdict} it; ${data.points.length} data points`;
}

// Your returns next to a ticker's over the same window. Money you add or take out is not counted
// as a gain or a loss (the server works out a time-weighted return), so the two lines are fair to compare.
export default function BenchmarkChart({ refreshKey }) {
  const [tickers, setTickers] = useState(null);
  const [symbol, setSymbol] = useState(null);
  const [range, setRange] = useState("1M");
  const [data, setData] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let live = true;
    getTickers()
      .then((list) => {
        if (!live) return;
        setTickers(list);
        setSymbol((current) => (current && list.some((t) => t.symbol === current) ? current : pickDefault(list) ?? null));
      })
      .catch(() => live && setFailed(true));
    return () => {
      live = false;
    };
  }, [refreshKey]);

  // A stale answer (an earlier, slower request) is ignored.
  useEffect(() => {
    if (!symbol) return;
    let live = true;
    getBenchmark(symbol, range)
      .then((d) => live && (setData(d), setFailed(false)))
      .catch(() => live && setFailed(true));
    return () => {
      live = false;
    };
  }, [symbol, range, refreshKey]);

  function choose(next) {
    setSymbol(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // storage unavailable: the choice just isn't remembered
    }
  }

  if (failed && !data) return null;
  if (tickers && tickers.length === 0) return null;

  const enough = data && data.points.length >= 2;

  return (
    <section className="bg-panel rounded-[28px] p-5 sm:p-6" aria-labelledby="benchmark-heading">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h2 id="benchmark-heading" className="text-lg font-bold">
          You vs the market
        </h2>
        {tickers && symbol && (
          <label className="flex items-center gap-2 text-[13px] text-muted">
            Compare with
            <select
              value={symbol}
              onChange={(e) => choose(e.target.value)}
              className="h-8 rounded-full bg-panel-2 border border-control px-3 text-bone font-semibold"
            >
              {tickers.map((t) => (
                <option key={t.symbol} value={t.symbol}>
                  {t.symbol}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {!data ? (
        <Skeleton className="h-48 w-full" />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 mb-4">
            <Legend color="var(--c-s1)" label="Your portfolio" value={data.portfolioReturn} />
            <Legend color="var(--c-s2)" label={data.name ? `${data.symbol} · ${data.name}` : data.symbol} value={data.benchmarkReturn} />
          </div>
          {enough ? (
            <div role="img" aria-label={summary(data)}>
              <ResponsiveContainer width="100%" height={180}>
                <LineChart data={data.points} margin={{ top: 5, right: 0, left: 0, bottom: 0 }}>
                  <YAxis domain={["dataMin", "dataMax"]} hide />
                  <ReferenceLine y={0} stroke="var(--c-line)" strokeDasharray="3 3" />
                  <Tooltip content={<ChartTooltip symbol={data.symbol} />} />
                  <Line type="monotone" dataKey="benchmark" stroke="var(--c-s2)" strokeWidth={2} dot={false} connectNulls={false} isAnimationActive={false} />
                  <Line type="monotone" dataKey="portfolio" stroke="var(--c-s1)" strokeWidth={2} dot={false} isAnimationActive={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <div className="text-sm text-dim py-10 text-center">
              Not enough history in this window yet. Your portfolio's value is recorded every minute, so try a longer range or check back soon.
            </div>
          )}
        </>
      )}
      <div className="flex flex-wrap items-center justify-between gap-3 mt-4">
        <Segmented options={RANGES} value={range} onChange={setRange} label="Comparison window" />
        <p className="text-xs text-dim">Deposits and withdrawals don't count as gains.</p>
      </div>
    </section>
  );
}

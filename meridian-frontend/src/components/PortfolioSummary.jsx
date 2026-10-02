import { useEffect, useState } from "react";
import { Area, AreaChart, ResponsiveContainer, Tooltip, YAxis } from "recharts";
import { getPortfolio, getPortfolioHistory } from "../lib/api";
import { summarizeSeries } from "../lib/chartSummary";
import { formatMoney } from "../lib/formatMoney";
import { useAnimatedNumber } from "../lib/useAnimatedNumber";
import Amount from "./Amount";
import HoldingsList from "./HoldingsList";
import Segmented from "./Segmented";
import Skeleton from "./Skeleton";

const RANGES = [
  { key: "1D", label: "1D", words: "today" },
  { key: "1W", label: "1W", words: "past week" },
  { key: "1M", label: "1M", words: "past month" },
  { key: "3M", label: "3M", words: "past 3 months" },
  { key: "1Y", label: "1Y", words: "past year" },
  { key: "ALL", label: "All", words: "all time" },
];

function ChartTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  return (
    <div className="bg-panel-2 border border-line rounded-xl px-3 py-2 text-xs shadow-xl font-mono">
      <div className="text-bone text-sm">{formatMoney(point.totalValue)}</div>
      <div className="text-dim mt-0.5">{new Date(point.recordedAt).toLocaleString()}</div>
    </div>
  );
}

// The top of the Portfolio tab, as an investing app shows it: the total value in large type straight on
// the page, how it has moved over the chosen window, its curve and the window buttons under it; then the
// holdings. Everything but the curve comes from one /portfolio call.
export default function PortfolioSummary({ refreshKey }) {
  const [portfolio, setPortfolio] = useState(null);
  const [loading, setLoading] = useState(true);
  const [range, setRange] = useState("1M");
  const [history, setHistory] = useState(null);

  const animatedTotal = useAnimatedNumber(portfolio?.totalValue ?? 0);

  useEffect(() => {
    setLoading(true);
    getPortfolio()
      .then(setPortfolio)
      .finally(() => setLoading(false));
  }, [refreshKey]);

  useEffect(() => {
    let live = true;
    getPortfolioHistory(range)
      .then((h) => live && setHistory(h))
      .catch(() => live && setHistory([]));
    return () => {
      live = false;
    };
  }, [range, refreshKey]);

  if (loading) {
    return (
      <div className="space-y-5">
        <Skeleton className="h-16 w-56 mx-auto sm:mx-0" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }
  if (!portfolio) return null;

  const enough = history && history.length >= 2;
  const first = enough ? history[0].totalValue : null;
  const change = enough ? portfolio.totalValue - first : null;
  const changePct = enough && first ? (change / first) * 100 : null;
  const isUp = (change ?? 0) >= 0;
  const color = isUp ? "var(--c-gain)" : "var(--c-loss)";
  const words = RANGES.find((r) => r.key === range).words;
  const holdingsPct = portfolio.totalValue > 0 ? Math.round((portfolio.holdingsValue / portfolio.totalValue) * 100) : 0;

  return (
    <div className="space-y-6 fade-in">
      <section aria-label="Portfolio value">
        <div className="text-center sm:text-left">
          <div className="text-[13px] font-medium text-muted">Total value</div>
          <Amount value={animatedTotal} className="block font-display text-[44px] sm:text-[52px] leading-[1.05] mt-1" />
          <div className={`text-sm font-semibold mt-1.5 ${change == null ? "text-muted" : isUp ? "text-gain" : "text-loss"}`}>
            {change == null ? (
              "Building your history — check back soon"
            ) : (
              <>
                {isUp ? "+" : "−"}
                {formatMoney(Math.abs(change))} ({isUp ? "+" : "−"}
                {Math.abs(changePct).toFixed(2)}%) <span className="text-muted font-medium">{words}</span>
              </>
            )}
          </div>
          <dl className="flex justify-center sm:justify-start gap-6 mt-3 text-[13px]">
            <div className="flex gap-1.5">
              <dt className="text-muted">Invested</dt>
              <dd className="font-mono font-semibold">
                {formatMoney(portfolio.holdingsValue)} <span className="text-dim font-sans font-normal">· {holdingsPct}%</span>
              </dd>
            </div>
            <div className="flex gap-1.5">
              <dt className="text-muted">Cash</dt>
              <dd className="font-mono font-semibold">{formatMoney(portfolio.cashBalance)}</dd>
            </div>
          </dl>
          {portfolio.reservedCash > 0 && (
            <p className="text-xs text-dim mt-1">{formatMoney(portfolio.reservedCash)} of the cash is held for open orders</p>
          )}
        </div>

        <div className="mt-5 -mx-1">
          {enough ? (
            <div role="img" aria-label={summarizeSeries("Portfolio value chart", history.map((h) => h.totalValue))}>
              <ResponsiveContainer width="100%" height={200}>
                <AreaChart data={history} margin={{ top: 5, right: 0, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="portfolioFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" style={{ stopColor: color, stopOpacity: 0.22 }} />
                      <stop offset="100%" style={{ stopColor: color, stopOpacity: 0 }} />
                    </linearGradient>
                  </defs>
                  <YAxis domain={["dataMin", "dataMax"]} hide />
                  <Tooltip content={<ChartTooltip />} cursor={{ stroke: "var(--c-dim)", strokeDasharray: "3 3" }} />
                  <Area type="monotone" dataKey="totalValue" stroke={color} strokeWidth={2.25} fill="url(#portfolioFill)" dot={false} isAnimationActive={false} />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <div className="h-[200px] grid place-items-center text-sm text-dim text-center px-6">
              {history ? "Your portfolio's value is recorded every minute. Not enough of it in this window yet." : ""}
            </div>
          )}
        </div>
        <div className="flex justify-center sm:justify-start mt-3">
          <Segmented options={RANGES} value={range} onChange={setRange} label="Chart window" />
        </div>
      </section>

      <section className="bg-panel rounded-[28px] p-5 sm:p-6">
        <div className="flex items-center justify-between mb-2 sm:mb-4">
          <h2 className="text-lg font-bold">Holdings</h2>
          <div className="text-[13px] text-muted">
            {portfolio.holdings.length} {portfolio.holdings.length === 1 ? "position" : "positions"}
          </div>
        </div>

        {portfolio.holdings.length === 0 ? (
          <div className="text-sm text-dim">No holdings yet — place a trade to get started.</div>
        ) : (
          <HoldingsList holdings={portfolio.holdings} />
        )}
      </section>
    </div>
  );
}

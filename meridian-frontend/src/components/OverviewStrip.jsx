import { useEffect, useState } from "react";
import { getPortfolio, getPortfolioHistory } from "../lib/api";
import { formatMoney } from "../lib/formatMoney";
import { useAnimatedNumber } from "../lib/useAnimatedNumber";
import Icon from "./Icon";
import Skeleton from "./Skeleton";
import Sparkline from "./Sparkline";

const STARTING_CASH = 10000;

// The headline card at the top of Overview: total value, change since
// start, an equity sparkline and the cash / invested / available split —
// all from the real portfolio and snapshot endpoints.
export default function OverviewStrip({ refreshKey }) {
  const [portfolio, setPortfolio] = useState(null);
  const [history, setHistory] = useState([]);
  const animatedTotal = useAnimatedNumber(portfolio?.totalValue ?? 0);

  useEffect(() => {
    getPortfolio().then(setPortfolio).catch(() => {});
    getPortfolioHistory().then(setHistory).catch(() => setHistory([]));
  }, [refreshKey]);

  if (!portfolio) return <Skeleton className="h-40 w-full rounded-[20px]" />;

  const sinceStart = portfolio.totalValue - STARTING_CASH;
  const sinceStartPct = ((sinceStart / STARTING_CASH) * 100).toFixed(1);
  const isUp = sinceStart >= 0;
  const values = history.map((h) => h.totalValue);

  const stats = [
    { label: "Cash", value: formatMoney(portfolio.cashBalance) },
    { label: "Invested", value: formatMoney(portfolio.holdingsValue) },
    { label: "Available", value: formatMoney(portfolio.availableCash) },
  ];

  return (
    <section className="bg-panel border border-line rounded-[20px] p-5 sm:p-7 flex flex-col lg:flex-row lg:items-center justify-between gap-6 lg:gap-10 fade-in">
      <div>
        <div className="text-[13px] font-medium text-muted mb-2">Portfolio value</div>
        <div className="font-display text-[44px] sm:text-[52px] leading-none" style={{ letterSpacing: "-0.04em" }}>
          {formatMoney(animatedTotal)}
        </div>
        <div className="flex items-center gap-2.5 mt-3">
          <span
            className={`inline-flex items-center gap-1 whitespace-nowrap font-mono text-xs font-medium px-2.5 py-1 rounded-full ${
              isUp ? "bg-gain-dim text-gain" : "bg-loss-dim text-loss"
            }`}
          >
            <Icon name={isUp ? "up" : "down"} size={12} strokeWidth={2.2} />
            {isUp ? "+" : ""}
            {formatMoney(sinceStart)} ({isUp ? "+" : ""}
            {sinceStartPct}%)
          </span>
          <span className="text-[13px] text-muted whitespace-nowrap">since start</span>
        </div>
      </div>

      {values.length >= 2 && (
        <div className="hidden xl:block shrink-0">
          <Sparkline values={values} positive={isUp} width={300} height={76} />
        </div>
      )}

      <dl className="flex flex-wrap gap-x-8 gap-y-4 sm:gap-x-9">
        {stats.map((s) => (
          <div key={s.label}>
            <dt className="text-xs font-medium text-muted mb-1.5">{s.label}</dt>
            <dd className="font-mono font-medium text-[15px] sm:text-[17px]">{s.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

import { useEffect, useState } from "react";
import { getPortfolio } from "../lib/api";
import { formatMoney } from "../lib/formatMoney";
import { useAnimatedNumber } from "../lib/useAnimatedNumber";
import HoldingsList from "./HoldingsList";
import Skeleton from "./Skeleton";

function StatCard({ label, value, sub, display, className = "" }) {
  return (
    <div className={`bg-panel rounded-[28px] p-4 sm:p-5 min-w-0 transition-all hover:-translate-y-0.5 hover:shadow-lg hover:shadow-black/10 ${className}`}>
      <div className="text-xs font-medium text-muted mb-2.5">{label}</div>
      <div
        className={`leading-none truncate ${display ? "font-display text-[28px]" : "font-mono text-2xl"}`}
        style={display ? { letterSpacing: "-0.04em" } : undefined}
      >
        {value}
      </div>
      {sub && <div className="text-xs text-dim mt-2">{sub}</div>}
    </div>
  );
}

// Stats row + (optional children, e.g. the equity chart) + holdings table.
// Everything comes from one /portfolio call, so `children` is rendered
// between the stats and the table instead of fetching twice.
export default function PortfolioSummary({ refreshKey, children }) {
  const [portfolio, setPortfolio] = useState(null);
  const [loading, setLoading] = useState(true);

  const animatedTotal = useAnimatedNumber(portfolio?.totalValue ?? 0);
  const animatedCash = useAnimatedNumber(portfolio?.cashBalance ?? 0);
  const animatedInvested = useAnimatedNumber(portfolio?.holdingsValue ?? 0);

  useEffect(() => {
    setLoading(true);
    getPortfolio()
      .then(setPortfolio)
      .finally(() => setLoading(false));
  }, [refreshKey]);

  if (loading) {
    return (
      <div className="space-y-5">
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 sm:gap-4">
          <Skeleton className="h-24 col-span-2 sm:col-span-1" />
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }
  if (!portfolio) return null;

  const holdingsPct =
    portfolio.totalValue > 0 ? ((portfolio.holdingsValue / portfolio.totalValue) * 100).toFixed(0) : 0;

  return (
    <div className="space-y-5 fade-in">
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 sm:gap-4">
        <StatCard display className="col-span-2 sm:col-span-1" label="Total value" value={formatMoney(animatedTotal)} />
        <StatCard
          label="Cash balance"
          value={formatMoney(animatedCash)}
          sub={portfolio.reservedCash > 0 ? `${formatMoney(portfolio.reservedCash)} reserved by open orders` : "Fully available"}
        />
        <StatCard label="Invested" value={formatMoney(animatedInvested)} sub={`${holdingsPct}% of portfolio`} />
      </div>

      {children}

      <section className="bg-panel rounded-[28px] p-5 sm:p-6">
        <div className="flex items-center justify-between mb-4">
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

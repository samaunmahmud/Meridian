import { useEffect, useState } from "react";
import { getPortfolio } from "../lib/api";
import { useAnimatedNumber } from "../lib/useAnimatedNumber";
import Skeleton from "./Skeleton";

function StatCard({ label, value, sub, animated }) {
  return (
    <div className="bg-panel border border-line rounded-2xl p-5 flex-1 transition-all hover:border-line hover:-translate-y-0.5 hover:shadow-lg hover:shadow-black/20">
      <div className="text-xs text-dim mb-2">{label}</div>
      <div className="text-2xl font-mono">{animated}</div>
      {sub && <div className="text-xs text-dim mt-1">{sub}</div>}
    </div>
  );
}

export default function PortfolioSummary({ refreshKey }) {
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
        <div className="flex gap-4">
          <Skeleton className="h-24 flex-1" />
          <Skeleton className="h-24 flex-1" />
          <Skeleton className="h-24 flex-1" />
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
      <div className="flex gap-4">
        <StatCard label="Total value" animated={`$${animatedTotal.toFixed(2)}`} />
        <StatCard label="Cash balance" animated={`$${animatedCash.toFixed(2)}`} />
        <StatCard
          label="Invested"
          animated={`$${animatedInvested.toFixed(2)}`}
          sub={`${holdingsPct}% of portfolio`}
        />
      </div>

      <section className="bg-panel border border-line rounded-2xl p-6">
        <div className="text-sm font-medium mb-4">Holdings</div>
        {portfolio.holdings.length === 0 ? (
          <div className="text-sm text-dim">No holdings yet — place a trade to get started.</div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-dim border-b border-line">
                <th className="font-normal pb-3">Symbol</th>
                <th className="font-normal pb-3 text-right">Qty</th>
                <th className="font-normal pb-3 text-right">Avg cost</th>
                <th className="font-normal pb-3 text-right">Price</th>
                <th className="font-normal pb-3 text-right">Value</th>
                <th className="font-normal pb-3 text-right">Gain/Loss</th>
              </tr>
            </thead>
            <tbody className="font-mono">
              {portfolio.holdings.map((h) => {
                const isUp = h.gainLoss >= 0;
                return (
                  <tr key={h.symbol} className="border-b border-line/60 last:border-0">
                    <td className="py-3 font-sans font-medium">{h.symbol}</td>
                    <td className="py-3 text-right">{h.quantity}</td>
                    <td className="py-3 text-right text-muted">{h.avgCost.toFixed(2)}</td>
                    <td className="py-3 text-right">{h.currentPrice.toFixed(2)}</td>
                    <td className="py-3 text-right">{h.marketValue.toFixed(2)}</td>
                    <td className={`py-3 text-right ${isUp ? "text-gain" : "text-loss"}`}>
                      {isUp ? "+" : ""}{h.gainLoss.toFixed(2)} ({isUp ? "+" : ""}{h.gainLossPct.toFixed(2)}%)
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}

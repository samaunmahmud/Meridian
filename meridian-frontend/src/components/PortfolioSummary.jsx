import { useEffect, useState } from "react";
import { getPortfolio } from "../lib/api";
import { formatMoney, formatNumber } from "../lib/formatMoney";
import { useAnimatedNumber } from "../lib/useAnimatedNumber";
import Icon from "./Icon";
import Skeleton from "./Skeleton";
import TickerAvatar from "./TickerAvatar";

function StatCard({ label, value, sub, display, className = "" }) {
  return (
    <div className={`bg-panel border border-line rounded-[20px] p-4 sm:p-5 min-w-0 transition-all hover:-translate-y-0.5 hover:shadow-lg hover:shadow-black/10 ${className}`}>
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

      <section className="bg-panel border border-line rounded-[20px] p-5 sm:p-6">
        <div className="flex items-center justify-between mb-4">
          <div className="text-base font-semibold">Holdings</div>
          <div className="text-[13px] text-muted">
            {portfolio.holdings.length} {portfolio.holdings.length === 1 ? "position" : "positions"}
          </div>
        </div>

        {portfolio.holdings.length === 0 ? (
          <div className="text-sm text-dim">No holdings yet — place a trade to get started.</div>
        ) : (
          <div className="overflow-x-auto no-scrollbar -mx-1 px-1">
            <table className="w-full text-sm min-w-[560px]">
              <thead>
                <tr className="text-left text-dim text-xs">
                  <th className="font-medium pb-3">Asset</th>
                  <th className="font-medium pb-3 text-right">Qty</th>
                  <th className="font-medium pb-3 text-right">Avg cost</th>
                  <th className="font-medium pb-3 text-right">Price</th>
                  <th className="font-medium pb-3 text-right">Value</th>
                  <th className="font-medium pb-3 text-right">P&amp;L</th>
                </tr>
              </thead>
              <tbody className="font-mono">
                {portfolio.holdings.map((h) => {
                  const isUp = h.gainLoss >= 0;
                  return (
                    <tr key={h.symbol} className="border-t border-line/70">
                      <td className="py-3.5 font-sans">
                        <div className="flex items-center gap-3">
                          <TickerAvatar symbol={h.symbol} size={36} />
                          <div className="min-w-0">
                            <div className="font-semibold text-sm">{h.symbol}</div>
                            <div className="text-xs text-muted truncate max-w-[160px]">{h.name}</div>
                          </div>
                        </div>
                      </td>
                      <td className="py-3.5 text-right">{h.quantity}</td>
                      <td className="py-3.5 text-right text-muted">{formatNumber(h.avgCost)}</td>
                      <td className="py-3.5 text-right">{formatNumber(h.currentPrice)}</td>
                      <td className="py-3.5 text-right font-medium">{formatNumber(h.marketValue)}</td>
                      <td className={`py-3.5 text-right ${isUp ? "text-gain" : "text-loss"}`}>
                        <div className="font-medium inline-flex items-center gap-1 justify-end">
                          <Icon name={isUp ? "up" : "down"} size={12} strokeWidth={2.2} />
                          {isUp ? "+" : ""}
                          {formatNumber(h.gainLoss)}
                        </div>
                        <div className="text-xs">
                          {isUp ? "+" : ""}
                          {h.gainLossPct.toFixed(2)}%
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

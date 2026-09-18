import { useEffect, useState } from "react";
import { getPortfolio } from "../lib/api";
import TickerAvatar from "./TickerAvatar";

const SEGMENT_COLORS = ["#7c6fff", "#22c55e", "#eab308", "#06b6d4", "#ec4899", "#f97316"];

export default function PortfolioAllocation({ refreshKey }) {
  const [portfolio, setPortfolio] = useState(null);

  useEffect(() => {
    getPortfolio().then(setPortfolio).catch(() => {});
  }, [refreshKey]);

  if (!portfolio || portfolio.totalValue <= 0) return null;

  const segments = [
    { label: "Cash", value: portfolio.cashBalance, color: "#3a4150" },
    ...portfolio.holdings.map((h, i) => ({
      label: h.symbol,
      value: h.marketValue,
      color: SEGMENT_COLORS[i % SEGMENT_COLORS.length],
    })),
  ];

  return (
    <section className="bg-panel border border-line rounded-2xl p-6">
      <div className="text-sm font-medium mb-4">Allocation</div>

      <div className="flex h-2.5 rounded-full overflow-hidden mb-4">
        {segments.map((s, i) => (
          <div
            key={i}
            style={{ width: `${(s.value / portfolio.totalValue) * 100}%`, backgroundColor: s.color }}
            className="transition-all duration-500"
          />
        ))}
      </div>

      <div className="space-y-2.5">
        {segments.map((s, i) => (
          <div key={i} className="flex items-center justify-between text-sm">
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: s.color }} />
              <span className="text-muted">{s.label}</span>
            </div>
            <div className="flex items-center gap-2 font-mono text-xs">
              <span className="text-dim">{((s.value / portfolio.totalValue) * 100).toFixed(1)}%</span>
              <span>${s.value.toFixed(2)}</span>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

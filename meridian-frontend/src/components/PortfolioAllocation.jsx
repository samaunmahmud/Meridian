import { useEffect, useState } from "react";
import { getPortfolio } from "../lib/api";
import { formatMoney } from "../lib/formatMoney";

const SIZE = 150;
const STROKE = 18;
const RADIUS = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;
const GAP = 2; // px of empty arc between segments

export default function PortfolioAllocation({ refreshKey }) {
  const [portfolio, setPortfolio] = useState(null);

  useEffect(() => {
    getPortfolio().then(setPortfolio).catch(() => {});
  }, [refreshKey]);

  if (!portfolio || portfolio.totalValue <= 0) return null;

  const segments = [
    ...portfolio.holdings.map((h, i) => ({
      label: h.symbol,
      value: h.marketValue,
      color: `var(--c-s${(i % 8) + 1})`,
    })),
    { label: "Cash", value: portfolio.cashBalance, color: "var(--c-cash)" },
  ].filter((s) => s.value > 0);

  let offset = 0;

  return (
    <section className="bg-panel rounded-[28px] p-5 sm:p-6">
      <h2 className="text-lg font-bold mb-5">Allocation</h2>

      <div className="flex justify-center mb-5">
        <div className="relative" style={{ width: SIZE, height: SIZE }}>
          <svg width={SIZE} height={SIZE} viewBox={`0 0 ${SIZE} ${SIZE}`} role="img" aria-label="Portfolio allocation">
            <g transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}>
              {segments.map((s) => {
                const length = (s.value / portfolio.totalValue) * CIRCUMFERENCE;
                const visible = Math.max(length - GAP, 0);
                const circle = (
                  <circle
                    key={s.label}
                    cx={SIZE / 2}
                    cy={SIZE / 2}
                    r={RADIUS}
                    fill="none"
                    stroke={s.color}
                    strokeWidth={STROKE}
                    strokeDasharray={`${visible} ${CIRCUMFERENCE - visible}`}
                    strokeDashoffset={-offset}
                    className="transition-all duration-500"
                  />
                );
                offset += length;
                return circle;
              })}
            </g>
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <div className="font-display text-[32px] leading-none" style={{ letterSpacing: "-0.04em" }}>
              {portfolio.holdings.length}
            </div>
            <div className="text-xs text-muted mt-1">{portfolio.holdings.length === 1 ? "position" : "positions"}</div>
          </div>
        </div>
      </div>

      <div className="space-y-2.5">
        {segments.map((s) => (
          <div key={s.label} className="flex items-center justify-between text-sm gap-3">
            <div className="flex items-center gap-2.5 min-w-0">
              <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: s.color }} />
              <span className="font-medium truncate">{s.label}</span>
            </div>
            <div className="flex items-center gap-3 font-mono text-[13px] shrink-0">
              <span className="text-muted">{formatMoney(s.value)}</span>
              <span className="text-dim w-12 text-right">{((s.value / portfolio.totalValue) * 100).toFixed(1)}%</span>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

import { useEffect, useState } from "react";
import { AreaChart, Area, ResponsiveContainer, YAxis, Tooltip } from "recharts";
import { getPortfolioHistory } from "../lib/api";
import { summarizeSeries } from "../lib/chartSummary";
import Skeleton from "./Skeleton";

function ChartTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  return (
    <div className="bg-panel-2 border border-line rounded-xl px-3 py-2 text-xs shadow-xl font-mono">
      <div className="text-bone text-sm">${point.totalValue.toFixed(2)}</div>
      <div className="text-dim mt-0.5">{new Date(point.recordedAt).toLocaleString()}</div>
    </div>
  );
}

export default function EquityChart({ refreshKey }) {
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    getPortfolioHistory()
      .then(setHistory)
      .finally(() => setLoading(false));
  }, [refreshKey]);

  if (loading) {
    return (
      <section className="bg-panel rounded-[28px] p-6">
        <Skeleton className="h-4 w-32 mb-4" />
        <Skeleton className="h-40 w-full" />
      </section>
    );
  }

  if (history.length < 2) {
    return (
      <section className="bg-panel rounded-[28px] p-6">
        <h2 className="text-lg font-bold mb-2">Portfolio performance</h2>
        <div className="text-sm text-dim py-10 text-center">
          Building your equity curve — check back in a minute or two as more snapshots are recorded.
        </div>
      </section>
    );
  }

  const first = history[0].totalValue;
  const last = history[history.length - 1].totalValue;
  const isUp = last >= first;
  const lineColor = isUp ? "var(--c-gain)" : "var(--c-loss)";

  return (
    <section className="bg-panel rounded-[28px] p-6 fade-in">
      <h2 className="text-lg font-bold mb-4">Portfolio performance</h2>
      <div role="img" aria-label={summarizeSeries("Portfolio value chart", history.map((h) => h.totalValue))}>
      <ResponsiveContainer width="100%" height={180}>
        <AreaChart data={history} margin={{ top: 5, right: 0, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="equityFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" style={{ stopColor: lineColor, stopOpacity: 0.28 }} />
              <stop offset="100%" style={{ stopColor: lineColor, stopOpacity: 0 }} />
            </linearGradient>
          </defs>
          <YAxis domain={["dataMin", "dataMax"]} hide />
          <Tooltip content={<ChartTooltip />} />
          <Area
            type="monotone"
            dataKey="totalValue"
            stroke={lineColor}
            strokeWidth={2}
            fill="url(#equityFill)"
          />
        </AreaChart>
      </ResponsiveContainer>
      </div>
    </section>
  );
}

import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, YAxis } from "recharts";
import { formatNumber } from "../lib/formatMoney";

function ChartTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  return (
    <div className="bg-panel-2 border border-line rounded-lg px-3 py-2 text-xs shadow-xl font-mono">
      <div className="text-bone text-sm">${formatNumber(point.price)}</div>
      <div className="text-dim mt-0.5">{new Date(point.recordedAt).toLocaleString()}</div>
    </div>
  );
}

// Smooth area chart of the price history — the default view. Colour follows
// the direction of the visible window (gain / loss theme colours).
export default function PriceChart({ points, positive, height = 260 }) {
  if (!points || points.length < 2) {
    return <div className="text-sm text-dim py-16 text-center">Not enough data yet for a chart.</div>;
  }

  const color = positive ? "var(--c-gain)" : "var(--c-loss)";

  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={points} margin={{ top: 10, right: 2, left: 2, bottom: 0 }}>
        <defs>
          <linearGradient id="priceFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" style={{ stopColor: color, stopOpacity: 0.28 }} />
            <stop offset="100%" style={{ stopColor: color, stopOpacity: 0 }} />
          </linearGradient>
        </defs>
        <CartesianGrid stroke="var(--c-line)" vertical={false} opacity={0.7} />
        <YAxis domain={["dataMin", "dataMax"]} hide />
        <Tooltip content={<ChartTooltip />} cursor={{ stroke: "var(--c-dim)", strokeDasharray: "3 3" }} />
        <Area
          type="monotone"
          dataKey="price"
          stroke={color}
          strokeWidth={2.25}
          fill="url(#priceFill)"
          dot={false}
          isAnimationActive={false}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}

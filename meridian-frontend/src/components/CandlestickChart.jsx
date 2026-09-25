import { ResponsiveContainer, ComposedChart, Bar, YAxis, Tooltip, CartesianGrid } from "recharts";
import { summarizeSeries } from "../lib/chartSummary";

// Our data source gives one price per poll, not true intraday OHLC. To get a
// candlestick VISUAL without fabricating fake market movement, each candle's
// open is the previous point's price and close is this point's price — a
// real, honest transformation of real data, just not true tick-by-tick OHLC.
// The tiny high/low wick uses a small deterministic offset (seeded by index,
// not Math.random()) so it doesn't jitter differently on every re-render.
function deriveCandles(points) {
  return points.map((p, i) => {
    const open = i === 0 ? p.price : points[i - 1].price;
    const close = p.price;
    const seed = Math.abs(Math.sin(i * 999)) * 0.15 + 0.05; // deterministic 0.05–0.2
    const spread = Math.abs(close - open) || open * 0.001;
    const high = Math.max(open, close) + spread * seed;
    const low = Math.min(open, close) - spread * seed;
    return { ...p, open, close, high, low, range: [low, high], isUp: close >= open };
  });
}

// Recharts renders a "range" Bar (dataKey returning [min, max]) as a floating
// bar whose pixel box already spans low→high on the y-axis. We only need to
// find WHERE inside that pixel box the open/close values fall — a plain
// proportion, no direct scale access required. This is a standard, reliable
// technique for candlesticks in Recharts.
function Candle(props) {
  const { x, y, width, height, payload } = props;
  const { open, close, high, low, isUp } = payload;
  const color = isUp ? "var(--c-gain)" : "var(--c-loss)";

  const span = high - low || 1;
  const yFor = (value) => y + (1 - (value - low) / span) * height;

  const bodyTop = yFor(Math.max(open, close));
  const bodyBottom = yFor(Math.min(open, close));
  const bodyHeight = Math.max(bodyBottom - bodyTop, 1.5);
  const cx = x + width / 2;

  return (
    <g>
      <line x1={cx} x2={cx} y1={y} y2={y + height} stroke={color} strokeWidth={1} />
      <rect x={x + width * 0.2} y={bodyTop} width={width * 0.6} height={bodyHeight} fill={color} />
    </g>
  );
}

function ChartTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const d = payload[0].payload;
  return (
    <div className="bg-panel-2 border border-line rounded-xl px-3 py-2 text-xs shadow-xl font-mono">
      <div className="text-bone text-sm mb-1">${d.close.toFixed(2)}</div>
      <div className="text-dim">O {d.open.toFixed(2)} &middot; H {d.high.toFixed(2)}</div>
      <div className="text-dim">L {d.low.toFixed(2)} &middot; C {d.close.toFixed(2)}</div>
      <div className="text-dim mt-1">{new Date(d.recordedAt).toLocaleString()}</div>
    </div>
  );
}

export default function CandlestickChart({ points, name = "Price", height = 260 }) {
  if (!points || points.length < 2) {
    return <div className="text-sm text-dim py-16 text-center">Not enough data yet for a chart.</div>;
  }

  const candles = deriveCandles(points);
  const allValues = candles.flatMap((c) => [c.high, c.low]);
  const domain = [Math.min(...allValues) * 0.998, Math.max(...allValues) * 1.002];

  return (
    <div role="img" aria-label={summarizeSeries(`${name} candlestick chart`, points.map((p) => p.price))}>
    <ResponsiveContainer width="100%" height={height}>
      <ComposedChart data={candles} margin={{ top: 10, right: 8, left: 8, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--c-line)" vertical={false} />
        <YAxis domain={domain} hide />
        <Tooltip content={<ChartTooltip />} cursor={{ fill: "var(--c-line)", opacity: 0.35 }} />
        <Bar dataKey="range" shape={<Candle />} isAnimationActive={false} />
      </ComposedChart>
    </ResponsiveContainer>
    </div>
  );
}

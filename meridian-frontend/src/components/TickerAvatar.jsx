// Generates a consistent color per symbol (same symbol always gets the same
// color) purely from a hash of its characters — no external logo images,
// which avoids any trademark/copyright issue with real company logos.
const PALETTE = [
  "#7c6fff", "#22c55e", "#f0455a", "#eab308",
  "#06b6d4", "#ec4899", "#84cc16", "#f97316",
];

function colorFor(symbol) {
  let hash = 0;
  for (let i = 0; i < symbol.length; i++) hash = symbol.charCodeAt(i) + ((hash << 5) - hash);
  return PALETTE[Math.abs(hash) % PALETTE.length];
}

export default function TickerAvatar({ symbol, size = 32 }) {
  const color = colorFor(symbol);
  return (
    <div
      className="rounded-full flex items-center justify-center font-semibold shrink-0"
      style={{
        width: size,
        height: size,
        backgroundColor: `${color}22`,
        color,
        fontSize: size * 0.38,
      }}
    >
      {symbol.slice(0, 2)}
    </div>
  );
}

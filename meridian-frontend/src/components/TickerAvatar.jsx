// Same symbol always gets the same colour (hash of its characters) — no
// external logo images, which avoids any trademark issue with real company
// logos. Colours come from the theme's categorical palette (--c-s1…--c-s8)
// so they stay legible in both dark and light mode.
const SLOTS = 8;

function slotFor(symbol) {
  let hash = 0;
  for (let i = 0; i < symbol.length; i++) hash = symbol.charCodeAt(i) + ((hash << 5) - hash);
  return (Math.abs(hash) % SLOTS) + 1;
}

export default function TickerAvatar({ symbol, size = 32 }) {
  const color = `var(--c-s${slotFor(symbol)})`;
  return (
    <div
      className="rounded-full flex items-center justify-center font-semibold shrink-0 select-none"
      style={{
        width: size,
        height: size,
        backgroundColor: `color-mix(in srgb, ${color} 16%, transparent)`,
        color,
        fontSize: size * 0.4,
      }}
      aria-hidden="true"
    >
      {symbol.slice(0, 1)}
    </div>
  );
}

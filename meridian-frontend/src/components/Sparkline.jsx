// Renders a tiny line chart from an array of numbers. No library needed —
// just maps values onto an SVG viewBox. Used in the watchlist so each row
// shows real recent movement, not a placeholder icon.
export default function Sparkline({ values, positive, width = 64, height = 24 }) {
  if (!values || values.length < 2) {
    return <svg width={width} height={height} />;
  }

  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;

  const points = values.map((v, i) => {
    const x = (i / (values.length - 1)) * width;
    const y = height - ((v - min) / range) * height;
    return `${x},${y}`;
  });

  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`}>
      <polyline
        points={points.join(" ")}
        fill="none"
        stroke={positive ? "var(--color-gain)" : "var(--color-loss)"}
        strokeWidth="1.75"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

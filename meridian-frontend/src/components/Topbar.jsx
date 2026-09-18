const INDICES = [
  { name: "S&P 500", value: "5,842.11", delta: "+0.62%", up: true },
  { name: "Dow Jones", value: "41,203.4", delta: "+0.28%", up: true },
  { name: "Nasdaq", value: "18,412.9", delta: "+0.94%", up: true },
  { name: "FTSE 100", value: "8,204.6", delta: "-0.15%", up: false },
];

export default function Topbar() {
  return (
    <div className="flex px-8 py-4 border-b border-line overflow-x-auto">
      {INDICES.map((idx, i) => (
        <div key={i} className={`px-5 first:pl-0 ${i > 0 ? "border-l border-line" : ""}`}>
          <div className="text-xs text-dim mb-1">{idx.name}</div>
          <div className="flex items-baseline gap-2">
            <span className="text-sm font-mono">{idx.value}</span>
            <span className={`text-xs font-mono ${idx.up ? "text-gain" : "text-loss"}`}>{idx.delta}</span>
          </div>
        </div>
      ))}
    </div>
  );
}

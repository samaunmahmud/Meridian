// A row of pill buttons where one is chosen (chart ranges, chart types).
export default function Segmented({ options, value, onChange, label }) {
  return (
    <div role="group" aria-label={label} className="flex gap-1 text-[13px]">
      {options.map((o) => (
        <button
          key={o.key}
          type="button"
          aria-pressed={value === o.key}
          onClick={() => onChange(o.key)}
          className={`h-8 px-3.5 rounded-full font-semibold transition-colors ${
            value === o.key ? "bg-bone text-ink" : "text-muted hover:text-bone hover:bg-panel-2"
          }`}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

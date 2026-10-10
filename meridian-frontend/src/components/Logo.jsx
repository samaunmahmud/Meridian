import { useId } from "react";

// The Meridian mark: an M whose last stroke climbs like a rising chart and ends in an arrow, white on a
// blue-to-violet rounded tile. The tile carries its own colours, so it reads the same in both themes.
export function LogoMark({ size = 28, className = "" }) {
  const gradient = useId();
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" className={className} aria-hidden="true">
      <defs>
        <linearGradient id={gradient} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#3d5afe" />
          <stop offset="1" stopColor="#7c3aed" />
        </linearGradient>
      </defs>
      <rect width="64" height="64" rx="16" fill={`url(#${gradient})`} />
      <g fill="none" stroke="#fff" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round">
        <path d="M16 46V20l15 16 17-17" />
        <path d="M38 19h10v10" />
      </g>
    </svg>
  );
}

// Mark + wordmark, set in the bold display cut of the interface font.
export default function Logo({ size = 24 }) {
  return (
    <div className="flex items-center text-bone select-none" style={{ gap: size * 0.4 }}>
      <LogoMark size={Math.round(size * 1.2)} />
      <span className="font-display font-extrabold leading-none" style={{ fontSize: size, letterSpacing: "-0.035em" }}>
        Meridian
      </span>
    </div>
  );
}

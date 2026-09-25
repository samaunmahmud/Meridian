// The Meridian mark: a globe with a single meridian line. Stroke-only so it
// inherits colour (accent) in both themes.
export function LogoMark({ size = 28, className = "" }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 32 32"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      className={className}
      aria-hidden="true"
    >
      <circle cx="16" cy="16" r="14" />
      <ellipse cx="16" cy="16" rx="6.2" ry="14" />
      <path d="M2 16h28" opacity="0.55" />
    </svg>
  );
}

// Mark + wordmark, set in the bold display cut of the interface font.
export default function Logo({ size = 24 }) {
  return (
    <div className="flex items-center text-bone select-none" style={{ gap: size * 0.36 }}>
      <LogoMark size={Math.round(size * 1.05)} className="text-accent" />
      <span
        className="font-display font-extrabold leading-none"
        style={{ fontSize: size, letterSpacing: "-0.035em" }}
      >
        Meridian
      </span>
    </div>
  );
}

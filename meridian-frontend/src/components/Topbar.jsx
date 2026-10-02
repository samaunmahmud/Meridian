import Icon from "./Icon";
import ThemeToggle from "./ThemeToggle";

// The page header, laid out like a banking app's: your round profile avatar (opens Settings) or a back
// button on the left, a wide search pill in the middle, and the theme switch on the right. Pages that
// open with their own large heading (Home's balance, a stock's name and price) keep the title for screen
// readers only; the other tabs show it in large bold type below the bar.
// Mac keyboards show ⌘ for the search shortcut, everything else Ctrl.
const SHORTCUT = typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.platform ?? "") ? "⌘K" : "Ctrl K";

export default function Topbar({ title, hideTitle = false, userEmail, onSettings, settingsActive, onSearch, onBack }) {
  return (
    <header className="px-4 sm:px-6 lg:px-8 pt-4 pb-3 lg:pt-7 lg:pb-5 max-w-[1240px]">
      <div className="flex items-center gap-3">
        {onBack ? (
          <BackButton onBack={onBack} />
        ) : (
          <button
            type="button"
            onClick={onSettings}
            aria-label="Settings and profile"
            aria-current={settingsActive ? "page" : undefined}
            className={`lg:hidden w-10 h-10 rounded-full shrink-0 flex items-center justify-center text-sm font-semibold transition-shadow ${
              settingsActive ? "bg-bone text-ink" : "bg-accent text-accent-ink"
            }`}
          >
            {(userEmail?.[0] ?? "?").toUpperCase()}
          </button>
        )}

        <button
          type="button"
          onClick={onSearch}
          aria-label="Search"
          aria-keyshortcuts="Meta+K Control+K /"
          className="flex-1 lg:flex-none lg:w-80 h-10 pl-3.5 pr-2 rounded-full bg-panel text-muted flex items-center gap-2 hover:text-bone transition-colors lg:order-2 lg:ml-auto"
        >
          <Icon name="search" size={17} />
          <span className="text-sm">Search</span>
          <kbd className="hidden sm:inline ml-auto font-sans text-[11px] px-1.5 py-0.5 rounded-md bg-panel-2 text-dim">{SHORTCUT}</kbd>
        </button>

        <div className="lg:order-3 shrink-0">
          <ThemeToggle />
        </div>

        <h1
          className={
            hideTitle
              ? "sr-only"
              : "hidden lg:block lg:order-1 font-display text-[34px] leading-tight truncate min-w-0"
          }
          style={hideTitle ? undefined : { letterSpacing: "-0.03em" }}
        >
          {title}
        </h1>
      </div>

      {!hideTitle && (
        // The same heading where phones have room for it; only one of the two is ever displayed.
        <h1 className="lg:hidden font-display text-[30px] leading-tight truncate mt-4" style={{ letterSpacing: "-0.03em" }}>
          {title}
        </h1>
      )}
    </header>
  );
}

function BackButton({ onBack }) {
  return (
    <button
      type="button"
      onClick={onBack}
      aria-label="Back"
      className="w-10 h-10 rounded-full bg-panel shrink-0 flex items-center justify-center hover:bg-panel-2 transition-colors"
    >
      <Icon name="back" size={20} strokeWidth={2.2} />
    </button>
  );
}

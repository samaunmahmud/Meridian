import Icon from "./Icon";
import Logo from "./Logo";
import ThemeToggle from "./ThemeToggle";

// The page header. A tab shows its title in large bold type; a stock's page shows a round
// back button instead of the logo on phones and next to the title on wide screens.
// Phones have no sidebar, so the logo and log-out live here too.
export default function Topbar({ title, onLogout, onBack }) {
  return (
    <header className="px-4 sm:px-6 lg:px-8 pt-4 pb-3 lg:pt-7 lg:pb-5 flex flex-wrap lg:flex-nowrap items-center justify-between gap-y-4">
      <div className="lg:hidden order-1">
        {onBack ? (
          <BackButton onBack={onBack} />
        ) : (
          <Logo size={22} />
        )}
      </div>

      <div className="order-2 lg:order-3 flex items-center gap-2">
        <ThemeToggle />
        <button
          type="button"
          onClick={onLogout}
          aria-label="Log out"
          className="lg:hidden w-10 h-10 rounded-full bg-panel text-muted flex items-center justify-center hover:text-bone transition-colors"
        >
          <Icon name="logout" size={17} />
        </button>
      </div>

      <div className="order-3 lg:order-1 w-full lg:w-auto flex items-center gap-3 min-w-0">
        {onBack && (
          <div className="hidden lg:block">
            <BackButton onBack={onBack} />
          </div>
        )}
        <h1 className="font-display text-[30px] lg:text-[34px] leading-tight truncate" style={{ letterSpacing: "-0.03em" }}>
          {title}
        </h1>
      </div>
    </header>
  );
}

function BackButton({ onBack }) {
  return (
    <button
      type="button"
      onClick={onBack}
      aria-label="Back"
      className="w-10 h-10 rounded-full bg-panel flex items-center justify-center hover:bg-panel-2 transition-colors"
    >
      <Icon name="back" size={20} strokeWidth={2.2} />
    </button>
  );
}

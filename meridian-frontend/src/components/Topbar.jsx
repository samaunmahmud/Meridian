import Icon from "./Icon";
import Logo from "./Logo";
import ThemeToggle from "./ThemeToggle";

// Desktop: page title (Fraunces) on the left, theme toggle on the right.
// Mobile: the sidebar is hidden, so the wordmark, theme toggle and log-out
// live here and the page title drops onto its own row underneath.
export default function Topbar({ title, onLogout }) {
  return (
    <header className="px-4 sm:px-6 lg:px-8 pt-3 pb-3 lg:py-0 lg:h-[72px] flex flex-wrap lg:flex-nowrap items-center justify-between gap-y-3">
      <div className="lg:hidden order-1">
        <Logo size={22} />
      </div>

      <div className="order-2 lg:order-3 flex items-center gap-2">
        <ThemeToggle />
        <button
          type="button"
          onClick={onLogout}
          aria-label="Log out"
          className="lg:hidden w-10 h-10 rounded-xl bg-panel border border-line text-muted flex items-center justify-center hover:text-bone transition-colors"
        >
          <Icon name="logout" size={17} />
        </button>
      </div>

      <h1
        className="order-3 lg:order-1 w-full lg:w-auto font-display text-[28px] lg:text-[30px] font-normal leading-none"
        style={{ letterSpacing: "-0.03em" }}
      >
        {title}
      </h1>
    </header>
  );
}

import { motion } from "framer-motion";
import AccountSummary from "./AccountSummary";
import Icon from "./Icon";
import Logo from "./Logo";

export const NAV_ITEMS = [
  { key: "overview", label: "Overview", icon: "dash" },
  { key: "portfolio", label: "Portfolio", icon: "pie" },
  { key: "alerts", label: "Alerts", icon: "bell" },
  { key: "accounts", label: "Accounts", icon: "wallet" },
  { key: "activity", label: "Activity", icon: "pulse" },
];

export default function Sidebar({ activeTab, onTabChange, userEmail, onLogout, refreshKey }) {
  return (
    <aside className="hidden lg:flex w-[248px] shrink-0 bg-panel border-r border-line flex-col h-screen sticky top-0 px-4 pt-6 pb-5 gap-6">
      <div className="px-2">
        <Logo size={24} />
      </div>

      <AccountSummary refreshKey={refreshKey} />

      <nav className="flex flex-col gap-1" aria-label="Main">
        {NAV_ITEMS.map((item) => {
          const active = activeTab === item.key;
          return (
            <button
              key={item.key}
              onClick={() => onTabChange(item.key)}
              aria-current={active ? "page" : undefined}
              className={`relative h-11 w-full flex items-center gap-3 px-3 rounded-xl text-sm transition-colors ${
                active ? "text-accent font-semibold" : "text-muted font-medium hover:bg-panel-2 hover:text-bone"
              }`}
            >
              {active && (
                <motion.div
                  layoutId="sidebar-active-pill"
                  className="absolute inset-0 bg-accent-dim rounded-xl"
                  transition={{ type: "spring", stiffness: 500, damping: 40 }}
                />
              )}
              <span className="relative z-10 flex items-center gap-3">
                <Icon name={item.icon} size={18} />
                {item.label}
              </span>
            </button>
          );
        })}
      </nav>

      <div className="flex-1" />

      <div className="flex items-center gap-2.5 px-1 pt-1">
        <div className="w-[34px] h-[34px] rounded-full bg-accent-dim text-accent flex items-center justify-center text-sm font-semibold shrink-0">
          {(userEmail?.[0] ?? "?").toUpperCase()}
        </div>
        <div className="flex-1 min-w-0 text-xs text-muted truncate" title={userEmail}>
          {userEmail}
        </div>
        <button
          onClick={onLogout}
          aria-label="Log out"
          className="w-8 h-8 rounded-lg flex items-center justify-center text-dim hover:text-bone hover:bg-panel-2 transition-colors"
        >
          <Icon name="logout" size={16} />
        </button>
      </div>
    </aside>
  );
}

// Below the lg breakpoint the sidebar is replaced by a bottom tab bar.
export function MobileNav({ activeTab, onTabChange }) {
  return (
    <nav
      aria-label="Main"
      className="lg:hidden fixed bottom-0 inset-x-0 z-30 bg-panel border-t border-line grid grid-cols-5 pt-2 pb-[max(env(safe-area-inset-bottom),10px)]"
    >
      {NAV_ITEMS.map((item) => {
        const active = activeTab === item.key;
        return (
          <button
            key={item.key}
            onClick={() => onTabChange(item.key)}
            aria-current={active ? "page" : undefined}
            className={`flex flex-col items-center gap-1 py-1 transition-colors ${
              active ? "text-accent" : "text-dim"
            }`}
          >
            <Icon name={item.icon} size={22} strokeWidth={active ? 2 : 1.8} />
            <span className={`text-[10.5px] ${active ? "font-semibold" : "font-medium"}`}>{item.label}</span>
          </button>
        );
      })}
    </nav>
  );
}

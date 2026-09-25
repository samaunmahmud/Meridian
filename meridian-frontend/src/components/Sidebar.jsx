import { motion } from "framer-motion";
import Icon from "./Icon";
import Logo from "./Logo";

export const NAV_ITEMS = [
  { key: "home", label: "Home", icon: "home" },
  { key: "portfolio", label: "Portfolio", icon: "pie" },
  { key: "alerts", label: "Alerts", icon: "bell" },
  { key: "accounts", label: "Accounts", icon: "wallet" },
  { key: "activity", label: "Activity", icon: "pulse" },
];

export default function Sidebar({ activeTab, onTabChange, userEmail, onLogout }) {
  return (
    <aside className="hidden lg:flex w-[240px] shrink-0 bg-ink flex-col h-screen sticky top-0 px-4 pt-7 pb-5 gap-8">
      <div className="px-3">
        <Logo size={24} />
      </div>

      <nav className="flex flex-col gap-1" aria-label="Main">
        {NAV_ITEMS.map((item) => {
          const active = activeTab === item.key;
          return (
            <button
              key={item.key}
              onClick={() => onTabChange(item.key)}
              aria-current={active ? "page" : undefined}
              className={`relative h-12 w-full flex items-center gap-3 px-4 rounded-full text-[15px] transition-colors ${
                active ? "text-bone font-semibold" : "text-muted font-medium hover:bg-panel hover:text-bone"
              }`}
            >
              {active && (
                <motion.div
                  layoutId="sidebar-active-pill"
                  className="absolute inset-0 bg-panel-2 rounded-full"
                  transition={{ type: "spring", stiffness: 500, damping: 40 }}
                />
              )}
              <span className="relative z-10 flex items-center gap-3">
                <Icon name={item.icon} size={20} strokeWidth={active ? 2.1 : 1.8} />
                {item.label}
              </span>
            </button>
          );
        })}
      </nav>

      <div className="flex-1" />

      <div className="flex items-center gap-2.5 px-1 pt-1">
        <div className="w-9 h-9 rounded-full bg-accent text-accent-ink flex items-center justify-center text-sm font-semibold shrink-0">
          {(userEmail?.[0] ?? "?").toUpperCase()}
        </div>
        <div className="flex-1 min-w-0 text-xs text-muted truncate" title={userEmail}>
          {userEmail}
        </div>
        <button
          onClick={onLogout}
          aria-label="Log out"
          className="w-9 h-9 rounded-full flex items-center justify-center text-dim hover:text-bone hover:bg-panel-2 transition-colors"
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
      className="lg:hidden fixed bottom-0 inset-x-0 z-30 bg-panel/90 backdrop-blur-xl border-t border-line grid grid-cols-5 pt-2 pb-[max(env(safe-area-inset-bottom),10px)]"
    >
      {NAV_ITEMS.map((item) => {
        const active = activeTab === item.key;
        return (
          <button
            key={item.key}
            onClick={() => onTabChange(item.key)}
            aria-current={active ? "page" : undefined}
            className={`flex flex-col items-center gap-1 py-1 transition-colors ${
              active ? "text-bone" : "text-dim"
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

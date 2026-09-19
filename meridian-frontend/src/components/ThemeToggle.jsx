import Icon from "./Icon";
import { useTheme } from "../lib/useTheme";

export default function ThemeToggle() {
  const { theme, setTheme } = useTheme();

  return (
    <div
      role="group"
      aria-label="Colour theme"
      className="flex items-center gap-0.5 p-1 rounded-xl bg-panel-2"
    >
      {[
        { key: "light", icon: "sun", label: "Light theme" },
        { key: "dark", icon: "moon", label: "Dark theme" },
      ].map((opt) => {
        const active = theme === opt.key;
        return (
          <button
            key={opt.key}
            type="button"
            aria-label={opt.label}
            aria-pressed={active}
            onClick={() => setTheme(opt.key)}
            className={`w-8 h-8 rounded-[9px] flex items-center justify-center transition-colors ${
              active ? "bg-accent-dim text-accent" : "text-dim hover:text-bone"
            }`}
          >
            <Icon name={opt.icon} size={16} />
          </button>
        );
      })}
    </div>
  );
}

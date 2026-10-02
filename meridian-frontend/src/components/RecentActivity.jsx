import { useEffect, useState } from "react";
import { getTransactions } from "../lib/api";
import { formatMoney } from "../lib/formatMoney";
import { KINDS } from "./ActivityFeed";
import Icon from "./Icon";
import Skeleton from "./Skeleton";

const SHOWN = 3;

function when(iso) {
  const d = new Date(iso);
  const sameDay = d.toDateString() === new Date().toDateString();
  return sameDay
    ? d.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })
    : d.toLocaleDateString([], { month: "short", day: "numeric" });
}

// The latest few transactions right under the balance, as a banking app's home screen shows them,
// with a way to the full Activity tab.
export default function RecentActivity({ refreshKey, onSeeAll }) {
  const [transactions, setTransactions] = useState(null);

  useEffect(() => {
    let live = true;
    getTransactions()
      .then((t) => live && setTransactions(t))
      .catch(() => live && setTransactions([]));
    return () => {
      live = false;
    };
  }, [refreshKey]);

  if (transactions && transactions.length === 0) return null;

  return (
    <section className="bg-panel rounded-[28px] px-5 sm:px-6 pt-2 pb-2" aria-labelledby="recent-activity-heading">
      <h2 id="recent-activity-heading" className="sr-only">
        Recent activity
      </h2>
      {!transactions ? (
        <div className="py-4 space-y-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      ) : (
        <ul>
          {transactions.slice(0, SHOWN).map((t) => {
            const kind = KINDS[t.type] ?? { icon: "pulse", tone: "bg-panel-2 text-muted" };
            const positive = t.amount >= 0;
            return (
              <li key={t.id} className="flex items-center gap-3.5 py-3">
                <div className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 ${kind.tone}`}>
                  <Icon name={kind.icon} size={17} strokeWidth={2} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium truncate">{t.description ?? t.type}</div>
                  <div className="text-xs text-muted">{when(t.createdAt)}</div>
                </div>
                <div className={`font-mono font-medium text-sm shrink-0 ${positive ? "text-gain" : ""}`}>
                  {positive ? "+" : ""}
                  {formatMoney(t.amount, t.currency ?? "USD")}
                </div>
              </li>
            );
          })}
        </ul>
      )}
      <button
        type="button"
        onClick={onSeeAll}
        aria-label="See all activity"
        className="w-full h-11 border-t border-line text-sm font-semibold text-accent hover:text-bone transition-colors"
      >
        See all
      </button>
    </section>
  );
}

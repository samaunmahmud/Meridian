import { useEffect, useState } from "react";
import { getTransactions } from "../lib/api";
import { formatMoney } from "../lib/formatMoney";
import Skeleton from "./Skeleton";

const ICONS = {
  DEPOSIT: "↓",
  WITHDRAWAL: "↑",
  BUY: "+",
  SELL: "−",
  FEE: "%",
  CONVERSION: "⇄",
};

export default function ActivityFeed({ refreshKey }) {
  const [transactions, setTransactions] = useState(null);

  useEffect(() => {
    getTransactions().then(setTransactions).catch(() => setTransactions([]));
  }, [refreshKey]);

  return (
    <main className="p-8 max-w-3xl fade-in">
      <div className="text-lg font-semibold mb-6">Activity</div>

      <section className="bg-panel border border-line rounded-2xl p-2">
        {!transactions ? (
          <div className="p-4 space-y-3">
            {[1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : transactions.length === 0 ? (
          <div className="text-sm text-dim p-6">No activity yet.</div>
        ) : (
          transactions.map((t, i) => {
            const positive = t.amount >= 0;
            const currency = t.currency ?? "USD";
            return (
              <div
                key={t.id}
                className={`flex items-center justify-between px-4 py-3.5 ${i > 0 ? "border-t border-line/60" : ""}`}
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-9 h-9 rounded-full bg-panel-2 flex items-center justify-center text-sm text-muted shrink-0">
                    {ICONS[t.type] ?? "•"}
                  </div>
                  <div className="min-w-0">
                    <div className="text-sm font-medium truncate">{t.description ?? t.type}</div>
                    <div className="text-xs text-dim">{new Date(t.createdAt).toLocaleString()}</div>
                  </div>
                </div>
                <div className={`text-sm font-mono shrink-0 ${positive ? "text-gain" : "text-loss"}`}>
                  {positive ? "+" : ""}
                  {formatMoney(t.amount, currency)}
                </div>
              </div>
            );
          })
        )}
      </section>
    </main>
  );
}

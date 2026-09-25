import { useEffect, useMemo, useState } from "react";
import { getTransactions } from "../lib/api";
import { formatMoney } from "../lib/formatMoney";
import Icon from "./Icon";
import Skeleton from "./Skeleton";

// How each transaction type looks: icon + tint of its badge.
const KINDS = {
  DEPOSIT: { icon: "plus", tone: "bg-gain-dim text-gain" },
  WITHDRAWAL: { icon: "up", tone: "bg-loss-dim text-loss" },
  BUY: { icon: "up", tone: "bg-loss-dim text-loss" },
  SELL: { icon: "down", tone: "bg-gain-dim text-gain" },
  FEE: { icon: "percent", tone: "bg-panel-2 text-muted" },
  CONVERSION: { icon: "swap", tone: "bg-accent-dim text-accent" },
};

const FILTERS = [
  { key: "ALL", label: "All", types: null },
  { key: "TRADES", label: "Trades", types: ["BUY", "SELL"] },
  { key: "DEPOSITS", label: "Deposits", types: ["DEPOSIT"] },
  { key: "WITHDRAWALS", label: "Withdrawals", types: ["WITHDRAWAL"] },
  { key: "CONVERSIONS", label: "Conversions", types: ["CONVERSION"] },
  { key: "FEES", label: "Fees", types: ["FEE"] },
];

function dayLabel(iso) {
  const d = new Date(iso);
  const startOfDay = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const diffDays = Math.round((startOfDay(new Date()) - startOfDay(d)) / 86400000);
  if (diffDays === 0) return "Today";
  if (diffDays === 1) return "Yesterday";
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric", year: d.getFullYear() === new Date().getFullYear() ? undefined : "numeric" });
}

function exportCsv(transactions) {
  const header = ["Date", "Type", "Description", "Amount", "Currency", "Balance after"];
  const escape = (v) => `"${String(v ?? "").replace(/"/g, '""')}"`;
  const rows = transactions.map((t) =>
    [new Date(t.createdAt).toISOString(), t.type, t.description, t.amount, t.currency ?? "USD", t.balanceAfter]
      .map(escape)
      .join(",")
  );
  const blob = new Blob([[header.join(","), ...rows].join("\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "meridian-activity.csv";
  a.click();
  URL.revokeObjectURL(url);
}

function MonthSummary({ transactions }) {
  const now = new Date();
  const monthly = transactions.filter((t) => {
    const d = new Date(t.createdAt);
    return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear() && (t.currency ?? "USD") === "USD";
  });
  const sum = (types, fn = (x) => x) => monthly.filter((t) => types.includes(t.type)).reduce((a, t) => a + fn(t.amount), 0);

  const rows = [
    { label: "Deposits", value: `+${formatMoney(sum(["DEPOSIT"]))}`, tone: "text-gain" },
    { label: "Withdrawals", value: `−${formatMoney(sum(["WITHDRAWAL"], Math.abs))}`, tone: "" },
    { label: "Trading volume", value: formatMoney(sum(["BUY", "SELL"], Math.abs)), tone: "" },
    { label: "Fees paid", value: formatMoney(sum(["FEE"], Math.abs)), tone: "text-loss" },
  ];

  return (
    <section className="bg-panel rounded-[28px] px-5 pt-5 pb-2">
      <div className="flex items-center justify-between mb-2">
        <h2 className="text-lg font-bold">This month</h2>
        <div className="text-xs text-muted">{now.toLocaleString("en-US", { month: "long" })} · USD</div>
      </div>
      {rows.map((r, i) => (
        <div key={r.label} className={`flex items-center justify-between h-12 ${i > 0 ? "border-t border-line/70" : ""}`}>
          <span className="text-[13px] text-muted">{r.label}</span>
          <span className={`font-mono font-medium text-sm ${r.tone}`}>{r.value}</span>
        </div>
      ))}
    </section>
  );
}

export default function ActivityFeed({ refreshKey }) {
  const [transactions, setTransactions] = useState(null);
  const [filter, setFilter] = useState("ALL");

  useEffect(() => {
    getTransactions().then(setTransactions).catch(() => setTransactions([]));
  }, [refreshKey]);

  const groups = useMemo(() => {
    if (!transactions) return [];
    const types = FILTERS.find((f) => f.key === filter).types;
    const visible = types ? transactions.filter((t) => types.includes(t.type)) : transactions;
    const map = new Map();
    for (const t of visible) {
      const label = dayLabel(t.createdAt);
      if (!map.has(label)) map.set(label, []);
      map.get(label).push(t);
    }
    return [...map.entries()];
  }, [transactions, filter]);

  return (
    <main id="main-content" tabIndex={-1} data-ring-parent className="px-4 sm:px-6 lg:px-8 pb-8 max-w-[1240px] fade-in">
      <div className="flex gap-2 overflow-x-auto no-scrollbar pb-1 mb-5" role="group" aria-label="Filter activity">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            type="button"
            aria-pressed={filter === f.key}
            onClick={() => setFilter(f.key)}
            className={`shrink-0 px-4 py-2 rounded-full text-[13px] font-medium border transition-colors ${
              filter === f.key
                ? "bg-accent-dim text-accent border-transparent"
                : "bg-panel text-muted border-line hover:text-bone"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_340px] gap-6 items-start">
        <section className="bg-panel rounded-[28px] px-5 sm:px-6 pb-3">
          {!transactions ? (
            <div className="py-5 space-y-3">
              {[1, 2, 3, 4].map((i) => (
                <Skeleton key={i} className="h-14 w-full" />
              ))}
            </div>
          ) : groups.length === 0 ? (
            <div className="text-sm text-dim py-8">No activity to show.</div>
          ) : (
            groups.map(([label, items]) => (
              <div key={label}>
                <div className="text-xs font-semibold text-dim pt-5 pb-2">{label}</div>
                {items.map((t, i) => {
                  const kind = KINDS[t.type] ?? { icon: "pulse", tone: "bg-panel-2 text-muted" };
                  const positive = t.amount >= 0;
                  const currency = t.currency ?? "USD";
                  return (
                    <div
                      key={t.id}
                      className={`flex items-center gap-3.5 py-3.5 ${i > 0 ? "border-t border-line/70" : ""}`}
                    >
                      <div className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 ${kind.tone}`}>
                        <Icon name={kind.icon} size={17} strokeWidth={2} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="text-sm font-medium truncate">{t.description ?? t.type}</div>
                        <div className="text-xs text-muted">
                          {new Date(t.createdAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}
                        </div>
                      </div>
                      <div className="text-right shrink-0">
                        <div className={`font-mono font-medium text-sm ${positive ? "text-gain" : ""}`}>
                          {positive ? "+" : ""}
                          {formatMoney(t.amount, currency)}
                        </div>
                        {t.balanceAfter != null && (
                          <div className="font-mono text-[11px] text-dim">Bal {formatMoney(t.balanceAfter, currency)}</div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            ))
          )}
        </section>

        {transactions && (
          <div className="space-y-4">
            <MonthSummary transactions={transactions} />
            <button
              type="button"
              disabled={transactions.length === 0}
              onClick={() => exportCsv(transactions)}
              className="w-full h-12 rounded-full bg-panel text-sm font-medium flex items-center justify-center gap-2 hover:bg-panel-2 transition-colors disabled:opacity-40"
            >
              <Icon name="download" size={16} />
              Export CSV
            </button>
          </div>
        )}
      </div>
    </main>
  );
}

import { useEffect, useId, useState } from "react";
import { getRebalancePlan, getTickers, setTargets } from "../lib/api";
import { formatMoney } from "../lib/formatMoney";
import { showToast } from "../lib/toast";
import Skeleton from "./Skeleton";
import TickerAvatar from "./TickerAvatar";

function pct(value) {
  return `${Number(value).toLocaleString("en-US", { maximumFractionDigits: 2 })}%`;
}

function formatQuantity(q) {
  return Number(q).toLocaleString("en-US", { maximumFractionDigits: 4 });
}

// Current share as a filled bar, the target as a tick on the same track.
function Bar({ current, target }) {
  return (
    <div className="relative h-1.5 rounded-full bg-panel-2 mt-2" aria-hidden="true">
      <div className="absolute inset-y-0 left-0 rounded-full bg-accent" style={{ width: `${Math.min(100, current)}%` }} />
      <div className="absolute -top-1 -bottom-1 w-0.5 rounded-full bg-bone" style={{ left: `calc(${Math.min(100, target)}% - 1px)` }} />
    </div>
  );
}

function PlanRow({ row, onTrade }) {
  const { trade } = row;
  const words = trade && `${trade.type === "BUY" ? "Buy" : "Sell"} ${formatQuantity(trade.quantity)} ${row.symbol}`;
  return (
    <li className="py-3">
      <div className="flex items-center gap-3">
        <TickerAvatar symbol={row.symbol} size={36} />
        <div className="min-w-0 flex-1">
          <div className="text-sm font-semibold">{row.symbol}</div>
          <div className="text-xs text-muted">
            {pct(row.currentPercent)} now · target {pct(row.targetPercent)}
          </div>
        </div>
        {trade ? (
          <button
            type="button"
            onClick={() => onTrade?.(row.symbol, trade.type, trade.quantity)}
            aria-label={`${words}, about ${formatMoney(trade.estimatedValue)}`}
            className={`shrink-0 h-9 px-3.5 rounded-full text-[13px] font-semibold ${
              trade.type === "BUY" ? "bg-gain-dim text-gain" : "bg-loss-dim text-loss"
            } hover:brightness-110`}
          >
            {trade.type === "BUY" ? "Buy" : "Sell"} {formatQuantity(trade.quantity)}
            <span className="font-normal opacity-80"> · {formatMoney(trade.estimatedValue)}</span>
          </button>
        ) : (
          <span className="shrink-0 text-xs text-dim">On target</span>
        )}
      </div>
      <Bar current={row.currentPercent} target={row.targetPercent} />
    </li>
  );
}

// Starting values for the editor: the saved targets, or (the first time) today's weights rounded to whole %.
function draftFrom(plan) {
  return plan.rows.map((r) => ({
    symbol: r.symbol,
    percent: String(plan.hasTargets ? Number(r.targetPercent) : Math.round(Number(r.currentPercent))),
  }));
}

function Editor({ plan, onSaved, onCancel }) {
  const uid = useId();
  const [draft, setDraft] = useState(() => draftFrom(plan));
  const [tickers, setTickers] = useState([]);
  const [adding, setAdding] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getTickers().then(setTickers).catch(() => {});
  }, []);

  const total = draft.reduce((sum, d) => sum + (Number(d.percent) || 0), 0);
  const over = total > 100.0001;
  const addable = tickers.filter((t) => !draft.some((d) => d.symbol === t.symbol));

  function update(symbol, percent) {
    setDraft((rows) => rows.map((d) => (d.symbol === symbol ? { ...d, percent } : d)));
  }

  function useCurrent() {
    setDraft(plan.rows.map((r) => ({ symbol: r.symbol, percent: String(Math.round(Number(r.currentPercent))) })));
  }

  function add() {
    const symbol = adding || addable[0]?.symbol;
    if (!symbol) return;
    setDraft((rows) => [...rows, { symbol, percent: "0" }]);
    setAdding("");
  }

  function save(e) {
    e.preventDefault();
    setSaving(true);
    setTargets(draft.map((d) => ({ symbol: d.symbol, percent: Number(d.percent) || 0 })))
      .then((next) => {
        showToast("success", next.hasTargets ? "Targets saved" : "Targets cleared");
        onSaved(next);
      })
      .catch((err) => showToast("error", err.message))
      .finally(() => setSaving(false));
  }

  return (
    <form onSubmit={save}>
      <ul className="divide-y divide-line">
        {draft.map((d) => (
          <li key={d.symbol} className="flex items-center gap-3 py-2.5">
            <TickerAvatar symbol={d.symbol} size={32} />
            <label htmlFor={`${uid}-${d.symbol}`} className="flex-1 text-sm font-semibold">
              {d.symbol}
              <span className="sr-only"> target (%)</span>
            </label>
            <div className="flex items-center gap-1.5">
              <input
                id={`${uid}-${d.symbol}`}
                type="number"
                inputMode="decimal"
                min="0"
                max="100"
                step="0.01"
                value={d.percent}
                onChange={(e) => update(d.symbol, e.target.value)}
                className="w-24 h-9 rounded-xl bg-panel-2 border border-control px-3 text-right font-mono text-sm"
              />
              <span className="text-sm text-muted" aria-hidden="true">%</span>
            </div>
          </li>
        ))}
      </ul>

      {addable.length > 0 && (
        <div className="flex items-center gap-2 mt-3">
          <label htmlFor={`${uid}-add`} className="sr-only">Stock to add</label>
          <select
            id={`${uid}-add`}
            value={adding || addable[0].symbol}
            onChange={(e) => setAdding(e.target.value)}
            className="h-9 rounded-full bg-panel-2 border border-control px-3 text-sm font-semibold"
          >
            {addable.map((t) => (
              <option key={t.symbol} value={t.symbol}>
                {t.symbol}
              </option>
            ))}
          </select>
          <button type="button" onClick={add} className="h-9 px-4 rounded-full text-sm font-semibold bg-panel-2 hover:brightness-110">
            Add stock
          </button>
        </div>
      )}

      <p className={`text-sm mt-4 ${over ? "text-loss" : "text-muted"}`} role="status">
        Stocks {pct(Math.round(total * 100) / 100)} · Cash {pct(Math.max(0, Math.round((100 - total) * 100) / 100))}
        {over && " — targets can add up to at most 100%"}
      </p>

      <div className="flex flex-wrap items-center gap-2 mt-4">
        <button
          type="submit"
          disabled={saving || over}
          className="h-10 px-5 rounded-full bg-accent text-accent-ink text-sm font-semibold disabled:opacity-50"
        >
          {saving ? "Saving…" : "Save targets"}
        </button>
        <button type="button" onClick={useCurrent} className="h-10 px-4 rounded-full text-sm font-semibold bg-panel-2 hover:brightness-110">
          Use current weights
        </button>
        <button type="button" onClick={onCancel} className="h-10 px-4 rounded-full text-sm font-semibold text-muted hover:text-bone">
          Cancel
        </button>
      </div>
    </form>
  );
}

// Target allocation: what share of the portfolio each stock should be, how far it has drifted, and one-tap
// trades that bring it back (the trade form opens filled in; nothing happens until the order is placed).
export default function RebalancePanel({ refreshKey, onTrade }) {
  const [plan, setPlan] = useState(null);
  const [failed, setFailed] = useState(false);
  const [editing, setEditing] = useState(false);

  useEffect(() => {
    let live = true;
    getRebalancePlan()
      .then((p) => live && (setPlan(p), setFailed(false)))
      .catch(() => live && setFailed(true));
    return () => {
      live = false;
    };
  }, [refreshKey]);

  if (failed && !plan) return null;

  const trades = plan?.rows.filter((r) => r.trade) ?? [];
  const sells = trades.filter((r) => r.trade.type === "SELL").length;
  const buys = trades.length - sells;

  return (
    <section className="bg-panel rounded-[28px] p-5 sm:p-6" aria-labelledby="rebalance-heading">
      <div className="flex items-center justify-between gap-3 mb-2">
        <h2 id="rebalance-heading" className="text-lg font-bold">
          Target allocation
        </h2>
        {plan && !editing && (plan.hasTargets || plan.rows.length > 0) && (
          <button
            type="button"
            onClick={() => setEditing(true)}
            className="h-8 px-3.5 rounded-full text-[13px] font-semibold bg-panel-2 hover:brightness-110"
          >
            {plan.hasTargets ? "Edit targets" : "Set targets"}
          </button>
        )}
      </div>

      {!plan ? (
        <Skeleton className="h-32 w-full" />
      ) : editing ? (
        <Editor
          plan={plan}
          onSaved={(next) => {
            setPlan(next);
            setEditing(false);
          }}
          onCancel={() => setEditing(false)}
        />
      ) : !plan.hasTargets ? (
        <p className="text-sm text-muted py-2">
          {plan.rows.length > 0
            ? "Decide what share of your portfolio each stock should be, and Meridian shows the trades that keep you there."
            : "Buy a stock to start, then set what share of your portfolio it should be."}
        </p>
      ) : (
        <>
          <p className="text-sm text-muted">
            {trades.length === 0
              ? "Everything is within 1 point of its target."
              : `${trades.length} ${trades.length === 1 ? "trade" : "trades"} would bring you back to target${
                  sells && buys ? ": sell first, so the buys have the cash." : "."
                }`}
          </p>
          <ul className="divide-y divide-line mt-1">
            {plan.rows.map((r) => (
              <PlanRow key={r.symbol} row={r} onTrade={onTrade} />
            ))}
            <li className="py-3">
              <div className="flex items-center gap-3">
                <span className="w-9 h-9 rounded-full bg-panel-2 grid place-items-center text-xs font-bold text-muted" aria-hidden="true">
                  $
                </span>
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-semibold">Cash</div>
                  <div className="text-xs text-muted">
                    {pct(plan.cash.currentPercent)} now · target {pct(plan.cash.targetPercent)}
                  </div>
                </div>
                <span className="shrink-0 font-mono text-sm">{formatMoney(plan.cash.value)}</span>
              </div>
              <Bar current={plan.cash.currentPercent} target={plan.cash.targetPercent} />
            </li>
          </ul>
          <p className="text-xs text-dim mt-3">
            Of your USD balance and holdings ({formatMoney(plan.totalValue)}); EUR and GBP wallets are not included. Buys
            leave room for the commission.
          </p>
        </>
      )}
    </section>
  );
}

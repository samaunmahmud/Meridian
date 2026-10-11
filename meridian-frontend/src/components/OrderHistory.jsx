import { useEffect, useState } from "react";
import { getOrders, cancelOrder } from "../lib/api";
import { formatNumber } from "../lib/formatMoney";
import { showToast } from "../lib/toast";
import Skeleton from "./Skeleton";
import Icon from "./Icon";
import TickerAvatar from "./TickerAvatar";
import EditOrderModal from "./EditOrderModal";
import OrderNoteModal from "./OrderNoteModal";

const ACTION = "inline-flex items-center justify-center w-7 h-7 rounded-full hover:bg-panel-2 transition-colors align-middle";

// A pending order has no fill price yet: show the price it waits for instead.
function PendingPrice({ order }) {
  if (order.status !== "PENDING") return "—";
  if (order.kind === "MARKET") return <span className="font-sans text-xs text-dim">at open</span>;
  const price = order.kind === "LIMIT" ? order.limitPrice : order.stopPrice;
  if (price == null) return "—";
  const label = order.kind === "LIMIT" ? "Limit" : order.kind === "TRAILING_STOP" ? "Trailing stop" : "Stop";
  return (
    <span className="text-dim" title={`${label} price`}>
      @ {price.toFixed(2)}
    </span>
  );
}

const STATUS_WORDS = { FILLED: "Filled", PENDING: "Pending", CANCELLED: "Cancelled", REJECTED: "Rejected" };
const STATUS_TONES = { PENDING: "text-accent", REJECTED: "text-loss" };

// Like a bank's transaction list: the latest few, the rest one tap away.
const SHOWN_AT_FIRST = 10;

const when = (o) => new Date(o.executedAt ?? o.createdAt);
const signedPnl = (pnl) => `${pnl >= 0 ? "+" : ""}${pnl.toFixed(2)}`;

// "Today", "Yesterday", then "7 Oct" (with the year once it isn't this year), as a bank groups transactions.
function dayLabel(date, now = new Date()) {
  const startOf = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const days = Math.round((startOf(now) - startOf(date)) / 86400000);
  if (days === 0) return "Today";
  if (days === 1) return "Yesterday";
  return date.toLocaleDateString([], { day: "numeric", month: "short", ...(date.getFullYear() !== now.getFullYear() && { year: "numeric" }) });
}

function groupByDay(orders) {
  const groups = [];
  for (const o of [...orders].sort((a, b) => when(b) - when(a))) {
    const label = dayLabel(when(o));
    if (groups.at(-1)?.label !== label) groups.push({ label, orders: [] });
    groups.at(-1).orders.push(o);
  }
  return groups;
}

function OrderActions({ order: o, onNote, onEdit, onCancel }) {
  return (
    <>
      <button
        onClick={() => onNote(o)}
        aria-label={`${o.note ? "Edit note on" : "Add a note to"} ${o.type.toLowerCase()} order: ${o.symbol}`}
        title={o.note ? "Edit note" : "Add a note"}
        className={`${ACTION} ${o.note ? "text-muted" : "text-dim"} hover:text-bone`}
      >
        <Icon name="note" size={16} />
      </button>
      {o.status === "PENDING" && o.kind !== "MARKET" && (
        <button
          onClick={() => onEdit(o)}
          aria-label={`Edit pending ${o.type.toLowerCase()} order: ${o.symbol}`}
          title="Edit order"
          className={`${ACTION} text-dim hover:text-bone`}
        >
          <Icon name="pencil" size={16} />
        </button>
      )}
      {o.status === "PENDING" && (
        <button
          onClick={() => onCancel(o.id)}
          aria-label={`Cancel pending ${o.type.toLowerCase()} order: ${o.symbol}`}
          title="Cancel order"
          className={`${ACTION} text-dim hover:text-loss`}
        >
          <Icon name="x" size={16} />
        </button>
      )}
    </>
  );
}

// One row per order, grouped by day like a bank's transaction list: what happened on the left, the money it
// moved on the right (spent on a buy, received on a sell), the time, status, fee and note underneath.
function OrderRow({ order: o, actions }) {
  const filled = o.status === "FILLED" && o.price != null;
  const amount = filled ? o.price * o.quantity : null;
  return (
    <li className="flex items-center gap-3.5 py-3">
      <TickerAvatar symbol={o.symbol} size={40} />
      <div className="flex-1 min-w-0">
        <div className="text-[15px] font-semibold truncate">
          <span className={o.type === "BUY" ? "text-gain" : "text-loss"}>{o.type === "BUY" ? "Buy" : "Sell"}</span> {o.symbol}
        </div>
        <div className="text-[13px] text-muted">
          {when(o).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })} &middot;{" "}
          <span className={STATUS_TONES[o.status] ?? ""} title={o.rejectionReason ?? undefined}>
            {STATUS_WORDS[o.status] ?? o.status}
          </span>
          {o.feeAmount != null && <> &middot; <span className="whitespace-nowrap">fee {o.feeAmount.toFixed(2)}</span></>}
        </div>
        {o.note && (
          <div className="text-[13px] text-dim truncate" title={o.note}>
            {o.note}
          </div>
        )}
      </div>
      <div className="text-right font-mono shrink-0">
        <div className={`text-[15px] font-semibold ${o.status === "CANCELLED" || o.status === "REJECTED" ? "text-dim line-through" : ""}`}>
          {amount != null ? `${o.type === "BUY" ? "−" : "+"}$${formatNumber(amount)}` : <PendingPrice order={o} />}
        </div>
        <div className="text-[13px] text-muted">
          {o.quantity} {o.price != null && <>&times; {o.price.toFixed(2)}</>}
        </div>
        {o.realizedPnL != null && (
          <div className={`text-[13px] ${o.realizedPnL >= 0 ? "text-gain" : "text-loss"}`}>P/L {signedPnl(o.realizedPnL)}</div>
        )}
      </div>
      <div className="flex flex-wrap justify-end w-14 shrink-0">{actions(o)}</div>
    </li>
  );
}

export default function OrderHistory({ refreshKey, onChanged }) {
  const [orders, setOrders] = useState([]);
  const [editing, setEditing] = useState(null);
  const [noting, setNoting] = useState(null);
  const [loading, setLoading] = useState(true);
  const [showAll, setShowAll] = useState(false);

  function load() {
    setLoading(true);
    getOrders()
      .then(setOrders)
      .finally(() => setLoading(false));
  }

  useEffect(load, [refreshKey]);

  async function handleCancel(id) {
    try {
      await cancelOrder(id);
      load();
    } catch (err) {
      showToast("error", err.message);
    }
  }

  const actions = (o) => <OrderActions order={o} onNote={setNoting} onEdit={setEditing} onCancel={handleCancel} />;

  if (loading) {
    return (
      <section className="bg-panel rounded-[28px] p-6 space-y-3">
        <Skeleton className="h-4 w-28" />
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-8 w-full" />
        ))}
      </section>
    );
  }

  return (
    <section className="bg-panel rounded-[28px] p-6 fade-in">
      <h2 className="text-lg font-bold mb-2">Order history</h2>

      {orders.length === 0 ? (
        <div className="text-sm text-dim">No orders placed yet.</div>
      ) : (
        groupByDay(showAll ? orders : [...orders].sort((a, b) => when(b) - when(a)).slice(0, SHOWN_AT_FIRST)).map((g) => (
          <div key={g.label}>
            <h3 className="text-[13px] font-semibold text-muted pt-4 pb-1">{g.label}</h3>
            <ul aria-label={`Orders, ${g.label}`} className="divide-y divide-line">
              {g.orders.map((o) => (
                <OrderRow key={o.id} order={o} actions={actions} />
              ))}
            </ul>
          </div>
        ))
      )}
      {orders.length > SHOWN_AT_FIRST && (
        <button
          type="button"
          onClick={() => setShowAll((v) => !v)}
          className="w-full mt-3 pt-3 border-t border-line text-sm font-semibold text-accent hover:brightness-110"
        >
          {showAll ? "Show fewer" : `See all ${orders.length} orders`}
        </button>
      )}
      {noting && (
        <OrderNoteModal
          order={noting}
          onClose={() => setNoting(null)}
          onSaved={(updated) => {
            setNoting(null);
            setOrders((list) => list.map((o) => (o.id === updated.id ? updated : o)));
          }}
        />
      )}
      {editing && (
        <EditOrderModal
          order={editing}
          onClose={() => setEditing(null)}
          onReplaced={() => {
            setEditing(null);
            load();
            onChanged?.();
          }}
        />
      )}
    </section>
  );
}

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { getOrders, cancelOrder } from "../lib/api";
import { showToast } from "../lib/toast";
import Skeleton from "./Skeleton";
import Icon from "./Icon";
import EditOrderModal from "./EditOrderModal";
import OrderNoteModal from "./OrderNoteModal";

const STATUS_STYLES = {
  FILLED: "text-muted bg-panel-2",
  PENDING: "text-accent bg-accent-dim",
  CANCELLED: "text-dim bg-panel-2",
  REJECTED: "text-loss bg-loss-dim",
};

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

const shortDate = (o) => new Date(o.executedAt ?? o.createdAt).toLocaleDateString([], { day: "numeric", month: "short" });
const signedPnl = (pnl) => `${pnl >= 0 ? "+" : ""}${pnl.toFixed(2)}`;

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

// The narrow-card layout: one order per row, details stacked under the symbol and the price.
function OrderList({ orders, actions }) {
  return (
    <ul aria-label="Orders" className="text-sm">
      {orders.map((o) => (
        <li key={o.id} className="flex items-start gap-3 py-3 border-b border-line/60 last:border-0">
          <div className="flex-1 min-w-0">
            <div className="font-medium">
              <span className={o.type === "BUY" ? "text-gain" : "text-loss"}>{o.type === "BUY" ? "Buy" : "Sell"}</span> {o.symbol}
            </div>
            <div className="text-xs text-dim">
              {shortDate(o)} &middot;{" "}
              <span className={STATUS_TONES[o.status] ?? ""} title={o.rejectionReason ?? undefined}>
                {STATUS_WORDS[o.status] ?? o.status}
              </span>
              {o.feeAmount != null && <> &middot; <span className="whitespace-nowrap">fee {o.feeAmount.toFixed(2)}</span></>}
            </div>
            {o.note && (
              <div className="text-xs text-muted truncate" title={o.note}>
                {o.note}
              </div>
            )}
          </div>
          <div className="text-right font-mono">
            <div>{o.price != null ? o.price.toFixed(2) : <PendingPrice order={o} />}</div>
            <div className="text-xs text-dim">&times; {o.quantity}</div>
            {o.realizedPnL != null && (
              <div className={`text-xs ${o.realizedPnL >= 0 ? "text-gain" : "text-loss"}`}>P/L {signedPnl(o.realizedPnL)}</div>
            )}
          </div>
          <div className="flex flex-wrap justify-end w-14 shrink-0">{actions(o)}</div>
        </li>
      ))}
    </ul>
  );
}

// Width the card's content box has, followed as the window resizes (null where it can't be measured).
function useWidth(ref) {
  const [width, setWidth] = useState(null);
  useEffect(() => {
    if (!ref.current || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    observer.observe(ref.current);
    return () => observer.disconnect();
  });
  return width;
}

export default function OrderHistory({ refreshKey, onChanged }) {
  const [orders, setOrders] = useState([]);
  const [editing, setEditing] = useState(null);
  const [noting, setNoting] = useState(null);
  const [loading, setLoading] = useState(true);

  // The table when it fits the card, else the stacked list. The table is measured before paint; the
  // width it needed is kept so a card that grows back to it shows the table again.
  const boxRef = useRef(null);
  const tableRef = useRef(null);
  const tableWidth = useRef(0);
  const [compact, setCompact] = useState(false);
  const width = useWidth(boxRef);
  useLayoutEffect(() => {
    tableWidth.current = 0;
    setCompact(false);
  }, [orders]);
  useLayoutEffect(() => {
    const box = boxRef.current;
    if (!box) return;
    if (!compact && tableRef.current && tableRef.current.scrollWidth > box.clientWidth) {
      tableWidth.current = tableRef.current.scrollWidth;
      setCompact(true);
    } else if (compact && box.clientWidth >= tableWidth.current) {
      setCompact(false);
    }
  }, [compact, width, orders]);

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
      <h2 className="text-lg font-bold mb-4">Order history</h2>

      {orders.length === 0 ? (
        <div className="text-sm text-dim">No orders placed yet.</div>
      ) : (
        <div ref={boxRef}>
        {compact ? (
          <OrderList orders={orders} actions={actions} />
        ) : (
        /* relative: the sr-only header text is absolutely positioned and must be clipped by this box */
        <div role="region" aria-label="Order history table" tabIndex={0} className="relative overflow-x-auto">
        <table ref={tableRef} className="w-full text-sm [&_th]:px-1.5 [&_td]:px-1.5 [&_th]:whitespace-nowrap [&_td]:whitespace-nowrap [&_th:first-child]:pl-0 [&_td:first-child]:pl-0 [&_th:last-child]:pr-0 [&_td:last-child]:pr-0">
          <thead>
            <tr className="text-left text-dim border-b border-line">
              <th scope="col" className="font-normal pb-3">Type</th>
              <th scope="col" className="font-normal pb-3">Symbol</th>
              <th scope="col" className="font-normal pb-3">Status</th>
              <th scope="col" className="font-normal pb-3 text-right">Qty</th>
              <th scope="col" className="font-normal pb-3 text-right">Price</th>
              <th scope="col" className="font-normal pb-3 text-right">Fee</th>
              <th scope="col" className="font-normal pb-3 text-right">P/L</th>
              <th scope="col" className="font-normal pb-3 text-right">When</th>
              <th scope="col" className="font-normal pb-3">
                <span className="sr-only">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody className="font-mono">
            {orders.map((o) => (
              <tr key={o.id} className="border-b border-line/60 last:border-0">
                <td className="py-3">
                  <span
                    className={`font-sans text-xs font-medium px-2.5 py-1 rounded-full ${
                      o.type === "BUY" ? "text-gain bg-gain-dim" : "text-loss bg-loss-dim"
                    }`}
                  >
                    {o.type}
                  </span>
                </td>
                <td className="py-3 font-sans">
                  {o.symbol}
                  {o.note && (
                    <div className="text-xs text-muted max-w-[140px] truncate whitespace-nowrap" title={o.note}>
                      {o.note}
                    </div>
                  )}
                </td>
                <td className="py-3">
                  <span
                    title={o.rejectionReason ?? undefined}
                    className={`font-sans text-xs font-medium px-2.5 py-1 rounded-full ${STATUS_STYLES[o.status] ?? "text-dim"}`}
                  >
                    {o.status}
                  </span>
                </td>
                <td className="py-3 text-right">{o.quantity}</td>
                <td className="py-3 text-right">{o.price != null ? o.price.toFixed(2) : <PendingPrice order={o} />}</td>
                <td className="py-3 text-right text-muted">{o.feeAmount != null ? o.feeAmount.toFixed(2) : "—"}</td>
                <td className="py-3 text-right">
                  {o.realizedPnL != null ? signedPnl(o.realizedPnL) : "—"}
                </td>
                <td
                  className="py-3 text-right text-xs text-dim"
                  title={new Date(o.executedAt ?? o.createdAt).toLocaleString([], { day: "numeric", month: "short", year: "numeric", hour: "numeric", minute: "2-digit" })}
                >
                  {shortDate(o)}
                </td>
                <td className="py-3 text-right whitespace-nowrap">
                  {actions(o)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
        )}
        </div>
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

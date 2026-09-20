import { useEffect, useState } from "react";
import { getOrders, cancelOrder } from "../lib/api";
import { showToast } from "../lib/toast";
import Skeleton from "./Skeleton";

const STATUS_STYLES = {
  FILLED: "text-muted bg-panel-2",
  PENDING: "text-accent bg-accent-dim",
  CANCELLED: "text-dim bg-panel-2",
  REJECTED: "text-loss bg-loss-dim",
};

export default function OrderHistory({ refreshKey }) {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);

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

  if (loading) {
    return (
      <section className="bg-panel border border-line rounded-[20px] p-6 space-y-3">
        <Skeleton className="h-4 w-28" />
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-8 w-full" />
        ))}
      </section>
    );
  }

  return (
    <section className="bg-panel border border-line rounded-[20px] p-6 fade-in">
      <h2 className="text-base font-semibold mb-4">Order history</h2>

      {orders.length === 0 ? (
        <div className="text-sm text-dim">No orders placed yet.</div>
      ) : (
        /* relative: the sr-only header text is absolutely positioned and must be clipped by this box */
        <div role="region" aria-label="Order history table" tabIndex={0} className="relative overflow-x-auto">
        <table className="w-full text-sm min-w-[640px]">
          <thead>
            <tr className="text-left text-dim border-b border-line">
              <th scope="col" className="font-normal pb-3">Type</th>
              <th scope="col" className="font-normal pb-3">Symbol</th>
              <th scope="col" className="font-normal pb-3">Status</th>
              <th scope="col" className="font-normal pb-3 text-right">Qty</th>
              <th scope="col" className="font-normal pb-3 text-right">Price</th>
              <th scope="col" className="font-normal pb-3 text-right">Fee</th>
              <th scope="col" className="font-normal pb-3 text-right">Realized P/L</th>
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
                    className={`font-sans text-xs font-medium px-2 py-1 rounded-md ${
                      o.type === "BUY" ? "text-gain bg-gain-dim" : "text-loss bg-loss-dim"
                    }`}
                  >
                    {o.type}
                  </span>
                </td>
                <td className="py-3 font-sans">{o.symbol}</td>
                <td className="py-3">
                  <span
                    title={o.rejectionReason ?? undefined}
                    className={`font-sans text-xs font-medium px-2 py-1 rounded-md ${STATUS_STYLES[o.status] ?? "text-dim"}`}
                  >
                    {o.status}
                    {o.status === "PENDING" && o.kind !== "MARKET" ? ` (${o.kind === "LIMIT" ? o.limitPrice?.toFixed(2) : o.stopPrice?.toFixed(2)})` : ""}
                    {o.status === "PENDING" && o.kind === "MARKET" ? " (at open)" : ""}
                  </span>
                </td>
                <td className="py-3 text-right">{o.quantity}</td>
                <td className="py-3 text-right">{o.price != null ? o.price.toFixed(2) : "—"}</td>
                <td className="py-3 text-right text-muted">{o.feeAmount != null ? o.feeAmount.toFixed(2) : "—"}</td>
                <td className="py-3 text-right">
                  {o.realizedPnL != null
                    ? `${o.realizedPnL >= 0 ? "+" : ""}${o.realizedPnL.toFixed(2)}`
                    : "—"}
                </td>
                <td className="py-3 text-right text-xs text-dim">
                  {new Date(o.executedAt ?? o.createdAt).toLocaleString()}
                </td>
                <td className="py-3 text-right">
                  {o.status === "PENDING" && (
                    <button
                      onClick={() => handleCancel(o.id)}
                      aria-label={`Cancel pending ${o.type.toLowerCase()} order: ${o.symbol}`}
                      className="font-sans text-xs text-dim hover:text-loss transition-colors px-2 py-1"
                    >
                      Cancel
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      )}
    </section>
  );
}

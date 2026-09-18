import { useEffect, useState } from "react";
import { getOrders } from "../lib/api";
import Skeleton from "./Skeleton";

export default function OrderHistory({ refreshKey }) {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    getOrders()
      .then(setOrders)
      .finally(() => setLoading(false));
  }, [refreshKey]);

  if (loading) {
    return (
      <section className="bg-panel border border-line rounded-2xl p-6 space-y-3">
        <Skeleton className="h-4 w-28" />
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-8 w-full" />
        ))}
      </section>
    );
  }

  return (
    <section className="bg-panel border border-line rounded-2xl p-6 fade-in">
      <div className="text-sm font-medium mb-4">Order history</div>

      {orders.length === 0 ? (
        <div className="text-sm text-dim">No orders placed yet.</div>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-dim border-b border-line">
              <th className="font-normal pb-3">Type</th>
              <th className="font-normal pb-3">Symbol</th>
              <th className="font-normal pb-3 text-right">Qty</th>
              <th className="font-normal pb-3 text-right">Price</th>
              <th className="font-normal pb-3 text-right">Realized P/L</th>
              <th className="font-normal pb-3 text-right">Executed</th>
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
                <td className="py-3 text-right">{o.quantity}</td>
                <td className="py-3 text-right">{o.price.toFixed(2)}</td>
                <td className="py-3 text-right">
                  {o.realizedPnL != null
                    ? `${o.realizedPnL >= 0 ? "+" : ""}${o.realizedPnL.toFixed(2)}`
                    : "\u2014"}
                </td>
                <td className="py-3 text-right text-xs text-dim">
                  {new Date(o.executedAt).toLocaleString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

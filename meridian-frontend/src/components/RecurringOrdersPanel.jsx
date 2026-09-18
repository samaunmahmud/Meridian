import { useEffect, useState } from "react";
import { getTickers, getRecurringOrders, createRecurringOrder, deleteRecurringOrder } from "../lib/api";
import { showToast } from "../lib/toast";

const FREQUENCIES = [
  { key: "DAILY", label: "Daily" },
  { key: "WEEKLY", label: "Weekly" },
  { key: "MONTHLY", label: "Monthly" },
];

export default function RecurringOrdersPanel({ refreshKey }) {
  const [tickers, setTickers] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [symbol, setSymbol] = useState("");
  const [amount, setAmount] = useState("");
  const [frequency, setFrequency] = useState("WEEKLY");
  const [submitting, setSubmitting] = useState(false);

  function refresh() {
    setLoading(true);
    Promise.all([getTickers(), getRecurringOrders()])
      .then(([tickerData, orderData]) => {
        setTickers(tickerData);
        setOrders(orderData);
        if (tickerData.length > 0 && !symbol) setSymbol(tickerData[0].symbol);
      })
      .finally(() => setLoading(false));
  }

  useEffect(refresh, [refreshKey]);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!amount) return;
    setSubmitting(true);
    try {
      await createRecurringOrder(symbol, Number(amount), frequency);
      showToast("success", `Recurring buy set: $${amount} of ${symbol} ${frequency.toLowerCase()}`);
      setAmount("");
      refresh();
    } catch (err) {
      showToast("error", err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(id) {
    try {
      await deleteRecurringOrder(id);
      setOrders((prev) => prev.filter((o) => o.id !== id));
    } catch (err) {
      showToast("error", err.message);
    }
  }

  return (
    <section className="bg-panel border border-line rounded-2xl p-6">
      <div className="text-sm font-medium mb-4">Recurring buys</div>

      {!loading && orders.length > 0 && (
        <div className="space-y-1 mb-4">
          {orders.map((o) => (
            <div key={o.id} className="flex items-center justify-between py-2 px-2 rounded-lg hover:bg-panel-2">
              <div className="text-sm">
                <span className="font-medium">{o.symbol}</span>{" "}
                <span className="text-dim font-mono">
                  ${o.amount.toFixed(2)} {o.frequency.toLowerCase()}
                </span>
              </div>
              <button
                onClick={() => handleDelete(o.id)}
                className="text-xs text-dim hover:text-loss transition-colors px-2 py-1"
              >
                Cancel
              </button>
            </div>
          ))}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <select
            value={symbol}
            onChange={(e) => setSymbol(e.target.value)}
            className="bg-panel-2 border border-line rounded-lg px-3 py-2.5 text-sm outline-none focus:border-accent transition-colors"
          >
            {tickers.map((t) => (
              <option key={t.symbol} value={t.symbol}>
                {t.symbol}
              </option>
            ))}
          </select>
          <input
            type="number"
            min="1"
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="Amount ($)"
            className="bg-panel-2 border border-line rounded-lg px-3 py-2.5 text-sm font-mono outline-none focus:border-accent transition-colors"
          />
        </div>

        <div className="flex gap-1 bg-panel-2 rounded-lg p-1 text-xs">
          {FREQUENCIES.map((f) => (
            <button
              key={f.key}
              type="button"
              onClick={() => setFrequency(f.key)}
              className={`flex-1 py-1.5 rounded-md transition-colors ${
                frequency === f.key ? "bg-line text-bone" : "text-dim"
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>

        <button
          type="submit"
          disabled={submitting || !symbol || !amount}
          className="w-full py-2.5 rounded-lg bg-accent hover:bg-accent-2 transition-all active:scale-[0.98] text-white text-sm font-medium disabled:opacity-50"
        >
          {submitting ? "Setting up..." : "Set up recurring buy"}
        </button>
      </form>
    </section>
  );
}

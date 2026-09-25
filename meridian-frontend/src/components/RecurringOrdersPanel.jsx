import { useEffect, useId, useState } from "react";
import { getTickers, getRecurringOrders, createRecurringOrder, deleteRecurringOrder, getWallets } from "../lib/api";
import { currencySymbol, formatMoney } from "../lib/formatMoney";
import { showToast } from "../lib/toast";

const FREQUENCIES = [
  { key: "DAILY", label: "Daily" },
  { key: "WEEKLY", label: "Weekly" },
  { key: "MONTHLY", label: "Monthly" },
];

export default function RecurringOrdersPanel({ refreshKey }) {
  const uid = useId();
  const [tickers, setTickers] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [symbol, setSymbol] = useState("");
  const [amount, setAmount] = useState("");
  const [frequency, setFrequency] = useState("WEEKLY");
  const [currency, setCurrency] = useState("USD");
  const [wallets, setWallets] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  function refresh() {
    setLoading(true);
    Promise.all([getTickers(), getRecurringOrders(), getWallets().catch(() => [])])
      .then(([tickerData, orderData, walletData]) => {
        setTickers(tickerData);
        setOrders(orderData);
        setWallets(walletData);
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
      await createRecurringOrder(symbol, Number(amount), frequency, currency === "USD" ? null : currency);
      showToast("success", `Recurring buy set: ${formatMoney(Number(amount), currency)} of ${symbol} ${frequency.toLowerCase()}`);
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
    <section className="bg-panel rounded-[28px] p-6">
      <h2 className="text-lg font-bold mb-4">Recurring buys</h2>

      {!loading && orders.length > 0 && (
        <div className="space-y-1 mb-4">
          {orders.map((o) => (
            <div key={o.id} className="flex items-center justify-between py-2 px-2 rounded-xl hover:bg-panel-2">
              <div className="text-sm">
                <span className="font-medium">{o.symbol}</span>{" "}
                <span className="text-dim font-mono">
                  {formatMoney(o.amount, o.settlementCurrency ?? "USD")} {o.frequency.toLowerCase()}
                </span>
              </div>
              <button
                onClick={() => handleDelete(o.id)}
                aria-label={`Cancel recurring buy: ${o.symbol} ${formatMoney(o.amount, o.settlementCurrency ?? "USD")} ${o.frequency.toLowerCase()}`}
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
            aria-label="Symbol"
            value={symbol}
            onChange={(e) => setSymbol(e.target.value)}
            className="bg-panel-2 border border-control rounded-2xl px-3 py-2.5 text-sm outline-none focus:border-accent transition-colors"
          >
            {tickers.map((t) => (
              <option key={t.symbol} value={t.symbol}>
                {t.symbol}
              </option>
            ))}
          </select>
          <input
            aria-label={`Amount in ${currency}`}
            type="number"
            min="1"
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder={`Amount (${currencySymbol(currency).trim()})`}
            className="bg-panel-2 border border-control rounded-2xl px-3 py-2.5 text-sm font-mono outline-none focus:border-accent transition-colors"
          />
        </div>

        <div>
          <label htmlFor={`${uid}-currency`} className="text-xs text-dim block mb-1.5">Pay with</label>
          <select
            id={`${uid}-currency`}
            value={currency}
            onChange={(e) => setCurrency(e.target.value)}
            className="w-full bg-panel-2 border border-control rounded-2xl px-3 py-2.5 text-sm outline-none focus:border-accent transition-colors"
          >
            {(wallets.length ? wallets : [{ currency: "USD", balance: 0 }]).map((w) => (
              <option key={w.currency} value={w.currency}>
                {w.currency} — {formatMoney(w.available ?? w.balance, w.currency)} available
              </option>
            ))}
          </select>
          {currency !== "USD" && (
            <div className="text-xs text-dim mt-1.5">
              Buys {currencySymbol(currency).trim()}
              {amount || "…"} worth of shares each time; commission and the 0.5% conversion spread are charged on top.
            </div>
          )}
        </div>

        <div role="group" aria-label="How often" className="flex gap-1 bg-panel-2 rounded-full p-1 text-xs">
          {FREQUENCIES.map((f) => (
            <button
              key={f.key}
              type="button"
              aria-pressed={frequency === f.key}
              onClick={() => setFrequency(f.key)}
              className={`flex-1 py-1.5 rounded-full transition-colors ${
                frequency === f.key ? "bg-accent-dim text-accent" : "text-dim"
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>

        <button
          type="submit"
          disabled={submitting || !symbol || !amount}
          className="w-full py-2.5 rounded-full bg-accent hover:brightness-110 transition-all active:scale-[0.98] text-accent-ink text-sm font-medium disabled:opacity-50"
        >
          {submitting ? "Setting up..." : "Set up recurring buy"}
        </button>
      </form>
    </section>
  );
}

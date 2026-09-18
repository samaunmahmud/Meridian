import { useEffect, useState } from "react";
import { getTickers, placeOrder } from "../lib/api";
import { showToast } from "../lib/toast";

export default function TradePanel({ onOrderPlaced, prefill }) {
  const [tickers, setTickers] = useState([]);
  const [symbol, setSymbol] = useState("");
  const [type, setType] = useState("BUY");
  const [quantity, setQuantity] = useState("1");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    getTickers().then((data) => {
      setTickers(data);
      if (data.length > 0 && !symbol) setSymbol(data[0].symbol);
    });
  }, []);

  useEffect(() => {
    if (!prefill) return;
    setSymbol(prefill.symbol);
    setType(prefill.type);
  }, [prefill]);

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    try {
      const order = await placeOrder(symbol, type, Number(quantity));
      showToast(
        "success",
        `${order.type === "BUY" ? "Bought" : "Sold"} ${order.quantity} ${order.symbol} @ $${order.price.toFixed(2)}`
      );
      onOrderPlaced();
    } catch (err) {
      showToast("error", err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="bg-panel border border-line rounded-2xl p-6">
      <div className="text-sm font-medium mb-4">Place an order</div>

      <div className="relative bg-panel-2 rounded-lg p-1 mb-4 grid grid-cols-2">
        <div
          className={`absolute top-1 bottom-1 w-[calc(50%-4px)] rounded-md transition-transform duration-200 ${
            type === "BUY" ? "bg-gain translate-x-0" : "bg-loss translate-x-[calc(100%+8px)]"
          }`}
        />
        <button
          type="button"
          onClick={() => setType("BUY")}
          className={`relative z-10 py-2 text-sm font-medium transition-colors ${
            type === "BUY" ? "text-ink" : "text-muted"
          }`}
        >
          Buy
        </button>
        <button
          type="button"
          onClick={() => setType("SELL")}
          className={`relative z-10 py-2 text-sm font-medium transition-colors ${
            type === "SELL" ? "text-ink" : "text-muted"
          }`}
        >
          Sell
        </button>
      </div>

      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <label className="text-xs text-dim block mb-1.5">Symbol</label>
          <select
            value={symbol}
            onChange={(e) => setSymbol(e.target.value)}
            className="w-full bg-panel-2 border border-line rounded-lg px-3.5 py-2.5 text-sm outline-none focus:border-accent transition-colors"
          >
            {tickers.map((t) => (
              <option key={t.symbol} value={t.symbol}>
                {t.symbol} — {t.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="text-xs text-dim block mb-1.5">Quantity</label>
          <input
            type="number"
            min="0.0001"
            step="0.0001"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            className="w-full bg-panel-2 border border-line rounded-lg px-3.5 py-2.5 text-sm font-mono outline-none focus:border-accent transition-colors"
          />
        </div>

        <button
          type="submit"
          disabled={submitting || !symbol}
          className={`w-full py-2.5 rounded-lg text-sm font-medium text-ink disabled:opacity-50 transition-all active:scale-[0.98] ${
            type === "BUY" ? "bg-gain hover:brightness-110" : "bg-loss hover:brightness-110"
          }`}
        >
          {submitting ? "Placing order..." : `${type === "BUY" ? "Buy" : "Sell"} ${symbol}`}
        </button>
      </form>
    </section>
  );
}

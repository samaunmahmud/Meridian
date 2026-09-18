import { useEffect, useState } from "react";
import { getTickers, getPrices, placeOrder } from "../lib/api";
import { showToast } from "../lib/toast";

const KINDS = [
  { key: "MARKET", label: "Market" },
  { key: "LIMIT", label: "Limit" },
  { key: "STOP_LOSS", label: "Stop-loss" },
];

const COMMISSION_RATE = 0.0025;
const MINIMUM_FEE = 1;

export default function TradePanel({ onOrderPlaced, prefill }) {
  const [tickers, setTickers] = useState([]);
  const [symbol, setSymbol] = useState("");
  const [type, setType] = useState("BUY");
  const [kind, setKind] = useState("MARKET");
  const [quantity, setQuantity] = useState("1");
  const [limitPrice, setLimitPrice] = useState("");
  const [stopPrice, setStopPrice] = useState("");
  const [lastPrice, setLastPrice] = useState(null);
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

  // STOP_LOSS is sell-only on the backend — switching to BUY while it's
  // selected would otherwise fail on submit with a confusing error.
  useEffect(() => {
    if (type === "BUY" && kind === "STOP_LOSS") setKind("MARKET");
  }, [type, kind]);

  useEffect(() => {
    if (!symbol) return;
    getPrices(symbol)
      .then((data) => setLastPrice(data[0]?.price ?? null))
      .catch(() => setLastPrice(null));
  }, [symbol]);

  const referencePrice = kind === "LIMIT" ? Number(limitPrice) || lastPrice : kind === "STOP_LOSS" ? Number(stopPrice) || lastPrice : lastPrice;
  const notional = referencePrice && quantity ? referencePrice * Number(quantity) : null;
  const estimatedFee = notional ? Math.max(notional * COMMISSION_RATE, MINIMUM_FEE) : null;

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    try {
      const order = await placeOrder(
        symbol,
        type,
        Number(quantity),
        kind,
        kind === "LIMIT" ? Number(limitPrice) : null,
        kind === "STOP_LOSS" ? Number(stopPrice) : null
      );
      if (order.status === "PENDING") {
        showToast(
          "success",
          `${kind === "LIMIT" ? "Limit" : "Stop-loss"} order placed: ${type === "BUY" ? "buy" : "sell"} ${order.quantity} ${order.symbol}`
        );
      } else {
        showToast(
          "success",
          `${order.type === "BUY" ? "Bought" : "Sold"} ${order.quantity} ${order.symbol} @ $${order.price.toFixed(2)}`
        );
      }
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

      <div className="relative bg-panel-2 rounded-lg p-1 mb-3 grid grid-cols-2">
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

      <div className="flex gap-1 bg-panel-2 rounded-lg p-1 mb-4 text-xs">
        {KINDS.map((k) => {
          const disabled = k.key === "STOP_LOSS" && type === "BUY";
          return (
            <button
              key={k.key}
              type="button"
              disabled={disabled}
              onClick={() => setKind(k.key)}
              className={`flex-1 px-2 py-1.5 rounded-md transition-colors disabled:opacity-30 disabled:cursor-not-allowed ${
                kind === k.key ? "bg-line text-bone" : "text-dim"
              }`}
            >
              {k.label}
            </button>
          );
        })}
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

        {kind === "LIMIT" && (
          <div>
            <label className="text-xs text-dim block mb-1.5">Limit price</label>
            <input
              type="number"
              min="0.01"
              step="0.01"
              value={limitPrice}
              onChange={(e) => setLimitPrice(e.target.value)}
              placeholder={lastPrice ? lastPrice.toFixed(2) : "0.00"}
              className="w-full bg-panel-2 border border-line rounded-lg px-3.5 py-2.5 text-sm font-mono outline-none focus:border-accent transition-colors"
            />
          </div>
        )}

        {kind === "STOP_LOSS" && (
          <div>
            <label className="text-xs text-dim block mb-1.5">Stop price</label>
            <input
              type="number"
              min="0.01"
              step="0.01"
              value={stopPrice}
              onChange={(e) => setStopPrice(e.target.value)}
              placeholder={lastPrice ? lastPrice.toFixed(2) : "0.00"}
              className="w-full bg-panel-2 border border-line rounded-lg px-3.5 py-2.5 text-sm font-mono outline-none focus:border-accent transition-colors"
            />
          </div>
        )}

        {estimatedFee != null && (
          <div className="text-xs text-dim">
            Est. commission: <span className="font-mono text-muted">${estimatedFee.toFixed(2)}</span>
          </div>
        )}

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

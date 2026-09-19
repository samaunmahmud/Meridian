import { useEffect, useState } from "react";
import { getTickers, getPrices, placeOrder, getWallets, getFxRates } from "../lib/api";
import { formatMoney } from "../lib/formatMoney";
import { showToast } from "../lib/toast";

const KINDS = [
  { key: "MARKET", label: "Market" },
  { key: "LIMIT", label: "Limit" },
  { key: "STOP_LOSS", label: "Stop-loss" },
];

const COMMISSION_RATE = 0.0025;
const MINIMUM_FEE = 1;
const FX_SPREAD = 0.005; // same markup the backend applies to every conversion

export default function TradePanel({ onOrderPlaced, prefill, refreshKey }) {
  const [tickers, setTickers] = useState([]);
  const [symbol, setSymbol] = useState("");
  const [type, setType] = useState("BUY");
  const [kind, setKind] = useState("MARKET");
  const [quantity, setQuantity] = useState("1");
  const [limitPrice, setLimitPrice] = useState("");
  const [stopPrice, setStopPrice] = useState("");
  const [lastPrice, setLastPrice] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [wallets, setWallets] = useState([]);
  const [fxRates, setFxRates] = useState([]);
  const [currency, setCurrency] = useState("USD");

  useEffect(() => {
    getTickers().then((data) => {
      setTickers(data);
      if (data.length > 0 && !symbol) setSymbol(data[0].symbol);
    });
  }, []);

  // Wallet balances (for the "Pay with" picker) and FX rates (for the
  // estimate). Refreshed after every order so balances stay current.
  useEffect(() => {
    getWallets().then(setWallets).catch(() => {});
    getFxRates().then(setFxRates).catch(() => {});
  }, [refreshKey]);

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

  // Every order type can settle in any wallet. A limit buy holds its money
  // (in that wallet's currency) until it fills or is cancelled.
  const settleCurrency = currency;
  const reservesMoney = kind === "LIMIT" && type === "BUY";
  const usdPerUnit =
    settleCurrency === "USD"
      ? 1
      : fxRates.find((r) => r.baseCurrency === settleCurrency && r.quoteCurrency === "USD")?.rate;
  // What leaves (buy) or arrives (sell) in the chosen wallet, incl. the spread.
  const spreadFactor = settleCurrency === "USD" ? 1 : 1 - FX_SPREAD;
  const settlementEstimate =
    notional && estimatedFee && usdPerUnit
      ? type === "BUY"
        ? (notional + estimatedFee) / (usdPerUnit * spreadFactor)
        : ((notional - estimatedFee) * spreadFactor) / usdPerUnit
      : null;

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
        kind === "STOP_LOSS" ? Number(stopPrice) : null,
        settleCurrency === "USD" ? null : settleCurrency
      );
      if (order.status === "PENDING") {
        showToast(
          "success",
          `${kind === "LIMIT" ? "Limit" : "Stop-loss"} order placed: ${type === "BUY" ? "buy" : "sell"} ${order.quantity} ${order.symbol}` +
            (order.settlementCurrency ? ` (${order.type === "BUY" ? "from" : "into"} your ${order.settlementCurrency} wallet)` : "")
        );
      } else {
        showToast(
          "success",
          `${order.type === "BUY" ? "Bought" : "Sold"} ${order.quantity} ${order.symbol} @ $${order.price.toFixed(2)}` +
            (order.settlementCurrency
              ? ` (${order.type === "BUY" ? "paid" : "received"} ${formatMoney(order.settlementAmount, order.settlementCurrency)})`
              : "")
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
    <section className="bg-panel border border-line rounded-[20px] p-6">
      <div className="text-base font-semibold mb-4">Trade</div>

      <div className="relative bg-panel-2 rounded-xl p-1 mb-3 grid grid-cols-2">
        <div
          className={`absolute top-1 bottom-1 w-[calc(50%-4px)] rounded-md transition-transform duration-200 ${
            type === "BUY" ? "bg-accent translate-x-0" : "bg-loss translate-x-[calc(100%+8px)]"
          }`}
        />
        <button
          type="button"
          onClick={() => setType("BUY")}
          className={`relative z-10 py-2 text-sm font-medium transition-colors ${
            type === "BUY" ? "text-accent-ink" : "text-muted"
          }`}
        >
          Buy
        </button>
        <button
          type="button"
          onClick={() => setType("SELL")}
          className={`relative z-10 py-2 text-sm font-medium transition-colors ${
            type === "SELL" ? "text-on-loss" : "text-muted"
          }`}
        >
          Sell
        </button>
      </div>

      <div className="flex gap-1 bg-panel-2 rounded-xl p-1 mb-4 text-xs">
        {KINDS.map((k) => {
          const disabled = k.key === "STOP_LOSS" && type === "BUY";
          return (
            <button
              key={k.key}
              type="button"
              disabled={disabled}
              onClick={() => setKind(k.key)}
              className={`flex-1 px-2 py-1.5 rounded-md transition-colors disabled:opacity-30 disabled:cursor-not-allowed ${
                kind === k.key ? "bg-accent-dim text-accent" : "text-dim"
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
            className="w-full bg-panel-2 border border-line rounded-xl px-3.5 py-2.5 text-sm outline-none focus:border-accent transition-colors"
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
            className="w-full bg-panel-2 border border-line rounded-xl px-3.5 py-2.5 text-sm font-mono outline-none focus:border-accent transition-colors"
          />
        </div>

        <div>
          <label className="text-xs text-dim block mb-1.5">{type === "BUY" ? "Pay with" : "Receive in"}</label>
          <select
            value={currency}
            onChange={(e) => setCurrency(e.target.value)}
            className="w-full bg-panel-2 border border-line rounded-xl px-3.5 py-2.5 text-sm outline-none focus:border-accent transition-colors"
          >
            {(wallets.length ? wallets : [{ currency: "USD", balance: 0 }]).map((w) => (
              <option key={w.currency} value={w.currency}>
                {w.currency} — {formatMoney(w.available ?? w.balance, w.currency)}
                {type === "BUY" ? " available" : ""}
              </option>
            ))}
          </select>
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
              className="w-full bg-panel-2 border border-line rounded-xl px-3.5 py-2.5 text-sm font-mono outline-none focus:border-accent transition-colors"
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
              className="w-full bg-panel-2 border border-line rounded-xl px-3.5 py-2.5 text-sm font-mono outline-none focus:border-accent transition-colors"
            />
          </div>
        )}

        {estimatedFee != null && (
          <div className="text-xs text-dim">
            Est. commission: <span className="font-mono text-muted">${estimatedFee.toFixed(2)}</span>
          </div>
        )}

        {settleCurrency !== "USD" && settlementEstimate != null && (
          <div className="text-xs text-dim">
            {reservesMoney ? "Reserves" : `Est. you ${type === "BUY" ? "pay" : "receive"}:`}{" "}
            <span className="font-mono text-bone">{formatMoney(settlementEstimate, settleCurrency)}</span>
            <span> · includes the 0.5% conversion spread</span>
            {kind !== "MARKET" && (
              <div className="mt-1">
                {reservesMoney ? "Held until the order fills or you cancel it. " : ""}
                The final amount follows the exchange rate when it fills.
              </div>
            )}
          </div>
        )}

        <button
          type="submit"
          disabled={submitting || !symbol}
          className={`w-full h-12 rounded-[14px] text-[15px] font-semibold disabled:opacity-50 transition-all active:scale-[0.98] ${
            type === "BUY" ? "bg-accent text-accent-ink hover:brightness-110" : "bg-loss text-on-loss hover:brightness-110"
          }`}
        >
          {submitting ? "Placing order..." : `${type === "BUY" ? "Buy" : "Sell"} ${symbol}`}
        </button>
      </form>
    </section>
  );
}

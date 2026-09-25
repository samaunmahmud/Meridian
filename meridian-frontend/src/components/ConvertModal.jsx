import { useEffect, useId, useState } from "react";
import { convertCurrency, getFxRates } from "../lib/api";
import { formatMoney, currencySymbol } from "../lib/formatMoney";
import { showToast } from "../lib/toast";
import Modal from "./Modal";

const CURRENCIES = ["USD", "EUR", "GBP"];

export default function ConvertModal({ wallets, onClose, onConverted }) {
  const uid = useId();
  const [fromCurrency, setFromCurrency] = useState("USD");
  const [toCurrency, setToCurrency] = useState("EUR");
  const [amount, setAmount] = useState("");
  const [rates, setRates] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    getFxRates().then(setRates).catch(() => {});
  }, []);

  useEffect(() => {
    if (fromCurrency === toCurrency) {
      setToCurrency(CURRENCIES.find((c) => c !== fromCurrency));
    }
  }, [fromCurrency]);

  // Every rate is polled against USD, so any pair is derived via USD as
  // the common leg — same approach the backend uses.
  function rateFor(base, quote) {
    if (base === quote) return 1;
    const toUsd = (c) => (c === "USD" ? 1 : rates.find((r) => r.baseCurrency === c && r.quoteCurrency === "USD")?.rate ?? null);
    const baseToUsd = toUsd(base);
    const quoteToUsd = toUsd(quote);
    if (baseToUsd == null || quoteToUsd == null) return null;
    return baseToUsd / quoteToUsd;
  }

  const rate = rateFor(fromCurrency, toCurrency);
  const estimated = rate && amount ? Number(amount) * rate * 0.995 : null;
  const from = wallets?.find((w) => w.currency === fromCurrency);
  const balance = from?.available ?? from?.balance ?? 0; // money held by open orders cannot be converted

  async function handleSubmit(e) {
    e.preventDefault();
    if (!amount) return;
    setSubmitting(true);
    try {
      const result = await convertCurrency(fromCurrency, toCurrency, Number(amount));
      showToast(
        "success",
        `Converted ${formatMoney(result.amountDebited, result.fromCurrency)} → ${formatMoney(result.amountCredited, result.toCurrency)}`
      );
      onConverted();
      onClose();
    } catch (err) {
      showToast("error", err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal title="Convert currency" onClose={onClose}>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor={`${uid}-from`} className="text-xs text-dim block mb-1.5">From</label>
              <select
                id={`${uid}-from`}
                value={fromCurrency}
                onChange={(e) => setFromCurrency(e.target.value)}
                className="w-full bg-panel-2 border border-control rounded-2xl px-3 py-2.5 text-sm outline-none focus:border-accent transition-colors"
              >
                {CURRENCIES.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor={`${uid}-to`} className="text-xs text-dim block mb-1.5">To</label>
              <select
                id={`${uid}-to`}
                value={toCurrency}
                onChange={(e) => setToCurrency(e.target.value)}
                className="w-full bg-panel-2 border border-control rounded-2xl px-3 py-2.5 text-sm outline-none focus:border-accent transition-colors"
              >
                {CURRENCIES.filter((c) => c !== fromCurrency).map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label htmlFor={`${uid}-amount`} className="text-xs text-dim block mb-1.5">
              Amount <span className="text-dim">({currencySymbol(fromCurrency)}{balance.toFixed(2)} available)</span>
            </label>
            <input
              id={`${uid}-amount`}
              data-autofocus
              type="number"
              min="0.01"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              className="w-full bg-panel-2 border border-control rounded-2xl px-3.5 py-2.5 text-sm font-mono outline-none focus:border-accent transition-colors"
            />
          </div>

          {estimated != null && (
            <div className="text-xs text-dim">
              You'll receive approx. <span className="font-mono text-bone">{formatMoney(estimated, toCurrency)}</span> (rate incl. 0.5% spread)
            </div>
          )}

          <button
            type="submit"
            disabled={submitting || !amount}
            className="w-full py-2.5 rounded-full bg-accent hover:brightness-110 transition-all active:scale-[0.98] text-accent-ink text-sm font-medium disabled:opacity-50"
          >
            {submitting ? "Converting..." : "Convert"}
          </button>
        </form>
    </Modal>
  );
}

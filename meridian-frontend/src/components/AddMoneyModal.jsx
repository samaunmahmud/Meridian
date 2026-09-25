import { useId, useState } from "react";
import { depositToWallet } from "../lib/api";
import { currencySymbol, formatMoney } from "../lib/formatMoney";
import { showToast } from "../lib/toast";
import Modal from "./Modal";

const CURRENCIES = ["USD", "EUR", "GBP"];
const QUICK_AMOUNTS = [100, 500, 1000];

// "Add money" from the Home screen: pick a wallet and an amount, the way a banking app tops up.
// The money is virtual (see the deposit endpoint), so there is no card or bank step.
export default function AddMoneyModal({ onClose, onAdded }) {
  const uid = useId();
  const [currency, setCurrency] = useState("USD");
  const [amount, setAmount] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    const value = Number(amount);
    if (!value) return;
    setSubmitting(true);
    try {
      await depositToWallet(currency, value);
      showToast("success", `Added ${formatMoney(value, currency)} to your ${currency} account`);
      onAdded();
      onClose();
    } catch (err) {
      showToast("error", err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal title="Add money" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-5">
        <div role="group" aria-label="Account" className="flex gap-2">
          {CURRENCIES.map((c) => (
            <button
              key={c}
              type="button"
              aria-pressed={currency === c}
              onClick={() => setCurrency(c)}
              className={`h-9 px-4 rounded-full text-sm font-semibold transition-colors ${
                currency === c ? "bg-bone text-ink" : "bg-panel-2 text-muted hover:text-bone"
              }`}
            >
              {c}
            </button>
          ))}
        </div>

        <div>
          <label htmlFor={`${uid}-amount`} className="text-sm text-muted block mb-2">
            Amount
          </label>
          <div className="flex items-baseline gap-1 border-b-2 border-control focus-within:border-accent focus-within:border-b-[3px] transition-colors pb-1">
            <span className="font-display text-4xl text-muted" aria-hidden="true">
              {currencySymbol(currency)}
            </span>
            <input
              id={`${uid}-amount`}
              data-autofocus
              type="number"
              inputMode="decimal"
              min="0.01"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0"
              data-ring-parent
              className="no-spin w-full bg-transparent font-display text-4xl outline-none placeholder:text-dim"
            />
          </div>
        </div>

        <div className="flex gap-2">
          {QUICK_AMOUNTS.map((q) => (
            <button
              key={q}
              type="button"
              onClick={() => setAmount(String(q))}
              className="h-9 px-4 rounded-full bg-panel-2 text-sm font-medium hover:brightness-110 transition-all"
            >
              {formatMoney(q, currency).replace(/\.00$/, "")}
            </button>
          ))}
        </div>

        <button
          type="submit"
          disabled={submitting || !Number(amount)}
          className="w-full h-12 rounded-full bg-accent text-accent-ink text-[15px] font-semibold disabled:opacity-50 hover:brightness-110 active:scale-[0.98] transition-all"
        >
          {submitting ? "Adding…" : "Add money"}
        </button>
      </form>
    </Modal>
  );
}

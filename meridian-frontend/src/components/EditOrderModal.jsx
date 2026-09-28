import { useId, useState } from "react";
import { replaceOrder } from "../lib/api";
import { showToast } from "../lib/toast";
import Modal from "./Modal";

// Which price field each kind of pending order has.
const PRICE_FIELD = {
  LIMIT: { key: "limitPrice", label: "Limit price", step: "0.01" },
  STOP_LOSS: { key: "stopPrice", label: "Stop price", step: "0.01" },
  TRAILING_STOP: { key: "trailPercent", label: "Trail (%)", step: "0.5" },
};

/**
 * Changes the quantity and price (or trail) of a pending order. The server replaces it with a new order in
 * one step, so if the new one can't be placed (not enough cash or shares) the old one stays as it was.
 */
export default function EditOrderModal({ order, onClose, onReplaced }) {
  const uid = useId();
  const field = PRICE_FIELD[order.kind];
  const [quantity, setQuantity] = useState(String(order.quantity));
  const [price, setPrice] = useState(field ? String(Number(order[field.key])) : "");
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSaving(true);
    try {
      const next = await replaceOrder(order.id, {
        quantity: Number(quantity),
        limitPrice: order.kind === "LIMIT" ? Number(price) : null,
        stopPrice: order.kind === "STOP_LOSS" ? Number(price) : null,
        trailPercent: order.kind === "TRAILING_STOP" ? Number(price) : null,
      });
      showToast("success", `Order updated: ${next.type === "BUY" ? "buy" : "sell"} ${next.quantity} ${next.symbol}`);
      onReplaced(next);
    } catch (err) {
      setError(err.message);
      setSaving(false);
    }
  }

  const inputClass =
    "w-full bg-panel-2 border border-control rounded-2xl px-4 py-3 text-sm font-mono outline-none focus:border-accent transition-colors";

  return (
    <Modal title={`Edit ${order.type === "BUY" ? "buy" : "sell"} order · ${order.symbol}`} onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <label htmlFor={`${uid}-qty`} className="text-[13px] text-muted block mb-1.5">Quantity</label>
          <input id={`${uid}-qty`} data-autofocus type="number" min="0.0001" step="0.0001" value={quantity} onChange={(e) => setQuantity(e.target.value)} className={inputClass} />
        </div>
        {field && (
          <div>
            <label htmlFor={`${uid}-price`} className="text-[13px] text-muted block mb-1.5">{field.label}</label>
            <input id={`${uid}-price`} type="number" min="0" step={field.step} value={price} onChange={(e) => setPrice(e.target.value)} className={inputClass} />
          </div>
        )}
        {order.kind === "TRAILING_STOP" && (
          <p className="text-xs text-dim">The stop restarts from the current price with the new trail.</p>
        )}
        {error && (
          <p role="alert" className="text-sm text-loss">
            {error}
          </p>
        )}
        <div className="flex gap-3 pt-1">
          <button type="button" onClick={onClose} className="flex-1 h-11 rounded-full bg-panel-2 text-sm font-semibold hover:brightness-110">
            Keep as is
          </button>
          <button
            type="submit"
            disabled={saving || !(Number(quantity) > 0) || (field && !(Number(price) > 0))}
            className="flex-1 h-11 rounded-full bg-accent text-accent-ink text-sm font-semibold hover:brightness-110 disabled:opacity-50"
          >
            {saving ? "Saving…" : "Save changes"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

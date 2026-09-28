import { useId, useState } from "react";
import { setOrderNote } from "../lib/api";
import { showToast } from "../lib/toast";
import Modal from "./Modal";

const MAX = 500;

// A private note on an order: why it was placed, what the plan is. Saving an empty note removes it.
export default function OrderNoteModal({ order, onClose, onSaved }) {
  const id = useId();
  const [text, setText] = useState(order.note ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  async function handleSubmit(e) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const updated = await setOrderNote(order.id, text);
      showToast("success", updated.note ? "Note saved" : "Note removed");
      onSaved(updated);
    } catch (err) {
      setError(err.message);
      setSaving(false);
    }
  }

  const side = order.type === "BUY" ? "Buy" : "Sell";
  return (
    <Modal title={`Note · ${side} ${order.quantity} ${order.symbol}`} onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-3">
        <label htmlFor={id} className="text-[13px] text-muted block">
          Why you placed it, what you&rsquo;re watching for. Only you can see it.
        </label>
        <textarea
          id={id}
          data-autofocus
          rows={5}
          maxLength={MAX}
          value={text}
          onChange={(e) => setText(e.target.value)}
          aria-describedby={`${id}-count`}
          className="w-full bg-panel-2 border border-control rounded-2xl px-4 py-3 text-sm outline-none focus:border-accent transition-colors resize-y"
        />
        <div id={`${id}-count`} className="text-xs text-dim text-right">
          {text.length}/{MAX}
        </div>
        {error && (
          <p role="alert" className="text-sm text-loss">
            {error}
          </p>
        )}
        <div className="flex gap-3">
          <button type="button" onClick={onClose} className="flex-1 h-11 rounded-full bg-panel-2 text-sm font-semibold hover:brightness-110">
            Cancel
          </button>
          <button type="submit" disabled={saving} className="flex-1 h-11 rounded-full bg-accent text-accent-ink text-sm font-semibold hover:brightness-110 disabled:opacity-50">
            {saving ? "Saving…" : "Save note"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

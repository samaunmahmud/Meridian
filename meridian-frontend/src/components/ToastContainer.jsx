import { useCallback, useEffect, useState } from "react";
import { createPortal } from "react-dom";

// How long a toast stays. Errors carry the reason an order was rejected, so they stay
// longer; hovering over a toast or focusing inside it pauses the countdown.
const DURATION_MS = { success: 6000, info: 6000, error: 10000 };

function Toast({ toast, onDismiss }) {
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (paused) return;
    const timer = setTimeout(() => onDismiss(toast.id), DURATION_MS[toast.kind] ?? 6000);
    return () => clearTimeout(timer);
  }, [paused, toast.id, toast.kind, onDismiss]);

  return (
    <div
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
      className={`toast-in flex items-start gap-3 rounded-xl border pl-4 pr-2 py-3 text-sm shadow-2xl backdrop-blur ${
        toast.kind === "success"
          ? "bg-gain-dim/95 border-gain/30 text-gain"
          : toast.kind === "error"
          ? "bg-loss-dim/95 border-loss/30 text-loss"
          : "bg-panel-2/95 border-line text-bone"
      }`}
    >
      <span className="flex-1 min-w-0 pt-0.5">{toast.message}</span>
      <button
        type="button"
        onClick={() => onDismiss(toast.id)}
        aria-label="Dismiss notification"
        className="w-8 h-8 shrink-0 rounded-lg flex items-center justify-center hover:bg-black/10 transition-colors"
      >
        <span aria-hidden="true" className="text-lg leading-none">
          &times;
        </span>
      </button>
    </div>
  );
}

// Screen readers only announce a live region whose container already exists when its
// content changes, so both regions are always rendered (empty) and toasts are added to
// them. Errors go to an assertive region, everything else to a polite one. They are
// rendered into <body> so they still work while a modal has made #root inert.
export default function ToastContainer() {
  const [toasts, setToasts] = useState([]);

  const dismiss = useCallback((id) => setToasts((prev) => prev.filter((t) => t.id !== id)), []);

  useEffect(() => {
    function handle(e) {
      setToasts((prev) => [...prev, { id: Date.now() + Math.random(), ...e.detail }]);
    }
    window.addEventListener("meridian:toast", handle);
    return () => window.removeEventListener("meridian:toast", handle);
  }, []);

  const errors = toasts.filter((t) => t.kind === "error");
  const others = toasts.filter((t) => t.kind !== "error");

  return createPortal(
    <div className="fixed top-5 right-5 z-50 flex flex-col gap-2 w-80 max-w-[calc(100vw-2.5rem)] pointer-events-none [&>*]:pointer-events-auto">
      <div role="status" aria-live="polite" aria-atomic="false" className="flex flex-col gap-2">
        {others.map((t) => (
          <Toast key={t.id} toast={t} onDismiss={dismiss} />
        ))}
      </div>
      <div role="alert" aria-live="assertive" aria-atomic="false" className="flex flex-col gap-2">
        {errors.map((t) => (
          <Toast key={t.id} toast={t} onDismiss={dismiss} />
        ))}
      </div>
    </div>,
    document.body
  );
}

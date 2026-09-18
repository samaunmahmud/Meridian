import { useEffect, useState } from "react";

export default function ToastContainer() {
  const [toasts, setToasts] = useState([]);

  useEffect(() => {
    function handle(e) {
      const id = Date.now() + Math.random();
      setToasts((prev) => [...prev, { id, ...e.detail }]);
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, 3500);
    }
    window.addEventListener("meridian:toast", handle);
    return () => window.removeEventListener("meridian:toast", handle);
  }, []);

  if (toasts.length === 0) return null;

  return (
    <div className="fixed top-5 right-5 z-50 flex flex-col gap-2 w-80">
      {toasts.map((t) => (
        <div
          key={t.id}
          className={`toast-in rounded-xl border px-4 py-3 text-sm shadow-2xl backdrop-blur ${
            t.kind === "success"
              ? "bg-gain-dim/95 border-gain/30 text-gain"
              : t.kind === "error"
              ? "bg-loss-dim/95 border-loss/30 text-loss"
              : "bg-panel-2/95 border-line text-bone"
          }`}
        >
          {t.message}
        </div>
      ))}
    </div>
  );
}

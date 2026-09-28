import { useEffect, useId, useState } from "react";
import { getTickers, getAlerts, createAlert, deleteAlert } from "../lib/api";
import { showToast } from "../lib/toast";
import TickerAvatar from "./TickerAvatar";
import Skeleton from "./Skeleton";

// "AAPL above $200.00", or for a percentage alert "AAPL down 5% from $210.00 ($199.50)".
function describe(a) {
  if (a.movePercent != null) {
    return `${a.symbol} ${a.direction === "ABOVE" ? "up" : "down"} ${Number(a.movePercent)}% from $${a.referencePrice.toFixed(2)} ($${a.targetPrice.toFixed(2)})`;
  }
  return `${a.symbol} ${a.direction === "ABOVE" ? "above" : "below"} $${a.targetPrice.toFixed(2)}`;
}

export default function AlertsPanel() {
  const uid = useId();
  const [alerts, setAlerts] = useState([]);
  const [tickers, setTickers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [symbol, setSymbol] = useState("");
  const [direction, setDirection] = useState("ABOVE");
  const [targetPrice, setTargetPrice] = useState("");
  const [mode, setMode] = useState("PRICE"); // "PRICE" | "MOVE"
  const [submitting, setSubmitting] = useState(false);

  function refresh() {
    setLoading(true);
    Promise.all([getAlerts(), getTickers()])
      .then(([alertData, tickerData]) => {
        setAlerts(alertData);
        setTickers(tickerData);
        if (tickerData.length > 0 && !symbol) setSymbol(tickerData[0].symbol);
      })
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!targetPrice) return;
    setSubmitting(true);
    try {
      if (mode === "MOVE") {
        const alert = await createAlert(symbol, direction, null, Number(targetPrice));
        showToast("success", `Alert set: ${describe(alert)}`);
      } else {
        await createAlert(symbol, direction, Number(targetPrice));
        showToast("success", `Alert set: ${symbol} ${direction === "ABOVE" ? "above" : "below"} $${targetPrice}`);
      }
      setTargetPrice("");
      refresh();
    } catch (err) {
      showToast("error", err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(id) {
    try {
      await deleteAlert(id);
      setAlerts((prev) => prev.filter((a) => a.id !== id));
    } catch (err) {
      showToast("error", err.message);
    }
  }

  return (
    <main id="main-content" tabIndex={-1} data-ring-parent className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_360px] gap-6 px-4 sm:px-6 lg:px-8 pb-8 max-w-[1240px] fade-in">
      <section className="bg-panel rounded-[28px] p-6">
        <h2 className="text-lg font-bold mb-4">Your alerts</h2>

        {loading ? (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : alerts.length === 0 ? (
          <div className="text-sm text-dim">No alerts yet — create one to get notified live.</div>
        ) : (
          <div className="space-y-1">
            {alerts.map((a) => (
              <div
                key={a.id}
                className={`flex items-center justify-between py-3 px-2 rounded-xl ${
                  a.triggered ? "opacity-50" : ""
                }`}
              >
                <div className="flex items-center gap-3">
                  <TickerAvatar symbol={a.symbol} size={32} />
                  <div>
                    <div className="text-sm font-medium">
                      {a.movePercent != null ? (
                        <>
                          {a.symbol} {a.direction === "ABOVE" ? "up" : "down"} <span className="font-mono">{Number(a.movePercent)}%</span>
                          <span className="text-muted font-normal">
                            {" "}from <span className="font-mono">${a.referencePrice.toFixed(2)}</span> ·{" "}
                            <span className="font-mono">${a.targetPrice.toFixed(2)}</span>
                          </span>
                        </>
                      ) : (
                        <>
                          {a.symbol} {a.direction === "ABOVE" ? "above" : "below"}{" "}
                          <span className="font-mono">${a.targetPrice.toFixed(2)}</span>
                        </>
                      )}
                    </div>
                    <div className="text-xs text-dim">
                      {a.triggered
                        ? `Triggered ${new Date(a.triggeredAt).toLocaleString()}`
                        : "Waiting..."}
                    </div>
                  </div>
                </div>
                <button
                  onClick={() => handleDelete(a.id)}
                  aria-label={`Remove alert: ${describe(a)}`}
                  className="text-xs text-dim hover:text-loss transition-colors px-2 py-1"
                >
                  Remove
                </button>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="bg-panel rounded-[28px] p-6 h-fit">
        <h2 className="text-lg font-bold mb-1">New alert</h2>
        <p className="text-xs text-muted mb-4">
          You'll be told here the moment it fires, and by email once your address is confirmed.
        </p>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label htmlFor={`${uid}-symbol`} className="text-xs text-dim block mb-1.5">Symbol</label>
            <select
              id={`${uid}-symbol`}
              value={symbol}
              onChange={(e) => setSymbol(e.target.value)}
              className="w-full bg-panel-2 border border-control rounded-2xl px-3.5 py-2.5 text-sm outline-none focus:border-accent transition-colors"
            >
              {tickers.map((t) => (
                <option key={t.symbol} value={t.symbol}>
                  {t.symbol} — {t.name}
                </option>
              ))}
            </select>
          </div>

          <div role="group" aria-label="Alert type" className="grid grid-cols-2 gap-1.5 text-[13px]">
            {[
              ["PRICE", "At a price"],
              ["MOVE", "On a % move"],
            ].map(([key, label]) => (
              <button
                key={key}
                type="button"
                aria-pressed={mode === key}
                onClick={() => {
                  setMode(key);
                  setTargetPrice("");
                }}
                className={`h-8 rounded-full font-semibold transition-colors ${
                  mode === key ? "bg-bone text-ink" : "bg-panel-2 text-muted hover:text-bone"
                }`}
              >
                {label}
              </button>
            ))}
          </div>

          <div role="group" aria-label="Alert direction" className="relative bg-panel-2 rounded-full p-1 grid grid-cols-2">
            <div
              className={`absolute top-1 bottom-1 w-[calc(50%-4px)] rounded-full bg-accent transition-transform duration-200 ${
                direction === "ABOVE" ? "translate-x-0" : "translate-x-[calc(100%+8px)]"
              }`}
            />
            <button
              type="button"
              aria-pressed={direction === "ABOVE"}
              onClick={() => setDirection("ABOVE")}
              className={`relative z-10 py-2 text-sm font-medium transition-colors ${
                direction === "ABOVE" ? "text-accent-ink" : "text-muted"
              }`}
            >
              {mode === "MOVE" ? "Rises" : "Above"}
            </button>
            <button
              type="button"
              aria-pressed={direction === "BELOW"}
              onClick={() => setDirection("BELOW")}
              className={`relative z-10 py-2 text-sm font-medium transition-colors ${
                direction === "BELOW" ? "text-accent-ink" : "text-muted"
              }`}
            >
              {mode === "MOVE" ? "Falls" : "Below"}
            </button>
          </div>

          <div>
            <label htmlFor={`${uid}-target`} className="text-xs text-dim block mb-1.5">
              {mode === "MOVE" ? "Move (%) from the current price" : "Target price"}
            </label>
            <input
              id={`${uid}-target`}
              type="number"
              step={mode === "MOVE" ? "0.1" : "0.01"}
              min="0"
              value={targetPrice}
              onChange={(e) => setTargetPrice(e.target.value)}
              placeholder={mode === "MOVE" ? "5" : "0.00"}
              className="w-full bg-panel-2 border border-control rounded-2xl px-3.5 py-2.5 text-sm font-mono outline-none focus:border-accent transition-colors"
            />
          </div>

          <button
            type="submit"
            disabled={submitting || !symbol || !targetPrice}
            className="w-full py-2.5 rounded-full bg-accent hover:brightness-110 transition-all active:scale-[0.98] text-accent-ink text-sm font-medium disabled:opacity-50"
          >
            {submitting ? "Creating..." : "Create alert"}
          </button>
        </form>
      </section>
    </main>
  );
}

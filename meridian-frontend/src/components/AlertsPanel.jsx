import { useEffect, useState } from "react";
import { getTickers, getAlerts, createAlert, deleteAlert } from "../lib/api";
import { showToast } from "../lib/toast";
import TickerAvatar from "./TickerAvatar";
import Skeleton from "./Skeleton";

export default function AlertsPanel() {
  const [alerts, setAlerts] = useState([]);
  const [tickers, setTickers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [symbol, setSymbol] = useState("");
  const [direction, setDirection] = useState("ABOVE");
  const [targetPrice, setTargetPrice] = useState("");
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
      await createAlert(symbol, direction, Number(targetPrice));
      showToast("success", `Alert set: ${symbol} ${direction === "ABOVE" ? "above" : "below"} $${targetPrice}`);
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
    <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_360px] gap-6 px-4 sm:px-6 lg:px-8 pb-8 max-w-[1240px] fade-in">
      <section className="bg-panel border border-line rounded-[20px] p-6">
        <div className="text-base font-semibold mb-4">Your alerts</div>

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
                      {a.symbol} {a.direction === "ABOVE" ? "above" : "below"}{" "}
                      <span className="font-mono">${a.targetPrice.toFixed(2)}</span>
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
                  className="text-xs text-dim hover:text-loss transition-colors px-2 py-1"
                >
                  Remove
                </button>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="bg-panel border border-line rounded-[20px] p-6 h-fit">
        <div className="text-base font-semibold mb-4">New alert</div>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="text-xs text-dim block mb-1.5">Symbol</label>
            <select
              value={symbol}
              onChange={(e) => setSymbol(e.target.value)}
              className="w-full bg-panel-2 border border-control rounded-xl px-3.5 py-2.5 text-sm outline-none focus:border-accent transition-colors"
            >
              {tickers.map((t) => (
                <option key={t.symbol} value={t.symbol}>
                  {t.symbol} — {t.name}
                </option>
              ))}
            </select>
          </div>

          <div className="relative bg-panel-2 rounded-xl p-1 grid grid-cols-2">
            <div
              className={`absolute top-1 bottom-1 w-[calc(50%-4px)] rounded-md bg-accent transition-transform duration-200 ${
                direction === "ABOVE" ? "translate-x-0" : "translate-x-[calc(100%+8px)]"
              }`}
            />
            <button
              type="button"
              onClick={() => setDirection("ABOVE")}
              className={`relative z-10 py-2 text-sm font-medium transition-colors ${
                direction === "ABOVE" ? "text-accent-ink" : "text-muted"
              }`}
            >
              Above
            </button>
            <button
              type="button"
              onClick={() => setDirection("BELOW")}
              className={`relative z-10 py-2 text-sm font-medium transition-colors ${
                direction === "BELOW" ? "text-accent-ink" : "text-muted"
              }`}
            >
              Below
            </button>
          </div>

          <div>
            <label className="text-xs text-dim block mb-1.5">Target price</label>
            <input
              type="number"
              step="0.01"
              min="0"
              value={targetPrice}
              onChange={(e) => setTargetPrice(e.target.value)}
              placeholder="0.00"
              className="w-full bg-panel-2 border border-control rounded-xl px-3.5 py-2.5 text-sm font-mono outline-none focus:border-accent transition-colors"
            />
          </div>

          <button
            type="submit"
            disabled={submitting || !symbol || !targetPrice}
            className="w-full py-2.5 rounded-xl bg-accent hover:brightness-110 transition-all active:scale-[0.98] text-accent-ink text-sm font-medium disabled:opacity-50"
          >
            {submitting ? "Creating..." : "Create alert"}
          </button>
        </form>
      </section>
    </div>
  );
}

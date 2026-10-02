import { useEffect, useId, useState } from "react";
import { createAlert, deleteAlert, getAlerts, getWatchlist, saveWatchlistNote } from "../lib/api";
import { formatNumber } from "../lib/formatMoney";
import { targetDistance } from "../lib/targetDistance";
import { showToast } from "../lib/toast";
import Icon from "./Icon";

const MAX_NOTE = 500;

// The waiting price alert that matches your target (a fall to it), if there is one.
function alertFor(alerts, symbol, target) {
  if (target == null) return null;
  return (
    alerts.find(
      (a) => a.symbol === symbol && !a.triggered && a.direction === "BELOW" && a.movePercent == null && Number(a.targetPrice) === Number(target)
    ) ?? null
  );
}

// Your own note on a stock and the price you'd like to buy it at, kept on your watchlist. The target can
// come with a price alert ("tell me when it gets there"), which is an ordinary alert on the Alerts tab.
export default function StockNotes({ ticker, liveUpdate }) {
  const uid = useId();
  const [entry, setEntry] = useState(undefined); // undefined: loading, null: nothing saved
  const [editing, setEditing] = useState(false);
  const [note, setNote] = useState("");
  const [target, setTarget] = useState("");
  const [saving, setSaving] = useState(false);
  const [livePrice, setLivePrice] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [notify, setNotify] = useState(true);

  useEffect(() => {
    let live = true;
    setEntry(undefined);
    setEditing(false);
    setLivePrice(null);
    getWatchlist()
      .then((list) => live && setEntry(list.find((i) => i.symbol === ticker.symbol) ?? null))
      .catch(() => live && setEntry(null));
    getAlerts()
      .then((a) => live && setAlerts(a))
      .catch(() => live && setAlerts([]));
    return () => {
      live = false;
    };
  }, [ticker.symbol]);

  useEffect(() => {
    if (liveUpdate?.kind === "PRICE_UPDATE" && liveUpdate.symbol === ticker.symbol) setLivePrice(liveUpdate.price);
  }, [liveUpdate, ticker.symbol]);

  if (entry === undefined) return null;

  const hasContent = entry && (entry.note || entry.targetPrice);
  const price = livePrice ?? entry?.currentPrice ?? null;
  const distance = targetDistance(price, entry?.targetPrice);
  const alert = alertFor(alerts, ticker.symbol, entry?.targetPrice);

  function startEditing() {
    setNote(entry?.note ?? "");
    setTarget(entry?.targetPrice != null ? String(entry.targetPrice) : "");
    setNotify(entry?.targetPrice == null || alert != null);
    setEditing(true);
  }

  // Keeps the alert in step with the target: a changed target moves it, unticking removes it, and a price
  // already at or below the target needs none (it would go off at once).
  async function syncAlert(newTarget, wanted, currentPrice) {
    const keep = alert && wanted && Number(alert.targetPrice) === Number(newTarget);
    if (alert && !keep) {
      await deleteAlert(alert.id);
      setAlerts((list) => list.filter((a) => a.id !== alert.id));
    }
    if (wanted && newTarget != null && !keep && currentPrice != null && currentPrice > newTarget) {
      const created = await createAlert(ticker.symbol, "BELOW", newTarget);
      setAlerts((list) => [...list, created]);
      return true;
    }
    return false;
  }

  function addAlert() {
    createAlert(ticker.symbol, "BELOW", entry.targetPrice)
      .then((created) => {
        setAlerts((list) => [...list, created]);
        showToast("success", `We'll tell you when ${ticker.symbol} falls to $${formatNumber(entry.targetPrice)}`);
      })
      .catch((err) => showToast("error", err.message));
  }

  function save(e) {
    e.preventDefault();
    setSaving(true);
    const newTarget = target === "" ? null : Number(target);
    saveWatchlistNote(ticker.symbol, note.trim() || null, newTarget)
      .then(async (saved) => {
        setEntry(saved);
        setEditing(false);
        let alerted = false;
        try {
          alerted = await syncAlert(saved.targetPrice ?? null, notify, livePrice ?? saved.currentPrice ?? null);
        } catch (err) {
          showToast("error", `Note saved, but the alert could not be set: ${err.message}`);
          return;
        }
        showToast("success", alerted ? `Note saved. We'll tell you when ${ticker.symbol} falls to $${formatNumber(newTarget)}` : "Note saved");
      })
      .catch((err) => showToast("error", err.message))
      .finally(() => setSaving(false));
  }

  return (
    <section aria-labelledby="notes-heading" className="bg-panel rounded-[28px] p-5 sm:p-7">
      <div className="flex items-center justify-between gap-3 mb-3">
        <h2 id="notes-heading" className="text-lg font-bold">
          Your notes
        </h2>
        {!editing && (
          <button
            type="button"
            onClick={startEditing}
            className="h-8 px-3.5 rounded-full text-[13px] font-semibold bg-panel-2 hover:brightness-110"
          >
            {hasContent ? "Edit" : "Add a note"}
          </button>
        )}
      </div>

      {editing ? (
        <form onSubmit={save} className="space-y-4">
          <div>
            <label htmlFor={`${uid}-note`} className="text-[13px] text-muted block mb-1.5">
              Why you're watching {ticker.symbol}
            </label>
            <textarea
              id={`${uid}-note`}
              value={note}
              maxLength={MAX_NOTE}
              rows={3}
              onChange={(e) => setNote(e.target.value)}
              className="w-full rounded-2xl bg-panel-2 border border-control px-4 py-3 text-sm resize-y"
            />
            <div className="text-xs text-dim text-right mt-1">
              {note.length} / {MAX_NOTE}
            </div>
          </div>
          <div>
            <label htmlFor={`${uid}-target`} className="text-[13px] text-muted block mb-1.5">
              Price you'd like to buy at (optional)
            </label>
            <input
              id={`${uid}-target`}
              type="number"
              inputMode="decimal"
              min="0.0001"
              step="0.0001"
              value={target}
              onChange={(e) => setTarget(e.target.value)}
              className="w-40 h-10 rounded-2xl bg-panel-2 border border-control px-4 font-mono text-sm"
            />
            {target !== "" && (
              <label className="flex items-center gap-2 mt-3 text-sm">
                <input
                  type="checkbox"
                  checked={notify}
                  onChange={(e) => setNotify(e.target.checked)}
                  className="w-4 h-4 accent-[var(--c-accent)]"
                />
                Tell me when it gets there
              </label>
            )}
          </div>
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={saving}
              className="h-10 px-5 rounded-full bg-accent text-accent-ink text-sm font-semibold disabled:opacity-50"
            >
              {saving ? "Saving…" : "Save"}
            </button>
            <button type="button" onClick={() => setEditing(false)} className="h-10 px-4 rounded-full text-sm font-semibold text-muted hover:text-bone">
              Cancel
            </button>
          </div>
        </form>
      ) : hasContent ? (
        <div className="space-y-3">
          {entry.note && <p className="text-sm whitespace-pre-wrap break-words">{entry.note}</p>}
          {entry.targetPrice != null && (
            <p className="text-sm">
              <span className="text-muted">Target </span>
              <span className="font-mono font-semibold">${formatNumber(entry.targetPrice)}</span>
              {distance && (
                <span className={`ml-2 text-xs font-semibold px-2 py-0.5 rounded-full ${distance.reached ? "bg-gain-dim text-gain" : "bg-panel-2 text-muted"}`}>
                  {distance.text}
                </span>
              )}
            </p>
          )}
          {entry.targetPrice != null &&
            (alert ? (
              <p className="text-xs text-muted flex items-center gap-1.5">
                <Icon name="bell" size={13} />
                We'll tell you when it falls to your target.
              </p>
            ) : (
              distance &&
              !distance.reached && (
                <button
                  type="button"
                  onClick={addAlert}
                  className="h-8 px-3.5 rounded-full text-[13px] font-semibold bg-accent-dim text-accent hover:brightness-110 inline-flex items-center gap-1.5"
                >
                  <Icon name="bell" size={13} />
                  Tell me when it gets there
                </button>
              )
            ))}
        </div>
      ) : (
        <p className="text-sm text-muted">Write down why you're watching {ticker.symbol} and the price you'd buy at.</p>
      )}
    </section>
  );
}

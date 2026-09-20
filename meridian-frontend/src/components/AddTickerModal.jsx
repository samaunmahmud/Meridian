import { useEffect, useState } from "react";
import { searchTickers, addTicker } from "../lib/api";
import { showToast } from "../lib/toast";
import Modal from "./Modal";
import TickerAvatar from "./TickerAvatar";

export default function AddTickerModal({ onClose, onAdded }) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [addingSymbol, setAddingSymbol] = useState(null);

  // Debounce: wait 400ms after the user stops typing before actually
  // calling the API, so we don't fire a request on every keystroke.
  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      return;
    }
    setSearching(true);
    const timer = setTimeout(() => {
      searchTickers(query)
        .then(setResults)
        .catch(() => setResults([]))
        .finally(() => setSearching(false));
    }, 400);
    return () => clearTimeout(timer);
  }, [query]);

  async function handleAdd(match) {
    setAddingSymbol(match.symbol);
    try {
      await addTicker(match.symbol, match.name, match.region);
      showToast("success", `${match.symbol} added to Markets`);
      onAdded();
      onClose();
    } catch (err) {
      showToast("error", err.message);
    } finally {
      setAddingSymbol(null);
    }
  }

  // Read out by screen readers as the search runs (the visible text is not in a live region).
  const status = searching
    ? "Searching"
    : query.trim()
    ? results.length === 0
      ? `No matches for ${query}`
      : `${results.length} ${results.length === 1 ? "match" : "matches"}`
    : "";

  return (
    <Modal title="Add a stock" onClose={onClose}>
        <input
          data-autofocus
          aria-label="Search by company name or symbol"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by company name or symbol..."
          className="w-full bg-panel-2 border border-control rounded-xl px-3.5 py-2.5 text-sm outline-none focus:border-accent transition-colors mb-3"
        />

        <div role="status" className="sr-only">
          {status}
        </div>

        <div className="max-h-80 overflow-y-auto space-y-1">
          {searching && <div className="text-xs text-dim px-2 py-3">Searching...</div>}

          {!searching && query && results.length === 0 && (
            <div className="text-xs text-dim px-2 py-3">No matches for "{query}"</div>
          )}

          {results.map((r) => (
            <div
              key={r.symbol}
              className="flex items-center justify-between px-2 py-2.5 rounded-xl hover:bg-panel-2"
            >
              <div className="flex items-center gap-3 min-w-0">
                <TickerAvatar symbol={r.symbol} size={30} />
                <div className="min-w-0">
                  <div className="text-sm font-medium">{r.symbol}</div>
                  <div className="text-xs text-dim truncate">{r.name}</div>
                </div>
              </div>
              <button
                onClick={() => handleAdd(r)}
                disabled={addingSymbol === r.symbol}
                aria-label={`Add ${r.symbol} to the watchlist`}
                className="text-xs bg-accent hover:brightness-110 transition-colors text-accent-ink px-3 py-1.5 rounded-md disabled:opacity-50 shrink-0"
              >
                {addingSymbol === r.symbol ? "Adding..." : "Add"}
              </button>
            </div>
          ))}
        </div>
    </Modal>
  );
}

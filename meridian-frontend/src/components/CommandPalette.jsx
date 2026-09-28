import { useEffect, useId, useMemo, useRef, useState } from "react";
import { getTickers } from "../lib/api";
import Icon from "./Icon";
import Modal from "./Modal";
import TickerAvatar from "./TickerAvatar";

const MAX_STOCKS = 8;

// Best matches first: exact symbol, then symbol prefix, then name word prefix, then anywhere.
function rank(ticker, q) {
  const symbol = ticker.symbol.toLowerCase();
  const name = (ticker.name ?? "").toLowerCase();
  if (symbol === q) return 0;
  if (symbol.startsWith(q)) return 1;
  if (name.split(/\s+/).some((word) => word.startsWith(q))) return 2;
  if (symbol.includes(q) || name.includes(q)) return 3;
  return -1;
}

export function matchTickers(tickers, query) {
  const q = query.trim().toLowerCase();
  if (!q) return tickers.slice(0, MAX_STOCKS);
  return tickers
    .map((t) => [t, rank(t, q)])
    .filter(([, r]) => r >= 0)
    .sort((a, b) => a[1] - b[1] || a[0].symbol.localeCompare(b[0].symbol))
    .slice(0, MAX_STOCKS)
    .map(([t]) => t);
}

// Search anything from anywhere (⌘K / Ctrl+K, or "/"): stocks, pages and actions. The input is
// an ARIA combobox: arrow keys move through the results, Enter runs one, Escape closes.
export default function CommandPalette({ commands, onOpenStock, onClose }) {
  const [query, setQuery] = useState("");
  const [tickers, setTickers] = useState([]);
  const [active, setActive] = useState(0);
  const listId = useId();
  const listRef = useRef(null);

  useEffect(() => {
    getTickers().then(setTickers).catch(() => {}); // pages and actions still work
  }, []);

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    const stocks = matchTickers(tickers, query).map((t) => ({
      id: `stock-${t.symbol}`,
      group: "Stocks",
      label: t.symbol,
      detail: t.name,
      ticker: t,
      run: () => onOpenStock(t),
    }));
    const others = commands.filter(
      (c) => !q || c.label.toLowerCase().includes(q) || c.keywords?.some((k) => k.includes(q))
    );
    return [...stocks, ...others];
  }, [tickers, query, commands, onOpenStock]);

  useEffect(() => setActive(0), [query]);

  useEffect(() => {
    listRef.current?.querySelector(`[data-index="${active}"]`)?.scrollIntoView?.({ block: "nearest" });
  }, [active]);

  function run(item) {
    onClose();
    item.run();
  }

  function handleKeyDown(e) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive((i) => (results.length ? (i + 1) % results.length : 0));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((i) => (results.length ? (i - 1 + results.length) % results.length : 0));
    } else if (e.key === "Enter" && results[active]) {
      e.preventDefault();
      run(results[active]);
    }
  }

  const optionId = (i) => `${listId}-option-${i}`;
  let lastGroup = null;

  return (
    <Modal title="Search" onClose={onClose}>
      <div className="flex items-center gap-3 h-[52px] px-4 rounded-2xl bg-panel-2 border border-control focus-within:border-accent has-[input:focus-visible]:ring-2 has-[input:focus-visible]:ring-accent">
        <Icon name="search" size={18} className="text-dim" />
        <input
          data-autofocus
          data-ring-parent
          role="combobox"
          aria-label="Search stocks, pages and actions"
          aria-expanded="true"
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={results[active] ? optionId(active) : undefined}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Stocks, pages, actions…"
          autoComplete="off"
          spellCheck={false}
          className="flex-1 min-w-0 bg-transparent outline-none text-[15px] placeholder:text-dim"
        />
      </div>

      <ul id={listId} ref={listRef} role="listbox" aria-label="Results" className="mt-3 max-h-[52vh] overflow-y-auto -mx-2">
        {results.length === 0 && <li className="px-3 py-6 text-center text-sm text-dim">Nothing matches “{query}”</li>}
        {results.map((item, i) => {
          const header = item.group !== lastGroup ? item.group : null;
          lastGroup = item.group;
          return (
            <li key={item.id} role="presentation">
              {header && (
                <div role="presentation" className="px-3 pt-3 pb-1 text-[11px] font-semibold uppercase tracking-wide text-dim">
                  {header}
                </div>
              )}
              <div
                id={optionId(i)}
                role="option"
                aria-selected={i === active}
                data-index={i}
                onMouseMove={() => setActive(i)}
                onClick={() => run(item)}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-2xl cursor-pointer ${i === active ? "bg-panel-2" : ""}`}
              >
                {item.ticker ? (
                  <TickerAvatar symbol={item.ticker.symbol} size={32} />
                ) : (
                  <span className="w-8 h-8 rounded-full bg-accent-dim text-accent flex items-center justify-center shrink-0">
                    <Icon name={item.icon} size={16} />
                  </span>
                )}
                <span className="flex-1 min-w-0">
                  <span className="block text-sm font-semibold">{item.label}</span>
                  {item.detail && <span className="block text-xs text-muted truncate">{item.detail}</span>}
                </span>
                {i === active && <Icon name="right" size={16} className="text-dim" />}
              </div>
            </li>
          );
        })}
      </ul>
      <p className="hidden sm:block mt-3 text-xs text-dim">
        <kbd className="font-sans">↑</kbd> <kbd className="font-sans">↓</kbd> to move · <kbd className="font-sans">Enter</kbd> to open ·{" "}
        <kbd className="font-sans">Esc</kbd> to close
      </p>
    </Modal>
  );
}

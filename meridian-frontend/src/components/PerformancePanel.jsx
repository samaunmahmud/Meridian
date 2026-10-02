import { useEffect, useState } from "react";
import { getPerformance } from "../lib/api";
import { formatMoney } from "../lib/formatMoney";
import Icon from "./Icon";
import Skeleton from "./Skeleton";
import TickerAvatar from "./TickerAvatar";

function signed(value) {
  if (value == null) return "—";
  return `${value > 0 ? "+" : value < 0 ? "−" : ""}${formatMoney(Math.abs(value))}`;
}

function tone(value) {
  return value > 0 ? "text-gain" : value < 0 ? "text-loss" : "text-bone";
}

function Stat({ label, value, sub, valueClass = "" }) {
  return (
    <div className="rounded-2xl bg-panel-2 p-4 min-w-0">
      <dt className="text-xs font-medium text-muted">{label}</dt>
      <dd className={`font-mono text-xl mt-1.5 truncate ${valueClass}`}>{value}</dd>
      {sub && <dd className="text-xs text-dim mt-1 truncate">{sub}</dd>}
    </div>
  );
}

function TradeLine({ label, trade }) {
  return (
    <div className="flex items-center justify-between gap-3 py-2">
      <span className="text-sm text-muted">{label}</span>
      {trade ? (
        <span className="text-sm text-right">
          <span className="font-semibold">{trade.symbol}</span>
          <span className="text-dim"> · sold {trade.quantity}</span>
          <span className={`font-mono ml-2 ${tone(trade.realizedPnL)}`}>{signed(trade.realizedPnL)}</span>
        </span>
      ) : (
        <span className="text-sm text-dim">No sells yet</span>
      )}
    </div>
  );
}

// Profit and loss across the account: what selling has locked in (realized), what open
// positions are up or down (unrealized), fees, win rate and a per-stock breakdown.
export default function PerformancePanel({ refreshKey, onSelect }) {
  const [data, setData] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let live = true;
    getPerformance()
      .then((p) => live && (setData(p), setFailed(false)))
      .catch(() => live && setFailed(true));
    return () => {
      live = false;
    };
  }, [refreshKey]);

  if (failed && !data) return null;

  return (
    <section className="bg-panel rounded-[28px] p-5 sm:p-6" aria-labelledby="performance-heading">
      <div className="flex items-baseline justify-between mb-4 gap-3">
        <h2 id="performance-heading" className="text-lg font-bold">
          Performance
        </h2>
        {data && (
          <span className="text-[13px] text-muted">
            {data.filledOrders} {data.filledOrders === 1 ? "trade" : "trades"}
          </span>
        )}
      </div>

      {!data ? (
        <div className="grid grid-cols-2 gap-3">
          {[0, 1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-20" />
          ))}
        </div>
      ) : (
        <>
          <dl className="grid grid-cols-2 gap-3">
            <Stat label="Total P&L" value={signed(data.totalPnL)} valueClass={tone(data.totalPnL)} sub="Realized + unrealized" />
            <Stat label="Realized" value={signed(data.realizedPnL)} valueClass={tone(data.realizedPnL)} sub="Locked in by selling" />
            <Stat label="Unrealized" value={signed(data.unrealizedPnL)} valueClass={tone(data.unrealizedPnL)} sub="On shares you hold" />
            <Stat
              label="Win rate"
              value={data.winRate == null ? "—" : `${data.winRate}%`}
              sub={data.closedTrades ? `${data.winningTrades} of ${data.closedTrades} sells in profit` : "Sell to see it"}
            />
          </dl>

          <div className="mt-4 divide-y divide-line">
            <TradeLine label="Best trade" trade={data.bestTrade} />
            <TradeLine label="Worst trade" trade={data.worstTrade} />
            <div className="flex items-center justify-between gap-3 py-2">
              <span className="text-sm text-muted">Fees paid</span>
              <span className="font-mono text-sm">{formatMoney(data.feesPaid)}</span>
            </div>
            {data.dividendsReceived > 0 && (
              <div className="flex items-center justify-between gap-3 py-2">
                <span className="text-sm text-muted">Dividends received</span>
                <span className="font-mono text-sm text-gain">+{formatMoney(data.dividendsReceived)}</span>
              </div>
            )}
          </div>

          {data.bySymbol.length > 0 && (
            <>
              <h3 className="text-sm font-semibold mt-5 mb-2">By stock</h3>
              <ul className="space-y-1">
                {data.bySymbol.map((s) => (
                  <li key={s.symbol}>
                    <button
                      type="button"
                      onClick={() => onSelect?.({ symbol: s.symbol, name: s.name })}
                      className="w-full flex items-center gap-3 p-2 -mx-2 rounded-2xl text-left hover:bg-panel-2 transition-colors"
                    >
                      <TickerAvatar symbol={s.symbol} size={36} />
                      <span className="flex-1 min-w-0">
                        <span className="block text-sm font-semibold">{s.symbol}</span>
                        <span className="block text-xs text-muted truncate">
                          {signed(s.realizedPnL)} realized · {signed(s.unrealizedPnL)} open
                        </span>
                      </span>
                      <span className={`font-mono text-sm font-medium inline-flex items-center gap-1 ${tone(s.totalPnL)}`}>
                        {s.totalPnL !== 0 && <Icon name={s.totalPnL > 0 ? "up" : "down"} size={12} strokeWidth={2.2} />}
                        {signed(s.totalPnL)}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}
    </section>
  );
}

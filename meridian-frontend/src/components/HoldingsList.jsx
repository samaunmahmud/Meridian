import { formatNumber } from "../lib/formatMoney";
import Icon from "./Icon";
import TickerAvatar from "./TickerAvatar";

function GainLoss({ holding, className = "" }) {
  const isUp = holding.gainLoss >= 0;
  return (
    <div className={`${isUp ? "text-gain" : "text-loss"} ${className}`}>
      <div className="font-medium inline-flex items-center gap-1 justify-end">
        <Icon name={isUp ? "up" : "down"} size={12} strokeWidth={2.2} />
        {isUp ? "+" : ""}
        {formatNumber(holding.gainLoss)}
      </div>
      <div className="text-xs">
        {isUp ? "+" : ""}
        {holding.gainLossPct.toFixed(2)}%
      </div>
    </div>
  );
}

function Asset({ holding }) {
  return (
    <div className="flex items-center gap-3 min-w-0">
      <TickerAvatar symbol={holding.symbol} size={36} />
      <div className="min-w-0">
        <div className="font-semibold text-sm">{holding.symbol}</div>
        <div className="text-xs text-muted truncate max-w-[160px]">{holding.name}</div>
      </div>
    </div>
  );
}

// Phones: one card per position, so nothing needs sideways scrolling.
function HoldingCards({ holdings }) {
  return (
    <ul className="sm:hidden space-y-3">
      {holdings.map((h) => (
        <li key={h.symbol} className="rounded-2xl border border-line bg-panel-2/40 p-4">
          <div className="flex items-start justify-between gap-3">
            <Asset holding={h} />
            <div className="text-right font-mono text-sm shrink-0">
              <div className="font-medium">{formatNumber(h.marketValue)}</div>
              <GainLoss holding={h} />
            </div>
          </div>
          <dl className="grid grid-cols-3 gap-2 mt-3 pt-3 border-t border-line/70 text-xs">
            {[
              ["Qty", h.quantity, ""],
              ["Avg cost", formatNumber(h.avgCost), "text-muted"],
              ["Price", formatNumber(h.currentPrice), ""],
            ].map(([label, value, tone]) => (
              <div key={label} className="min-w-0">
                <dt className="text-dim">{label}</dt>
                <dd className={`font-mono mt-0.5 truncate ${tone}`}>{value}</dd>
              </div>
            ))}
          </dl>
        </li>
      ))}
    </ul>
  );
}

// Tablets and up: the full table.
function HoldingsTable({ holdings }) {
  return (
    <div className="hidden sm:block overflow-x-auto no-scrollbar -mx-1 px-1">
      <table className="w-full text-sm min-w-[560px]">
        <thead>
          <tr className="text-left text-dim text-xs">
            <th className="font-medium pb-3">Asset</th>
            <th className="font-medium pb-3 text-right">Qty</th>
            <th className="font-medium pb-3 text-right">Avg cost</th>
            <th className="font-medium pb-3 text-right">Price</th>
            <th className="font-medium pb-3 text-right">Value</th>
            <th className="font-medium pb-3 text-right">P&amp;L</th>
          </tr>
        </thead>
        <tbody className="font-mono">
          {holdings.map((h) => (
            <tr key={h.symbol} className="border-t border-line/70">
              <td className="py-3.5 font-sans">
                <Asset holding={h} />
              </td>
              <td className="py-3.5 text-right">{h.quantity}</td>
              <td className="py-3.5 text-right text-muted">{formatNumber(h.avgCost)}</td>
              <td className="py-3.5 text-right">{formatNumber(h.currentPrice)}</td>
              <td className="py-3.5 text-right font-medium">{formatNumber(h.marketValue)}</td>
              <td className="py-3.5 text-right">
                <GainLoss holding={h} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function HoldingsList({ holdings }) {
  return (
    <>
      <HoldingCards holdings={holdings} />
      <HoldingsTable holdings={holdings} />
    </>
  );
}

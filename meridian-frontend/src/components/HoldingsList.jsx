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

// Phones: one row per position, as a banking app lists them: what it is and how much you hold on the
// left, its value and how it has done on the right.
function HoldingCards({ holdings }) {
  return (
    <ul className="sm:hidden divide-y divide-line">
      {holdings.map((h) => (
        <li key={h.symbol} className="flex items-center gap-3 py-3">
          <TickerAvatar symbol={h.symbol} size={40} />
          <div className="flex-1 min-w-0">
            <div className="text-[15px] font-semibold truncate">{h.name}</div>
            <div className="text-[13px] text-muted truncate">
              <span>{h.quantity}</span> <span>{h.symbol}</span> · avg <span>{formatNumber(h.avgCost)}</span>
            </div>
          </div>
          <div className="text-right font-mono text-sm shrink-0">
            <div className="font-semibold">{formatNumber(h.marketValue)}</div>
            <div className="text-[11px] text-dim">
              at <span>{formatNumber(h.currentPrice)}</span>
            </div>
            <GainLoss holding={h} className="text-[13px]" />
          </div>
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

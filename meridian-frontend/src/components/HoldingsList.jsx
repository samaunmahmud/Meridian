import { formatNumber } from "../lib/formatMoney";
import Icon from "./Icon";
import TickerAvatar from "./TickerAvatar";

function GainLoss({ holding }) {
  const isUp = holding.gainLoss >= 0;
  return (
    <div className={`${isUp ? "text-gain" : "text-loss"} text-[13px] font-medium inline-flex items-center gap-1 justify-end`}>
      <Icon name={isUp ? "up" : "down"} size={12} strokeWidth={2.2} />
      <span>
        {isUp ? "+" : ""}
        {formatNumber(holding.gainLoss)}
      </span>
      <span>
        {isUp ? "+" : ""}
        {holding.gainLossPct.toFixed(2)}%
      </span>
    </div>
  );
}

// One row per position at every size, as Revolut lists them: what it is and how much you hold on the left,
// what it is worth and how it has done on the right. The price and average cost ride along in the subtitle.
export default function HoldingsList({ holdings }) {
  return (
    <ul className="divide-y divide-line">
      {holdings.map((h) => (
        <li key={h.symbol} className="flex items-center gap-3.5 py-3.5">
          <TickerAvatar symbol={h.symbol} size={44} />
          <div className="flex-1 min-w-0">
            <div className="text-[15px] font-semibold truncate">{h.name}</div>
            <div className="text-[13px] text-muted truncate">
              <span>{h.quantity}</span> <span>{h.symbol}</span> · avg <span>{formatNumber(h.avgCost)}</span>
              <span className="hidden sm:inline">
                {" "}· now <span>{formatNumber(h.currentPrice)}</span>
              </span>
            </div>
          </div>
          <div className="text-right font-mono shrink-0">
            <div className="text-[15px] font-semibold">${formatNumber(h.marketValue)}</div>
            <GainLoss holding={h} />
          </div>
        </li>
      ))}
    </ul>
  );
}

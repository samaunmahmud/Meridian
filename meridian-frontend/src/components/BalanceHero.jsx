import { useEffect, useState } from "react";
import { getPortfolio } from "../lib/api";
import { formatMoney } from "../lib/formatMoney";
import { useAnimatedNumber } from "../lib/useAnimatedNumber";
import Amount from "./Amount";
import Icon from "./Icon";
import Skeleton from "./Skeleton";

const STARTING_CASH = 10000;

// A round icon button with its label underneath, as in a banking app's action row.
function Action({ icon, label, onClick }) {
  return (
    <button type="button" onClick={onClick} className="group flex flex-col items-center gap-2 min-w-[64px]">
      <span className="w-12 h-12 sm:w-[52px] sm:h-[52px] rounded-full bg-panel text-bone flex items-center justify-center group-hover:bg-accent group-hover:text-accent-ink transition-colors">
        <Icon name={icon} size={20} strokeWidth={2} />
      </span>
      <span className="text-[13px] font-medium whitespace-nowrap">{label}</span>
    </button>
  );
}

// The top of Home, as in a banking app: the total value in large type straight on the page over a soft
// coloured glow, how it has done since the start, and the everyday actions as round buttons.
// (The value curve lives on the Portfolio tab.)
export default function BalanceHero({ refreshKey, onBuy, onSell, onExchange, onAddMoney }) {
  const [portfolio, setPortfolio] = useState(null);
  const animatedTotal = useAnimatedNumber(portfolio?.totalValue ?? 0);

  useEffect(() => {
    getPortfolio().then(setPortfolio).catch(() => {});
  }, [refreshKey]);

  if (!portfolio) return <Skeleton className="h-[260px] w-full rounded-[28px]" />;

  const sinceStart = portfolio.totalValue - STARTING_CASH;
  const sinceStartPct = (sinceStart / STARTING_CASH) * 100;
  const isUp = sinceStart >= 0;

  return (
    <section aria-label="Balance" className="relative isolate pt-6 pb-2 sm:pt-10 fade-in">
      <div aria-hidden="true" className="balance-glow" />
      <div className="text-center">
        <div className="inline-flex items-center gap-1.5 text-[13px] font-medium text-muted px-3 py-1 rounded-full bg-panel/70 backdrop-blur">
          Investing · USD
        </div>
        <Amount value={animatedTotal} className="block font-display text-[48px] sm:text-[64px] leading-[1.05] mt-3" />
        <div className={`text-sm font-semibold mt-1.5 ${isUp ? "text-gain" : "text-loss"}`}>
          {isUp ? "+" : "−"}
          {formatMoney(Math.abs(sinceStart))} ({isUp ? "+" : "−"}
          {Math.abs(sinceStartPct).toFixed(2)}%) <span className="text-muted font-medium">all time</span>
        </div>
        <dl className="flex justify-center gap-6 mt-3 text-[13px]">
          <div className="flex gap-1.5">
            <dt className="text-muted">Invested</dt>
            <dd className="font-mono font-semibold">{formatMoney(portfolio.holdingsValue)}</dd>
          </div>
          <div className="flex gap-1.5">
            <dt className="text-muted">Cash</dt>
            <dd className="font-mono font-semibold">{formatMoney(portfolio.cashBalance)}</dd>
          </div>
        </dl>
      </div>

      <div className="flex justify-center gap-4 sm:gap-8 mt-8">
        <Action icon="plus" label="Add money" onClick={onAddMoney} />
        <Action icon="swap" label="Exchange" onClick={onExchange} />
        <Action icon="up" label="Buy" onClick={onBuy} />
        <Action icon="down" label="Sell" onClick={onSell} />
      </div>
    </section>
  );
}

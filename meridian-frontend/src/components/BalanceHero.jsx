import { useEffect, useState } from "react";
import { getPortfolio, getPortfolioHistory } from "../lib/api";
import { formatMoney } from "../lib/formatMoney";
import { useAnimatedNumber } from "../lib/useAnimatedNumber";
import Icon from "./Icon";
import Skeleton from "./Skeleton";
import Sparkline from "./Sparkline";

const STARTING_CASH = 10000;

// A round icon button with its label underneath, as in a banking app's action row.
function Action({ icon, label, onClick }) {
  return (
    <button type="button" onClick={onClick} className="group flex flex-col items-center gap-2 min-w-[64px]">
      <span className="w-12 h-12 sm:w-[52px] sm:h-[52px] rounded-full bg-panel-2 text-bone flex items-center justify-center group-hover:bg-accent group-hover:text-accent-ink transition-colors">
        <Icon name={icon} size={20} strokeWidth={2} />
      </span>
      <span className="text-[13px] font-medium whitespace-nowrap">{label}</span>
    </button>
  );
}

// The top of Home: the total value in large type, how it has done since the start, its curve,
// and the everyday actions (buy, sell, exchange, add money) as round buttons.
export default function BalanceHero({ refreshKey, onBuy, onSell, onExchange, onAddMoney }) {
  const [portfolio, setPortfolio] = useState(null);
  const [history, setHistory] = useState([]);
  const animatedTotal = useAnimatedNumber(portfolio?.totalValue ?? 0);

  useEffect(() => {
    getPortfolio().then(setPortfolio).catch(() => {});
    getPortfolioHistory().then(setHistory).catch(() => setHistory([]));
  }, [refreshKey]);

  if (!portfolio) return <Skeleton className="h-[260px] w-full rounded-[28px]" />;

  const sinceStart = portfolio.totalValue - STARTING_CASH;
  const sinceStartPct = (sinceStart / STARTING_CASH) * 100;
  const isUp = sinceStart >= 0;
  const values = history.map((h) => h.totalValue);

  return (
    <section aria-label="Balance" className="bg-panel rounded-[28px] p-6 sm:p-8 fade-in">
      <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-6">
        <div className="text-center lg:text-left">
          <div className="text-sm font-medium text-muted">Total balance</div>
          <div className="font-display text-[44px] sm:text-[56px] leading-[1.05] mt-1" style={{ letterSpacing: "-0.035em" }}>
            {formatMoney(animatedTotal)}
          </div>
          <div className={`text-sm font-semibold mt-1.5 ${isUp ? "text-gain" : "text-loss"}`}>
            {isUp ? "+" : "−"}
            {formatMoney(Math.abs(sinceStart))} ({isUp ? "+" : "−"}
            {Math.abs(sinceStartPct).toFixed(2)}%) <span className="text-muted font-medium">all time</span>
          </div>
        </div>

        <dl className="flex justify-center lg:justify-end gap-8 text-center lg:text-right">
          <div>
            <dt className="text-xs text-muted">Invested</dt>
            <dd className="font-mono font-semibold mt-0.5">{formatMoney(portfolio.holdingsValue)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Cash</dt>
            <dd className="font-mono font-semibold mt-0.5">{formatMoney(portfolio.cashBalance)}</dd>
          </div>
        </dl>
      </div>

      {values.length >= 2 && (
        <div className="mt-6 -mx-1">
          <Sparkline values={values} positive={isUp} width={1000} height={90} fluid />
        </div>
      )}

      <div className="flex justify-center lg:justify-start gap-3 sm:gap-6 mt-7">
        <Action icon="plus" label="Buy" onClick={onBuy} />
        <Action icon="down" label="Sell" onClick={onSell} />
        <Action icon="swap" label="Exchange" onClick={onExchange} />
        <Action icon="wallet" label="Add money" onClick={onAddMoney} />
      </div>
    </section>
  );
}

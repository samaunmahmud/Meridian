import { useEffect, useState } from "react";
import { getPortfolio } from "../lib/api";
import { useAnimatedNumber } from "../lib/useAnimatedNumber";
import Skeleton from "./Skeleton";

const STARTING_CASH = 10000;

export default function AccountSummary({ refreshKey }) {
  const [portfolio, setPortfolio] = useState(null);
  const animatedTotal = useAnimatedNumber(portfolio?.totalValue ?? 0);

  useEffect(() => {
    getPortfolio().then(setPortfolio).catch(() => {});
  }, [refreshKey]);

  if (!portfolio) {
    return (
      <div className="px-5 pt-5 pb-4 border-b border-line space-y-2">
        <Skeleton className="h-3 w-20" />
        <Skeleton className="h-7 w-32" />
        <Skeleton className="h-3 w-28" />
      </div>
    );
  }

  const sinceStart = portfolio.totalValue - STARTING_CASH;
  const sinceStartPct = ((sinceStart / STARTING_CASH) * 100).toFixed(2);
  const isUp = sinceStart >= 0;

  return (
    <div className="px-5 pt-5 pb-4 border-b border-line">
      <div className="text-xs text-dim mb-1.5">Account value</div>
      <div className="text-2xl font-mono font-medium mb-1.5">
        ${animatedTotal.toFixed(2)}
      </div>
      <div className={`text-xs font-mono mb-4 ${isUp ? "text-gain" : "text-loss"}`}>
        {isUp ? "+" : ""}{sinceStart.toFixed(2)} ({isUp ? "+" : ""}{sinceStartPct}%) since start
      </div>

      <div className="grid grid-cols-2 gap-2">
        <div className="bg-panel-2 rounded-lg p-2.5">
          <div className="text-[10px] text-dim mb-0.5">Cash</div>
          <div className="text-xs font-mono">${portfolio.cashBalance.toFixed(0)}</div>
        </div>
        <div className="bg-panel-2 rounded-lg p-2.5">
          <div className="text-[10px] text-dim mb-0.5">Invested</div>
          <div className="text-xs font-mono">${portfolio.holdingsValue.toFixed(0)}</div>
        </div>
      </div>
    </div>
  );
}

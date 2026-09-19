import { useEffect, useState } from "react";
import { getPortfolio } from "../lib/api";
import { formatMoney } from "../lib/formatMoney";
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
      <div className="bg-panel-2 rounded-[20px] p-4 space-y-2.5">
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
    <div className="bg-panel-2 rounded-[20px] p-4">
      <div className="text-xs font-medium text-muted mb-1.5">Total balance</div>
      <div className="font-display text-[28px] leading-none" style={{ letterSpacing: "-0.045em" }}>
        {formatMoney(animatedTotal)}
      </div>
      <div className={`text-xs font-mono font-medium mt-2.5 ${isUp ? "text-gain" : "text-loss"}`}>
        {isUp ? "+" : ""}
        {formatMoney(sinceStart)} ({isUp ? "+" : ""}
        {sinceStartPct}%)
      </div>
      <div className="text-[11px] text-dim mt-0.5">since start</div>
    </div>
  );
}

import { useMemo } from "react";
import StockDetails from "./StockDetails";
import StockHero from "./StockHero";
import TradePanel from "./TradePanel";

// One stock's own screen: price and chart, today's move and your position, and (on wide
// screens) the trade form beside them. On phones Buy/Sell open the trade sheet instead.
export default function StockPage({ ticker, liveUpdate, refreshKey, onTrade, onOrderPlaced }) {
  // A new object only when the stock changes, so the form is not reset on every render.
  const prefill = useMemo(() => ({ symbol: ticker.symbol, type: "BUY", nonce: ticker.symbol }), [ticker.symbol]);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_380px] gap-6 items-start">
      <div className="space-y-6 min-w-0">
        <StockHero ticker={ticker} liveUpdate={liveUpdate} onTrade={onTrade} tradeBesideChart />
        <StockDetails ticker={ticker} liveUpdate={liveUpdate} refreshKey={refreshKey} />
      </div>
      <div className="hidden lg:block lg:sticky lg:top-6">
        <TradePanel prefill={prefill} refreshKey={refreshKey} onOrderPlaced={onOrderPlaced} />
      </div>
    </div>
  );
}

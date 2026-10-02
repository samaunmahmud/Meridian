import { useMemo } from "react";
import CompanyNews from "./CompanyNews";
import StockDetails from "./StockDetails";
import StockDividends from "./StockDividends";
import StockHero from "./StockHero";
import TradePanel from "./TradePanel";

// One stock's own screen: price and chart, today's move and your position, its dividends and news, and (on wide
// screens) the trade form beside them. On phones Buy/Sell open the trade sheet instead.
export default function StockPage({ ticker, liveUpdate, refreshKey, onTrade, onOrderPlaced }) {
  // A new object only when the stock changes, so the form is not reset on every render.
  const prefill = useMemo(() => ({ symbol: ticker.symbol, type: "BUY", nonce: ticker.symbol }), [ticker.symbol]);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_380px] gap-6 items-start">
      <div className="space-y-6 min-w-0">
        <StockHero ticker={ticker} liveUpdate={liveUpdate} onTrade={onTrade} tradeBesideChart />
        <StockDetails ticker={ticker} liveUpdate={liveUpdate} refreshKey={refreshKey} onOrdersChanged={onOrderPlaced} />
        <StockDividends ticker={ticker} refreshKey={refreshKey} />
        <CompanyNews ticker={ticker} />
      </div>
      <div className="hidden lg:block lg:sticky lg:top-6">
        <TradePanel prefill={prefill} refreshKey={refreshKey} onOrderPlaced={onOrderPlaced} />
      </div>

      {/* Phones and tablets: Buy and Sell stay at the bottom of the screen, where the tab bar usually is. */}
      <div className="lg:hidden fixed bottom-0 inset-x-0 z-30 bg-ink/85 backdrop-blur-xl border-t border-line px-4 pt-3 pb-[max(env(safe-area-inset-bottom),12px)] grid grid-cols-2 gap-3">
        <button
          type="button"
          onClick={() => onTrade(ticker.symbol, "BUY")}
          className="h-12 rounded-full bg-accent text-accent-ink text-[15px] font-semibold hover:brightness-110 active:scale-[0.98] transition-all"
        >
          Buy {ticker.symbol}
        </button>
        <button
          type="button"
          onClick={() => onTrade(ticker.symbol, "SELL")}
          className="h-12 rounded-full bg-panel-2 text-bone text-[15px] font-semibold hover:brightness-110 active:scale-[0.98] transition-all"
        >
          Sell {ticker.symbol}
        </button>
      </div>
    </div>
  );
}

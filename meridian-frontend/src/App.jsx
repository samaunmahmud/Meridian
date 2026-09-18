import { useEffect, useState } from "react";
import AuthPage from "./components/AuthPage";
import Sidebar from "./components/Sidebar";
import Topbar from "./components/Topbar";
import StockHero from "./components/StockHero";
import Watchlist from "./components/Watchlist";
import PortfolioSummary from "./components/PortfolioSummary";
import PortfolioAllocation from "./components/PortfolioAllocation";
import EquityChart from "./components/EquityChart";
import TradePanel from "./components/TradePanel";
import OrderHistory from "./components/OrderHistory";
import AlertsPanel from "./components/AlertsPanel";
import ToastContainer from "./components/ToastContainer";
import { getSession, clearSession } from "./lib/api";
import { usePriceSocket } from "./lib/usePriceSocket";
import { showToast } from "./lib/toast";

export default function App() {
  const [session, setSession] = useState(undefined);
  const [activeTab, setActiveTab] = useState("overview");
  const [selectedTicker, setSelectedTicker] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [tradePrefill, setTradePrefill] = useState(null);

  const liveUpdate = usePriceSocket();

  useEffect(() => {
    const existing = getSession();
    setSession(existing ? { email: existing.email } : false);
  }, []);

  useEffect(() => {
    function handleExpired() {
      setSession(false);
    }
    window.addEventListener("meridian:session-expired", handleExpired);
    return () => window.removeEventListener("meridian:session-expired", handleExpired);
  }, []);

  // A triggered alert arrives over the same WebSocket as price updates,
  // distinguished by `kind`. Surface it as a toast the moment it happens.
  useEffect(() => {
    if (!liveUpdate || liveUpdate.kind !== "ALERT_TRIGGERED") return;
    const dir = liveUpdate.direction === "ABOVE" ? "rose above" : "fell below";
    showToast(
      "success",
      `${liveUpdate.symbol} ${dir} $${liveUpdate.targetPrice.toFixed(2)} — now $${liveUpdate.triggeredPrice.toFixed(2)}`
    );
  }, [liveUpdate]);

  function handleLogout() {
    clearSession();
    setSession(false);
  }

  function handleOrderPlaced() {
    setRefreshKey((k) => k + 1);
  }

  function handleTrade(symbol, type) {
    setTradePrefill({ symbol, type, nonce: Date.now() });
    setActiveTab("portfolio");
  }

  if (session === undefined) return null;
  if (!session) return <AuthPage onAuthenticated={(email) => setSession({ email })} />;

  return (
    <div className="flex min-h-screen bg-ink text-bone">
      <ToastContainer />
      <Sidebar
        activeTab={activeTab}
        onTabChange={setActiveTab}
        userEmail={session.email}
        onLogout={handleLogout}
        refreshKey={refreshKey}
      />

      <div className="flex-1 min-w-0">
        <Topbar />

        {activeTab === "overview" && (
          <main key="overview" className="grid grid-cols-[1fr_340px] gap-6 p-8 max-w-6xl fade-in">
            <StockHero ticker={selectedTicker} liveUpdate={liveUpdate} onTrade={handleTrade} />
            <Watchlist
              selectedSymbol={selectedTicker?.symbol}
              onSelect={setSelectedTicker}
              liveUpdate={liveUpdate}
            />
          </main>
        )}

        {activeTab === "portfolio" && (
          <main key="portfolio" className="grid grid-cols-[1fr_340px] gap-6 p-8 max-w-6xl fade-in">
            <div className="space-y-6">
              <PortfolioSummary refreshKey={refreshKey} />
              <EquityChart refreshKey={refreshKey} />
              <OrderHistory refreshKey={refreshKey} />
            </div>
            <div className="space-y-6">
              <TradePanel onOrderPlaced={handleOrderPlaced} prefill={tradePrefill} />
              <PortfolioAllocation refreshKey={refreshKey} />
            </div>
          </main>
        )}

        {activeTab === "alerts" && <AlertsPanel />}
      </div>
    </div>
  );
}

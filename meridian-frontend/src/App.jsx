import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import AuthPage from "./components/AuthPage";
import Sidebar, { MobileNav } from "./components/Sidebar";
import Topbar from "./components/Topbar";
import OverviewStrip from "./components/OverviewStrip";
import StockHero from "./components/StockHero";
import Watchlist from "./components/Watchlist";
import PortfolioSummary from "./components/PortfolioSummary";
import PortfolioAllocation from "./components/PortfolioAllocation";
import EquityChart from "./components/EquityChart";
import TradePanel from "./components/TradePanel";
import OrderHistory from "./components/OrderHistory";
import AlertsPanel from "./components/AlertsPanel";
import AccountsPanel from "./components/AccountsPanel";
import ActivityFeed from "./components/ActivityFeed";
import RecurringOrdersPanel from "./components/RecurringOrdersPanel";
import ToastContainer from "./components/ToastContainer";
import { getSession, clearSession } from "./lib/api";
import { usePriceSocket } from "./lib/usePriceSocket";
import { showToast } from "./lib/toast";

const TITLES = {
  overview: "Overview",
  portfolio: "Portfolio",
  alerts: "Alerts",
  accounts: "Accounts",
  activity: "Activity",
};

// Shared page gutter: 16px on phones, 24px on tablets, 32px on desktop.
const PAGE_PADDING = "px-4 sm:px-6 lg:px-8 pb-8 max-w-[1240px]";

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

  // A pending LIMIT/STOP_LOSS order fills asynchronously, whenever a later
  // price tick satisfies it — this is the only way the user finds out
  // without polling. Also bump refreshKey so Order History/Portfolio pick
  // it up immediately.
  useEffect(() => {
    if (!liveUpdate || liveUpdate.kind !== "ORDER_FILLED") return;
    showToast(
      "success",
      `Order filled: ${liveUpdate.type === "BUY" ? "bought" : "sold"} ${liveUpdate.quantity} ${liveUpdate.symbol} @ $${liveUpdate.price.toFixed(2)}`
    );
    setRefreshKey((k) => k + 1);
  }, [liveUpdate]);

  useEffect(() => {
    if (!liveUpdate || liveUpdate.kind !== "RECURRING_ORDER_EXECUTED") return;
    showToast(
      "success",
      `Recurring buy executed: ${liveUpdate.quantity} ${liveUpdate.symbol} @ $${liveUpdate.price.toFixed(2)}`
    );
    setRefreshKey((k) => k + 1);
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

      <div className="flex-1 min-w-0 pb-24 lg:pb-0">
        <Topbar title={TITLES[activeTab]} onLogout={handleLogout} />

        <AnimatePresence mode="wait">
          <motion.div
            key={activeTab}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.18, ease: "easeOut" }}
          >
            {activeTab === "overview" && (
              <main className={`${PAGE_PADDING} space-y-6`}>
                <OverviewStrip refreshKey={refreshKey} />
                <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_360px] gap-6">
                  <StockHero ticker={selectedTicker} liveUpdate={liveUpdate} onTrade={handleTrade} />
                  <Watchlist
                    selectedSymbol={selectedTicker?.symbol}
                    onSelect={setSelectedTicker}
                    liveUpdate={liveUpdate}
                  />
                </div>
              </main>
            )}

            {activeTab === "portfolio" && (
              <main className={`${PAGE_PADDING} grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_360px] gap-6`}>
                <div className="space-y-6 min-w-0">
                  <PortfolioSummary refreshKey={refreshKey}>
                    <EquityChart refreshKey={refreshKey} />
                  </PortfolioSummary>
                  <OrderHistory refreshKey={refreshKey} />
                </div>
                <div className="space-y-6 min-w-0">
                  <TradePanel onOrderPlaced={handleOrderPlaced} prefill={tradePrefill} />
                  <PortfolioAllocation refreshKey={refreshKey} />
                  <RecurringOrdersPanel refreshKey={refreshKey} />
                </div>
              </main>
            )}

            {activeTab === "alerts" && <AlertsPanel />}
            {activeTab === "accounts" && <AccountsPanel />}
            {activeTab === "activity" && <ActivityFeed refreshKey={refreshKey} />}
          </motion.div>
        </AnimatePresence>
      </div>

      <MobileNav activeTab={activeTab} onTabChange={setActiveTab} />
    </div>
  );
}

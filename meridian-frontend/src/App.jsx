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
import VerifyEmailBanner from "./components/VerifyEmailBanner";
import { getMe, logout, verifyEmail } from "./lib/api";
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

// Links in emails open the site as /?reset=<token> or /?verify=<token>. Read
// them once, when the page loads, and take the token out of the address bar
// straight away so it isn't left in the browser history or a copied URL.
const LINK_PARAMS = (() => {
  const params = new URLSearchParams(window.location.search);
  const found = { reset: params.get("reset"), verify: params.get("verify") };
  if (found.reset || found.verify) {
    window.history.replaceState({}, "", window.location.pathname);
  }
  return found;
})();

export default function App() {
  const [session, setSession] = useState(undefined);
  const [resetToken, setResetToken] = useState(LINK_PARAMS.reset);
  const [linkNotice, setLinkNotice] = useState(null); // result of opening an email-verification link
  const [activeTab, setActiveTab] = useState("overview");
  const [selectedTicker, setSelectedTicker] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [tradePrefill, setTradePrefill] = useState(null);

  const liveUpdate = usePriceSocket(session ? session.email : null);

  // Ask the server whether the session cookie is still valid (the page can't
  // read the cookie itself).
  useEffect(() => {
    let cancelled = false;
    getMe()
      .then((me) => {
        if (!cancelled) setSession({ email: me.email, emailVerified: me.emailVerified });
      })
      .catch(() => {
        if (!cancelled) setSession(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Opened from the "confirm your email" link.
  useEffect(() => {
    if (!LINK_PARAMS.verify) return;
    verifyEmail(LINK_PARAMS.verify)
      .then((response) => {
        setLinkNotice({ kind: "success", message: response.message });
        getMe()
          .then((me) => setSession({ email: me.email, emailVerified: me.emailVerified }))
          .catch(() => {}); // not signed in: they will see the confirmation on the login page
      })
      .catch((err) => setLinkNotice({ kind: "error", message: err.message }));
  }, []);

  // Once signed in, show the result of that link as a toast.
  useEffect(() => {
    if (session && linkNotice) {
      showToast(linkNotice.kind, linkNotice.message);
      setLinkNotice(null);
    }
  }, [session, linkNotice]);

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

  // The server gives up on a pending order it can no longer pay for and
  // frees the cash/shares it had reserved — tell the user why.
  useEffect(() => {
    if (!liveUpdate || liveUpdate.kind !== "ORDER_REJECTED") return;
    showToast(
      "error",
      `Order rejected: ${liveUpdate.type === "BUY" ? "buy" : "sell"} ${liveUpdate.quantity} ${liveUpdate.symbol} — ${liveUpdate.reason}`
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

  // The cookie is HttpOnly, so only the server can delete it. If that call
  // fails, stay signed in and say so rather than pretending to log out.
  function handleLogout() {
    logout()
      .then(() => setSession(false))
      .catch(() => showToast("error", "Could not log out. Check your connection and try again."));
  }

  function handleOrderPlaced() {
    setRefreshKey((k) => k + 1);
  }

  function handleTrade(symbol, type) {
    setTradePrefill({ symbol, type, nonce: Date.now() });
    setActiveTab("portfolio");
  }

  if (session === undefined) return null;
  // Opening a reset link always shows the "choose a new password" form, even
  // if someone is signed in (the reset ends their session anyway).
  if (resetToken) {
    return (
      <AuthPage
        resetToken={resetToken}
        onAuthenticated={() => {}}
        onResetDone={() => {
          setResetToken(null);
          setSession(false);
        }}
      />
    );
  }
  if (!session) {
    return (
      <AuthPage
        notice={linkNotice}
        onAuthenticated={(response) => setSession({ email: response.email, emailVerified: response.emailVerified })}
      />
    );
  }

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
        {session.emailVerified === false && <VerifyEmailBanner email={session.email} />}

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
                  <TradePanel onOrderPlaced={handleOrderPlaced} prefill={tradePrefill} refreshKey={refreshKey} />
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

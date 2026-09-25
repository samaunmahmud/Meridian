import { useCallback, useEffect, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import AuthPage from "./components/AuthPage";
import Sidebar, { MobileNav } from "./components/Sidebar";
import Topbar from "./components/Topbar";
import BalanceHero from "./components/BalanceHero";
import InvestmentsList from "./components/InvestmentsList";
import StockPage from "./components/StockPage";
import Watchlist from "./components/Watchlist";
import TopMovers from "./components/TopMovers";
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
import ServiceUnavailable from "./components/ServiceUnavailable";
import Modal from "./components/Modal";
import AddMoneyModal from "./components/AddMoneyModal";
import ConvertModal from "./components/ConvertModal";
import { getMe, getTickers, getWallets, isServerUnavailable, logout, verifyEmail } from "./lib/api";
import { usePriceSocket } from "./lib/usePriceSocket";
import { showToast } from "./lib/toast";

const TITLES = {
  home: "Home",
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
  const [session, setSession] = useState(undefined); // undefined while asking, false when signed out
  const [unavailable, setUnavailable] = useState(false); // the server could not answer that question
  const [checking, setChecking] = useState(false);
  const [resetToken, setResetToken] = useState(LINK_PARAMS.reset);
  const [linkNotice, setLinkNotice] = useState(null); // result of opening an email-verification link
  const [activeTab, setActiveTab] = useState("home");
  const [stock, setStock] = useState(null); // the stock whose page is open, over the tab
  const [refreshKey, setRefreshKey] = useState(0);
  const [sheet, setSheet] = useState(null); // { kind: "trade" | "exchange" | "addMoney", ... }

  const liveUpdate = usePriceSocket(session ? session.email : null);

  // There is no router, so keep the page title in step with the tab for screen-reader
  // users and the browser history/tab strip.
  useEffect(() => {
    document.title = session ? `${stock ? stock.symbol : TITLES[activeTab]} · Meridian` : "Meridian";
  }, [session, activeTab, stock]);

  // A stock's page is a history entry, so the browser's (or the phone's) back gesture closes it.
  useEffect(() => {
    function handlePop(e) {
      setStock(e.state?.meridianStock ?? null);
    }
    window.addEventListener("popstate", handlePop);
    return () => window.removeEventListener("popstate", handlePop);
  }, []);

  // Ask the server whether the session cookie is still valid (the page can't
  // read the cookie itself). If the server is down it cannot tell us, and the
  // login page would be wrong: say it is unavailable and keep asking.
  const checkSession = useCallback(() => {
    setChecking(true);
    return getMe()
      .then((me) => {
        setUnavailable(false);
        setSession({ email: me.email, emailVerified: me.emailVerified });
      })
      .catch((err) => {
        if (isServerUnavailable(err)) {
          setUnavailable(true);
        } else {
          setUnavailable(false);
          setSession(false);
        }
      })
      .finally(() => setChecking(false));
  }, []);

  useEffect(() => {
    checkSession();
  }, [checkSession]);

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

  // Rows from different lists carry different fields (a holding has no exchange or asset type),
  // so look the full ticker up before showing its page.
  function openStock(item) {
    const show = (ticker) => {
      window.history.pushState({ meridianStock: ticker }, "", window.location.pathname);
      setStock(ticker);
      window.scrollTo?.(0, 0);
    };
    if (item.assetType && item.exchange) return show(item);
    getTickers()
      .then((list) => show(list.find((t) => t.symbol === item.symbol) ?? item))
      .catch(() => show(item));
  }

  function closeStock() {
    if (window.history.state?.meridianStock) window.history.back();
    else setStock(null);
  }

  function changeTab(tab) {
    if (stock) closeStock();
    setActiveTab(tab);
  }

  function handleTrade(symbol, type) {
    setSheet({ kind: "trade", prefill: { symbol, type, nonce: Date.now() } });
  }

  function openExchange() {
    getWallets()
      .then((wallets) => setSheet({ kind: "exchange", wallets }))
      .catch((err) => showToast("error", err.message));
  }

  function handleSheetOrderPlaced() {
    setSheet(null);
    handleOrderPlaced();
  }

  if (unavailable && !session) return <ServiceUnavailable onRetry={checkSession} retrying={checking} />;
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
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-[60] focus:px-4 focus:py-2 focus:rounded-lg focus:bg-accent focus:text-accent-ink focus:font-semibold"
      >
        Skip to main content
      </a>
      <ToastContainer />
      <Sidebar activeTab={activeTab} onTabChange={changeTab} userEmail={session.email} onLogout={handleLogout} />

      <div className="flex-1 min-w-0 pb-24 lg:pb-0">
        <Topbar
          title={stock ? stock.name : TITLES[activeTab]}
          onLogout={handleLogout}
          onBack={stock ? closeStock : undefined}
        />
        {session.emailVerified === false && <VerifyEmailBanner email={session.email} />}

        <AnimatePresence mode="wait">
          <motion.div
            key={stock ? `stock-${stock.symbol}` : activeTab}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.18, ease: "easeOut" }}
          >
            {stock && (
              <main id="main-content" tabIndex={-1} data-ring-parent className={PAGE_PADDING}>
                <StockPage
                  ticker={stock}
                  liveUpdate={liveUpdate}
                  refreshKey={refreshKey}
                  onTrade={handleTrade}
                  onOrderPlaced={handleOrderPlaced}
                />
              </main>
            )}

            {!stock && activeTab === "home" && (
              <main id="main-content" tabIndex={-1} data-ring-parent className={`${PAGE_PADDING} grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_380px] gap-6 items-start`}>
                <div className="space-y-6 min-w-0">
                  <BalanceHero
                    refreshKey={refreshKey}
                    onBuy={() => handleTrade(undefined, "BUY")}
                    onSell={() => handleTrade(undefined, "SELL")}
                    onExchange={openExchange}
                    onAddMoney={() => setSheet({ kind: "addMoney" })}
                  />
                  <InvestmentsList
                    refreshKey={refreshKey}
                    liveUpdate={liveUpdate}
                    onSelect={openStock}
                    onStart={() => handleTrade(undefined, "BUY")}
                  />
                  <TopMovers onSelect={openStock} liveUpdate={liveUpdate} />
                </div>
                <Watchlist onSelect={openStock} liveUpdate={liveUpdate} />
              </main>
            )}

            {!stock && activeTab === "portfolio" && (
              <main id="main-content" tabIndex={-1} data-ring-parent className={`${PAGE_PADDING} grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_360px] gap-6`}>
                <div className="space-y-6 min-w-0">
                  <PortfolioSummary refreshKey={refreshKey}>
                    <EquityChart refreshKey={refreshKey} />
                  </PortfolioSummary>
                  <OrderHistory refreshKey={refreshKey} />
                </div>
                <div className="space-y-6 min-w-0">
                  <TradePanel onOrderPlaced={handleOrderPlaced} refreshKey={refreshKey} />
                  <PortfolioAllocation refreshKey={refreshKey} />
                  <RecurringOrdersPanel refreshKey={refreshKey} />
                </div>
              </main>
            )}

            {!stock && activeTab === "alerts" && <AlertsPanel />}
            {!stock && activeTab === "accounts" && <AccountsPanel />}
            {!stock && activeTab === "activity" && <ActivityFeed refreshKey={refreshKey} />}
          </motion.div>
        </AnimatePresence>
      </div>

      <MobileNav activeTab={activeTab} onTabChange={changeTab} />

      {sheet?.kind === "trade" && (
        <Modal title="Trade" onClose={() => setSheet(null)}>
          <TradePanel bare prefill={sheet.prefill} refreshKey={refreshKey} onOrderPlaced={handleSheetOrderPlaced} />
        </Modal>
      )}
      {sheet?.kind === "exchange" && (
        <ConvertModal wallets={sheet.wallets} onClose={() => setSheet(null)} onConverted={handleOrderPlaced} />
      )}
      {sheet?.kind === "addMoney" && <AddMoneyModal onClose={() => setSheet(null)} onAdded={handleOrderPlaced} />}
    </div>
  );
}

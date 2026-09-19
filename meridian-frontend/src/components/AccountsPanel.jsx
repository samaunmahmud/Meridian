import { useEffect, useState } from "react";
import { getWallets, depositToWallet, withdrawFromWallet } from "../lib/api";
import { formatMoney, currencySymbol } from "../lib/formatMoney";
import { showToast } from "../lib/toast";
import Icon from "./Icon";
import Skeleton from "./Skeleton";
import ConvertModal from "./ConvertModal";

const CURRENCY_NAME = { USD: "US Dollar", EUR: "Euro", GBP: "British Pound" };
// Which categorical theme colour (--c-s1…) tints each currency badge.
const CURRENCY_SLOT = { USD: 1, EUR: 3, GBP: 2 };

function WalletCard({ wallet, onChanged }) {
  const [mode, setMode] = useState(null); // null | "deposit" | "withdraw"
  const [amount, setAmount] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!amount) return;
    setSubmitting(true);
    try {
      if (mode === "deposit") {
        await depositToWallet(wallet.currency, Number(amount));
        showToast("success", `Deposited ${formatMoney(Number(amount), wallet.currency)}`);
      } else {
        await withdrawFromWallet(wallet.currency, Number(amount));
        showToast("success", `Withdrew ${formatMoney(Number(amount), wallet.currency)}`);
      }
      setAmount("");
      setMode(null);
      onChanged();
    } catch (err) {
      showToast("error", err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="bg-panel border border-line rounded-[20px] p-6 transition-all hover:-translate-y-0.5 hover:shadow-lg hover:shadow-black/10">
      <div className="flex items-center justify-between mb-5">
        <div className="flex items-center gap-3">
          <div
            className="w-[42px] h-[42px] rounded-full flex items-center justify-center font-display font-semibold text-xl"
            style={{
              color: `var(--c-s${CURRENCY_SLOT[wallet.currency] ?? 8})`,
              backgroundColor: `color-mix(in srgb, var(--c-s${CURRENCY_SLOT[wallet.currency] ?? 8}) 16%, transparent)`,
            }}
          >
            {currencySymbol(wallet.currency).trim()}
          </div>
          <div>
            <div className="text-[15px] font-semibold leading-tight">{CURRENCY_NAME[wallet.currency] ?? wallet.currency}</div>
            <div className="text-xs font-mono text-muted">{wallet.currency}</div>
          </div>
        </div>
        {wallet.currency === "USD" && (
          <span className="text-xs font-medium px-2.5 py-1 rounded-full bg-accent-dim text-accent">Primary</span>
        )}
      </div>
      <div className="font-display text-[38px] leading-none mb-5" style={{ letterSpacing: "-0.04em" }}>
        {formatMoney(wallet.balance, wallet.currency)}
      </div>

      {mode ? (
        <form onSubmit={handleSubmit} className="flex gap-2">
          <input
            autoFocus
            type="number"
            min="0.01"
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="0.00"
            className="flex-1 min-w-0 bg-panel-2 border border-line rounded-xl px-3 py-2 text-sm font-mono outline-none focus:border-accent transition-colors"
          />
          <button
            type="submit"
            disabled={submitting}
            className="px-3 py-2 rounded-xl bg-accent hover:brightness-110 text-accent-ink text-xs font-medium disabled:opacity-50 transition-colors shrink-0"
          >
            {submitting ? "..." : "Confirm"}
          </button>
          <button type="button" onClick={() => setMode(null)} className="px-1 text-dim hover:text-bone text-xs shrink-0">
            Cancel
          </button>
        </form>
      ) : (
        <div className="flex gap-2">
          <button
            onClick={() => setMode("deposit")}
            className="flex-1 h-10 rounded-xl bg-accent-dim text-accent text-[13px] font-medium flex items-center justify-center gap-1.5 hover:brightness-110 transition-all"
          >
            <Icon name="plus" size={15} strokeWidth={2.2} />
            Deposit
          </button>
          <button
            onClick={() => setMode("withdraw")}
            className="flex-1 h-10 rounded-xl bg-panel-2 text-bone text-[13px] font-medium flex items-center justify-center gap-1.5 hover:brightness-110 transition-all"
          >
            <Icon name="up" size={15} strokeWidth={2} />
            Withdraw
          </button>
        </div>
      )}
    </div>
  );
}

export default function AccountsPanel() {
  const [wallets, setWallets] = useState(null);
  const [showConvert, setShowConvert] = useState(false);

  function load() {
    getWallets().then(setWallets).catch(() => {});
  }

  useEffect(load, []);

  return (
    <main className="px-4 sm:px-6 lg:px-8 pb-8 max-w-[1240px] fade-in">
      <div className="flex items-center justify-between mb-5">
        <p className="text-sm text-muted">Your balances by currency.</p>
        <button
          onClick={() => setShowConvert(true)}
          className="h-10 px-4 rounded-xl bg-accent hover:brightness-110 transition-all text-accent-ink text-sm font-semibold active:scale-[0.98] flex items-center gap-2"
        >
          <Icon name="swap" size={15} strokeWidth={2.2} />
          Convert currency
        </button>
      </div>

      {!wallets ? (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-44" />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {wallets.map((w) => (
            <WalletCard key={w.currency} wallet={w} onChanged={load} />
          ))}
        </div>
      )}

      {showConvert && <ConvertModal wallets={wallets} onClose={() => setShowConvert(false)} onConverted={load} />}
    </main>
  );
}

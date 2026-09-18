import { useEffect, useState } from "react";
import { getWallets, depositToWallet, withdrawFromWallet } from "../lib/api";
import { formatMoney, currencySymbol } from "../lib/formatMoney";
import { showToast } from "../lib/toast";
import Skeleton from "./Skeleton";
import ConvertModal from "./ConvertModal";

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
    <div className="bg-panel border border-line rounded-2xl p-5 transition-all hover:-translate-y-0.5 hover:shadow-lg hover:shadow-black/20">
      <div className="flex items-center justify-between mb-3">
        <div className="w-9 h-9 rounded-full bg-panel-2 flex items-center justify-center text-sm font-mono text-muted">
          {currencySymbol(wallet.currency)}
        </div>
        <div className="text-xs text-dim">{wallet.currency}</div>
      </div>
      <div className="text-2xl font-mono font-medium mb-4">{formatMoney(wallet.balance, wallet.currency)}</div>

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
            className="flex-1 min-w-0 bg-panel-2 border border-line rounded-lg px-3 py-2 text-sm font-mono outline-none focus:border-accent transition-colors"
          />
          <button
            type="submit"
            disabled={submitting}
            className="px-3 py-2 rounded-lg bg-accent hover:bg-accent-2 text-white text-xs font-medium disabled:opacity-50 transition-colors shrink-0"
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
            className="flex-1 py-1.5 rounded-lg bg-panel-2 border border-line text-xs font-medium hover:bg-line transition-colors"
          >
            Deposit
          </button>
          <button
            onClick={() => setMode("withdraw")}
            className="flex-1 py-1.5 rounded-lg bg-panel-2 border border-line text-xs font-medium hover:bg-line transition-colors"
          >
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
    <main className="p-8 max-w-6xl fade-in">
      <div className="flex items-center justify-between mb-6">
        <div className="text-lg font-semibold">Accounts</div>
        <button
          onClick={() => setShowConvert(true)}
          className="px-4 py-2 rounded-lg bg-accent hover:bg-accent-2 transition-colors text-white text-sm font-medium active:scale-[0.98]"
        >
          Convert currency
        </button>
      </div>

      {!wallets ? (
        <div className="grid grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-40" />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-3 gap-4">
          {wallets.map((w) => (
            <WalletCard key={w.currency} wallet={w} onChanged={load} />
          ))}
        </div>
      )}

      {showConvert && <ConvertModal wallets={wallets} onClose={() => setShowConvert(false)} onConverted={load} />}
    </main>
  );
}

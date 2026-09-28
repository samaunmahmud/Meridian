import { useId, useState } from "react";
import { changePassword, deleteAccount, getOrders, getTransactions } from "../lib/api";
import { downloadCsv, toCsv } from "../lib/csv";
import { showToast } from "../lib/toast";
import { useTheme } from "../lib/useTheme";
import Icon from "./Icon";
import Modal from "./Modal";

const ORDER_COLUMNS = [
  ["Order ID", (o) => o.id],
  ["Created", (o) => o.createdAt],
  ["Executed", (o) => o.executedAt],
  ["Symbol", (o) => o.symbol],
  ["Side", (o) => o.type],
  ["Kind", (o) => o.kind],
  ["Status", (o) => o.status],
  ["Quantity", (o) => o.quantity],
  ["Fill price (USD)", (o) => o.price],
  ["Limit price", (o) => o.limitPrice],
  ["Stop price", (o) => o.stopPrice],
  ["Fee (USD)", (o) => o.feeAmount],
  ["Realized P&L (USD)", (o) => o.realizedPnL],
  ["Settlement currency", (o) => o.settlementCurrency ?? (o.status === "FILLED" ? "USD" : null)],
  ["Settlement amount", (o) => o.settlementAmount],
  ["Rejection reason", (o) => o.rejectionReason],
];

const TRANSACTION_COLUMNS = [
  ["Transaction ID", (t) => t.id],
  ["Date", (t) => t.createdAt],
  ["Type", (t) => t.type],
  ["Description", (t) => t.description],
  ["Amount", (t) => t.amount],
  ["Currency", (t) => t.currency ?? "USD"],
  ["Balance after", (t) => t.balanceAfter],
  ["Order ID", (t) => t.relatedOrderId],
];

function today() {
  return new Date().toISOString().slice(0, 10);
}

function Section({ title, description, children }) {
  return (
    <section className="bg-panel rounded-[28px] p-6">
      <h2 className="text-lg font-bold">{title}</h2>
      {description && <p className="text-sm text-muted mt-1">{description}</p>}
      <div className="mt-5">{children}</div>
    </section>
  );
}

function PasswordField({ label, ...inputProps }) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="text-[13px] font-medium block mb-2">
        {label}
      </label>
      <input
        id={id}
        type="password"
        required
        {...inputProps}
        className="w-full h-12 px-4 rounded-2xl bg-panel-2 border border-control outline-none text-[15px] focus:border-accent focus-visible:ring-2 focus-visible:ring-accent transition-colors"
      />
    </div>
  );
}

function ChangePasswordForm() {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    if (next !== confirm) {
      setError("The new passwords don't match.");
      return;
    }
    setError(null);
    setSaving(true);
    try {
      const response = await changePassword(current, next);
      showToast("success", response.message);
      setCurrent("");
      setNext("");
      setConfirm("");
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      <PasswordField label="Current password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <PasswordField label="New password" autoComplete="new-password" minLength={8} value={next} onChange={(e) => setNext(e.target.value)} />
        <PasswordField label="Confirm new password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
      </div>
      <p className="text-xs text-dim">At least 8 characters. Your other devices will be signed out.</p>
      {error && (
        <p role="alert" className="text-sm text-loss">
          {error}
        </p>
      )}
      <button
        type="submit"
        disabled={saving || !current || !next || !confirm}
        className="h-11 px-6 rounded-full bg-accent text-accent-ink text-sm font-semibold hover:brightness-110 transition-all disabled:opacity-50"
      >
        {saving ? "Saving…" : "Change password"}
      </button>
    </form>
  );
}

function ExportButton({ label, detail, onExport }) {
  const [busy, setBusy] = useState(false);
  async function handleClick() {
    setBusy(true);
    try {
      await onExport();
    } catch (err) {
      showToast("error", err.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={busy}
      className="w-full flex items-center gap-4 p-4 rounded-2xl bg-panel-2 hover:brightness-110 text-left transition-all disabled:opacity-60"
    >
      <span className="w-10 h-10 rounded-full bg-accent-dim text-accent flex items-center justify-center">
        <Icon name="download" size={18} />
      </span>
      <span className="flex-1 min-w-0">
        <span className="block text-[15px] font-semibold">{label}</span>
        <span className="block text-xs text-muted">{busy ? "Preparing…" : detail}</span>
      </span>
    </button>
  );
}

function DeleteAccountDialog({ email, onClose, onDeleted }) {
  const [password, setPassword] = useState("");
  const [typed, setTyped] = useState("");
  const [error, setError] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const confirmId = useId();

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setDeleting(true);
    try {
      await deleteAccount(password);
      onDeleted();
    } catch (err) {
      setError(err.message);
      setDeleting(false);
    }
  }

  return (
    <Modal title="Delete account" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <p className="text-sm text-muted">
          This permanently deletes your portfolio, wallets, orders, watchlist and alerts. It can't be undone.
          Export anything you want to keep first.
        </p>
        <PasswordField label="Password" autoComplete="current-password" data-autofocus value={password} onChange={(e) => setPassword(e.target.value)} />
        <div>
          <label htmlFor={confirmId} className="text-[13px] font-medium block mb-2">
            Type <span className="font-mono">DELETE</span> to confirm
          </label>
          <input
            id={confirmId}
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            autoComplete="off"
            className="w-full h-12 px-4 rounded-2xl bg-panel-2 border border-control outline-none text-[15px] focus:border-accent focus-visible:ring-2 focus-visible:ring-accent transition-colors"
          />
        </div>
        {error && (
          <p role="alert" className="text-sm text-loss">
            {error}
          </p>
        )}
        <div className="flex gap-3 pt-1">
          <button type="button" onClick={onClose} className="flex-1 h-11 rounded-full bg-panel-2 text-sm font-semibold hover:brightness-110">
            Cancel
          </button>
          <button
            type="submit"
            disabled={deleting || !password || typed !== "DELETE"}
            className="flex-1 h-11 rounded-full bg-loss text-on-loss text-sm font-semibold hover:brightness-110 disabled:opacity-50"
          >
            {deleting ? "Deleting…" : `Delete ${email}`}
          </button>
        </div>
      </form>
    </Modal>
  );
}

export default function SettingsPanel({ session, onLogout, onDeleted }) {
  const { theme, setTheme } = useTheme();
  const [deleting, setDeleting] = useState(false);

  async function exportOrders() {
    const orders = await getOrders();
    downloadCsv(`meridian-orders-${today()}.csv`, toCsv(orders, ORDER_COLUMNS));
    showToast("success", `Exported ${orders.length} order${orders.length === 1 ? "" : "s"}`);
  }

  async function exportTransactions() {
    const transactions = await getTransactions();
    downloadCsv(`meridian-activity-${today()}.csv`, toCsv(transactions, TRANSACTION_COLUMNS));
    showToast("success", `Exported ${transactions.length} transaction${transactions.length === 1 ? "" : "s"}`);
  }

  return (
    <main id="main-content" tabIndex={-1} data-ring-parent className="px-4 sm:px-6 lg:px-8 pb-8 max-w-[820px] space-y-6 fade-in">
      <section className="bg-panel rounded-[28px] p-6 flex items-center gap-4">
        <div className="w-14 h-14 rounded-full bg-accent text-accent-ink flex items-center justify-center text-2xl font-display shrink-0">
          {(session.email?.[0] ?? "?").toUpperCase()}
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-[17px] font-semibold truncate">{session.email}</div>
          <div className={`text-sm ${session.emailVerified ? "text-gain" : "text-muted"}`}>
            {session.emailVerified ? "Email confirmed" : "Email not confirmed yet"}
          </div>
        </div>
        <button
          type="button"
          onClick={onLogout}
          className="h-10 px-4 rounded-full bg-panel-2 text-sm font-semibold flex items-center gap-2 hover:brightness-110"
        >
          <Icon name="logout" size={16} />
          Log out
        </button>
      </section>

      <Section title="Appearance">
        <div role="radiogroup" aria-label="Theme" className="grid grid-cols-2 gap-1 p-1 rounded-full bg-panel-2 max-w-xs">
          {[
            { key: "dark", label: "Dark", icon: "moon" },
            { key: "light", label: "Light", icon: "sun" },
          ].map((opt) => (
            <button
              key={opt.key}
              type="button"
              role="radio"
              aria-checked={theme === opt.key}
              onClick={() => setTheme(opt.key)}
              className={`h-10 rounded-full text-sm font-semibold flex items-center justify-center gap-2 transition-colors ${
                theme === opt.key ? "bg-bone text-ink" : "text-muted hover:text-bone"
              }`}
            >
              <Icon name={opt.icon} size={16} />
              {opt.label}
            </button>
          ))}
        </div>
      </Section>

      <Section title="Password">
        <ChangePasswordForm />
      </Section>

      <Section title="Your data" description="Download your history as CSV files that open in Excel, Numbers or Google Sheets.">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <ExportButton label="Orders" detail="Every order, with fills, fees and P&L" onExport={exportOrders} />
          <ExportButton label="Activity" detail="Deposits, trades, fees and exchanges" onExport={exportTransactions} />
        </div>
      </Section>

      <Section title="Delete account" description="Removes your account and all of its data for good.">
        <button
          type="button"
          onClick={() => setDeleting(true)}
          className="h-11 px-5 rounded-full bg-loss-dim text-loss text-sm font-semibold flex items-center gap-2 hover:brightness-110"
        >
          <Icon name="trash" size={16} />
          Delete account…
        </button>
      </Section>

      {deleting && <DeleteAccountDialog email={session.email} onClose={() => setDeleting(false)} onDeleted={onDeleted} />}
    </main>
  );
}

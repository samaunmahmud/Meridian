import Icon from "./Icon";
import TickerAvatar from "./TickerAvatar";

const CURRENCY_SIGNS = { USD: "$", EUR: "€", GBP: "£" };

// A tappable row, like Revolut's account and stock pickers. The native select lies invisibly on top of it, so
// it keeps its label, keyboard use and the phone's own picker while the row shows what is chosen.
export default function PickerRow({ id, label, value, onChange, options, children }) {
  return (
    <div className="relative flex items-center gap-3.5 bg-panel-2 rounded-2xl px-4 py-3 focus-within:ring-2 focus-within:ring-accent hover:brightness-110 transition">
      {children}
      <Icon name="chev" size={18} className="text-muted shrink-0" />
      <label htmlFor={id} className="sr-only">{label}</label>
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)} className="absolute inset-0 w-full h-full opacity-0 cursor-pointer">
        {options.map((o) => (
          <option key={o.value} value={o.value}>{o.text}</option>
        ))}
      </select>
    </div>
  );
}

// What a stock picker row shows: the stock's badge, symbol and name, and its price when known.
export function StockChoice({ ticker, symbol, price }) {
  return (
    <>
      {symbol ? <TickerAvatar symbol={symbol} size={40} /> : <div className="w-10 h-10 rounded-full bg-panel shrink-0" />}
      <div className="flex-1 min-w-0">
        <div className="text-[15px] font-semibold">{symbol || "Choose a stock"}</div>
        <div className="text-[13px] text-muted truncate">{ticker?.name ?? ""}</div>
      </div>
      {price}
    </>
  );
}

// What a wallet picker row shows: the currency's sign, a caption ("Pay with", "From") and its balance.
export function WalletChoice({ currency, caption, amount, amountCaption = "available" }) {
  return (
    <>
      <div className="w-10 h-10 rounded-full bg-panel flex items-center justify-center text-sm font-semibold shrink-0" aria-hidden="true">
        {CURRENCY_SIGNS[currency] ?? currency.slice(0, 1)}
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-[13px] text-muted">{caption}</div>
        <div className="text-[15px] font-semibold">{currency} wallet</div>
      </div>
      {amount != null && (
        <div className="text-right">
          <div className="font-mono text-[15px] font-semibold">{amount}</div>
          <div className="text-[13px] text-muted">{amountCaption}</div>
        </div>
      )}
    </>
  );
}

// A small pill menu ("Compare with SPY"): the native select without its browser arrow, with a chevron of ours.
export function PillSelect({ className = "", ...props }) {
  return (
    <span className="relative inline-flex items-center">
      <select {...props} className={`appearance-none h-8 rounded-full bg-panel-2 pl-3.5 pr-8 font-semibold text-bone cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-accent hover:brightness-110 ${className}`} />
      <Icon name="chev" size={14} className="absolute right-3 text-muted pointer-events-none" />
    </span>
  );
}

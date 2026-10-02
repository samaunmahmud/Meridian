import { formatMoney } from "../lib/formatMoney";

// A large money amount with the cents set smaller, as banking apps show balances: $12,699.93.
// Screen readers get the plain amount.
export default function Amount({ value, currency = "USD", className = "" }) {
  const text = formatMoney(value, currency);
  const dot = text.lastIndexOf(".");
  if (dot < 0) return <span className={className}>{text}</span>;
  return (
    <span className={className}>
      <span className="sr-only">{text}</span>
      <span aria-hidden="true">
        {text.slice(0, dot)}
        <span className="text-[0.55em] align-[0.62em] ml-px">{text.slice(dot)}</span>
      </span>
    </span>
  );
}

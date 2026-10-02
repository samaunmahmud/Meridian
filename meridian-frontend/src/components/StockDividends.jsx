import { useEffect, useState } from "react";
import { getDividends, getPrices } from "../lib/api";
import { formatMoney } from "../lib/formatMoney";

const SHOWN = 4;

// Dates from the server are calendar days ("2026-11-07"): shown as written, never shifted by the time zone.
function day(iso, withYear = true) {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: withYear ? "numeric" : undefined,
    timeZone: "UTC",
  });
}

function todayIso() {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function perShare(amount) {
  return `$${Number(amount).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 6 })}`;
}

function Stat({ label, value, sub }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs font-medium text-muted mb-1">{label}</dt>
      <dd className="font-mono font-medium text-sm">{value}</dd>
      {sub && <dd className="text-xs text-dim mt-0.5">{sub}</dd>}
    </div>
  );
}

// A stock's cash dividends: the next (or last) one, the yield over the last year, what you've been paid, and
// the recent history. Holders when the ex-date begins are paid in cash on the payment date. Left out for
// crypto and for stocks that pay nothing.
export default function StockDividends({ ticker, refreshKey }) {
  const [info, setInfo] = useState(null);
  const [price, setPrice] = useState(null);

  useEffect(() => {
    if (ticker.assetType === "CRYPTO") return;
    let live = true;
    setInfo(null);
    getDividends(ticker.symbol)
      .then((d) => live && setInfo(d))
      .catch(() => live && setInfo(null));
    getPrices(ticker.symbol, { limit: 1 })
      .then((p) => live && setPrice(p?.[0]?.price ?? null))
      .catch(() => {});
    return () => {
      live = false;
    };
  }, [ticker.symbol, ticker.assetType, refreshKey]);

  if (!info || info.symbol !== ticker.symbol || (info.dividends.length === 0 && !(info.received > 0))) return null;

  const today = todayIso();
  const upcoming = [...info.dividends].reverse().find((d) => d.exDate >= today || d.payDate >= today);
  const last = info.dividends.find((d) => d.exDate < today);
  const featured = upcoming ?? last;
  const yieldPct = price && info.trailingYearPerShare > 0 ? (info.trailingYearPerShare / price) * 100 : null;

  return (
    <section aria-labelledby="dividends-heading" className="bg-panel rounded-[28px] p-5 sm:p-7">
      <h2 id="dividends-heading" className="text-lg font-bold mb-4">
        Dividends
      </h2>
      <dl className="grid grid-cols-2 sm:grid-cols-4 gap-x-4 gap-y-4">
        {featured && (
          <Stat
            label={upcoming ? "Next dividend" : "Last dividend"}
            value={`${perShare(featured.amount)} / share`}
            sub={`Ex ${day(featured.exDate, false)} · paid ${day(featured.payDate, false)}`}
          />
        )}
        <Stat label="Paid last 12 months" value={`${perShare(info.trailingYearPerShare)} / share`} />
        <Stat label="Dividend yield" value={yieldPct == null ? "—" : `${yieldPct.toFixed(2)}%`} sub="Last 12 months ÷ price" />
        <Stat label="You've received" value={formatMoney(info.received)} />
      </dl>
      <p className="text-xs text-dim mt-4">
        Own shares when the ex-dividend date begins and the dividend is paid to your USD balance on the payment date.
      </p>
      {info.dividends.length > 0 && (
        <table className="w-full text-sm mt-4">
          <caption className="sr-only">Recent dividends of {ticker.symbol}</caption>
          <thead>
            <tr className="text-xs text-muted text-left">
              <th scope="col" className="font-medium py-1.5">Ex-date</th>
              <th scope="col" className="font-medium py-1.5">Paid</th>
              <th scope="col" className="font-medium py-1.5 text-right">Per share</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {info.dividends.slice(0, SHOWN).map((d) => (
              <tr key={d.exDate}>
                <td className="py-2">{day(d.exDate)}</td>
                <td className="py-2">{day(d.payDate)}</td>
                <td className="py-2 text-right font-mono">{perShare(d.amount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

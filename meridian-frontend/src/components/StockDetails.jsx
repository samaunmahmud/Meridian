import { useEffect, useState } from "react";
import { getAlerts, getChanges, getOrders, getPortfolio } from "../lib/api";
import { formatNumber } from "../lib/formatMoney";

const KIND_LABELS = { MARKET: "Market", LIMIT: "Limit", STOP_LOSS: "Stop" };

function formatQuantity(q) {
  return Number(q).toLocaleString("en-US", { maximumFractionDigits: 8 });
}

function signed(value, text) {
  return `${value >= 0 ? "+" : "-"}${text}`;
}

function Stat({ label, children, tone }) {
  return (
    <div>
      <dt className="text-xs font-medium text-muted mb-1">{label}</dt>
      <dd className={`font-mono font-medium text-sm ${tone ?? ""}`}>{children}</dd>
    </div>
  );
}

function orderText(o) {
  const side = o.type === "BUY" ? "Buy" : "Sell";
  const at =
    o.kind === "LIMIT" ? ` at $${formatNumber(o.limitPrice)}`
    : o.kind === "STOP_LOSS" ? ` if it falls to $${formatNumber(o.stopPrice)}`
    : " at the open";
  return `${side} ${formatQuantity(o.quantity)} · ${KIND_LABELS[o.kind] ?? o.kind}${at}`;
}

/**
 * What a broker shows under a stock's chart: its move today, your position in it, and the orders and
 * alerts you have waiting on it. Everything comes from the existing endpoints; a part that fails to
 * load is left out rather than shown wrong. The price follows live updates between reloads.
 */
export default function StockDetails({ ticker, liveUpdate, refreshKey }) {
  const [data, setData] = useState(null);
  const [livePrice, setLivePrice] = useState(null);

  useEffect(() => {
    if (!ticker) return;
    let cancelled = false;
    setLivePrice(null);
    Promise.allSettled([getChanges(), getPortfolio(), getOrders(), getAlerts()]).then(
      ([changes, portfolio, orders, alerts]) => {
        if (cancelled) return;
        const value = (r) => (r.status === "fulfilled" ? r.value : null);
        setData({
          symbol: ticker.symbol,
          change: value(changes)?.find((c) => c.symbol === ticker.symbol) ?? null,
          holding: value(portfolio)?.holdings?.find((h) => h.symbol === ticker.symbol) ?? null,
          portfolioLoaded: portfolio.status === "fulfilled",
          orders: (value(orders) ?? []).filter((o) => o.symbol === ticker.symbol && o.status === "PENDING"),
          alerts: (value(alerts) ?? []).filter((a) => a.symbol === ticker.symbol && !a.triggered),
        });
      }
    );
    return () => {
      cancelled = true;
    };
  }, [ticker, refreshKey]);

  useEffect(() => {
    if (liveUpdate?.kind === "PRICE_UPDATE" && liveUpdate.symbol === ticker?.symbol) setLivePrice(liveUpdate.price);
  }, [liveUpdate, ticker]);

  if (!ticker || !data || data.symbol !== ticker.symbol) return null;

  const { change, holding, orders, alerts } = data;
  const price = livePrice ?? change?.price ?? holding?.currentPrice ?? null;

  const reference = change?.referencePrice;
  const dayChange = price != null && reference ? price - reference : null;
  const dayPct = dayChange != null ? (dayChange / reference) * 100 : null;
  const dayTone = dayChange == null ? "" : dayChange >= 0 ? "text-gain" : "text-loss";

  const quantity = holding ? Number(holding.quantity) : 0;
  const cost = holding ? quantity * holding.avgCost : 0;
  const value = holding && price != null ? quantity * price : holding?.marketValue;
  const gain = holding ? value - cost : null;
  const gainPct = holding && cost ? (gain / cost) * 100 : null;
  const gainTone = gain == null ? "" : gain >= 0 ? "text-gain" : "text-loss";

  const isCrypto = ticker.assetType === "CRYPTO";

  return (
    <section aria-labelledby="stock-details-heading" className="bg-panel rounded-[28px] p-5 sm:p-7 fade-in">
      <h2 id="stock-details-heading" className="text-lg font-bold mb-4">
        {ticker.symbol} today and your position
      </h2>

      <dl className="grid grid-cols-2 sm:grid-cols-4 gap-x-4 gap-y-4">
        <Stat label={isCrypto ? "Price 24 h ago" : "Previous close"}>{reference ? `$${formatNumber(reference)}` : "—"}</Stat>
        <Stat label={isCrypto ? "Change (24 h)" : "Change today"} tone={dayTone}>
          {dayChange == null
            ? "—"
            : `${signed(dayChange, `$${formatNumber(Math.abs(dayChange))}`)} (${signed(dayPct, `${Math.abs(dayPct).toFixed(2)}%`)})`}
        </Stat>
        {holding && (
          <>
            <Stat label="You own">{formatQuantity(holding.quantity)}</Stat>
            <Stat label="Average cost">${formatNumber(holding.avgCost)}</Stat>
            <Stat label="Market value">${formatNumber(value)}</Stat>
            <Stat label="Total return" tone={gainTone}>
              {`${signed(gain, `$${formatNumber(Math.abs(gain))}`)}${gainPct == null ? "" : ` (${signed(gainPct, `${Math.abs(gainPct).toFixed(2)}%`)})`}`}
            </Stat>
            {Number(holding.reservedQuantity) > 0 && (
              <Stat label="Held for sell orders">{formatQuantity(holding.reservedQuantity)}</Stat>
            )}
          </>
        )}
      </dl>

      {!holding && data.portfolioLoaded && (
        <p className="text-sm text-muted mt-4">You don&rsquo;t own any {ticker.symbol} yet.</p>
      )}

      {orders.length > 0 && (
        <div className="mt-5">
          <h3 className="text-[13px] font-medium text-muted mb-2">Open orders</h3>
          <ul className="space-y-1.5 text-sm">
            {orders.map((o) => (
              <li key={o.id}>{orderText(o)}</li>
            ))}
          </ul>
        </div>
      )}

      {alerts.length > 0 && (
        <div className="mt-5">
          <h3 className="text-[13px] font-medium text-muted mb-2">Price alerts</h3>
          <ul className="space-y-1.5 text-sm">
            {alerts.map((a) => (
              <li key={a.id}>
                Tell me when it goes {a.direction === "ABOVE" ? "above" : "below"} ${formatNumber(a.targetPrice)}
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

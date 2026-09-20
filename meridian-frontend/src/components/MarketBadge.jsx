import { describeMarket } from "../lib/marketStatus";
import { useMarketStatus } from "../lib/useMarketStatus";

// "Market open · closes today 1:00 PM EST" / "Market closed · opens Mon 2:30 PM EDT" / "Crypto trades 24/7".
// The words carry the meaning; the coloured dot is decoration.
export default function MarketBadge({ assetType }) {
  const status = useMarketStatus();
  const { open, label } = describeMarket(assetType === "CRYPTO" ? null : status?.stocks, assetType);
  if (open === null) return null;
  return (
    <div className="flex items-center gap-2 text-xs text-muted">
      <span
        className="w-2 h-2 rounded-full shrink-0"
        style={{ background: open ? "var(--c-gain)" : "var(--c-s2)" }}
        aria-hidden="true"
      />
      {label}
    </div>
  );
}

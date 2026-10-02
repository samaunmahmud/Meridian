import { useEffect, useState } from "react";
import { getNews } from "../lib/api";
import { formatAge } from "../lib/priceAge";
import Skeleton from "./Skeleton";

const SHOWN_AT_FIRST = 5;
const SENTIMENT_STYLES = {
  Bullish: "bg-gain-dim text-gain",
  Bearish: "bg-loss-dim text-loss",
  Neutral: "bg-panel-2 text-muted",
};

function Thumbnail({ src }) {
  const [broken, setBroken] = useState(false);
  if (!src || broken) return null;
  return (
    <img
      src={src}
      alt=""
      loading="lazy"
      referrerPolicy="no-referrer"
      onError={() => setBroken(true)}
      className="w-16 h-16 sm:w-20 sm:h-20 rounded-2xl object-cover shrink-0 bg-panel-2"
    />
  );
}

function Article({ article }) {
  const meta = [article.source, article.publishedAt && formatAge(article.publishedAt)].filter(Boolean).join(" · ");
  return (
    <li>
      <a
        href={article.url}
        target="_blank"
        rel="noopener noreferrer"
        className="flex gap-4 py-3 -mx-2 px-2 rounded-2xl hover:bg-panel-2 transition-colors"
      >
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 text-xs text-muted mb-1">
            {meta && <span className="truncate">{meta}</span>}
            {article.sentiment && (
              <span className={`shrink-0 px-2 py-0.5 rounded-full font-semibold ${SENTIMENT_STYLES[article.sentiment] ?? SENTIMENT_STYLES.Neutral}`}>
                {article.sentiment}
              </span>
            )}
          </div>
          <h3 className="font-semibold text-[15px] leading-snug">
            {article.headline}
            <span className="sr-only"> (opens in a new tab)</span>
          </h3>
          {article.summary && <p className="text-sm text-dim mt-1 line-clamp-2">{article.summary}</p>}
        </div>
        <Thumbnail src={article.imageUrl} />
      </a>
    </li>
  );
}

// Recent articles about the stock, from the market data provider. The server keeps them for a while
// (news costs requests from the same allowance as prices), so this asks once per stock opened.
export default function CompanyNews({ ticker }) {
  const [news, setNews] = useState(null);
  const [failed, setFailed] = useState(false);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    let live = true;
    setNews(null);
    setFailed(false);
    setExpanded(false);
    getNews(ticker.symbol)
      .then((n) => live && setNews(n))
      .catch(() => live && setFailed(true));
    return () => {
      live = false;
    };
  }, [ticker.symbol]);

  const shown = news && !expanded ? news.slice(0, SHOWN_AT_FIRST) : news;

  return (
    <section className="bg-panel rounded-[28px] p-5 sm:p-6" aria-labelledby="news-heading">
      <h2 id="news-heading" className="text-lg font-bold mb-2">
        News
      </h2>
      {failed ? (
        <p className="text-sm text-dim py-6 text-center">News isn't available right now. Try again later.</p>
      ) : !news ? (
        <div className="space-y-3 py-2">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : news.length === 0 ? (
        <p className="text-sm text-dim py-6 text-center">No recent news about {ticker.symbol}.</p>
      ) : (
        <>
          <ul className="divide-y divide-line">
            {shown.map((a) => (
              <Article key={a.url} article={a} />
            ))}
          </ul>
          {news.length > SHOWN_AT_FIRST && (
            <button
              type="button"
              onClick={() => setExpanded((e) => !e)}
              aria-expanded={expanded}
              className="mt-2 -ml-4 h-9 px-4 rounded-full text-sm font-semibold text-accent hover:bg-panel-2"
            >
              {expanded ? "Show less" : `Show ${news.length - SHOWN_AT_FIRST} more`}
            </button>
          )}
        </>
      )}
    </section>
  );
}

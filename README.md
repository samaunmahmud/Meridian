<p align="center">
  <img src="docs/screenshots/hero.png" alt="Meridian in dark and light: the Home screen with a $96,614 balance up 7.02% all time and a watchlist with sparklines, NVIDIA's stock page on a phone, the Portfolio page with holdings, and the trade sheet buying NVIDIA" width="100%" />
</p>

<h1 align="center">Meridian: practise investing without risking a penny</h1>

<p align="center">
  A paper-trading app for stocks and crypto that behaves like a real broker.<br>
  Market, limit, stop and trailing-stop orders, USD, EUR and GBP wallets, dividends and live prices over WebSocket.<br>
  Every order runs against real exchange hours, and every cent and share is checked under load.
</p>

<p align="center">
  <a href="https://github.com/samaunmahmud/Meridian/releases/tag/v1.0.0"><img src="https://img.shields.io/badge/status-complete_·_v1.0.0-0666eb?style=flat-square" alt="Status: complete, v1.0.0" /></a>
  <a href="https://github.com/samaunmahmud/Meridian/actions/workflows/ci.yml"><img src="https://github.com/samaunmahmud/Meridian/actions/workflows/ci.yml/badge.svg" alt="CI" /></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 3.3" />
  <img src="https://img.shields.io/badge/React_19-20232A?style=flat-square&logo=react&logoColor=61DAFB" alt="React 19" />
  <img src="https://img.shields.io/badge/Tailwind_4-0F172A?style=flat-square&logo=tailwindcss&logoColor=38BDF8" alt="Tailwind CSS 4" />
  <img src="https://img.shields.io/badge/MySQL-8.4-00758F?style=flat-square&logo=mysql&logoColor=white" alt="MySQL 8.4" />
  <img src="https://img.shields.io/badge/Flyway-CC0200?style=flat-square&logo=flyway&logoColor=white" alt="Flyway" />
  <img src="https://img.shields.io/badge/WebSocket-010101?style=flat-square" alt="WebSocket" />
  <img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker" />
</p>

> [!WARNING]
> **Simulated money only. Not financial advice.** Deposits are fake and there is no connection to a real broker or payment provider. Prices come from free market data plans and may be delayed.

> [!NOTE]
> **This project is finished.** Meridian reached v1.0.0 in October 2026 and is no longer in active development. Everything described here works and is tested; see [Project status](#project-status) for what was built and where it could go next.

---

## Contents

- [The problem](#the-problem)
- [What Meridian does](#what-meridian-does)
- [Results in numbers](#results-in-numbers)
- [Features](#features)
- [Screenshots](#screenshots)
- [How it works](#how-it-works)
- [Getting the money right](#getting-the-money-right)
- [Security](#security)
- [Getting started](#getting-started)
- [Deploying with Docker](#deploying-with-docker)
- [Running it in production](#running-it-in-production)
- [Configuration](#configuration)
- [API](#api)
- [Tests](#tests)
- [Project structure](#project-structure)
- [Design decisions](#design-decisions)
- [Limitations](#limitations)
- [Project status](#project-status)

---

## The problem

The best way to learn investing is to do it, but doing it with real money is an expensive way to learn. The usual alternatives have their own problems:

1. **Toy simulators are too forgiving.** They fill every order instantly at any hour, so you never learn what a limit order, a stop-loss or a closed market actually does.
2. **One currency, no costs.** Real investors pay commission, hold cash in more than one currency and lose money on conversions. A simulator that ignores this teaches the wrong habits.
3. **No sense of how you're really doing.** A big number on a dashboard doesn't say whether you beat simply buying the S&P 500, or whether your gains came from deposits.
4. **Spreadsheets don't scale.** Tracking orders, dividends and target allocations by hand falls apart after a few weeks.

**Meridian is a full paper-trading account that follows the rules of a real one**, with a banking-app interface that works the same on a phone and a desktop.

## What Meridian does

Sign up and you get a virtual account. From there:

1. **Add money to USD, EUR or GBP wallets** and convert between them at live exchange rates.
2. **Trade stocks and crypto** with market, limit, stop-loss and trailing-stop orders, or set up recurring buys. Stocks trade only in the NYSE/Nasdaq session; an order placed while the market is closed waits for the open.
3. **Watch it live.** Prices, fills, alerts and dividends are pushed over WebSocket, so the screen updates without a refresh.
4. **See how you're really doing:** realized and unrealized profit, fees, win rate, and your time-weighted return against any ticker (SPY by default), with deposits and currency moves left out.
5. **Keep a plan:** notes and target prices on every stock, a target allocation that shows the trades to rebalance, and price alerts by email.

## Results in numbers

| | |
|---|---|
| **Market orders, 50 users at once** | **about 830 orders/s**, p99 about 100 ms, 0 errors, every cash balance and holding consistent afterwards |
| **Authenticated reads** | **about 3,500 requests/s**, p99 under 35 ms with 50 clients |
| **WebSocket fan-out** | a price update reaches **1,000 connected clients in about 20 ms** |
| **Database outage** | `/api/health` answers 503 within 5 s; everything works again **2–4 s after MySQL returns**, with nobody signed out |
| **History clean-up** | 300,000 one-minute prices (208 days) shrank to **about 12,000 rows in 12 s**, with no visible change to any chart |
| **Tests** | **316 backend (JUnit) + 210 frontend (Vitest)**, including real-MySQL concurrency tests and accessibility checks, run by CI on every push |

The load numbers come from one 8-core Mac running the load generator, the backend and MySQL together, so treat them as a floor, not production sizing. The scripts and method are in [`scripts/load-test`](scripts/load-test/README.md).

## Features

| | |
|---|---|
| 📈 **Stocks and crypto** | Search any ticker, add it to your watchlist, and see live prices, today's change and a sparkline for each. |
| 🧾 **Real order types** | Market, limit, stop-loss and trailing stop (the stop follows the price up by a set percentage). Pending orders can be edited or cancelled, and each order can carry a note. Order history reads like a bank statement, grouped by Today, Yesterday and date. |
| 🔁 **Recurring buys** | Buy a fixed amount daily, weekly or monthly. A buy that falls due while the market is closed waits for the open. |
| 🕰️ **Exchange hours** | 09:30–16:00 New York time, early closes and holidays worked out by rule for any year. A market order placed while the market is closed is queued and fills at the open. Crypto never closes. |
| 💱 **Three currencies** | USD, EUR and GBP wallets. Pay for any order from any wallet; a pending limit buy reserves its money and commission. |
| 💰 **Dividends** | Whoever holds a stock when its ex-dividend date begins is paid in USD cash on the payment date, with a live notice. Each stock page shows the next and recent dividends, the yield and what you've received. |
| 📊 **Performance** | Realized and unrealized profit and loss, fees paid, win rate, best and worst trade, and a per-stock breakdown. |
| 🏁 **You vs the market** | Your time-weighted return next to any tracked ticker (SPY by default) over 1W / 1M / 3M / All. Deposits, withdrawals and currency moves don't count as gains. |
| ⚖️ **Target allocation** | Set what share of the portfolio each stock should be. Meridian shows the drift and the trades that bring it back, each opening the trade form already filled in. |
| 📝 **Notes and target prices** | Why you're watching a stock and the price you'd buy at. The watchlist shows how far each stock is from your target, and a price alert can tell you when it gets there. |
| 🔔 **Alerts and emails** | Price and percentage-move alerts, plus emails when an alert fires or a queued order fills or is rejected (confirmed addresses only). |
| 📰 **Company news** | Newest first on every stock page, with the source, age and (on Alpha Vantage) a bullish or bearish rating. |
| ⌨️ **Search from anywhere** | <kbd>⌘K</kbd> / <kbd>Ctrl K</kbd> or <kbd>/</kbd> jumps to any stock, page or action. |
| 🔐 **Account** | Sign-up with email verification, password reset, change password (signs out other devices), CSV export of orders and activity, and account deletion. |
| 🌗 **Light and dark, phone and desktop** | A Revolut-style interface that follows your device: high-contrast dark mode, tappable stock and wallet pickers, a large amount with a price / fee / total summary when trading, and installable on a phone's home screen. Every colour is tested against WCAG contrast in both themes. |


## Screenshots

Every screen comes in both themes; the app follows your device's setting, and the sun / moon switch overrides it. All of them show a seeded demo account with months of trading history.

| Dark | Light |
| :---: | :---: |
| **Home** | |
| ![Home, dark theme: Balance, quick actions, recent activity, your investments and the watchlist with sparklines](docs/screenshots/home-dark.png) | ![Home, light theme](docs/screenshots/home-light.png) |
| **A stock's page** | |
| ![A stock's page, dark theme: Price chart, key stats and the trade form, with the stock and wallet as tappable rows](docs/screenshots/stock-dark.png) | ![A stock's page, light theme](docs/screenshots/stock-light.png) |
| **Portfolio** | |
| ![Portfolio, dark theme: Total value over the past month, holdings with profit and loss, and the trade form](docs/screenshots/portfolio-dark.png) | ![Portfolio, light theme](docs/screenshots/portfolio-light.png) |
| **You vs the market and target allocation** | |
| ![You vs the market and target allocation, dark theme: Time-weighted return against SPY, and the trades that bring each holding back to its target](docs/screenshots/insights-dark.png) | ![You vs the market and target allocation, light theme](docs/screenshots/insights-light.png) |
| **Order history** | |
| ![Order history, dark theme: Grouped by day like a bank statement: what each order cost or paid, its fee, status, note and profit](docs/screenshots/orders-dark.png) | ![Order history, light theme](docs/screenshots/orders-light.png) |
| **The whole stock page** | |
| ![The whole stock page, dark theme: Your position, open orders, notes and target price, dividends and company news](docs/screenshots/stock-full-dark.png) | ![The whole stock page, light theme](docs/screenshots/stock-full-light.png) |

### On a phone

<p align="center"><b>Dark</b><br>
  <img src="docs/screenshots/phone-home-dark.png" width="190" alt="Home on a phone, dark theme" />
  <img src="docs/screenshots/phone-stock-dark.png" width="190" alt="NVIDIA's page on a phone, dark theme" />
  <img src="docs/screenshots/phone-buy-dark.png" width="190" alt="Trade sheet on a phone, dark theme" />
  <img src="docs/screenshots/phone-portfolio-dark.png" width="190" alt="Portfolio on a phone, dark theme" />
</p>

<p align="center"><b>Light</b><br>
  <img src="docs/screenshots/phone-home-light.png" width="190" alt="Home on a phone, light theme" />
  <img src="docs/screenshots/phone-stock-light.png" width="190" alt="NVIDIA's page on a phone, light theme" />
  <img src="docs/screenshots/phone-buy-light.png" width="190" alt="Trade sheet on a phone, light theme" />
  <img src="docs/screenshots/phone-portfolio-light.png" width="190" alt="Portfolio on a phone, light theme" />
</p>

## How it works

```mermaid
flowchart LR
    P[Price provider<br/>Alpha Vantage or Finnhub] --> PS[Price and FX pollers<br/>daily request budget]
    PS --> DB[(MySQL 8.4<br/>Flyway schema)]
    PS --> E[Order engine<br/>limit, stop, trailing stop,<br/>queued market orders]
    E --> DB
    S[Schedulers<br/>recurring buys, dividends,<br/>snapshots, clean-up] --> DB
    E --> WS[WebSocket<br/>/ws/prices]
    PS --> WS
    WS --> UI[React app<br/>phone and desktop]
    UI -->|REST /api| API[Spring Boot API<br/>JWT cookie session]
    API --> DB
    E --> M[Emails<br/>SMTP]
```

1. **Prices.** A poller fetches quotes and exchange rates from Alpha Vantage, or quotes from Finnhub and rates from Frankfurter (ECB), within a daily request budget. A closed stock market isn't polled once its closing price is recorded, so nights and weekends cost nothing.
2. **Orders.** Every new price runs the order engine: limit and stop orders fill only while the market is open, trailing stops ratchet up, and queued market orders fill at the first price after the open.
3. **Background jobs** run recurring buys, pay dividends, snapshot every portfolio once a minute and thin old history every night.
4. **Live updates.** Prices, fills, rejections, triggered alerts and dividends are pushed to the browser over a WebSocket, as small toasts and live numbers.
5. **The app** talks to a REST API with a session cookie. nginx serves it and proxies `/api` and `/ws` in the Docker setup.

## Getting the money right

A paper-trading app is still a ledger, and a race in money code doesn't crash; it quietly gets the balance wrong. Meridian treats that as the main risk:

- **Locks on every money path.** Orders, conversions, dividends and recurring buys lock the account's rows before they read a balance, and MySQL runs at `READ COMMITTED`. Under MySQL's default `REPEATABLE READ`, simultaneous orders from one account overwrote each other: a load test once left 130 of 1,550 positions wrong while every request answered 200.
- **Invariants checked after load.** [`invariants.sql`](scripts/load-test/invariants.sql) checks that every user's cash equals their deposits plus their ledger, and every holding equals filled buys minus filled sells. Both must report 0 mismatches.
- **Real-MySQL tests in CI.** `MySqlConcurrencyTest` fires simultaneous orders from one account at a real MySQL 8.4, because the in-memory H2 used by the other tests can't show the bug.
- **Reservations, not hopes.** A pending limit buy holds its money and commission; a queued market buy holds the last price plus a 10% cushion and is rejected (never overdrawn) if the stock opens beyond it.
- **No trading on old prices.** Market orders, recurring buys and conversions refuse to run on a stale price or exchange rate (HTTP 503 with the reason).

## Security

- **Session in an HttpOnly cookie.** A signed JWT that page scripts can't read; `AUTH_COOKIE_SECURE=true` keeps it to HTTPS. Changing your password ends every other session.
- **Rate limits in the database.** Sign-up, login and password reset are rate-limited per IP address and per email (5 failed logins lock that email for a while), stored in MySQL so the limits survive restarts.
- **Strict CORS.** In Docker the API accepts only `PUBLIC_URL` as an origin.
- **Emails only to confirmed addresses.** Notifications go out only after the address is verified.
- **No database on the host.** In the Docker setup MySQL is reachable only by the backend.
- **Formula-safe CSV.** Exported cells that start with `=`, `+`, `-` or `@` are escaped, so a ticker name can't run as a spreadsheet formula.

## Getting started

### Prerequisites

- **Java 21** and **Maven** (on JDK 25, seven Mockito tests fail)
- **Node.js 22+**
- **Docker** (for MySQL)
- A free **Alpha Vantage** key, or a **Finnhub** key for near-live prices

### 1. Configure the backend

```sh
git clone https://github.com/samaunmahmud/Meridian.git
cd Meridian
cp meridian-backend/.env.example meridian-backend/.env
```

Fill in `JWT_SECRET` (at least 32 random characters: `openssl rand -base64 48`) and `ALPHA_VANTAGE_API_KEY`, or set `MARKETDATA_PROVIDER=finnhub` and `FINNHUB_API_KEY`.

### 2. Run it

```sh
# MySQL on localhost:3306
cd meridian-backend && docker compose up -d

# Backend on http://localhost:8080 (Flyway creates the schema on first start)
mvn spring-boot:run

# Frontend on http://localhost:5173
cd ../meridian-frontend
npm install     # an npm warning about fsevents' install script is harmless
npm run dev
```

Then sign up on the login page. In development the frontend calls `http://localhost:8080/api` unless `VITE_API_BASE` is set. With `MAIL_HOST` empty, password-reset and verification emails are only printed in the backend log, so copy the link from there.

## Deploying with Docker

```sh
cp .env.example .env
# set MYSQL_ROOT_PASSWORD, MYSQL_PASSWORD, JWT_SECRET (and the price key, MAIL_*, PUBLIC_URL)
docker compose up -d --build
```

The site is then at `http://localhost` (or the `WEB_PORT` you set). nginx serves the frontend and proxies `/api` and `/ws` to the backend.

For a real site, put HTTPS in front (a load balancer or a TLS-terminating proxy), then set:

- `PUBLIC_URL` to the exact address people type, for example `https://app.example.com`. It is the only origin the API accepts and the base of the links in emails.
- `AUTH_COOKIE_SECURE=true`, so the session cookie is only sent over HTTPS.
- `MAIL_*` to a real SMTP server. Without it nobody receives reset or verification emails.

If you don't terminate TLS at the bundled nginx, make sure whatever sits in front of it overwrites `X-Forwarded-For`; the login rate limits trust that header in the Docker setup.

## Running it in production

<details>
<summary><b>Health check</b></summary>

<br>

`GET /api/health` returns `200` with `{"status":"ok","database":"up","priceFeed":"ok","newestPriceAgeSeconds":12}`, or `503` when the backend can't reach MySQL. Docker uses it, and you can point an uptime monitor at it. `priceFeed` is `ok`, `stale` (prices should be arriving and aren't) or `empty`; it never turns the response into a 503, because the site is up and restarting it wouldn't fix the feed. A closed stock market whose closing price is recorded isn't stale, so a quiet weekend stays `ok`. To be told about a provider outage, alert on `"priceFeed":"stale"`.

</details>

<details>
<summary><b>Trading hours</b></summary>

<br>

Stocks trade in the regular NYSE/Nasdaq session, **09:30–16:00 New York time, Monday–Friday**, with the 13:00 early closes and closed on exchange holidays (worked out by rule for any year, so nothing to update each January). Pre-market and after-hours are not modelled. Crypto trades around the clock.

- A **market order placed while a stock market is closed is queued** (status `PENDING`, shown as "(at open)"). A buy holds the last price plus a 10% cushion (and the commission) until then, a sell holds its shares. It fills at the first price polled after the open and releases what it didn't need; if the stock opens beyond the cushion the order is rejected and everything is released, never overdrawn. It can be cancelled like any pending order.
- Limit and stop-loss orders only fill while the market is open (a closed market just repeats the last close). Recurring buys wait for the open.
- A closed stock market isn't polled: once a stock's closing price is recorded the poller makes no more requests for it until the open, so nights, weekends and holidays cost none of the provider's daily allowance. Crypto keeps being polled.
- `GET /api/market/status` says whether each market is open and when that changes. Set `MARKET_HOURS_ENFORCED=false` to let stocks trade at any hour (for a demo).

</details>

<details>
<summary><b>Keeping the database small</b></summary>

<br>

Prices are stored on every poll and each user's portfolio value every minute, so those two tables would grow for ever. Every night at 03:30 (server time) old history is thinned: the last 7 days keep every row, older rows are reduced to the last value of each hour, and rows older than 90 days to the last value of each day (`HISTORY_FULL_RESOLUTION_DAYS`, `HISTORY_DAILY_AFTER_DAYS`). The newest price of every stock is never removed, and running it twice changes nothing. The chart endpoints return a bounded number of points anyway (`/api/prices/{symbol}` and `/api/portfolio/history` accept `points`), so nothing visible changes.

</details>

<details>
<summary><b>When the database or the price provider is down</b></summary>

<br>

**Database.** Requests that need it answer `503` ("temporarily unavailable") after at most 5 s (`spring.datasource.hikari.connection-timeout`), which the browser shows as an error without signing anyone out, and `/api/health` answers `503`. Nothing has to be restarted: everything works again a few seconds after MySQL is back, with the same sessions.

**Price provider.**

- The site keeps working and keeps showing the last known prices and portfolio values; the stock page says "Prices delayed — last update 3 h ago" once the newest price is over 15 minutes old.
- **Market orders, recurring buys and currency conversions refuse to run on an old price or exchange rate** (HTTP 503 with the reason), and a recurring buy that is due waits for the next run. Limit and stop-loss orders aren't affected: they only fill when a new price arrives. The limit is `MARKETDATA_MAX_PRICE_AGE_MINUTES` / `MARKETDATA_MAX_FX_RATE_AGE_MINUTES`; at 0 it is three refresh rounds, never under 15 (prices) or 30 (rates) minutes.
- A provider request is abandoned after 5 s to connect / 10 s to read (`MARKETDATA_CONNECT_TIMEOUT_MS`, `MARKETDATA_READ_TIMEOUT_MS`), so a provider that hangs can't freeze the background jobs, which run on their own small thread pool. A failed poll still counts against the poll spacing, so an outage doesn't use up the daily request allowance.
- Everything recovers by itself when the provider does.
- On Alpha Vantage's free plan a price is hours old even when everything works (25 requests a day shared by all tickers), so "live" trading needs Finnhub or a paid plan.

</details>

<details>
<summary><b>Installing on a phone</b></summary>

<br>

The site ships a web manifest and icons, so "Add to Home Screen" (iPhone Safari, Android Chrome) gives a full-screen app with the Meridian icon. It needs the live API, so there is no offline mode and no push notifications; the emails cover what push would.

</details>

<details>
<summary><b>Backups</b></summary>

<br>

```sh
./scripts/backup.sh                      # backups/meridian-<date>.sql.gz
./scripts/backup.sh /path/to/file.sql.gz

docker compose stop backend              # nothing should write during a restore
./scripts/restore.sh backups/meridian-<date>.sql.gz
docker compose start backend
```

Backups are a `mysqldump` taken through `docker compose exec`, safe to run while the site is up. `restore.sh` asks for confirmation (`--yes` skips it) and replaces the data of every table in the backup; a table created after the backup was taken is left as it is. A nightly cron line is shown at the top of `backup.sh`. Copy the files off the machine: a backup on the same disk doesn't survive losing the disk. When you use more than one stack on the same machine, set `COMPOSE_PROJECT_NAME` so the scripts talk to the right one.

</details>

## Configuration

Set these in `.env` (Docker) or `meridian-backend/.env` (development).

<details>
<summary><b>All settings</b></summary>

<br>

| Variable | Default | Meaning |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` | required (Docker) | Database passwords |
| `MYSQL_DATABASE`, `MYSQL_USER` | `meridian` | Database name and app user |
| `MYSQL_HOST`, `MYSQL_PORT` | `localhost`, `3306` | Where the backend finds MySQL (Docker sets the host for you) |
| `JWT_SECRET` | required | Session signing key, at least 32 random characters |
| `MARKETDATA_PROVIDER` | `alphavantage` | `alphavantage` or `finnhub` |
| `ALPHA_VANTAGE_API_KEY` / `FINNHUB_API_KEY` | empty | Key for the chosen provider |
| `MARKETDATA_DAILY_REQUEST_BUDGET` | `0` | Provider calls per UTC day; `0` means the free-plan default (Alpha Vantage 25, Finnhub 50,000) |
| `MARKETDATA_FX_POLL_INTERVAL_MS` | `0` | FX refresh interval; `0` means the provider default |
| `MARKETDATA_DIVIDEND_REFRESH_HOURS` | `0` | How often a held stock's dividends are looked up; `0` means the provider default (Alpha Vantage weekly, Finnhub daily; Finnhub's dividend data needs a paid plan) |
| `MARKETDATA_NEWS_CACHE_MINUTES` | `0` | How long a stock's news is reused before asking the provider again; `0` means the provider default (Alpha Vantage 6 h, since news shares the daily allowance with prices; Finnhub 30 min) |
| `MARKET_HOURS_ENFORCED` | `true` | Stocks trade only in the exchange session; `false` = any hour |
| `HISTORY_FULL_RESOLUTION_DAYS`, `HISTORY_DAILY_AFTER_DAYS` | `7`, `90` | Keep every price / portfolio value for this many days, then one per hour, then (after the second number of days) one per day |
| `HISTORY_RETENTION_CRON` | `0 30 3 * * *` | When the nightly clean-up runs |
| `MARKETDATA_MAX_PRICE_AGE_MINUTES`, `MARKETDATA_MAX_FX_RATE_AGE_MINUTES` | `0` | Oldest price / exchange rate a trade may use; `0` = derived from the polling settings (at least 15 / 30 minutes) |
| `MARKETDATA_CONNECT_TIMEOUT_MS`, `MARKETDATA_READ_TIMEOUT_MS` | `5000`, `10000` | When to give up on a provider request |
| `PUBLIC_URL` | `http://localhost` (Docker) | Address people use in the browser; the only allowed CORS origin in Docker and the base of email links |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:5174` | Comma-separated origins, for development (Docker derives it from `PUBLIC_URL`) |
| `AUTH_COOKIE_SECURE` | `false` | Set `true` once the site is served over HTTPS |
| `FORWARD_HEADERS_STRATEGY` | `none` (Docker: `framework`) | Trust `X-Forwarded-*` from a reverse proxy. Leave `none` if the backend is reachable directly |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_STARTTLS`, `MAIL_FROM` | host empty | SMTP settings; with no host, emails are only logged |
| `WEB_PORT` | `80` | Host port of the site (Docker) |
| `SHOW_SQL` | `false` | Print every SQL statement (debugging only) |

</details>

The free Alpha Vantage plan allows about 25 requests a day, so prices refresh roughly every 1.5 hours. For near-live prices use Finnhub (`MARKETDATA_PROVIDER=finnhub`).

## API

Every endpoint except sign-up, login, password reset and health needs the session cookie.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/register`, `/login`, `/logout` | Sign up, sign in (sets the session cookie), sign out |
| `POST` | `/api/auth/forgot-password`, `/reset-password`, `/verify-email` | Account recovery and email verification |
| `POST` / `DELETE` | `/api/auth/change-password`, `/api/auth/account` | Change password (ends other sessions), delete the account and all its data |
| `GET` | `/api/tickers`, `/api/tickers/search?q=` | Tracked tickers; search for new ones |
| `GET` | `/api/prices/{symbol}?range=&points=` | Price history for a chart |
| `GET` | `/api/news/{symbol}` | Company news, newest first |
| `GET` | `/api/dividends/{symbol}` | Next and recent dividends, yield and what you've received |
| `GET` | `/api/market/status`, `/movers`, `/changes` | Whether each market is open, top movers, today's change for every ticker |
| `GET` | `/api/portfolio`, `/history`, `/transactions` | Holdings and value, value over time, activity feed |
| `GET` | `/api/portfolio/performance` | Realized and unrealized P/L, fees, win rate, per-stock breakdown |
| `GET` | `/api/portfolio/benchmark?symbol=SPY&range=1M` | Your time-weighted return against a ticker |
| `GET` / `PUT` | `/api/portfolio/rebalance`, `/api/portfolio/targets` | Drift from your target allocation and the trades to fix it; set the targets |
| `POST` / `GET` | `/api/orders`, `/api/orders/open` | Place an order; order history; open orders |
| `PUT` / `DELETE` | `/api/orders/{id}`, `/api/orders/{id}/note` | Edit a pending order (cancel and replace in one transaction), cancel it, or set its note |
| `GET` / `POST` | `/api/wallets`, `/deposit`, `/withdraw`, `/convert`, `/api/fx-rates` | USD, EUR and GBP wallets and exchange rates |
| `GET` / `POST` / `PUT` / `DELETE` | `/api/watchlist`, `/api/watchlist/{symbol}` | Watchlist, with a note and target price per stock |
| `GET` / `POST` / `DELETE` | `/api/alerts`, `/api/recurring-orders` | Price alerts and recurring buys |
| `GET` | `/api/health` | Database and price-feed status |
| WebSocket | `/ws/prices` | Live prices, exchange rates, fills, rejections, alerts and dividends |

## Tests

```sh
# Backend: H2 in memory, runs the real Flyway migrations
cd meridian-backend && mvn -B clean test

# Frontend
cd meridian-frontend && npm test && npm run build
```

- **Accessibility.** axe-core runs on every form panel, modal and the login page, and `src/lib/contrast.test.js` reads the colour tokens in `src/index.css` and fails if any text colour drops under 4.5:1 (or a form-field border under 3:1) in either theme.
- **JDK 21.** Run the backend tests on JDK 21, which is what the project targets and CI uses. On JDK 25 seven tests that use Mockito fail with "Mockito cannot mock this class".
- **Real MySQL.** Some tests need a real MySQL server and skip themselves otherwise. CI runs them against a MySQL 8.4 service; locally:

```sh
docker run -d --name mysql-test -e MYSQL_ROOT_PASSWORD=test -e MYSQL_DATABASE=meridian_test -p 3399:3306 mysql:8.4
MERIDIAN_TEST_MYSQL_URL='jdbc:mysql://localhost:3399/meridian_test' \
MERIDIAN_TEST_MYSQL_USER=root MERIDIAN_TEST_MYSQL_PASSWORD=test \
  mvn -B clean test -Dtest='MySql*'
```

- **Load and consistency.** [`scripts/load-test`](scripts/load-test/README.md) runs many users trading at once and WebSocket fan-out, then checks that cash and shares add up.

CI (`.github/workflows/ci.yml`) runs the backend tests, the MySQL tests, the frontend tests and build, and a Docker image build on every push.

## Project structure

```
meridian-backend/src/main/java/com/meridian/backend/
├── controller/   REST endpoints (auth, portfolio and orders, wallets, market data, watchlist, alerts, recurring buys, dividends, health)
├── service/      order engine, wallets and FX, performance, benchmark (time-weighted return), rebalancing, dividends, news
├── client/       Alpha Vantage and Finnhub providers behind one MarketDataProvider interface, daily request budget
├── scheduler/    price and FX polling, recurring buys, dividends, portfolio snapshots, clean-up jobs
├── market/       NYSE/Nasdaq calendar: sessions, early closes, holidays
├── retention/    nightly thinning of price and portfolio history
├── security/     JWT cookie sessions, database-backed rate limits, WebSocket auth
├── websocket/    live price and event push
├── mail/         SMTP or log-only email
└── model/, repository/, dto/, exception/, config/
meridian-backend/src/main/resources/db/migration/   Flyway schema, V1–V13

meridian-frontend/src/
├── components/   Home, StockPage, Portfolio, trade sheet, pickers, charts, modals, command palette
├── lib/          API client, formatting, CSV export, target distance, contrast checks
└── index.css     colour tokens for both themes

scripts/          backup.sh, restore.sh, load-test/
docker-compose.yml   the full stack (MySQL, backend, nginx)
.github/workflows/   CI
```

## Design decisions

| Decision | Why |
|---|---|
| **MySQL at `READ COMMITTED` with row locks** | Money paths read a balance and then write it. Locking the account's rows makes concurrent orders queue instead of overwriting each other, which a load test proved was happening. |
| **Flyway owns the schema** | Hibernate runs with `ddl-auto=validate`, so the backend refuses to start if the code and the schema disagree. Every change is a new numbered migration, never an edit. |
| **JWT in an HttpOnly cookie** | Page scripts can't read the session, and one cookie covers both the REST API and the WebSocket handshake. |
| **One `MarketDataProvider` interface** | Alpha Vantage and Finnhub are swappable with one setting, and a daily request budget keeps the free plans from running out by mid-morning. |
| **Exchange calendar worked out by rule** | Holidays and early closes are calculated for any year, so nothing needs updating each January. |
| **Time-weighted return for "you vs the market"** | A simple percentage would count a deposit as a gain. Chaining snapshot growth between cash flows measures only what the investments did. |
| **Thin old history instead of deleting it** | Charts keep their shape for any range while the two biggest tables stay small. |

## Changing the database

Add a new file `meridian-backend/src/main/resources/db/migration/V<n>__description.sql` and never edit an existing one. A database created before Flyway was introduced is treated as being at V1.

## Limitations

- Deposits are fake. Real money would need a licensed broker or payment partner and identity checks; that isn't a code change.
- On Finnhub's free plan, stock and crypto quotes (`BINANCE:<SYMBOL>USDT`) and company news work, but exchange rates and dividends are paid features. Exchange rates therefore come from [Frankfurter](https://frankfurter.dev) (the European Central Bank's daily reference rates, free, no key), refreshed hourly; dividends show none.
- There is no monitoring or alerting beyond `/api/health` (its `priceFeed` field is what to alert on). The interface has been checked with automated tools and by keyboard, but not with a real screen reader.
- Load numbers come from one laptop. WebSocket sessions live in the memory of one backend, so running several backends behind a load balancer would need shared messaging first.
- Prices come from a single provider at a time (see "When the database or the price provider is down" for what happens when it fails).

## Project status

**Complete, v1.0.0 (October 2026).** Meridian was built from 18 September to 11 October 2026 and is now finished. The code stays here as a working, documented reference; no new features are planned.

### What v1.0.0 contains

- **A broker-grade order engine:** market, limit, stop-loss and trailing-stop orders, queued orders for a closed market, recurring buys, and edits as one cancel-and-replace transaction.
- **Money that adds up:** row locks on every money path, reservations for pending orders, invariant checks after load tests and real-MySQL concurrency tests in CI.
- **Three currencies and dividends:** USD, EUR and GBP wallets at ECB rates (Frankfurter) or Alpha Vantage, and dividends paid to whoever held the stock on the ex-date.
- **Honest performance:** time-weighted return against any ticker, and value changes that leave out money you put in.
- **Live everything:** prices, fills, alerts and dividends over WebSocket, with near-live quotes from Finnhub.
- **A Revolut-style interface:** dark and light themes tested for contrast, a phone layout with a trade sheet, and day-grouped history.
- **Ready to run:** Docker Compose with nginx, health checks, backups and restore, nightly history thinning and CI on every push.

### By the numbers

| | |
|---|---|
| Built | 18 Sep – 11 Oct 2026 |
| Backend | about 10,000 lines of Java, 13 Flyway migrations |
| Frontend | about 7,200 lines of React, 49 components |
| Tests | 316 backend + 210 frontend, plus load and consistency scripts |

### If it is picked up again

The natural next steps, roughly in order:

1. **Shared messaging for WebSockets** (Redis or a broker), so several backends can run behind a load balancer.
2. **A second price provider as a fallback**, so an outage of one doesn't stall market orders.
3. **Monitoring and alerting** on `/api/health`'s `priceFeed`, and a screen-reader pass on the interface.
4. **Dividend reinvestment and more asset types** (ETFs are already tradable; options and bonds are not).

<p align="center">
  Built by <a href="https://github.com/samaunmahmud">Samaun Mahmud</a> · Computer Science (AI), Brunel University London
</p>

# Meridian

A paper-trading app: practise buying and selling stocks and crypto with virtual money, in USD, EUR or GBP, without risking any real cash.

- **Backend:** Spring Boot 3.3, Java 21, MySQL 8.4, Flyway migrations, JWT session in an HttpOnly cookie, WebSocket for live prices.
- **Frontend:** React 19, Vite, Tailwind 4 (dark and light themes, works on phones).
- **Deployment:** one `docker-compose.yml` (MySQL + backend + nginx).

## Features

- Stocks and crypto, with a watchlist, price alerts and live prices pushed over WebSocket
- Market, limit and stop-loss orders, recurring buys
- Trading hours: stocks trade in the exchange session (see "Trading hours" below); crypto is always open
- Emails when you are not looking: a price alert fired, a queued or pending order filled or was rejected (to confirmed addresses only)
- Chart ranges (1D / 1W / 1M / All) and a market open/closed badge on every stock; installable on a phone's home screen
- USD, EUR and GBP wallets. Any order type can be paid from any wallet; a pending limit buy reserves its money (and commission) in that wallet
- Portfolio with holdings, equity chart and an activity feed
- Sign-up and login with database-backed rate limits, password reset, email verification

Deposits are simulated. There is no connection to a real broker or payment provider.

## Repository layout

```
meridian-backend/    Spring Boot API (src/main/resources/db/migration holds the schema)
meridian-frontend/   React app, nginx.conf for the production image
scripts/             backup.sh and restore.sh
docker-compose.yml   The full stack (MySQL, backend, web)
.env.example         Settings for docker-compose.yml
.github/workflows/   CI
```

## Run it locally (development)

You need Java 21, Maven, Node 22 or newer and Docker (for MySQL).

```sh
# 1. Configuration for the backend (it reads meridian-backend/.env)
cp meridian-backend/.env.example meridian-backend/.env
#    then fill in JWT_SECRET (at least 32 random characters: openssl rand -base64 48)
#    and ALPHA_VANTAGE_API_KEY (or use Finnhub, see below)

# 2. MySQL only, on localhost:3306
cd meridian-backend
docker compose up -d

# 3. Backend on http://localhost:8080  (the schema is created by Flyway on first start)
mvn spring-boot:run

# 4. Frontend on http://localhost:5173
cd ../meridian-frontend
npm install     # an npm warning about fsevents' install script is harmless
npm run dev
```

Then sign up on the login page. In development the frontend calls `http://localhost:8080/api` unless `VITE_API_BASE` is set. With `MAIL_HOST` empty, password-reset and verification emails are only printed in the backend log, so copy the link from there.

## Deploy with Docker

```sh
cp .env.example .env
# edit .env: set MYSQL_ROOT_PASSWORD, MYSQL_PASSWORD, JWT_SECRET (and the price key, MAIL_*, PUBLIC_URL)
docker compose up -d --build
```

The site is then at `http://localhost` (or the `WEB_PORT` you set). nginx serves the frontend and proxies `/api` and `/ws` to the backend; the database is not published on the host.

For a real site, put HTTPS in front (a load balancer or a TLS-terminating proxy), then set:

- `PUBLIC_URL` to the exact address people type, for example `https://app.example.com`. It is the only origin the API accepts and the base of the links in emails.
- `AUTH_COOKIE_SECURE=true`, so the session cookie is only sent over HTTPS.
- `MAIL_*` to a real SMTP server. Without it nobody receives reset or verification emails.

If you do not terminate TLS at the bundled nginx, make sure whatever sits in front of it overwrites `X-Forwarded-For`; the login rate limits trust that header in the Docker setup.

### Health check

`GET /api/health` returns `200` with `{"status":"ok","database":"up","priceFeed":"ok","newestPriceAgeSeconds":12}`, or `503` when the backend cannot reach MySQL. Docker uses it, and you can point an uptime monitor at it. `priceFeed` is `ok`, `stale` (the newest price is older than trades accept, see below) or `empty`; it never turns the response into a 503, because the site is up and restarting it would not fix the feed. To be told about a provider outage, alert on `"priceFeed":"stale"`.

### Trading hours

Stocks trade in the regular NYSE/Nasdaq session, **09:30-16:00 New York time, Monday-Friday**, with the 13:00 early closes and closed on exchange holidays (worked out by rule for any year, so nothing to update each January). Pre-market and after-hours are not modelled. Crypto trades around the clock.

- A **market order placed while a stock market is closed is queued** (status `PENDING`, shown as "(at open)"). A buy holds the last price plus a 10% cushion (and the commission) until then, a sell holds its shares. It fills at the first price polled after the open and releases what it did not need; if the stock opens beyond the cushion the order is rejected and everything is released, never overdrawn. It can be cancelled like any pending order.
- Limit and stop-loss orders only fill while the market is open (a closed market just repeats the last close). Recurring buys wait for the open.
- `GET /api/market/status` says whether each market is open and when that changes. Set `MARKET_HOURS_ENFORCED=false` to let stocks trade at any hour (for a demo).

### Installing on a phone

The site ships a web manifest and icons, so "Add to Home Screen" (iPhone Safari, Android Chrome) gives a full-screen app with the Meridian icon. It needs the live API, so there is no offline mode and no push notifications; the emails cover what push would.

### When the database is down

Requests that need it answer `503` ("temporarily unavailable") after at most 5 s (`spring.datasource.hikari.connection-timeout`), which the browser shows as an error without signing anyone out, and `/api/health` answers `503`. Nothing has to be restarted: everything works again a few seconds after MySQL is back, with the same sessions.

### When the price provider is down

- The site keeps working and keeps showing the last known prices and portfolio values; the stock page says "Prices delayed — last update 3 h ago" once the newest price is over 15 minutes old.
- **Market orders, recurring buys and currency conversions refuse to run on an old price or exchange rate** (HTTP 503 with the reason), and a recurring buy that is due waits for the next run. Limit and stop-loss orders are not affected: they only fill when a new price arrives. The limit is `MARKETDATA_MAX_PRICE_AGE_MINUTES` / `MARKETDATA_MAX_FX_RATE_AGE_MINUTES`; at 0 it is three refresh rounds, never under 15 (prices) or 30 (rates) minutes.
- A provider request is abandoned after 5 s to connect / 10 s to read (`MARKETDATA_CONNECT_TIMEOUT_MS`, `MARKETDATA_READ_TIMEOUT_MS`), so a provider that hangs cannot freeze the background jobs, which run on their own small thread pool. Failures are logged as one line each, and a failed poll still counts against the poll spacing, so an outage does not use up the daily request allowance.
- Everything recovers by itself when the provider does: the next successful poll refreshes the price and trading resumes.
- On Alpha Vantage's free plan a price is hours old even when everything works (25 requests a day shared by all tickers), so "live" trading needs Finnhub or a paid plan.

### Backups

```sh
./scripts/backup.sh                      # backups/meridian-<date>.sql.gz
./scripts/backup.sh /path/to/file.sql.gz

docker compose stop backend              # nothing should write during a restore
./scripts/restore.sh backups/meridian-<date>.sql.gz
docker compose start backend
```

Backups are a `mysqldump` taken through `docker compose exec`, safe to run while the site is up. `restore.sh` asks for confirmation (`--yes` skips it) and replaces the data of every table in the backup; a table created after the backup was taken is left as it is. A nightly cron line is shown at the top of `backup.sh`. Copy the files off the machine: a backup on the same disk does not survive losing the disk. When you use more than one stack on the same machine, set `COMPOSE_PROJECT_NAME` so the scripts talk to the right one.

## Configuration

Set these in `.env` (Docker) or `meridian-backend/.env` (development).

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
| `MARKET_HOURS_ENFORCED` | `true` | Stocks trade only in the exchange session; `false` = any hour |
| `MARKETDATA_MAX_PRICE_AGE_MINUTES`, `MARKETDATA_MAX_FX_RATE_AGE_MINUTES` | `0` | Oldest price / exchange rate a trade may use; `0` = derived from the polling settings (at least 15 / 30 minutes) |
| `MARKETDATA_CONNECT_TIMEOUT_MS`, `MARKETDATA_READ_TIMEOUT_MS` | `5000`, `10000` | When to give up on a provider request |
| `PUBLIC_URL` | `http://localhost` (Docker) | Address people use in the browser; the only allowed CORS origin in Docker and the base of email links |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:5174` | Comma-separated origins, for development (Docker derives it from `PUBLIC_URL`) |
| `AUTH_COOKIE_SECURE` | `false` | Set `true` once the site is served over HTTPS |
| `FORWARD_HEADERS_STRATEGY` | `none` (Docker: `framework`) | Trust `X-Forwarded-*` from a reverse proxy. Leave `none` if the backend is reachable directly |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_STARTTLS`, `MAIL_FROM` | host empty | SMTP settings; with no host, emails are only logged |
| `WEB_PORT` | `80` | Host port of the site (Docker) |
| `SHOW_SQL` | `false` | Print every SQL statement (debugging only) |

The free Alpha Vantage plan allows about 25 requests a day, so prices refresh roughly every 1.5 hours. For near-live prices use Finnhub (`MARKETDATA_PROVIDER=finnhub`).

## Tests

```sh
# Backend: H2 in memory, runs the real Flyway migrations
cd meridian-backend && mvn -B clean test

# Frontend
cd meridian-frontend && npm test && npm run build
```

The frontend tests include accessibility checks: axe-core runs on every form panel, modal and the login page, and `src/lib/contrast.test.js` reads the colour tokens in `src/index.css` and fails if any text colour drops under 4.5:1 (or a form-field border under 3:1) in either theme. When you change a colour token, that test is the one to watch.

Run the backend tests on JDK 21, which is what the project targets and CI uses. On JDK 25 seven tests that use Mockito fail with "Mockito cannot mock this class".

Four tests need a real MySQL server and skip themselves otherwise. To run them:

```sh
docker run -d --name mysql-test -e MYSQL_ROOT_PASSWORD=test -e MYSQL_DATABASE=meridian_test -p 3399:3306 mysql:8.4
MERIDIAN_TEST_MYSQL_URL='jdbc:mysql://localhost:3399/meridian_test' \
MERIDIAN_TEST_MYSQL_USER=root MERIDIAN_TEST_MYSQL_PASSWORD=test \
  mvn -B clean test -Dtest='MySql*'
```

`MySqlConcurrencyTest` is the one to keep green: it fires simultaneous orders from one account at a real MySQL and checks that none is lost. The app depends on MySQL running at `READ COMMITTED` (set in `application.properties`); under MySQL's default `REPEATABLE READ` such orders overwrite each other, and H2 (the other tests) cannot show it.

For load and data-consistency checks (many users trading at once, then verifying cash and shares add up, WebSocket fan-out) see [`scripts/load-test`](scripts/load-test/README.md); it also lists what one laptop measured.

CI (`.github/workflows/ci.yml`) runs the backend tests, the MySQL tests against a MySQL 8.4 service, the frontend tests and build, and a Docker image build on every push.

## Changing the database

The schema belongs to Flyway. Add a new file `meridian-backend/src/main/resources/db/migration/V<n>__description.sql` and never edit an existing one. Hibernate runs with `ddl-auto=validate`, so the backend refuses to start if the entities and the schema disagree. A database created before Flyway was introduced is treated as being at V1.

## Known limitations

- Deposits are fake. Real money would need a licensed broker or payment partner and identity checks; that is not a code change.
- Finnhub support is covered by unit tests and by runs against a stand-in server that speaks its documented API, but has not been tried with a live API key. In particular, crypto is quoted as `BINANCE:<SYMBOL>USDT` through `/quote`, and whether Finnhub's free plan serves that (and `/forex/rates`) is unconfirmed.
- There is no monitoring or alerting beyond `/api/health` (its `priceFeed` field is what to alert on). The interface has been checked with automated tools and by keyboard, but not with a real screen reader.
- Load numbers come from one laptop (see `scripts/load-test`). WebSocket sessions live in the memory of one backend, so running several backends behind a load balancer would need shared messaging first.
- Prices come from a single provider (see "When the price provider is down" for what happens when it fails).

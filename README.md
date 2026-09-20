# Meridian

A paper-trading app: practise buying and selling stocks and crypto with virtual money, in USD, EUR or GBP, without risking any real cash.

- **Backend:** Spring Boot 3.3, Java 21, MySQL 8.4, Flyway migrations, JWT session in an HttpOnly cookie, WebSocket for live prices.
- **Frontend:** React 19, Vite, Tailwind 4 (dark and light themes, works on phones).
- **Deployment:** one `docker-compose.yml` (MySQL + backend + nginx).

## Features

- Stocks and crypto, with a watchlist, price alerts and live prices pushed over WebSocket
- Market, limit and stop-loss orders, recurring buys
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

`GET /api/health` returns `200` with `{"status":"ok","database":"up"}`, or `503` when the backend cannot reach MySQL. Docker uses it, and you can point an uptime monitor at it.

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

CI (`.github/workflows/ci.yml`) runs the backend tests, the MySQL tests against a MySQL 8.4 service, the frontend tests and build, and a Docker image build on every push.

## Changing the database

The schema belongs to Flyway. Add a new file `meridian-backend/src/main/resources/db/migration/V<n>__description.sql` and never edit an existing one. Hibernate runs with `ddl-auto=validate`, so the backend refuses to start if the entities and the schema disagree. A database created before Flyway was introduced is treated as being at V1.

## Known limitations

- Deposits are fake. Real money would need a licensed broker or payment partner and identity checks; that is not a code change.
- Finnhub support is covered by unit tests but has not been tried with a live API key.
- There is no monitoring or alerting beyond `/api/health`, no accessibility review and no load test.
- Prices come from a single provider; how the app behaves when it is down for hours has not been tested.

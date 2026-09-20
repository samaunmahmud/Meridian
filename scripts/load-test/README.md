# Load and consistency tests

Scripts that hammer a running Meridian with many users at once and then check that **the data is
still consistent**. They found real bugs (see the git history of `PortfolioService`,
`PriceWebSocketHandler` and `application.properties`), so run them after changing anything that
touches orders, holdings, wallets or WebSockets.

Use a **throwaway** stack (they create hundreds of users and thousands of orders), for example the
development backend on `:8080` with an empty MySQL, or `COMPOSE_PROJECT_NAME=meridian-load docker compose up -d`.
They need Python 3, Node 22 (`npm i ws` in this folder) and, for `seed.sh`, Docker.

```sh
# 1. users: load1..load360, all with the same password (one real sign-up, hash copied)
BASE=http://localhost:8080 ./scripts/load-test/seed.sh      # Docker stack: BASE=http://localhost:80

cd scripts/load-test
# Point the scripts at the API: default is the dev backend on localhost:8080; for the Docker
# stack (nginx) export LOAD_PORT=80 and LOAD_ORIGIN=http://localhost
python3 load.py login 20            # bcrypt login of every user, saves cookies.json
python3 load.py tickers             # AAPL MSFT NVDA TSLA (needs a price provider or existing prices)

python3 load.py reads  50 20        # mixed authenticated GETs: 50 clients for 20 s
python3 load.py trades 50 30        # market BUY/SELL across 300 users
python3 load.py hot    30 300       # 300 orders from ONE account, 30 at a time
python3 first_buy_race.py 55 8 AAPL # 55 fresh users x 8 simultaneous first-ever buys of a stock

npm i ws && node ws_load.mjs 1000 0 40      # 1000 WebSocket clients, 40 broadcasts, fan-out time
node ws_load.mjs 200 0 2000 16              # 16 broadcasts triggered at once: nothing may be lost
```

## Read the result

The timing tables show requests per second and p50/p95/p99 latency per endpoint. **`5xx/err` must be 0**
(a `4xx` on orders is a normal business refusal such as insufficient funds).

Then check the data. This is the important part, because a race in money code does not crash:

```sh
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' < scripts/load-test/invariants.sql
```

Both queries must report `0` mismatches: every user's cash equals 10 000 plus their ledger, and every
holding equals filled buys minus filled sells. (A bug that let two orders of one account overwrite each
other left 130 of 1 550 positions wrong while every request answered 200.)

## What one laptop showed

Measured on an 8-core Mac with the load generator, the backend (JDK 21) and MySQL 8.4 in Docker all on
the same machine, so treat these as a floor and a sanity check, not as production sizing:

| | result |
| --- | --- |
| authenticated reads | about 3 500 requests/s, no errors; p99 under 35 ms with 50 clients, about 160 ms with 200 |
| logins (bcrypt) | about 70 a second |
| market orders, 50 users at once | about 830 orders/s, p99 about 100 ms, 0 errors, cash and shares consistent |
| WebSocket, 1 000 clients | every broadcast reaches all of them in about 20 ms; server memory grew by about 140 MB |
| database stopped | `/api/health` answers 503 in 5 s, signed-in requests get 503 (not 401), and everything works again 2-4 s after MySQL returns, without anyone logging in again |

Not covered: more than one backend instance (WebSocket sessions live in the instance's memory), runs of
hours, thousands of simultaneous clients, and a client that stays stuck for a long time.

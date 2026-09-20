#!/usr/bin/env python3
"""Load driver.  Usage:
   load.py login  <concurrency>                 log in seeded users load1..load360 (bcrypt) and save their cookies
   load.py tickers                              add the stocks the mixes trade (AAPL MSFT NVDA TSLA)
   load.py reads  <concurrency> <seconds>       mixed authenticated GETs
   load.py trades <concurrency> <seconds>       mixed market BUY/SELL across many users
   load.py hot    <concurrency> <orders>        many concurrent market orders from ONE user
"""
import http.client, json, os, random, statistics, sys, threading, time, collections

# Where the API is: the dev backend (8080) by default; for the Docker stack use LOAD_PORT=80 (nginx).
HOST, PORT = os.environ.get("LOAD_HOST", "localhost"), int(os.environ.get("LOAD_PORT", "8080"))
PW = "CorrectHorse!42battery"
HDRS = {"Content-Type": "application/json", "X-Requested-With": "XMLHttpRequest"}
COOKIES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cookies.json")
SYMBOLS = ["IBM", "AAPL", "MSFT", "NVDA", "TSLA"]
TIMEOUT = 30


def call(conn, method, path, body=None, cookie=None):
    h = dict(HDRS)
    if cookie:
        h["Cookie"] = cookie
    t0 = time.perf_counter()
    try:
        conn.request(method, path, json.dumps(body) if body is not None else None, h)
        r = conn.getresponse()
        data = r.read()
        return r.status, time.perf_counter() - t0, r, data
    except Exception as e:  # timeout, reset, ...
        try:
            conn.close()
        except Exception:
            pass
        return 0, time.perf_counter() - t0, None, str(e).encode()


def new_conn():
    return http.client.HTTPConnection(HOST, PORT, timeout=TIMEOUT)


def pct(sorted_vals, p):
    if not sorted_vals:
        return 0
    return sorted_vals[min(len(sorted_vals) - 1, int(len(sorted_vals) * p))]


def report(title, results, wall):
    """results: list of (op, status, seconds)"""
    print(f"\n=== {title}  ({wall:.1f}s wall, {len(results)} requests, {len(results) / wall:.0f} req/s) ===")
    by = collections.defaultdict(list)
    for op, st, sec in results:
        by[op].append((st, sec))
    print(f"{'operation':22s} {'n':>6s} {'ok':>6s} {'4xx':>5s} {'5xx/err':>8s}   {'p50':>7s} {'p95':>7s} {'p99':>7s} {'max':>7s}  (ms)")
    for op, rows in sorted(by.items()):
        secs = sorted(s * 1000 for _, s in rows)
        ok = sum(1 for st, _ in rows if 200 <= st < 300)
        c4 = sum(1 for st, _ in rows if 400 <= st < 500)
        bad = sum(1 for st, _ in rows if st == 0 or st >= 500)
        print(f"{op:22s} {len(rows):6d} {ok:6d} {c4:5d} {bad:8d}   {pct(secs, .5):7.1f} {pct(secs, .95):7.1f} {pct(secs, .99):7.1f} {secs[-1]:7.1f}")
    codes = collections.Counter(st for _, st, _ in results)
    print("status codes:", dict(sorted(codes.items())))


def load_cookies():
    return json.load(open(COOKIES))


def scenario_login(conc):
    emails = [f"load{i}@example.com" for i in range(1, 361)]
    cookies, results, lock = {}, [], threading.Lock()
    q = list(emails)

    def w():
        conn = new_conn()
        while True:
            with lock:
                if not q:
                    return
                e = q.pop()
            st, sec, r, _ = call(conn, "POST", "/api/auth/login", {"email": e, "password": PW})
            with lock:
                results.append(("POST /auth/login", st, sec))
                if st == 200:
                    cookies[e] = r.getheader("Set-Cookie").split(";")[0]

    t0 = time.perf_counter()
    ts = [threading.Thread(target=w) for _ in range(conc)]
    [t.start() for t in ts]
    [t.join() for t in ts]
    json.dump(cookies, open(COOKIES, "w"))
    report(f"login storm, {conc} at a time", results, time.perf_counter() - t0)


def run_workers(conc, duration, op_fn):
    cookies = load_cookies()
    # users 1-300 take part in the mixed runs; 301-360 stay untouched for first_buy_race.py
    users = [cookies[f"load{i}@example.com"] for i in range(1, 301)]
    results, lock, stop = [], threading.Lock(), time.time() + duration

    def w():
        conn = new_conn()
        local = []
        while time.time() < stop:
            op, st, sec = op_fn(conn, random.choice(users))
            local.append((op, st, sec))
        with lock:
            results.extend(local)

    t0 = time.perf_counter()
    ts = [threading.Thread(target=w) for _ in range(conc)]
    [t.start() for t in ts]
    [t.join() for t in ts]
    return results, time.perf_counter() - t0


def read_op(conn, cookie):
    x = random.random()
    if x < .35:
        op, path = "GET /portfolio", "/api/portfolio"
    elif x < .55:
        op, path = "GET /tickers", "/api/tickers"
    elif x < .75:
        op, path = "GET /prices/{sym}", f"/api/prices/{random.choice(SYMBOLS)}"
    elif x < .87:
        op, path = "GET /wallets", "/api/wallets"
    else:
        op, path = "GET /orders", "/api/orders"
    st, sec, _, _ = call(conn, "GET", path, cookie=cookie)
    return op, st, sec


def trade_op(conn, cookie):
    if random.random() < .12:
        st, sec, _, _ = call(conn, "GET", "/api/portfolio", cookie=cookie)
        return "GET /portfolio", st, sec
    side = "BUY" if random.random() < .6 else "SELL"
    st, sec, _, _ = call(conn, "POST", "/api/orders", {"symbol": random.choice(SYMBOLS), "type": side, "kind": "MARKET", "quantity": random.choice([1, 1, 2, 0.5])}, cookie)
    return f"POST /orders {side}", st, sec


def scenario_hot(conc, n):
    cookie = list(load_cookies().values())[0]
    results, lock, left = [], threading.Lock(), [n]

    def w():
        conn = new_conn()
        while True:
            with lock:
                if left[0] <= 0:
                    return
                left[0] -= 1
            st, sec, _, _ = call(conn, "POST", "/api/orders", {"symbol": "IBM", "type": "BUY", "kind": "MARKET", "quantity": 0.1}, cookie)
            with lock:
                results.append(("POST /orders BUY (one user)", st, sec))

    t0 = time.perf_counter()
    ts = [threading.Thread(target=w) for _ in range(conc)]
    [t.start() for t in ts]
    [t.join() for t in ts]
    report(f"one account, {conc} concurrent market buys", results, time.perf_counter() - t0)


if __name__ == "__main__":
    what = sys.argv[1]
    if what == "login":
        scenario_login(int(sys.argv[2]))
    elif what == "tickers":
        conn, cookie = new_conn(), list(load_cookies().values())[0]
        for sym, name in [("AAPL", "Apple"), ("MSFT", "Microsoft"), ("NVDA", "Nvidia"), ("TSLA", "Tesla")]:
            print(sym, call(conn, "POST", "/api/tickers", {"symbol": sym, "name": name, "exchange": "NASDAQ", "assetType": "STOCK"}, cookie)[0])
    elif what == "reads":
        r, wall = run_workers(int(sys.argv[2]), float(sys.argv[3]), read_op)
        report(f"reads, {sys.argv[2]} concurrent clients", r, wall)
    elif what == "trades":
        r, wall = run_workers(int(sys.argv[2]), float(sys.argv[3]), trade_op)
        report(f"trades, {sys.argv[2]} concurrent clients", r, wall)
    elif what == "hot":
        scenario_hot(int(sys.argv[2]), int(sys.argv[3]))

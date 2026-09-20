"""For each of N fresh users (no holdings yet), fire K simultaneous market BUYs of a stock they do not own."""
import json, sys, threading, collections
sys.path.insert(0, ".")
from load import call, new_conn, load_cookies  # LOAD_HOST / LOAD_PORT as for load.py

N, K, SYMBOL = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3]
cookies = load_cookies()
emails = [f"load{i}@example.com" for i in range(301, 301 + N)]  # the untouched users (see seed.sh: 301-360)
codes = collections.Counter()
lock = threading.Lock()
for e in emails:
    ck, barrier = cookies[e], threading.Barrier(K)
    def one():
        conn = new_conn()
        barrier.wait()
        st, sec, _, data = call(conn, "POST", "/api/orders", {"symbol": SYMBOL, "type": "BUY", "kind": "MARKET", "quantity": 1}, ck)
        with lock:
            codes[st] += 1
    ts = [threading.Thread(target=one) for _ in range(K)]
    [t.start() for t in ts]; [t.join() for t in ts]
print(f"{N} fresh users x {K} simultaneous first buys of {SYMBOL}: status codes {dict(codes)}")

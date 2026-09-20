// node ws_load.mjs <healthyConnections> <stalledConnections> <broadcasts>
// Opens WebSocket clients, then triggers price broadcasts (POST /api/tickers re-polls a symbol, which
// broadcasts a PRICE_UPDATE to every connected client) and measures what the server does.
import WebSocket from "ws";
import fs from "node:fs";
import { execSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
// Usage: node ws_load.mjs <healthy> <stalled> <broadcasts> [triggeredAtOnce]   (npm i ws first)
const [healthyN, stalledN, broadcasts, parallel = 1] = process.argv.slice(2).map(Number);
const cookies = Object.values(JSON.parse(fs.readFileSync(path.join(here, "cookies.json"), "utf8")));
// LOAD_PORT / LOAD_ORIGIN as for load.py; the origin must be one the backend allows (CORS_ALLOWED_ORIGINS / PUBLIC_URL).
const PORT = process.env.LOAD_PORT || "8080";
const ORIGIN = process.env.LOAD_ORIGIN || "http://localhost:5173";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function serverRssMb() {
  try {
    return serverRssMbUnsafe();
  } catch {
    return "n/a (backend not a local process)";
  }
}
function serverRssMbUnsafe() {
  const pids = execSync("pgrep -f 'BackendApplication'").toString().trim().split("\n");
  const rss = pids.map((p) => Number(execSync(`ps -o rss= -p ${p}`).toString().trim() || 0));
  return Math.round(Math.max(...rss) / 1024);
}

const healthy = [];
const counts = [];
async function connect(i, stalled) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://localhost:${PORT}/ws/prices`, { headers: { Cookie: cookies[i % cookies.length], Origin: ORIGIN } });
    ws.on("open", () => {
      if (stalled) ws._socket.pause(); // stops reading: the kernel buffers fill and then the sender is blocked
      resolve(ws);
    });
    ws.on("error", reject);
    if (!stalled) {
      const idx = counts.length;
      counts.push(0);
      ws.on("message", () => { counts[idx]++; });
    }
  });
}

const rss0 = serverRssMb();
const t0 = Date.now();
for (let i = 0; i < healthyN; i += 100) {
  const batch = [];
  for (let j = i; j < Math.min(i + 100, healthyN); j++) batch.push(connect(j, false));
  healthy.push(...(await Promise.all(batch)));
}
const stalled = [];
for (let i = 0; i < stalledN; i++) stalled.push(await connect(healthyN + i, true));
console.log(`${healthy.length} healthy + ${stalled.length} stalled connections open in ${Date.now() - t0} ms; server RSS ${rss0} -> ${serverRssMb()} MB`);

const post = async () => {
  const t = performance.now();
  const r = await fetch(`http://localhost:${PORT}/api/tickers`, { method: "POST", headers: { "Content-Type": "application/json", "X-Requested-With": "XMLHttpRequest", Cookie: cookies[0] }, body: JSON.stringify({ symbol: "IBM", name: "IBM", exchange: "NYSE", assetType: "STOCK" }) });
  await r.text();
  return { status: r.status, ms: performance.now() - t };
};

if (parallel > 1) {
  // many broadcasts triggered at the same moment from different server threads
  let sent = 0, failed = 0; const codes = {};
  const worker = async () => { while (sent < broadcasts) { sent++; const r = await post(); codes[r.status] = (codes[r.status] || 0) + 1; if (r.status !== 200) failed++; } };
  await Promise.all(Array.from({ length: parallel }, worker));
  await sleep(3000);
  const missingP = counts.filter((c) => c < broadcasts).length;
  const total = counts.reduce((a, c) => a + c, 0);
  console.log(`\n${broadcasts} broadcasts, ${parallel} triggered at once, to ${healthyN} clients: ${total} of ${broadcasts * healthyN} messages delivered (${(100 * total / (broadcasts * healthyN)).toFixed(2)}%), ${missingP} clients short, ${failed} requests failed; status codes ${JSON.stringify(codes)}`);
  [...healthy, ...stalled].forEach((w) => { try { w.terminate(); } catch {} });
  process.exit(0);
}
const posts = [];
const fanout = [];
let slow = 0;
for (let k = 1; k <= broadcasts; k++) {
  const t = performance.now();
  const res = await post();
  posts.push(res.ms);
  if (res.status !== 200) console.log(`  broadcast ${k}: HTTP ${res.status}`);
  if (res.ms > 1000 && slow++ < 5) console.log(`  broadcast ${k}: the request that triggered it took ${(res.ms / 1000).toFixed(1)} s`);
  // wait until every healthy client has received message k
  const deadline = performance.now() + 30000;
  while (Math.min(...counts) < k && performance.now() < deadline) await sleep(2);
  fanout.push(performance.now() - t);
}
const missing = counts.filter((c) => c < broadcasts).length;
const pct = (a, p) => [...a].sort((x, y) => x - y)[Math.min(a.length - 1, Math.floor(a.length * p))].toFixed(1);
console.log(`\n${broadcasts} broadcasts to ${healthyN} healthy${stalledN ? ` (+${stalledN} clients that stopped reading)` : ""} clients:`);
console.log(`  triggering request latency ms: p50 ${pct(posts, .5)}  p95 ${pct(posts, .95)}  max ${pct(posts, 1)}`);
console.log(`  time until ALL healthy clients had the message ms: p50 ${pct(fanout, .5)}  p95 ${pct(fanout, .95)}  max ${pct(fanout, 1)}`);
console.log(`  healthy clients missing messages: ${missing} of ${healthyN};  server RSS ${serverRssMb()} MB`);
[...healthy, ...stalled].forEach((w) => { try { w.terminate(); } catch {} });
process.exit(0);

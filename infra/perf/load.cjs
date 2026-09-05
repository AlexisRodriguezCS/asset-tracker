/**
 * Closed-loop load test against the gateway.
 *   node load.cjs <concurrency> <seconds> <label>
 * Signs in once, then hammers a read path with N workers and reports
 * throughput plus latency percentiles. Warms up first so JIT state is
 * settled before anything is recorded.
 */
const BASE = process.env.BASE || "http://localhost:8080";
const CONC = Number(process.argv[2] || 20);
const SECS = Number(process.argv[3] || 20);
const LABEL = process.argv[4] || "run";
const WARMUP_MS = 5000;

async function login() {
  const r = await fetch(`${BASE}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: "tech@acme.example", password: "Passw0rd!" }),
  });
  if (!r.ok) throw new Error(`login ${r.status}`);
  return (await r.json()).token;
}

function pct(sorted, p) {
  if (!sorted.length) return 0;
  return sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))];
}

async function main() {
  const token = await login();
  const headers = { Authorization: `Bearer ${token}` };
  const url = `${BASE}/api/assets?clientId=1`;

  let recording = false;
  const lat = [];
  let ok = 0, bad = 0;
  const stop = Date.now() + WARMUP_MS + SECS * 1000;

  setTimeout(() => { recording = true; }, WARMUP_MS);

  async function worker() {
    while (Date.now() < stop) {
      const t0 = process.hrtime.bigint();
      try {
        const res = await fetch(url, { headers });
        await res.arrayBuffer();
        const ms = Number(process.hrtime.bigint() - t0) / 1e6;
        if (recording) { res.ok ? ok++ : bad++; lat.push(ms); }
      } catch {
        if (recording) bad++;
      }
    }
  }

  const started = Date.now() + WARMUP_MS;
  await Promise.all(Array.from({ length: CONC }, worker));
  const elapsed = (Date.now() - started) / 1000;

  lat.sort((a, b) => a - b);
  const rps = ok / elapsed;
  console.log(
    `${LABEL.padEnd(14)} conc=${String(CONC).padStart(3)}  ` +
      `${rps.toFixed(0).padStart(5)} req/s  ` +
      `p50 ${pct(lat, 50).toFixed(1).padStart(6)}ms  ` +
      `p95 ${pct(lat, 95).toFixed(1).padStart(6)}ms  ` +
      `p99 ${pct(lat, 99).toFixed(1).padStart(6)}ms  ` +
      `ok=${ok} err=${bad}`,
  );
}
main().catch((e) => { console.error("load test failed:", e.message); process.exit(1); });

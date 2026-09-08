/**
 * QA matrix over the gateway: who can read what, who can write what, and whether
 * tenant isolation holds. Prints PASS/FAIL per expectation so a regression is
 * obvious rather than buried in a wall of JSON.
 */
const B = process.env.BASE || "http://localhost:8080";
const PW = "Passw0rd!";

// Every run must be able to follow the last one. Tags are unique per run, the
// gear this script needs is created if the pool is short, and anything it checks
// out is returned at the end - otherwise run two dies on a tag collision and an
// empty stockroom, which looks like a product bug and is not one.
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
const checkedOut = [];

// Event gear is booked against a *day*: a client with 2 TVs has none left on a
// date that already has both spoken for. A fixed date therefore passed once and
// then refused every later run with a 409, which reads exactly like a broken
// availability rule instead of a harness reusing someone else's booking. Each
// run gets its own day, far enough out never to collide with demo data.
const EVENT_DATE = new Date(Date.now() + 400 * 86_400_000 + (Date.now() % 900) * 86_400_000)
  .toISOString()
  .slice(0, 10);

const nextDay = (iso) =>
  new Date(new Date(iso).getTime() + 86_400_000).toISOString().slice(0, 10);

const results = [];
function check(area, what, actual, expected) {
  const ok = Array.isArray(expected) ? expected.includes(actual) : actual === expected;
  results.push({ area, what, actual, expected: String(expected), ok });
}

/**
 * The gateway rate-limits POST /api/auth/** to ten a minute per IP, and this
 * script signs in five times. Run straight after the e2e suite - which also
 * signs in - and the budget is already gone, so a plain fetch here dies at 429
 * on an unrelated failure. Wait the window out rather than reporting a false
 * negative for the whole matrix.
 */
async function login(email, attempt = 1) {
  const r = await fetch(`${B}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password: PW }),
  });
  if (r.status === 429 && attempt <= 3) {
    const wait = Number(r.headers.get("Retry-After") ?? 60) * 1000;
    console.log(`  rate-limited signing in as ${email}; waiting ${wait / 1000}s`);
    await new Promise((res) => setTimeout(res, wait + 1000));
    return login(email, attempt + 1);
  }
  if (!r.ok) throw new Error(`login ${email} -> ${r.status}`);
  return (await r.json()).token;
}

const call = async (token, path, init = {}) => {
  const headers = { ...(init.headers || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (init.body) headers["Content-Type"] = "application/json";
  const r = await fetch(`${B}${path}`, { ...init, headers });
  return r;
};

const status = async (...args) => (await call(...args)).status;
const json = async (...args) => {
  const r = await call(...args);
  try { return await r.json(); } catch { return null; }
};

async function main() {
  const tok = {};
  for (const [k, email] of Object.entries({
    admin: "admin@platform.example",
    tech: "tech@acme.example",
    poc: "poc@acme.example",
    hr: "hr@acme.example",
    user: "dana.reyes@acme.example",
  })) {
    tok[k] = await login(email);
  }

  // --- 1. nothing is readable without a token -----------------------------
  for (const p of ["/api/assets?clientId=1", "/api/people?clientId=1", "/api/locations?clientId=1",
                   "/api/assignments?clientId=1", "/api/clients", "/api/assets/audit?clientId=1"]) {
    check("anonymous", `GET ${p}`, await status(null, p), 401);
  }

  // --- 2. staff can read their tenant -------------------------------------
  for (const role of ["admin", "tech", "poc", "hr"]) {
    check("read", `${role} GET /assets`, await status(tok[role], "/api/assets?clientId=1"), 200);
  }

  // --- 3. employee sees only their own gear -------------------------------
  const techAssets = await json(tok.tech, "/api/assets?clientId=1");
  const danaAssets = await json(tok.user, "/api/assets?clientId=1");
  check("scoping", "employee list is narrower than tech's",
    danaAssets.length < techAssets.length, true);
  check("scoping", "every row the employee sees is held by them",
    danaAssets.every((a) => a.holderType === "PERSON" && a.holderId === 1), true);
  const danaPeople = await json(tok.user, "/api/people?clientId=1");
  check("scoping", "employee sees only themselves in people", danaPeople.length, 1);

  // --- 4. audit is staff-only and tenant-scoped ---------------------------
  check("audit", "employee reads no audit rows", (await json(tok.user, "/api/assets/audit?clientId=1")).length, 0);
  check("audit", "tech reads audit rows", (await json(tok.tech, "/api/assets/audit?clientId=1")).length > 0, true);
  check("audit", "HR cannot read another tenant's audit", await status(tok.hr, "/api/assets/audit?clientId=2"), 403);

  // --- 5. tenant isolation on reads ---------------------------------------
  check("tenancy", "HR (client 1) reading client 2 assets", await status(tok.hr, "/api/assets?clientId=2"), 403);
  check("tenancy", "POC (client 1) reading client 3 people", await status(tok.poc, "/api/people?clientId=3"), 403);
  check("tenancy", "tech (clients 1-3) reading client 2", await status(tok.tech, "/api/assets?clientId=2"), 200);

  // --- 6. writes are role-gated -------------------------------------------
  const newAsset = (tag) => ({
    method: "POST",
    body: JSON.stringify({ clientId: 1, type: "Cable", assetTag: `QA-${RUN}-${tag}`, serialNumber: `QA-${RUN}-${tag}` }),
  });
  check("write", "employee create asset", await status(tok.user, "/api/assets", newAsset("U1")), 403);
  check("write", "HR create asset", await status(tok.hr, "/api/assets", newAsset("H1")), 403);
  check("write", "POC create asset", await status(tok.poc, "/api/assets", newAsset("P1")), 403);
  check("write", "tech create asset", await status(tok.tech, "/api/assets", newAsset("T1")), 201);

  const anyAsset = (await json(tok.tech, "/api/assets?clientId=1&status=IN_STOCK"))[0];
  const patch = { method: "PATCH", body: JSON.stringify({ notes: "qa" }) };
  check("write", "employee edit asset", await status(tok.user, `/api/assets/${anyAsset.id}`, patch), 403);
  check("write", "tech edit asset", await status(tok.tech, `/api/assets/${anyAsset.id}`, patch), 200);
  check("write", "employee create person", await status(tok.user, "/api/people",
    { method: "POST", body: JSON.stringify({ clientId: 1, fullName: "QA Ghost", email: "qa@acme.example", department: "QA" }) }), 403);
  check("write", "employee create location", await status(tok.user, "/api/locations",
    { method: "POST", body: JSON.stringify({ clientId: 1, kind: "DESK", label: "QA Desk", qrTag: "QA-D-1" }) }), 403);
  // Seating people. HR does this and cannot buy assets; a POC approves requests and does
  // neither. The desk moves and is put straight back, so the matrix leaves the floor as it
  // found it.
  const seat = (who, deskId) => status(tok[who], "/api/people/1/desk",
    { method: "POST", body: JSON.stringify({ deskId }) });
  const seated = (await json(tok.tech, "/api/people?clientId=1")).find((p) => p.id === 1);
  const desks = await json(tok.tech, "/api/locations?clientId=1&kind=DESK");
  const elsewhere = desks.find((d) => d.id !== seated.deskId).id;
  check("write", "employee cannot seat anybody", await seat("user", elsewhere), 403);
  check("write", "POC cannot seat anybody", await seat("poc", elsewhere), 403);
  check("write", "HR can seat somebody", await seat("hr", elsewhere), 200);
  check("write", "tech can seat somebody", await seat("tech", seated.deskId), 200);

  check("write", "employee create type", await status(tok.user, "/api/assets/types",
    { method: "POST", body: JSON.stringify({ clientId: 1, name: "QAType" }) }), 403);

  // --- 7. check-out / return ----------------------------------------------
  const stock = (await json(tok.tech, "/api/assets?clientId=1&status=IN_STOCK"))[0];
  const co = (who, holderId) => status(tok[who], "/api/assignments",
    { method: "POST", body: JSON.stringify({ clientId: 1, assetId: stock.id, holderType: "PERSON", holderId }) });
  check("custody", "employee cannot check out", await co("user", 2), 403);
  const checkoutStatus = await co("tech", 2);
  if (checkoutStatus === 201) checkedOut.push(stock.id);
  check("custody", "tech checks out", checkoutStatus, 201);
  check("custody", "double check-out is rejected", await co("tech", 3), 409);
  const returned = await status(tok.hr, `/api/assignments/return?assetId=${stock.id}&clientId=1`, { method: "POST" });
  if (returned === 200) checkedOut.length = 0;
  check("custody", "HR can return (collector)", returned, 200);

  // --- 8. event sign-out lifecycle ----------------------------------------
  const req = await json(tok.user, "/api/assignments/event-requests", {
    method: "POST",
    body: JSON.stringify({ clientId: 1, eventName: "QA Fair", eventDate: EVENT_DATE,
      location: "Gym", lines: [{ itemType: "TV", quantity: 2 }] }),
  });
  check("events", "employee submits a request", req && req.status, "SUBMITTED");
  check("events", "employee cannot approve their own",
    await status(tok.user, `/api/assignments/event-requests/${req.id}/approve`, { method: "POST", body: "{}" }), 403);
  check("events", "POC approves",
    await status(tok.poc, `/api/assignments/event-requests/${req.id}/approve`, { method: "POST", body: JSON.stringify({ note: "ok" }) }), 200);
  const full = await json(tok.tech, `/api/assignments/event-requests/${req.id}`);

  // Top the stockroom up rather than depending on what earlier runs left behind.
  let inStock = await json(tok.tech, "/api/assets?clientId=1&type=TV&status=IN_STOCK");
  for (let i = inStock.length; i < 2; i++) {
    await call(tok.tech, "/api/assets", {
      method: "POST",
      body: JSON.stringify({ clientId: 1, type: "TV", assetTag: `QA-${RUN}-TV${i}`, make: "QA" }),
    });
  }
  inStock = await json(tok.tech, "/api/assets?clientId=1&type=TV&status=IN_STOCK");
  const tvs = inStock.slice(0, 2).map((a) => a.id);

  // A body that passes validation, so a refusal can only come from the role gate.
  const validFulfil = { method: "POST", body: JSON.stringify({ lines: [{ lineId: full.lines[0].id, assetIds: tvs.slice(0, 1) }] }) };
  check("events", "POC cannot hand out gear (valid body, so this is the role gate)",
    await status(tok.poc, `/api/assignments/event-requests/${req.id}/fulfil`, validFulfil), 403);
  check("events", "two TVs are in stock to fulfil with", tvs.length, 2);
  const fulfilled = await json(tok.tech, `/api/assignments/event-requests/${req.id}/fulfil`, {
    method: "POST", body: JSON.stringify({ lines: [{ lineId: full.lines[0].id, assetIds: tvs }] }),
  });
  check("events", "tech fulfils", fulfilled && fulfilled.status, "FULFILLED");
  const tv0 = await json(tok.tech, `/api/assets/${tvs[0]}`);
  check("events", "fulfilled TV really moved custody", tv0.status, "ASSIGNED");
  check("events", "fulfilled TV is held by the requester", tv0.holderId, 1);

  // --- 8b. the event equipment pool ----------------------------------------
  // The request above booked both of Acme's TVs for EVENT_DATE, so the pool for
  // that day is now empty and a second ask has to be refused.
  const free = await json(tok.user, `/api/assignments/event-equipment?clientId=1&date=${EVENT_DATE}`);
  const tvPool = free.find((i) => i.itemType === "TV");
  check("pool", "employee can read the sign-out menu", Array.isArray(free), true);
  check("pool", "the day's TVs are all spoken for", tvPool && tvPool.available, 0);
  check("pool", "booking the same TVs again is refused",
    await status(tok.user, "/api/assignments/event-requests", {
      method: "POST",
      body: JSON.stringify({ clientId: 1, eventName: "QA Clash", eventDate: EVENT_DATE,
        lines: [{ itemType: "TV", quantity: 1 }] }),
    }), 409);
  check("pool", "another day is unaffected",
    await status(tok.user, "/api/assignments/event-requests", {
      method: "POST",
      body: JSON.stringify({ clientId: 1, eventName: "QA Next Day", eventDate: nextDay(EVENT_DATE),
        lines: [{ itemType: "TV", quantity: 1 }] }),
    }), 201);
  check("pool", "gear the client does not lend is refused",
    await status(tok.user, "/api/assignments/event-requests", {
      method: "POST",
      body: JSON.stringify({ clientId: 1, eventName: "QA Podium", eventDate: nextDay(EVENT_DATE),
        lines: [{ itemType: "Podium", quantity: 1 }] }),
    }), 409);
  const setPool = (who, qty) => status(tok[who], "/api/assignments/event-equipment", {
    method: "POST", body: JSON.stringify({ clientId: 1, itemType: "TV", quantity: qty }),
  });
  check("pool", "employee cannot change the pool", await setPool("user", 99), 403);
  check("pool", "HR cannot change the pool", await setPool("hr", 99), 403);
  check("pool", "POC cannot change the pool", await setPool("poc", 99), 403);
  check("pool", "tech can change the pool", await setPool("tech", 2), 200);
  check("pool", "another tenant's pool is refused",
    await status(tok.hr, "/api/assignments/event-equipment?clientId=2"), 403);

  // --- 9. error paths ------------------------------------------------------
  check("errors", "unknown asset id", await status(tok.tech, "/api/assets/99999"), 404);
  check("errors", "employee opening someone else's asset", await status(tok.user, `/api/assets/${stock.id}`), 404);
  check("errors", "invalid body rejected", await status(tok.tech, "/api/assets", { method: "POST", body: JSON.stringify({ clientId: 1 }) }), 400);

  // --- put the gear back ----------------------------------------------------
  for (const id of [...tvs, ...checkedOut]) {
    await call(tok.tech, `/api/assignments/return?assetId=${id}&clientId=1`, { method: "POST" });
  }

  // --- report ---------------------------------------------------------------
  const pad = (s, n) => String(s).padEnd(n);
  let area = "";
  for (const r of results) {
    if (r.area !== area) { area = r.area; console.log(`\n${area.toUpperCase()}`); }
    console.log(`  ${r.ok ? "PASS" : "FAIL"}  ${pad(r.what, 48)} got=${pad(r.actual, 10)} want=${r.expected}`);
  }
  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} passed`);
  if (failed.length) {
    console.log("\nFAILURES:");
    failed.forEach((r) => console.log(`  [${r.area}] ${r.what}: got ${r.actual}, wanted ${r.expected}`));
    process.exitCode = 1;
  }
}

main().catch((e) => { console.error("QA run failed:", e.message); process.exitCode = 2; });

/**
 * QA matrix over the gateway: who can read what, who can write what, and whether
 * tenant isolation holds. Prints PASS/FAIL per expectation so a regression is
 * obvious rather than buried in a wall of JSON.
 */
const B = process.env.BASE || "http://localhost:8080";
const PW = "Passw0rd!";

const results = [];
function check(area, what, actual, expected) {
  const ok = Array.isArray(expected) ? expected.includes(actual) : actual === expected;
  results.push({ area, what, actual, expected: String(expected), ok });
}

async function login(email) {
  const r = await fetch(`${B}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password: PW }),
  });
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
    method: "POST", body: JSON.stringify({ clientId: 1, type: "Cable", assetTag: tag, serialNumber: "QA-" + tag }),
  });
  check("write", "employee create asset", await status(tok.user, "/api/assets", newAsset("QA-U-1")), 403);
  check("write", "HR create asset", await status(tok.hr, "/api/assets", newAsset("QA-H-1")), 403);
  check("write", "POC create asset", await status(tok.poc, "/api/assets", newAsset("QA-P-1")), 403);
  check("write", "tech create asset", await status(tok.tech, "/api/assets", newAsset("QA-T-1")), 201);

  const anyAsset = (await json(tok.tech, "/api/assets?clientId=1&status=IN_STOCK"))[0];
  const patch = { method: "PATCH", body: JSON.stringify({ notes: "qa" }) };
  check("write", "employee edit asset", await status(tok.user, `/api/assets/${anyAsset.id}`, patch), 403);
  check("write", "tech edit asset", await status(tok.tech, `/api/assets/${anyAsset.id}`, patch), 200);
  check("write", "employee create person", await status(tok.user, "/api/people",
    { method: "POST", body: JSON.stringify({ clientId: 1, fullName: "QA Ghost", email: "qa@acme.example", department: "QA" }) }), 403);
  check("write", "employee create location", await status(tok.user, "/api/locations",
    { method: "POST", body: JSON.stringify({ clientId: 1, kind: "DESK", label: "QA Desk", qrTag: "QA-D-1" }) }), 403);
  check("write", "employee create type", await status(tok.user, "/api/assets/types",
    { method: "POST", body: JSON.stringify({ clientId: 1, name: "QAType" }) }), 403);

  // --- 7. check-out / return ----------------------------------------------
  const stock = (await json(tok.tech, "/api/assets?clientId=1&status=IN_STOCK"))[0];
  const co = (who, holderId) => status(tok[who], "/api/assignments",
    { method: "POST", body: JSON.stringify({ clientId: 1, assetId: stock.id, holderType: "PERSON", holderId }) });
  check("custody", "employee cannot check out", await co("user", 2), 403);
  check("custody", "tech checks out", await co("tech", 2), 201);
  check("custody", "double check-out is rejected", await co("tech", 3), 409);
  check("custody", "HR can return (collector)", await status(tok.hr, `/api/assignments/return?assetId=${stock.id}&clientId=1`, { method: "POST" }), 200);

  // --- 8. event sign-out lifecycle ----------------------------------------
  const req = await json(tok.user, "/api/assignments/event-requests", {
    method: "POST",
    body: JSON.stringify({ clientId: 1, eventName: "QA Fair", eventDate: "2026-11-01",
      location: "Gym", lines: [{ itemType: "TV", quantity: 2 }] }),
  });
  check("events", "employee submits a request", req && req.status, "SUBMITTED");
  check("events", "employee cannot approve their own",
    await status(tok.user, `/api/assignments/event-requests/${req.id}/approve`, { method: "POST", body: "{}" }), 403);
  check("events", "POC approves",
    await status(tok.poc, `/api/assignments/event-requests/${req.id}/approve`, { method: "POST", body: JSON.stringify({ note: "ok" }) }), 200);
  check("events", "POC cannot hand out gear",
    await status(tok.poc, `/api/assignments/event-requests/${req.id}/fulfil`, { method: "POST", body: JSON.stringify({ lines: [] }) }), [403, 400]);

  const full = await json(tok.tech, `/api/assignments/event-requests/${req.id}`);
  const tvs = (await json(tok.tech, "/api/assets?clientId=1&type=TV&status=IN_STOCK")).slice(0, 2).map((a) => a.id);
  check("events", "two TVs are in stock to fulfil with", tvs.length, 2);
  const fulfilled = await json(tok.tech, `/api/assignments/event-requests/${req.id}/fulfil`, {
    method: "POST", body: JSON.stringify({ lines: [{ lineId: full.lines[0].id, assetIds: tvs }] }),
  });
  check("events", "tech fulfils", fulfilled && fulfilled.status, "FULFILLED");
  const tv0 = await json(tok.tech, `/api/assets/${tvs[0]}`);
  check("events", "fulfilled TV really moved custody", tv0.status, "ASSIGNED");
  check("events", "fulfilled TV is held by the requester", tv0.holderId, 1);

  // --- 9. error paths ------------------------------------------------------
  check("errors", "unknown asset id", await status(tok.tech, "/api/assets/99999"), 404);
  check("errors", "employee opening someone else's asset", await status(tok.user, `/api/assets/${stock.id}`), 404);
  check("errors", "invalid body rejected", await status(tok.tech, "/api/assets", { method: "POST", body: JSON.stringify({ clientId: 1 }) }), 400);

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

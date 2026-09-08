#!/usr/bin/env node
/**
 * The core journey against a stack running the `prod` profile on PostgreSQL.
 *
 * The other suites all run against the demo stack, which is H2 with seeders. That leaves the
 * configuration a real deployment actually uses - Flyway migrations, ddl-auto=validate, Postgres
 * types, no seed data - proven only per service by Testcontainers, and never end to end. The first
 * time this ran it found that nobody could sign in at all: every seeder is @Profile("!prod"), so
 * the user table was empty and there was no way to make an account.
 *
 * Start the stack with:
 *   SECURITY_BOOTSTRAP_EMAIL=ops@acme.example SECURITY_BOOTSTRAP_PASSWORD='Boot5trap!' \
 *   docker compose -f docker-compose.yml -f docker-compose.postgres.yml up -d
 *
 * It creates its own tenant and records, so it needs no seed data - which is the point.
 */
const BASE = process.env.E2E_BASE_URL ?? "http://localhost:8080";
const EMAIL = process.env.SECURITY_BOOTSTRAP_EMAIL ?? "ops@acme.example";
const PASSWORD = process.env.SECURITY_BOOTSTRAP_PASSWORD ?? "Boot5trap!";

let token = "";
let passed = 0;
const failures = [];

function check(name, got, want) {
  const ok = got === want;
  console.log(`  ${ok ? "PASS" : "FAIL"}  ${name.padEnd(44)} got=${got} want=${want}`);
  ok ? passed++ : failures.push(name);
  return ok;
}

async function call(method, path, body) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    /* a non-JSON body is itself the answer */
  }
  return { status: res.status, body: json };
}

(async () => {
  const stamp = Date.now().toString(36).slice(-5);

  console.log("SIGN IN");
  const login = await call("POST", "/api/auth/login", { email: EMAIL, password: PASSWORD });
  if (!check("the bootstrap admin can sign in", login.status, 200)) {
    console.error("\nNo way in - is SECURITY_BOOTSTRAP_* set on auth-service?");
    process.exit(1);
  }
  token = login.body.token;

  console.log("\nA TENANT AND ITS RECORDS");
  // Reused if one is already there, because the bootstrap admin is scoped to the tenants named
  // in SECURITY_BOOTSTRAP_CLIENT_IDS and nothing can widen that afterwards - there is no user
  // administration yet. A second run that made a second tenant locked itself out of it: every
  // call after the create came back 403, which is the system being right and the script being
  // wrong.
  const existing = await call("GET", "/api/clients");
  let clientId = existing.body?.[0]?.id;
  if (clientId === undefined) {
    const created = await call("POST", "/api/clients", {
      name: `Smoke ${stamp}`,
      slug: `smoke-${stamp}`,
    });
    check("a client can be created", created.status, 201);
    clientId = created.body?.id;
  } else {
    check("a tenant exists to work in", typeof clientId, "number");
  }

  const person = await call("POST", "/api/people", {
    clientId,
    fullName: `Smoke Person ${stamp}`,
    email: `smoke.${stamp}@example.invalid`,
    department: "QA",
  });
  check("a person can be created", person.status, 201);

  const desk = await call("POST", "/api/locations", {
    clientId,
    kind: "DESK",
    label: `Smoke Desk ${stamp}`,
    qrTag: `SMOKE-${stamp}`,
  });
  check("a desk can be created", desk.status, 201);

  const asset = await call("POST", "/api/assets", {
    clientId,
    assetTag: `SMOKE-A-${stamp}`,
    type: "Laptop",
    make: "Smoke",
    model: "Test",
  });
  check("an asset can be created", asset.status, 201);

  console.log("\nCUSTODY");
  const out = await call("POST", "/api/assignments", {
    clientId,
    assetId: asset.body?.id,
    holderType: "PERSON",
    holderId: person.body?.id,
  });
  check("it can be checked out", out.status, 201);

  const held = await call(
    "GET",
    `/api/assets?clientId=${clientId}&holderType=PERSON&holderId=${person.body?.id}`,
  );
  check("it shows on the person", held.body?.length ?? 0, 1);

  // an idempotent retry of the same intent must replay, not check out twice
  const back = await call("POST", `/api/assignments/return?assetId=${asset.body?.id}`);
  check("it can be returned", back.status, 200);

  console.log("\nCORRECTIONS");
  const renamed = await call("PATCH", `/api/people/${person.body?.id}`, {
    fullName: `Smoke Person ${stamp} (renamed)`,
  });
  check("a person can be corrected", renamed.status, 200);

  // A desk with gear on it must refuse to disappear, and that answer comes from asset-service -
  // so this also proves one service asking another while carrying the caller's token.
  await call("POST", "/api/assignments", {
    clientId,
    assetId: asset.body?.id,
    holderType: "LOCATION",
    holderId: desk.body?.id,
  });
  const occupied = await call("DELETE", `/api/locations/${desk.body?.id}`);
  check("a desk with gear on it is not deleted", occupied.status, 409);
  await call("POST", `/api/assignments/return?assetId=${asset.body?.id}`);

  const deleted = await call("DELETE", `/api/locations/${desk.body?.id}`);
  check("an empty desk can be deleted", deleted.status, 204);

  console.log(`\n${passed}/${passed + failures.length} passed`);
  if (failures.length) {
    console.error(`failed: ${failures.join(", ")}`);
    process.exit(1);
  }
})();

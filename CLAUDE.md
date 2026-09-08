# asset-tracker — working notes

Multi-tenant IT asset tracking: 10 Spring Boot services (Java 21) behind an API
gateway, plus a Next.js console. See [README.md](README.md) for what it does and
[infra/RUNBOOK.md](infra/RUNBOOK.md) for how to operate it.

This file is for whoever works on it next. It records the things that are not
visible from the code, and the traps that have already cost someone a session.

## Workflow

- **Commits go straight to `main`.** No PR flow for your own work; Dependabot
  opens PRs and those get merged normally.
- **Keep CI green.** Run the same checks locally *before* pushing, not a subset
  of them — a formatting-only failure has burned a CI run more than once.
- **Verify against the running stack, not just tests.** This project has caught
  several bugs that every test suite reported as fine. If a change is visible in
  the console, click it.

## Before you push

```bash
./gradlew build                 # compile, tests, Spotless, Checkstyle, coverage floors
cd web && npm run lint && npm run format:check && npm test && npx tsc --noEmit && npm run build
```

- **Run `./gradlew spotlessApply` before committing Java.** Formatting is a hard
  gate, and `build` fails on it rather than fixing it.
- **Prettier checks the whole `web/` directory**, not just `src/`. Running it on
  a subdirectory and assuming you are clean is how a config file at the root
  slipped through and broke CI.

## Traps

These are all real, and each one has already wasted time.

- **`./gradlew build` runs the e2e suite against a live local stack** if one
  happens to be up. It self-skips only when nothing is reachable. That suite
  runs an offboarding sweep on the seeded employee — the same account the
  console's employee demo persona uses — so it used to silently empty her
  account. It now hands the gear back; do not remove that cleanup.
- **The gateway rate-limits `POST /api/auth/**` to 10/minute per IP.** Anything
  that signs in repeatedly will DOS itself and fail for a reason unrelated to
  what it was testing. Sign in once and reuse the session.
- **Checkstyle caps files at 400 lines, 7 parameters, complexity 10.** These
  bite regularly. The fix is usually to extract a class, not to raise the limit.
- **The stack defaults to in-memory H2.** Restarting a service wipes its data
  and re-seeds. Use the Postgres overlay when you need state to survive:
  `docker compose -f docker-compose.yml -f docker-compose.postgres.yml up -d`.
- **Never run `next build` in `web/` while `next dev` is running.** The
  production build overwrites the dev server's `.next` and every page loses its
  CSS. It looks exactly like the design broke. Restart the dev server to fix.
- **A config imported via `spring.config.import` has LOWER precedence than the
  file importing it.** A local `application.yml` silently overrides the config
  server. Also: `optional:configserver:` is skipped *without warning* if
  `spring-cloud-starter-config` is missing, which made the config server
  decorative for 8 of 9 services without anyone noticing.

## Things that look wrong and are not

- **`AssetImportService` uses `'\0'` as a key separator.** It stops `"AB"+"C"`
  colliding with `"A"+"BC"` in the upsert key. It has been deleted once as a
  stray character; it is load-bearing.
- **`config-server` and `discovery-server` keep local config** rather than
  reading from the config server. The config server cannot bootstrap from
  itself, and removing config-server's local actuator exposure made
  `/actuator/prometheus` return config JSON and dropped it from Prometheus.
- **Reads that a caller may not see return 404, not 403.** Whether an id exists
  is itself information they do not have.
- **Coverage floors in `gradle/quality.gradle` are a ratchet.** Raise one when
  its service climbs past it; **never lower one to make a red build green.**

## Design

[web/DESIGN.md](web/DESIGN.md) is the console's design system: tokens, the
component set, motion, and the rules. Two that get broken most:

- **Use the token, never the raw Tailwind palette.** There are no raw palette
  colours left in `web/src`; `amber` and `emerald` used to sit beside the
  tokens that meant the same thing and disagree with them about the shade.
- **Colour marks the exception.** If a colour lands on most rows it is
  decoration, and it costs you the ability to highlight anything.

## Testing layers

| layer | where | what only it can catch |
|---|---|---|
| unit / slice / repository | `services/*/src/test` | domain logic, queries |
| API end-to-end | `e2e/` (REST-Assured) | the platform through the gateway |
| console unit | `web/src/**/*.test.ts` (Vitest) | roles, session, the BFF allow-list |
| authorization matrix | `infra/qa/api-matrix.cjs` | that the refusals refuse |
| browser | `web/e2e/` (Playwright) | anything a person actually touches |

Every suite must be **repeatable**: each of the last three broke this rule once
and had to be fixed. If a test consumes shared state — books event gear, checks
an asset out — it has to put it back, and the cleanup has to work on the failure
path too, not only the happy one.

## Known gaps, ranked

The five that were here are done - the signing key, the offboarding misreport,
header trust, idempotency on transfer and offboarding, and the per-instance rate
limiter. What replaced them, ordered by how much they matter:

1. **The signing key is handed to the service.** It is loaded from a secret now
   rather than invented per process, so restarts and replicas are fine, but the
   private key still lives in the pod's memory. Signing via KMS/HSM would keep it
   in the store; rotation with an overlap window (publish two JWKS entries, sign
   with the newer) is the piece that has to come with it.
2. **The demo service account is a password in config.** Start-up seeding signs in
   as an ordinary account, which is honest but is still a credential in a secret.
   A client-credentials grant, or a workload identity the token is minted from,
   removes it.
3. **The gateway accepts an Entra token directly** when ENTRA_ISSUER_URI is set,
   and that token carries no role or clientIds. Services reject it (they trust
   only auth-service's issuer), so nothing is exposed - but the failure mode is a
   401 deep in the stack rather than a clear answer at the edge. Either exchange
   it at the gateway or stop advertising it as a way in.
4. **Offboarding's  bucket has no repair path.** The report is honest
   now - asset in stock, assignment not closed - but somebody still has to fix the
   row by hand. A reconciliation job that closes assignments whose asset is back in
   stock would finish the job.
5. **The rate limiter fails open.** Deliberate, and the right trade against a total
   authentication outage, but a Redis outage silently removes the brake. An alert
   on the miss it already logs would make that visible.

Deliberately *not* worth doing: distributed tracing, a service mesh, Kafka. They
add configuration without demonstrating anything the project has not already
shown.

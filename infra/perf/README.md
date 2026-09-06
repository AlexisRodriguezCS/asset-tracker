# Load test

A closed-loop load generator against the gateway. No install — it uses the Node
already needed for `web/`.

```bash
node infra/perf/load.cjs <concurrency> <seconds> <label>
node infra/perf/load.cjs 50 15 baseline
```

It signs in once, warms up for 5s so JIT state has settled, then drives
`GET /api/assets?clientId=1` with N workers and reports throughput and latency
percentiles.

## Results

Measured on the compose stack (each service capped at 2 CPUs / 576MB), on a
16-core host with WSL2 limited to 8 processors:

| concurrency | req/s | p50 | p95 | p99 |
|---|---|---|---|---|
| 10 | 1488 | 3.7ms | 37.8ms | 46.8ms |
| 50 | 2962 | 14.0ms | 36.2ms | 41.0ms |
| 100 | 3165 | 27.6ms | 48.5ms | 51.8ms |

Zero errors throughout. Throughput plateaus around 3000 req/s and latency then
grows linearly with concurrency, which is what saturation looks like — the limit
is CPU on the gateway and asset-service, not lock contention or the database.

## What this measurement changed

`-XX:TieredStopAtLevel=1` was in the JVM options as a footprint saving. It costs
~35MB a service, which looked worth having until it was measured: it **halves**
throughput once there is any concurrency (1481 vs 3060 req/s at 50 concurrent,
p95 60ms vs 35ms). It was removed. Paying for twice the hardware to save a third
of a gigabyte is the wrong way round, and there was no way to know without the
numbers.

## Footprint

The full stack — 10 services, RabbitMQ, Prometheus, Grafana — sits at ~2.85GB
resident. Roughly 250MB per Spring Boot service, of which ~100MB is metaspace
(loaded classes) and ~60MB live heap. That is close to the floor for Spring Boot
on the JVM; getting materially below it means GraalVM native images, which is a
much larger change than a flag.

Practical sizing: a 4GB host is the realistic minimum, 8GB comfortable. Dropping
Prometheus and Grafana (dev conveniences) saves ~85MB.

## Frontend

Measured the same way — production build, warm medians, real session.

Bundle is not the problem: 103 kB of shared First Load JS is essentially the
React 19 + Next 15 baseline, and no page adds more than 14 kB on top. Nine
runtime dependencies, no chart or date library. Server-rendered TTFB is 4–7 ms
because every page is a server component batching its gateway calls with
`Promise.all` — there is no client fetch waterfall to remove.

The problem was the catalog list, which rendered a row per asset with no bound:

| assets | HTML | page total | API payload |
|---|---|---|---|
| 69 | 212 kB | 29 ms | 416 kB |
| 1,069 (before) | **2,344 kB** | 176 ms | 416 kB |
| 1,069 (after) | **166 kB** | 33 ms | 21 kB |

~2.2 kB of HTML per asset, growing linearly — a 10,000-asset tenant would have
shipped roughly 22 MB per page load.

`GET /api/assets/paged` is a separate endpoint rather than a changed shape on
`GET /api/assets`, because the dashboard, reports and type counts legitimately
want every row to aggregate, and a response that is sometimes an array and
sometimes an envelope is worse than two honest endpoints. Size is clamped
server-side (`spring.data.web.pageable.max-page-size`) so the paged route cannot
be used to pull a whole tenant.

Free-text search still scans the full set, because it matches on the holder's
name and asset-service does not know people. That path is bounded by slicing
what gets *rendered*, so the HTML stays small either way — a search for one tag
across 1,069 assets returns 42 kB. Pushing search into the database (and
denormalising the holder name to do it) is the next step if tenants get large.

### Once unbounded

The stat strip and `/dashboard`, `/reports`, `/types` each fetched every asset
to aggregate — 416 kB server-side per render. Invisible to the user
(server-to-server, ~17 ms) but the cost grew with the tenant while the rendered
page stayed the same size. All four now read count/rollup endpoints instead, so
the aggregation happens in the database. What that took, in order:

### Aggregation moved into the database

The follow-up above is done for the catalog page. `GET /api/assets/stats` returns
the counts the summary strips need — totals by status, type and condition, plus
the two warranty buckets — instead of the console fetching every asset and
counting in the render:

| | payload |
|---|---|
| `GET /api/assets` (what the strip used to do) | 416,062 bytes |
| `GET /api/assets/stats` | **331 bytes** |

"Expiring soon" is the difference between two windowed counts rather than a
third query. Scoping is applied to the summary as well as the list — otherwise
an employee could read tenant-wide totals off a page that shows them none of the
rows — and there is a test for exactly that.

`/dashboard` and `/types` followed. Neither wanted plain counts:

- the dashboard needs four counts **and** a short preview of each, so
  `GET /api/assets/attention` answers that question in one call (5.7kB). Composing
  it from the generic list endpoint would have meant five round trips and a
  status filter that cannot express "IN_REPAIR or BROKEN".
- `/types` needs a count **and** up to 8 example tags per type for its delete
  confirmation, so `GET /api/assets/types/usage` returns both. Feeding it plain
  counts would have been wrong in the other direction - a type with 1,008 assets
  would have reported 8.

Desk occupancy still reads rows, but only those held by a location, which is
bounded by desk count rather than catalog size. Offboarding asks per departing
person - normally a handful - instead of building a map over every assignment.

Verified in the browser at 1,069 assets: 1069 / 31 in use / 1032 available /
7 of 28 desks, and attention reading 2 / 12 / 5 / 1, all matching the API.

`/reports` was the last one, and the most tangled: rollups by type, status,
condition and department, fleet value, break-and-loss, most-replaced tags. It
pulled the whole catalog **and** the whole audit trail — the trail being the one
nobody had noticed, because it is invisible until a tenant has been running for
a while.

`GET /api/assets/reports` answers all of it. Measured against the seeded demo
tenant (76 assets, 41 audit rows) rather than the 1,069-asset load tenant the
figures above use, because that is the state the stack was in:

| | payload |
|---|---|
| `GET /api/assets` + `GET /api/assets/audit` (what the page used to do) | 32,854 + 9,382 bytes |
| `GET /api/assets/reports` | **920 bytes** |

The point is not the 46× at this size — it is which way each number grows. The
two it replaces are linear in assets and in audit rows, both unbounded. The
report is bounded by the number of *buckets*: types, statuses, conditions,
actions, and one entry per person holding something. A tenant ten times the size
sends roughly the same response.

Two things made it more than another group-by:

- **Departments cannot be rolled up in asset-service.** An asset knows the id of
  the person holding it; the department lives in people-service. So the endpoint
  returns counts per holder id — bounded by headcount, not by catalog size — and
  the page folds those into departments against the people list it already
  loads. Pushing the whole rollup down would have meant either duplicating
  department into the asset row or a cross-service join; per-holder counts are
  the seam that keeps both services owning what they own.
- **Break-and-loss spans the two halves.** It counts audit rows but groups them
  by the *asset's* current type and holder, so it is an ad-hoc join from an
  entity id rather than a foreign key — the trail outlives what it describes, and
  an event whose asset is gone drops out. That is what the page did in the
  browser with a lookup map; `AssetReportQueriesTest` pins the behaviour.

Every rollup was diffed against the arithmetic the page used to do in the
browser — type, status, department, fleet value, replacements, incidents by type
and department, lifecycle — and all ten match exactly.

One number moved, deliberately. The warranty split is now in-service only,
matching the definition `/api/assets/stats` and the dashboard already used;
the page had been counting end-of-life units towards warranty coverage. On the
seeded tenant that is four assets — one `RETIRED`, one `RECYCLED`, one
`LOST`, one `PENDING_RECYCLE` — moving the split from 14 / 7 / 55 to
14 / 6 / 52. Reporting warranty cover on a recycled laptop was the bug; the two
pages disagreeing about it was how it stayed invisible.

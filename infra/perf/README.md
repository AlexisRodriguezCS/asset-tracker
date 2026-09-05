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

### Still unbounded

The stat strip and `/dashboard`, `/reports`, `/types` each still fetch every
asset to aggregate — 416 kB server-side per render. Invisible to the user
(server-to-server, ~17 ms) but it is the next thing to fix, with count/rollup
endpoints so the aggregation happens in the database.

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

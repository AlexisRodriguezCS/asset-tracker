# 0005 — Public reads, authenticated writes

**Status:** accepted

## Context

Asset inventory is internal data, but a portfolio reviewer should be able to look
at the console without creating an account, and the console renders fine for a
signed-out visitor.

## Decision

The gateway permits `GET` on `/api/assets`, `/api/people`, `/api/locations`,
`/api/assignments`, `/api/clients`, `/api/notifications` with no token. Every
write — check-out, return, transfer, offboard, create, edit, status change —
requires a valid bearer token. The Next.js console reads directly (server
components) and routes writes through its BFF, which attaches the cookie token
and only allows a fixed set of paths.

## Consequences

- The demo is browsable immediately; the "sign in to act" wall is only on writes.
- A real deployment would flip the reads to authenticated and rely on the
  forwarded `X-Client-Ids` for per-tenant visibility — a config change, not a
  redesign.

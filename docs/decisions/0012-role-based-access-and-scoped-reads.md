# 0012 — Role-based access, and reads scoped to the caller

**Status:** accepted · supersedes [0005](0005-public-reads-authenticated-writes.md)

## Context

The console started as a tool for IT staff, so [0005](0005-public-reads-authenticated-writes.md)
made reads public and required a token only to write. That was a reasonable trade
while every user was a technician looking at the same fleet.

Then ordinary employees became users: they sign in to see the gear issued to them
and to request kit for an event. An employee must not see a colleague's laptop,
their department's spend, or which technician touched what. That is impossible to
enforce while the same data is readable with no token at all, so 0005 had to go.

A second role arrived with it. Customers wanted their own point of contact to
approve event requests without being able to edit the asset catalog — "approve"
and "operate" are different jobs, and one person having both was not what anyone
asked for.

## Decision

**Five roles**, carried in the JWT and enforced per service:

| role | may |
|---|---|
| `ADMIN` | everything, every tenant |
| `TECH` | create/edit/retire assets, check out, transfer, import, manage people and locations |
| `POC` | see their own organisation; approve or deny event requests; **not** edit assets |
| `HR` | see their own organisation; collect gear back (offboarding sweeps) |
| `USER` | see only what is assigned to them; raise event requests |

**Nothing is readable without a token.** The gateway permits `/api/auth/**` and
actuator; everything else requires one.

**The token carries `personId`.** Scoping "what is assigned to me" needs to know
which employee is calling. Putting it in the token means no service has to call
people-service to find out — the same reason `role` and `clientIds` are already
there. The gateway forwards it as `X-Person-Id`.

**A `USER`'s query is rewritten, not validated.** Asset and people searches are
forced onto their own person record whatever filter the request asked for, so a
crafted query cannot widen them. A `USER` with no linked person sees nothing
rather than everything — the failure mode that matters.

**Records the caller may not see return 404, not 403.** Whether an id exists is
itself information they do not have.

**Three levels of write gate**, applied in the service layer rather than the
controller: *operator* (ADMIN, TECH), *collector* (+ HR, for returns and
offboarding), *staff* (+ POC). A null role means the call never came through the
gateway — one service calling another — and is left alone.

## Consequences

- The console is no longer browsable without an account. Demo access is five
  seeded logins and a persona switcher that performs a real sign-in; there is
  deliberately no impersonation path in the backend.
- Aggregates need scoping too, and that is easy to miss. Counts are computed in
  the database for staff, so a `USER` cannot read tenant-wide totals off a page
  that shows them none of the rows — it is a separate code path with its own test.
- The audit trail is staff-only, and tenant-checked. It had no scoping at all: any
  caller could read any client's history by passing its id.
- Pages whose whole purpose is an operator action redirect anyone else. A hidden
  nav link is not a control; the page has to refuse.

## What this cost to learn

Reads were scoped carefully and writes were not, for a while. Every mutating
endpoint checked the tenant and nothing else, so an employee could edit an asset,
retire it, and create new ones — the UI simply did not draw the buttons. Hiding a
control is not authorization, and the gap was invisible until someone called the
API directly.

`infra/qa/api-matrix.cjs` exists because of that: 43 expectations over a running
gateway, asserting that the refusals refuse. One of its own checks was worthless
until it was fixed — it sent a body that failed validation before the role check
ran, so it passed for the wrong reason.

# QA matrix

An authorization and tenancy sweep over the running gateway. Complements `e2e/`:
that one proves the happy path works, this one proves the things that must *not*
work don't.

```bash
docker compose -f infra/compose/docker-compose.yml up -d
node infra/qa/api-matrix.cjs          # BASE=... to point elsewhere
```

It signs in as all five seeded roles and asserts, per expectation, PASS or FAIL:

| area | what it pins down |
|---|---|
| anonymous | every read is 401 without a token |
| read | each staff role can read its own tenant |
| scoping | an employee's list is strictly narrower, and every row is theirs |
| audit | employees read nothing; a tenant cannot read another's trail |
| tenancy | cross-tenant reads are 403, granted tenants are 200 |
| write | create/edit/delete is 403 for USER, HR and POC; 200/201 for TECH |
| custody | check-out is operator-only, double check-out is 409, HR may return |
| events | submit → approve → fulfil, with each role refused at the wrong step |
| errors | unknown ids are 404, an employee gets 404 (not 403) for gear that is not theirs |

## Writing checks that actually check something

One expectation here originally passed for the wrong reason. "POC cannot hand out
gear" sent an empty `lines: []`, which fails bean validation *before* the role
check runs — so it returned 400 and the assertion accepted it, proving nothing
about the role gate. With a valid body it returns
`403 ROLE_FORBIDDEN role POC may not hand out assets`, which is the thing worth
asserting. The check now sends a valid body, so a refusal can only come from the role gate.
If a negative test can pass because the request was malformed, it is not testing
authorization.

## Data, and why the run is repeatable

The first version was not. It created `QA-T-1` and checked out every TV, so the
second run failed with a 409 on the tag and an empty stockroom - six red lines
that looked like product bugs and were entirely self-inflicted.

Now each run tags what it creates with a unique id, tops the stockroom up if the
gear it needs is not there, and returns everything it signed out. Three runs
back to back give 43/43 each time.

It still leaves the handful of assets it created, by design - "tech can create an
asset" has to actually create one. On the dev profile that is in-memory H2, so
`docker compose ... restart asset-service assignment-service` returns the seed to
a clean state. Do not point it at anything you care about.

## Running it after the e2e suite

The gateway allows ten `POST /api/auth/**` a minute per IP. This script signs in
five times and `e2e/` signs in several more, so running them back to back used to
kill the matrix at 429 on its first login — a false negative for the whole run.
The login helper now waits out `Retry-After` and retries, so the order does not
matter; it just pauses.

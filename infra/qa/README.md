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
asserting. If a negative test can pass because the request was malformed, it is
not testing authorization.

## Data

The run creates assets and event requests and moves custody. On the dev profile
everything is in-memory H2, so
`docker compose ... restart asset-service assignment-service` returns the seed to
a clean state. Do not point it at anything you care about.

# 0002 — HS256 JWT with `role` + `clientIds` claims for v1

**Status:** superseded by [0008](0008-rs256-jwt-with-jwks.md) — the `role` +
`clientIds` claim design stands; the HS256 shared secret was replaced with RS256
+ JWKS

## Context

The console and the services need to know *who* is acting and *which tenants*
they may touch. Options ran from "look it up in a users service on every request"
to full RS256 + a JWKS endpoint.

## Decision

`auth-service` issues an **HS256** token (shared secret with the gateway) whose
claims are `sub` (email), `role` (`TECH` / `HR` / `ADMIN`) and `clientIds` (the
tenant ids the user may act on). The gateway validates it as an OAuth2 resource
server and, after validation, forwards `X-User-Id` / `X-User-Role` /
`X-Client-Ids` to the downstream service, overriding anything the client sent.

## Consequences

- No extra network hop to authorize a request — the facts travel in the token.
- Services can scope and audit from headers without parsing the JWT.
- HS256 means every validator holds the signing secret. Fine for one deployment;
  RS256 + JWKS (auth-service signs, everyone verifies against the public set) is
  the documented next step, along with verifying the principal inside the mesh
  instead of trusting the gateway's headers.

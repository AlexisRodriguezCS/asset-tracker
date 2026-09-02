# 0008 — RS256 JWT validated against a JWKS endpoint

**Status:** accepted · replaces the HS256 half of [0002](0002-hs256-jwt-with-tenancy-claims.md)

## Context

[0002](0002-hs256-jwt-with-tenancy-claims.md) issued **HS256** tokens with a
secret shared between `auth-service` and the gateway. Every validator holding the
signing key is the weak point — a leak anywhere forges tokens everywhere.

## Decision

- `auth-service` generates an **RSA-2048** key pair at startup, signs tokens with
  **RS256**, and publishes the public half as a one-entry JWK Set at
  `GET /.well-known/jwks.json` (also `/oauth2/jwks`). The token header carries the
  `kid`.
- The gateway's resource-server decoder points at that JWK Set URI
  (`JWT_JWKS_URI`), fetches the key, caches it, and validates signature + issuer +
  expiry. No secret is configured anywhere; `JWT_SECRET` is gone.
- The claims are unchanged: `sub`, `role`, `clientIds`.

## Consequences

- Only `auth-service` can sign. Anyone can verify. Adding a service needs no key
  distribution.
- The key is ephemeral — tokens do not survive an `auth-service` restart. Fine
  for this deployment; a persisted key or KMS/HSM signing is the production step.
- The gateway fetches the JWKS lazily on first use and caches it, so it can start
  before `auth-service`; the first authenticated request after a cold start may
  wait on that fetch.
- Microsoft Entra was already validated by JWKS; the local issuer now works the
  same way, so the multi-issuer resolver has one code path.

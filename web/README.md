# web

The asset-tracker console — a Next.js 15 (App Router) front end for the
microservices backend.

## What it does

- **Assets** — one filterable table (by type, by status). "All laptops",
  "in for repair", etc.
- **Asset detail** — check an asset out to a person or a desk, return it to
  stock, and see its full custody history.
- **People** — everyone in the current client; click through to what they hold.
- **Person detail** — their assets, plus one-click **Collect all** for HR at
  offboarding.
- **Desks** — each desk and what is on it (the view a future QR scan would open).
- A client (tenant) switcher in the header scopes every page.

## Auth (the BFF pattern)

The browser never holds the JWT and never calls the platform directly. Login
forwards to the gateway through `app/api/auth/*`, which stores the token in an
**httpOnly, SameSite=Lax** cookie. Reads (assets / people / locations) are
public, so the console renders for a signed-out visitor; writes (check-out,
return, offboard, create) go through `app/api/bff/*`, which attaches the bearer
token server-side and only allows a fixed set of paths.

"Sign in with Microsoft" (Entra ID) is stubbed — it appears when
`NEXT_PUBLIC_ENTRA_CLIENT_ID` is set.

## Run it

```bash
cp .env.example .env.local          # GATEWAY_URL defaults to http://localhost:8080
npm install
npm run dev                         # http://localhost:3000
```

The platform must be up (`docker compose -f infra/compose/docker-compose.yml up -d`
from the repo root). Seeded login: `tech@acme.example` / `Passw0rd!`.

## Scripts

| command                                 | what                                     |
| --------------------------------------- | ---------------------------------------- |
| `npm run dev`                           | dev server                               |
| `npm run build` / `npm start`           | production build (standalone) / serve it |
| `npm run lint` / `typecheck` / `format` | ESLint / `tsc --noEmit` / Prettier       |

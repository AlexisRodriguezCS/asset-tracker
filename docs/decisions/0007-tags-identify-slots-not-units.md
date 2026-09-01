# 0007 — an asset tag identifies a slot, not a physical unit

**Status:** accepted

## Context

Techs label a laptop and its bundled accessories with the *same* asset tag, so a
loose charger on a desk can be traced back to its machine. Accessories then get
lost or broken and are swapped for new ones. A globally-unique `asset_tag` forces
either a fake "same" row (losing the history of the dead unit) or a brand-new tag
on the replacement (breaking the label convention).

## Decision

- `asset_tag` is unique only **per `(client_id, asset_tag, type)`** and only among
  **active** statuses — `IN_STOCK`, `ASSIGNED`, `IN_REPAIR`. Enforced by a partial
  unique index (`V4`) and by `AssetService.create` (`existsActiveWithTag`).
- A tag therefore carries at most one live asset of each type: a laptop plus its
  charger and cable can all share `ACME-L-001`.
- Retiring a unit (`LOST` / `BROKEN` / `RETIRED` / `PENDING_RECYCLE` / `RECYCLED`)
  frees its slot. A replacement is created on the same tag; the dead row stays for
  history.
- `GET /assets?tag=` returns everything a tag has ever carried; `getByTag` returns
  the current live unit.

## Consequences

- The console's retire-and-replace flow is: set the old unit's status, create the
  replacement with the tag pre-filled, check it back out to the same holder — the
  dead unit and the live one coexist under one tag.
- "All chargers" / "what's on desk 14" are unaffected — they filter by `type` /
  `holder`, not by tag.
- Dev (H2, `ddl-auto`) has no partial-index support, so there the service-layer
  check is the only guard; prod gets both.

-- A tag identifies a slot, not a single unit. A laptop and its bundled charger + cable can share
-- one tag (one active row per type), and a lost / retired / recycled unit frees its slot so a
-- replacement can be created with the same tag while the old row stays for history.
DROP INDEX ux_assets_tag;

CREATE UNIQUE INDEX ux_assets_tag_active
    ON assets (client_id, asset_tag, type)
    WHERE status IN ('IN_STOCK', 'ASSIGNED', 'IN_REPAIR');

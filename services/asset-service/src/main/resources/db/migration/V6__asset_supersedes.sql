-- Retire-and-replace now records which unit a replacement supersedes, instead of
-- the console guessing from matching tag + dates. Plain id, not a foreign key:
-- the superseded row is never deleted, and this mirrors how holder_id works.
ALTER TABLE assets ADD COLUMN supersedes_asset_id BIGINT;

CREATE INDEX ix_assets_supersedes ON assets (supersedes_asset_id);

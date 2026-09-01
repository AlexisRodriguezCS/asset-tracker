-- Free-text, per-client grouping chosen by the tech. Distinct values become the
-- category filter chips / the datalist on the add-asset form.
ALTER TABLE assets ADD COLUMN category VARCHAR(64);

CREATE INDEX ix_assets_category ON assets (client_id, category);

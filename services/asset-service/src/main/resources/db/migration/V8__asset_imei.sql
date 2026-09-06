-- A cellular device - hotspot, phone, tablet on a mobile plan - is identified by
-- its IMEI, which is what a carrier or insurer asks for. Some of those carry no
-- manufacturer serial at all, and a cable carries no identifier of any kind, so
-- serial_number stops being mandatory.
ALTER TABLE assets ADD COLUMN imei VARCHAR(32);
ALTER TABLE assets ALTER COLUMN serial_number DROP NOT NULL;

CREATE INDEX ix_assets_imei ON assets (imei);

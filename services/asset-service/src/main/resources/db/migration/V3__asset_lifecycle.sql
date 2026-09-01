-- Condition + service-life dates on assets. Status gains BROKEN / PENDING_RECYCLE / RECYCLED,
-- which are plain enum strings and need no column change.
ALTER TABLE assets ADD COLUMN condition       VARCHAR(20);
ALTER TABLE assets ADD COLUMN deployed_on     DATE;
ALTER TABLE assets ADD COLUMN warranty_ends_on DATE;

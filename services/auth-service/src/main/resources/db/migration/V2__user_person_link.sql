-- Links a login to the people-service employee record it belongs to, so the JWT can
-- carry a personId claim and downstream services can scope "assigned to me" without
-- a cross-service lookup. Null for platform staff who are not employees of a tenant.
ALTER TABLE users ADD COLUMN person_id BIGINT;

CREATE INDEX ix_users_person_id ON users (person_id);

ALTER TABLE users ADD COLUMN member_id BIGINT NULL;
ALTER TABLE users ADD CONSTRAINT uk_users_member_id UNIQUE (member_id);
ALTER TABLE users ADD CONSTRAINT fk_users_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL;

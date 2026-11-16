CREATE TABLE users (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(80) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(20) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE TABLE family_trees (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(200) NOT NULL, description LONGTEXT, owner_id BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_tree_owner FOREIGN KEY(owner_id) REFERENCES users(id)
);
CREATE TABLE tree_access (
 tree_id BIGINT NOT NULL, user_id BIGINT NOT NULL, access_role VARCHAR(20) NOT NULL,
 PRIMARY KEY(tree_id,user_id), FOREIGN KEY(tree_id) REFERENCES family_trees(id), FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE TABLE members (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tree_id BIGINT NOT NULL, name VARCHAR(160) NOT NULL, gender VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
 birth_date VARCHAR(10), birth_precision VARCHAR(20), birth_place VARCHAR(255), death_date VARCHAR(10), death_precision VARCHAR(20), death_place VARCHAR(255),
 occupation VARCHAR(160), height_cm DECIMAL(5,2), address VARCHAR(500), notes LONGTEXT, avatar_media_id BIGINT, sensitive_json LONGTEXT,
 deleted BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(tree_id) REFERENCES family_trees(id), INDEX idx_member_tree_name(tree_id,name), INDEX idx_member_tree_deleted(tree_id,deleted)
);
CREATE TABLE custom_field_definitions (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tree_id BIGINT NOT NULL, field_key VARCHAR(80) NOT NULL, label VARCHAR(120) NOT NULL, field_type VARCHAR(30) NOT NULL, `sensitive` BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE(tree_id,field_key), FOREIGN KEY(tree_id) REFERENCES family_trees(id)
);
CREATE TABLE member_custom_values (
 member_id BIGINT NOT NULL, field_definition_id BIGINT NOT NULL, value_text TEXT,
 PRIMARY KEY(member_id,field_definition_id), FOREIGN KEY(member_id) REFERENCES members(id), FOREIGN KEY(field_definition_id) REFERENCES custom_field_definitions(id)
);
CREATE TABLE relationships (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tree_id BIGINT NOT NULL, from_member_id BIGINT NOT NULL, to_member_id BIGINT NOT NULL,
 relation_type VARCHAR(20) NOT NULL, start_date VARCHAR(10), end_date VARCHAR(10), notes VARCHAR(1000), created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(tree_id) REFERENCES family_trees(id), FOREIGN KEY(from_member_id) REFERENCES members(id), FOREIGN KEY(to_member_id) REFERENCES members(id),
 UNIQUE(tree_id,from_member_id,to_member_id,relation_type), INDEX idx_rel_from(tree_id,from_member_id), INDEX idx_rel_to(tree_id,to_member_id)
);
CREATE TABLE media (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tree_id BIGINT NOT NULL, uploader_id BIGINT NOT NULL, original_name VARCHAR(255) NOT NULL, stored_name VARCHAR(255) NOT NULL UNIQUE,
 mime_type VARCHAR(120) NOT NULL, size_bytes BIGINT NOT NULL, sha256 CHAR(64) NOT NULL, scan_status VARCHAR(20) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(tree_id) REFERENCES family_trees(id), FOREIGN KEY(uploader_id) REFERENCES users(id), INDEX idx_media_tree(tree_id)
);
ALTER TABLE members ADD CONSTRAINT fk_member_avatar FOREIGN KEY(avatar_media_id) REFERENCES media(id);
CREATE TABLE change_suggestions (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tree_id BIGINT NOT NULL, submitter_id BIGINT NOT NULL, target_type VARCHAR(40) NOT NULL, target_id BIGINT,
 payload_json JSON NOT NULL, status VARCHAR(20) NOT NULL, reviewer_id BIGINT, review_comment VARCHAR(1000), created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), reviewed_at TIMESTAMP(6),
 FOREIGN KEY(tree_id) REFERENCES family_trees(id), FOREIGN KEY(submitter_id) REFERENCES users(id), FOREIGN KEY(reviewer_id) REFERENCES users(id), INDEX idx_suggestion_status(tree_id,status)
);
CREATE TABLE stories (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tree_id BIGINT NOT NULL, member_id BIGINT, author_id BIGINT NOT NULL, title VARCHAR(250) NOT NULL, content LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(tree_id) REFERENCES family_trees(id), FOREIGN KEY(member_id) REFERENCES members(id), FOREIGN KEY(author_id) REFERENCES users(id)
);
CREATE TABLE family_events (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tree_id BIGINT NOT NULL, member_id BIGINT, title VARCHAR(250) NOT NULL, event_type VARCHAR(40) NOT NULL, event_date VARCHAR(10) NOT NULL,
 date_precision VARCHAR(20) NOT NULL, place VARCHAR(255), description LONGTEXT, remind_at TIMESTAMP(6), created_by BIGINT NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(tree_id) REFERENCES family_trees(id), FOREIGN KEY(member_id) REFERENCES members(id), FOREIGN KEY(created_by) REFERENCES users(id), INDEX idx_event_tree_date(tree_id,event_date)
);
CREATE TABLE notifications (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, tree_id BIGINT, type VARCHAR(40) NOT NULL, message VARCHAR(1000) NOT NULL,
 read_at TIMESTAMP(6), created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(tree_id) REFERENCES family_trees(id), INDEX idx_notification_user(user_id,read_at)
);
CREATE TABLE transfer_jobs (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tree_id BIGINT NOT NULL, user_id BIGINT NOT NULL, direction VARCHAR(10) NOT NULL, format VARCHAR(20) NOT NULL,
 status VARCHAR(20) NOT NULL, source_media_id BIGINT, result_path VARCHAR(500), error_message LONGTEXT, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), finished_at TIMESTAMP(6),
 FOREIGN KEY(tree_id) REFERENCES family_trees(id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(source_media_id) REFERENCES media(id)
);
CREATE TABLE backup_records (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tree_id BIGINT NOT NULL, created_by BIGINT NOT NULL, stored_path VARCHAR(500) NOT NULL, size_bytes BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), FOREIGN KEY(tree_id) REFERENCES family_trees(id), FOREIGN KEY(created_by) REFERENCES users(id)
);
CREATE TABLE ocr_jobs (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tree_id BIGINT NOT NULL, media_id BIGINT NOT NULL, requested_by BIGINT NOT NULL, provider VARCHAR(60) NOT NULL,
 status VARCHAR(20) NOT NULL, raw_text LONGTEXT, corrected_text LONGTEXT, draft_member_json JSON, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), finished_at TIMESTAMP(6),
 FOREIGN KEY(tree_id) REFERENCES family_trees(id), FOREIGN KEY(media_id) REFERENCES media(id), FOREIGN KEY(requested_by) REFERENCES users(id)
);
CREATE TABLE audit_logs (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tree_id BIGINT, user_id BIGINT, action VARCHAR(80) NOT NULL, entity_type VARCHAR(60), entity_id BIGINT,
 details_json JSON, ip_address VARCHAR(64), created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), INDEX idx_audit_tree_time(tree_id,created_at), FOREIGN KEY(tree_id) REFERENCES family_trees(id), FOREIGN KEY(user_id) REFERENCES users(id)
);

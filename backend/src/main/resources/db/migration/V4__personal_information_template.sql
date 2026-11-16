ALTER TABLE custom_field_definitions ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE custom_field_definitions ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

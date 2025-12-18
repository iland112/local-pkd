-- H2-specific schema overrides for testing
-- This file is applied AFTER Flyway migrations to modify constraints for test data

-- Make upload_id nullable in certificate table for test fixtures
ALTER TABLE certificate ALTER COLUMN upload_id SET NULL;

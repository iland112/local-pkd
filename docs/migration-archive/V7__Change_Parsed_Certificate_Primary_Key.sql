-- V7: Change parsed_certificate primary key from (parsed_file_id, serial_number) to fingerprint_sha256
-- Reason: Multiple CSCA certificates from different countries can have the same serial_number
--         Certificate serial numbers are only unique within an issuer, not globally
--         fingerprint_sha256 (SHA-256 hash) is guaranteed to be unique per certificate

-- 1. Drop existing primary key constraint
ALTER TABLE parsed_certificate
    DROP CONSTRAINT parsed_certificate_pkey;

-- 2. Make fingerprint_sha256 NOT NULL (it was nullable before)
ALTER TABLE parsed_certificate
    ALTER COLUMN fingerprint_sha256 SET NOT NULL;

-- 3. Add new primary key on fingerprint_sha256
ALTER TABLE parsed_certificate
    ADD CONSTRAINT parsed_certificate_pkey PRIMARY KEY (fingerprint_sha256);

-- 4. Create index on (parsed_file_id, serial_number) for queries
--    (This was the old primary key, now it's just an index for lookups)
CREATE INDEX idx_parsed_certificate_file_serial ON parsed_certificate(parsed_file_id, serial_number);

-- Note: idx_parsed_certificate_fingerprint already exists, but it will be replaced by the new primary key index
DROP INDEX IF EXISTS idx_parsed_certificate_fingerprint;

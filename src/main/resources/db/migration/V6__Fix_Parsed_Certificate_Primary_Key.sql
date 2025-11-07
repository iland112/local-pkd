-- ============================================================================
-- V6: Fix parsed_certificate PRIMARY KEY
-- ============================================================================
-- Purpose: Change PRIMARY KEY from (parsed_file_id, serial_number)
--          to (parsed_file_id, fingerprint_sha256)
-- Reason: Multiple certificates can have the same serial_number from different issuers
--         fingerprint_sha256 is unique per certificate
-- Date: 2025-11-06
-- ============================================================================

-- Drop existing PRIMARY KEY constraint
ALTER TABLE parsed_certificate
DROP CONSTRAINT IF EXISTS parsed_certificate_pkey;

-- Add new PRIMARY KEY using fingerprint_sha256
-- fingerprint_sha256 is unique per certificate (SHA-256 hash of certificate content)
ALTER TABLE parsed_certificate
ADD CONSTRAINT parsed_certificate_pkey PRIMARY KEY (parsed_file_id, fingerprint_sha256);

-- ============================================================================
-- Comments for Documentation
-- ============================================================================
COMMENT ON CONSTRAINT parsed_certificate_pkey ON parsed_certificate
IS 'Composite PRIMARY KEY: (parsed_file_id, fingerprint_sha256). fingerprint_sha256 is unique per certificate, unlike serial_number which is only unique per issuer.';

-- ============================================================================
-- End of V6: Fix parsed_certificate PRIMARY KEY
-- ============================================================================

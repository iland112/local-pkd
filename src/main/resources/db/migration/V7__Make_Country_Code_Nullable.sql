-- V7: Make Country Code Columns Nullable
-- Purpose: Allow null country codes when DN extraction fails or returns invalid values
-- Date: 2025-11-14

-- Certificate Table: Make country code columns nullable
-- This fix allows validation to continue even when invalid country codes are extracted from LDIF data

ALTER TABLE certificate
ALTER COLUMN issuer_country_code DROP NOT NULL;

ALTER TABLE certificate
ALTER COLUMN subject_country_code DROP NOT NULL;

-- Add comment explaining the change
COMMENT ON COLUMN certificate.issuer_country_code IS 'Issuer country code (2-letter ISO code or null if invalid/missing from DN)';
COMMENT ON COLUMN certificate.subject_country_code IS 'Subject country code (2-letter ISO code or null if invalid/missing from DN)';

-- CertificateRevocationList Table: Make country code nullable
-- CRL entries extracted from LDIF files may have invalid or missing country codes in their DN
ALTER TABLE certificate_revocation_list
ALTER COLUMN country_code DROP NOT NULL;

-- Add comment explaining the change
COMMENT ON COLUMN certificate_revocation_list.country_code IS 'Country code (2-letter ISO code or null if invalid/missing from DN)';

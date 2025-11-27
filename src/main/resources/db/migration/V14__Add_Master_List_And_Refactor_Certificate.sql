-- ============================================================================
-- Migration V14: Add Master List Support and Refactor Certificate Table
-- ============================================================================
-- Purpose:
--   1. Create master_list table for ICAO Master List binary storage
--   2. Add validation result fields to certificate table
--   3. Add source tracking fields to certificate table
--   4. Support LDAP standard compliance (ICAO PKD)
--
-- Author: SmartCore Inc.
-- Date: 2025-11-27
-- ============================================================================

-- ============================================================================
-- 1. Create master_list table
-- ============================================================================
CREATE TABLE master_list (
    -- Primary Key
    id UUID PRIMARY KEY,

    -- Foreign Keys
    upload_id UUID NOT NULL REFERENCES uploaded_file(id) ON DELETE CASCADE,

    -- Master List Metadata
    country_code VARCHAR(3) NOT NULL,
    version VARCHAR(50),
    csca_count INTEGER NOT NULL DEFAULT 0,

    -- Binary Data
    cms_binary BYTEA NOT NULL,  -- CMS-signed Master List (complete binary)

    -- Signer Information (JSON format)
    signer_info JSONB,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT check_ml_country_code_format CHECK (country_code ~ '^[A-Z]{2,3}$'),
    CONSTRAINT check_ml_csca_count_positive CHECK (csca_count >= 0),
    CONSTRAINT check_ml_cms_binary_not_empty CHECK (length(cms_binary) > 0)
);

-- Indexes for master_list
CREATE INDEX idx_master_list_upload_id ON master_list(upload_id);
CREATE INDEX idx_master_list_country_code ON master_list(country_code);
CREATE INDEX idx_master_list_created_at ON master_list(created_at DESC);

-- Comments for master_list
COMMENT ON TABLE master_list IS 'Stores ICAO Master List CMS-signed binary data for LDAP upload compliance';
COMMENT ON COLUMN master_list.id IS 'Unique identifier for Master List';
COMMENT ON COLUMN master_list.upload_id IS 'Reference to uploaded file that contained this Master List';
COMMENT ON COLUMN master_list.country_code IS 'ISO 3166 ALPHA-2/3 country code';
COMMENT ON COLUMN master_list.version IS 'Master List version number';
COMMENT ON COLUMN master_list.csca_count IS 'Number of CSCA certificates contained in this Master List';
COMMENT ON COLUMN master_list.cms_binary IS 'Complete CMS-signed binary data as downloaded from ICAO PKD';
COMMENT ON COLUMN master_list.signer_info IS 'JSON containing signer DN, signature algorithm, signing time, etc.';

-- ============================================================================
-- 2. Add columns to certificate table
-- ============================================================================

-- Source tracking
ALTER TABLE certificate ADD COLUMN source_type VARCHAR(20);
ALTER TABLE certificate ADD COLUMN master_list_id UUID REFERENCES master_list(id) ON DELETE SET NULL;

-- Validation results
ALTER TABLE certificate ADD COLUMN validation_status VARCHAR(20);
ALTER TABLE certificate ADD COLUMN validation_message TEXT;
ALTER TABLE certificate ADD COLUMN validation_reason VARCHAR(50);
ALTER TABLE certificate ADD COLUMN validated_at TIMESTAMP;

-- Comments for new certificate columns
COMMENT ON COLUMN certificate.source_type IS 'Origin of certificate: MASTER_LIST (CSCA from ML), LDIF_DSC (DSC from LDIF), LDIF_CSCA (CSCA from LDIF)';
COMMENT ON COLUMN certificate.master_list_id IS 'References master_list if this CSCA was extracted from a Master List';
COMMENT ON COLUMN certificate.validation_status IS 'Validation result: VALID, INVALID, or PENDING';
COMMENT ON COLUMN certificate.validation_message IS 'Detailed validation message or error description';
COMMENT ON COLUMN certificate.validation_reason IS 'Short reason code for INVALID status (EXPIRED, UNTRUSTED, REVOKED, etc.)';
COMMENT ON COLUMN certificate.validated_at IS 'Timestamp when validation was performed';

-- ============================================================================
-- 3. Add constraints to certificate table
-- ============================================================================

ALTER TABLE certificate ADD CONSTRAINT check_cert_source_type
    CHECK (source_type IN ('MASTER_LIST', 'LDIF_DSC', 'LDIF_CSCA'));

ALTER TABLE certificate ADD CONSTRAINT check_cert_validation_status
    CHECK (validation_status IN ('VALID', 'INVALID', 'PENDING'));

-- Ensure master_list_id is only set for MASTER_LIST source type
ALTER TABLE certificate ADD CONSTRAINT check_cert_master_list_id_consistency
    CHECK (
        (source_type = 'MASTER_LIST' AND master_list_id IS NOT NULL) OR
        (source_type != 'MASTER_LIST' AND master_list_id IS NULL) OR
        (source_type IS NULL)
    );

-- ============================================================================
-- 4. Create indexes for new certificate columns
-- ============================================================================

CREATE INDEX idx_certificate_source_type ON certificate(source_type);
CREATE INDEX idx_certificate_master_list_id ON certificate(master_list_id)
    WHERE master_list_id IS NOT NULL;
CREATE INDEX idx_certificate_validation_status ON certificate(validation_status);
CREATE INDEX idx_certificate_validated_at ON certificate(validated_at DESC)
    WHERE validated_at IS NOT NULL;

-- Composite index for common queries
CREATE INDEX idx_certificate_upload_type_status ON certificate(upload_id, source_type, validation_status);

-- ============================================================================
-- 5. Migrate existing data
-- ============================================================================

-- Set source_type for existing certificates based on certificate_type
UPDATE certificate SET source_type =
    CASE
        WHEN certificate_type = 'CSCA' THEN 'LDIF_CSCA'
        WHEN certificate_type = 'DSC' THEN 'LDIF_DSC'
        WHEN certificate_type = 'DSC_NC' THEN 'LDIF_DSC'
        ELSE 'LDIF_DSC'  -- Default fallback
    END
WHERE source_type IS NULL;

-- Set validation_status to PENDING for existing certificates
UPDATE certificate SET validation_status =
    CASE
        WHEN status = 'VALID' THEN 'VALID'
        WHEN status = 'INVALID' THEN 'INVALID'
        ELSE 'PENDING'
    END
WHERE validation_status IS NULL;

-- Copy existing status messages to validation_message
UPDATE certificate SET validation_message =
    CASE
        WHEN status = 'VALID' THEN 'Certificate passed all validation checks (legacy data)'
        WHEN status = 'INVALID' THEN 'Certificate validation failed (legacy data)'
        ELSE 'Validation pending (legacy data)'
    END
WHERE validation_message IS NULL;

-- ============================================================================
-- 6. Make source_type NOT NULL (after setting defaults)
-- ============================================================================

ALTER TABLE certificate ALTER COLUMN source_type SET NOT NULL;

-- ============================================================================
-- 7. Create statistics view for Master Lists
-- ============================================================================

CREATE OR REPLACE VIEW v_master_list_statistics AS
SELECT
    ml.country_code,
    COUNT(ml.id) AS master_list_count,
    SUM(ml.csca_count) AS total_csca_count,
    MAX(ml.created_at) AS latest_upload,
    AVG(ml.csca_count) AS avg_csca_per_list,
    SUM(length(ml.cms_binary)) AS total_binary_size
FROM master_list ml
GROUP BY ml.country_code
ORDER BY total_csca_count DESC;

COMMENT ON VIEW v_master_list_statistics IS 'Statistics of Master Lists grouped by country';

-- ============================================================================
-- 8. Create view for certificate validation summary
-- ============================================================================

CREATE OR REPLACE VIEW v_certificate_validation_summary AS
SELECT
    c.upload_id,
    c.source_type,
    c.certificate_type,
    c.validation_status,
    COUNT(*) AS certificate_count,
    COUNT(DISTINCT c.subject_country_code) AS country_count
FROM certificate c
GROUP BY c.upload_id, c.source_type, c.certificate_type, c.validation_status
ORDER BY c.upload_id, c.source_type, c.validation_status;

COMMENT ON VIEW v_certificate_validation_summary IS 'Summary of certificate validation results grouped by upload, source, type, and status';

-- ============================================================================
-- 9. Grant permissions (if using specific roles)
-- ============================================================================

-- GRANT SELECT, INSERT, UPDATE, DELETE ON master_list TO localpkd_app;
-- GRANT SELECT ON v_master_list_statistics TO localpkd_app;
-- GRANT SELECT ON v_certificate_validation_summary TO localpkd_app;

-- ============================================================================
-- Migration Complete
-- ============================================================================

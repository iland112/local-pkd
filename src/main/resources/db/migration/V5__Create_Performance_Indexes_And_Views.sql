-- ============================================================================
-- V5: Performance Indexes and Monitoring Views
-- ============================================================================
-- Purpose: Additional performance optimization and monitoring infrastructure
-- Date: 2025-10-31
-- ============================================================================

-- ============================================================================
-- Additional Performance Indexes
-- ============================================================================

-- For uploaded_file searches
CREATE INDEX IF NOT EXISTS idx_uploaded_file_format_status
    ON uploaded_file(file_format, status);

CREATE INDEX IF NOT EXISTS idx_uploaded_file_format_date
    ON uploaded_file(file_format, uploaded_at DESC);

-- For parsed_file to uploaded_file relationship queries
CREATE INDEX IF NOT EXISTS idx_parsed_file_upload_id_status
    ON parsed_file(upload_id, status);

-- For certificate searches and joins
CREATE INDEX IF NOT EXISTS idx_certificate_country_type_status
    ON certificate(issuer_country_code, certificate_type, status);

-- For CRL country-based queries
CREATE INDEX IF NOT EXISTS idx_crl_country_next_update
    ON certificate_revocation_list(country_code, next_update DESC);

-- For fast deletion of expired data
CREATE INDEX IF NOT EXISTS idx_certificate_expiration
    ON certificate(not_after);

-- Note: Removed WHERE next_update > CURRENT_TIMESTAMP clause due to PostgreSQL immutability requirement

-- ============================================================================
-- Monitoring Views
-- ============================================================================

-- Upload Statistics
CREATE OR REPLACE VIEW v_upload_statistics AS
SELECT
    file_format,
    processing_mode,
    status,
    COUNT(*) AS count,
    AVG(file_size_bytes) AS avg_size,
    MAX(file_size_bytes) AS max_size,
    COUNT(*) FILTER (WHERE is_duplicate = TRUE) AS duplicate_count,
    COUNT(DISTINCT collection_number) AS collection_count
FROM uploaded_file
GROUP BY file_format, processing_mode, status;

-- Certificate Statistics
CREATE OR REPLACE VIEW v_certificate_statistics AS
SELECT
    certificate_type,
    issuer_country_code,
    status,
    COUNT(*) AS total_certificates,
    COUNT(*) FILTER (WHERE uploaded_to_ldap = TRUE) AS uploaded_to_ldap_count,
    COUNT(*) FILTER (WHERE not_after < CURRENT_TIMESTAMP) AS expired_count
FROM certificate
GROUP BY certificate_type, issuer_country_code, status;

-- CRL Statistics
CREATE OR REPLACE VIEW v_crl_statistics AS
SELECT
    country_code,
    issuer_name,
    COUNT(*) AS total_crls,
    COUNT(*) FILTER (WHERE next_update > CURRENT_TIMESTAMP) AS valid_crls,
    COUNT(*) FILTER (WHERE next_update <= CURRENT_TIMESTAMP) AS expired_crls,
    COUNT(*) FILTER (WHERE revoked_count > 0) AS crls_with_revocations,
    COALESCE(SUM(revoked_count), 0) AS total_revoked_certificates,
    MAX(revoked_count) AS max_revoked_in_single_crl,
    MIN(next_update) AS earliest_expiration,
    MAX(next_update) AS latest_expiration
FROM certificate_revocation_list
GROUP BY country_code, issuer_name;

-- Validation Error Statistics
CREATE OR REPLACE VIEW v_validation_error_statistics AS
SELECT
    error_severity,
    COUNT(*) AS error_count,
    COUNT(DISTINCT certificate_id) AS affected_certificates
FROM certificate_validation_error
GROUP BY error_severity;

-- Upload Health Check
CREATE OR REPLACE VIEW v_upload_health_check AS
SELECT
    'Total Uploads' AS metric,
    COUNT(*)::VARCHAR AS value
FROM uploaded_file
UNION ALL
SELECT
    'Duplicate Uploads',
    COUNT(*) FILTER (WHERE is_duplicate = TRUE)::VARCHAR
FROM uploaded_file
UNION ALL
SELECT
    'Total Certificates',
    COUNT(*)::VARCHAR
FROM certificate
UNION ALL
SELECT
    'Valid Certificates',
    COUNT(*) FILTER (WHERE status = 'VALID')::VARCHAR
FROM certificate
UNION ALL
SELECT
    'Total CRLs',
    COUNT(*)::VARCHAR
FROM certificate_revocation_list
UNION ALL
SELECT
    'Valid CRLs',
    COUNT(*) FILTER (WHERE next_update > CURRENT_TIMESTAMP)::VARCHAR
FROM certificate_revocation_list;

-- ============================================================================
-- Database Maintenance Comments
-- ============================================================================

COMMENT ON VIEW v_upload_statistics IS 'Upload activity and statistics by format and status';
COMMENT ON VIEW v_certificate_statistics IS 'Certificate distribution and validation status';
COMMENT ON VIEW v_crl_statistics IS 'CRL coverage and revocation information';
COMMENT ON VIEW v_validation_error_statistics IS 'Error types and affected certificates';
COMMENT ON VIEW v_upload_health_check IS 'System health check metrics';

-- ============================================================================
-- End of V5: Performance Indexes and Views
-- ============================================================================

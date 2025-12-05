-- ============================================================================
-- V1: Initial Database Schema (Consolidated)
-- ============================================================================
-- Purpose: Complete ICAO Local PKD database schema
-- Author: SmartCore Inc.
-- Date: 2025-12-05
-- Version: Consolidated from V1-V17 migrations
--
-- This file consolidates all previous migrations (V1-V17) into a single
-- initial schema creation script. All ALTER statements have been integrated
-- directly into the CREATE TABLE statements for cleaner schema initialization.
-- ============================================================================

-- ============================================================================
-- File Upload Context
-- ============================================================================

CREATE TABLE uploaded_file (
    -- Primary Key
    id UUID PRIMARY KEY,

    -- File Information
    file_name VARCHAR(255) NOT NULL,
    file_hash VARCHAR(64) NOT NULL UNIQUE,
    file_size_bytes BIGINT NOT NULL,
    file_size_display VARCHAR(20),
    file_format VARCHAR(50) NOT NULL,
    local_file_path VARCHAR(500),

    -- Metadata
    collection_number VARCHAR(10),
    version VARCHAR(50),

    -- Checksum
    expected_checksum VARCHAR(255),
    calculated_checksum VARCHAR(255),

    -- Status
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Duplicate Detection
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    original_upload_id UUID,
    is_newer_version BOOLEAN DEFAULT FALSE,

    -- Error Handling
    error_message TEXT,

    -- Processing Mode (Manual/Auto)
    processing_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    manual_pause_at_step VARCHAR(50),

    -- Constraints
    CONSTRAINT chk_file_size_positive CHECK (file_size_bytes > 0),
    CONSTRAINT chk_file_size_limit CHECK (file_size_bytes <= 104857600),
    CONSTRAINT chk_processing_mode CHECK (processing_mode IN ('AUTO', 'MANUAL')),
    CONSTRAINT chk_manual_pause_step CHECK (
        (processing_mode = 'MANUAL' AND manual_pause_at_step IS NOT NULL) OR
        (processing_mode = 'AUTO' AND manual_pause_at_step IS NULL)
    ),
    CONSTRAINT fk_original_upload FOREIGN KEY (original_upload_id)
        REFERENCES uploaded_file(id) ON DELETE CASCADE
);

CREATE INDEX idx_uploaded_file_hash ON uploaded_file(file_hash);
CREATE INDEX idx_uploaded_file_uploaded_at ON uploaded_file(uploaded_at DESC);
CREATE INDEX idx_uploaded_file_status ON uploaded_file(status);
CREATE INDEX idx_uploaded_file_processing_mode ON uploaded_file(processing_mode);
CREATE INDEX idx_uploaded_file_is_duplicate ON uploaded_file(is_duplicate) WHERE is_duplicate = TRUE;

COMMENT ON TABLE uploaded_file IS 'File Upload Aggregate Root';

-- ============================================================================
-- File Parsing Context
-- ============================================================================

CREATE TABLE parsed_file (
    -- Primary Key
    id UUID PRIMARY KEY,

    -- Foreign Key
    upload_id UUID NOT NULL REFERENCES uploaded_file(id) ON DELETE CASCADE,

    -- File Format
    file_format VARCHAR(50) NOT NULL,

    -- Parsing Status
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',

    -- Parsing Statistics
    total_entries INT DEFAULT 0,
    total_processed INT DEFAULT 0,
    certificate_count INT DEFAULT 0,
    crl_count INT DEFAULT 0,
    valid_count INT DEFAULT 0,
    invalid_count INT DEFAULT 0,
    error_count INT DEFAULT 0,
    duration_millis BIGINT DEFAULT 0,

    -- Timestamps
    parsing_started_at TIMESTAMP,
    parsing_completed_at TIMESTAMP
);

CREATE INDEX idx_parsed_file_upload_id ON parsed_file(upload_id);
CREATE INDEX idx_parsed_file_status ON parsed_file(status);
CREATE INDEX idx_parsed_file_parsing_completed_at ON parsed_file(parsing_completed_at DESC);

COMMENT ON TABLE parsed_file IS 'File Parsing Aggregate Root';

-- ============================================================================
-- Parsed Certificate (ElementCollection)
-- ============================================================================

CREATE TABLE parsed_certificate (
    -- Foreign Key
    parsed_file_id UUID NOT NULL REFERENCES parsed_file(id) ON DELETE CASCADE,

    -- Certificate Data
    cert_type VARCHAR(20) NOT NULL,
    country_code VARCHAR(3),
    subject_dn VARCHAR(500) NOT NULL,
    issuer_dn VARCHAR(500) NOT NULL,
    serial_number VARCHAR(100) NOT NULL,
    not_before TIMESTAMP NOT NULL,
    not_after TIMESTAMP NOT NULL,
    certificate_binary BYTEA NOT NULL,
    fingerprint_sha256 VARCHAR(64),
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,

    -- All Attributes (LDIF parsing)
    all_attributes JSONB,

    -- Composite Primary Key (allows audit trail for duplicate certificates)
    PRIMARY KEY (parsed_file_id, fingerprint_sha256)
);

CREATE INDEX idx_parsed_certificate_cert_type ON parsed_certificate(cert_type);
CREATE INDEX idx_parsed_certificate_fingerprint ON parsed_certificate(fingerprint_sha256);
CREATE INDEX idx_parsed_certificate_validity ON parsed_certificate(not_after) WHERE is_valid = TRUE;

COMMENT ON TABLE parsed_certificate IS 'Certificates extracted from parsed files (allows same certificate in multiple uploads for audit trail)';

-- ============================================================================
-- Parsed CRL (ElementCollection)
-- ============================================================================

CREATE TABLE parsed_crl (
    -- Foreign Key
    parsed_file_id UUID NOT NULL REFERENCES parsed_file(id) ON DELETE CASCADE,

    -- CRL Data
    crl_country_code VARCHAR(2),
    crl_issuer_dn VARCHAR(500) NOT NULL,
    crl_number VARCHAR(50),
    crl_this_update TIMESTAMP NOT NULL,
    crl_next_update TIMESTAMP,
    crl_binary BYTEA NOT NULL,
    revoked_certs_count INT,
    crl_is_valid BOOLEAN NOT NULL DEFAULT TRUE,

    -- Primary Key
    PRIMARY KEY (parsed_file_id, crl_issuer_dn)
);

CREATE INDEX idx_parsed_crl_validity ON parsed_crl(crl_next_update DESC) WHERE crl_is_valid = TRUE;

COMMENT ON TABLE parsed_crl IS 'CRL entries extracted from parsed files';

-- ============================================================================
-- Parsing Error (ElementCollection)
-- ============================================================================

CREATE TABLE parsing_error (
    -- Foreign Key
    parsed_file_id UUID NOT NULL REFERENCES parsed_file(id) ON DELETE CASCADE,

    -- Error Data
    error_type VARCHAR(50) NOT NULL,
    error_location VARCHAR(500),
    error_message VARCHAR(1000) NOT NULL,
    error_occurred_at TIMESTAMP NOT NULL,

    -- Primary Key
    PRIMARY KEY (parsed_file_id, error_occurred_at)
);

CREATE INDEX idx_parsing_error_type ON parsing_error(error_type);
CREATE INDEX idx_parsing_error_occurred_at ON parsing_error(error_occurred_at DESC);

COMMENT ON TABLE parsing_error IS 'Parsing errors extracted from parsed files';

-- ============================================================================
-- Certificate Validation Context
-- ============================================================================

CREATE TABLE master_list (
    -- Primary Key
    id UUID PRIMARY KEY,

    -- Foreign Key
    upload_id UUID NOT NULL REFERENCES uploaded_file(id) ON DELETE CASCADE,

    -- Master List Metadata
    country_code VARCHAR(3) NOT NULL,
    version VARCHAR(50),
    csca_count INTEGER NOT NULL DEFAULT 0,

    -- Binary Data
    cms_binary BYTEA NOT NULL,

    -- Signer Information
    signer_info JSONB,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT check_ml_country_code_format CHECK (country_code ~ '^[A-Z]{2,3}$'),
    CONSTRAINT check_ml_csca_count_positive CHECK (csca_count >= 0),
    CONSTRAINT check_ml_cms_binary_not_empty CHECK (length(cms_binary) > 0)
);

CREATE INDEX idx_master_list_upload_id ON master_list(upload_id);
CREATE INDEX idx_master_list_country_code ON master_list(country_code);
CREATE INDEX idx_master_list_created_at ON master_list(created_at DESC);

COMMENT ON TABLE master_list IS 'Stores ICAO Master List CMS-signed binary data for LDAP upload compliance';

-- ============================================================================
-- Certificate (Aggregate Root)
-- ============================================================================

CREATE TABLE certificate (
    -- Primary Key
    id UUID PRIMARY KEY,

    -- Foreign Keys
    upload_id UUID NOT NULL REFERENCES uploaded_file(id) ON DELETE CASCADE,
    master_list_id UUID REFERENCES master_list(id) ON DELETE SET NULL,

    -- Certificate Type
    certificate_type VARCHAR(30) NOT NULL,

    -- Source Tracking
    source_type VARCHAR(20) NOT NULL,

    -- ========== X509Data Value Object ==========
    -- DER-encoded certificate binary
    x509_certificate_binary BYTEA NOT NULL,
    -- Serial number (hex string)
    x509_serial_number VARCHAR(100) NOT NULL,
    -- SHA-256 fingerprint (64-char hex)
    x509_fingerprint_sha256 VARCHAR(64) NOT NULL UNIQUE,

    -- ========== SubjectInfo Value Object ==========
    -- Subject Distinguished Name
    subject_dn VARCHAR(500) NOT NULL,
    -- Subject country code (ISO 3166-1 alpha-3)
    subject_country_code VARCHAR(3),
    -- Subject organization
    subject_organization VARCHAR(255),
    -- Subject organizational unit
    subject_organizational_unit VARCHAR(255),
    -- Subject common name
    subject_common_name VARCHAR(255),

    -- ========== IssuerInfo Value Object ==========
    -- Issuer Distinguished Name
    issuer_dn VARCHAR(500) NOT NULL,
    -- Issuer country code (ISO 3166-1 alpha-3)
    issuer_country_code VARCHAR(3) NOT NULL,
    -- Issuer organization
    issuer_organization VARCHAR(255),
    -- Issuer organizational unit
    issuer_organizational_unit VARCHAR(255),
    -- Issuer common name
    issuer_common_name VARCHAR(255),
    -- Is issuer a CA?
    issuer_is_ca BOOLEAN,

    -- ========== ValidityPeriod Value Object ==========
    -- Certificate not before timestamp
    not_before TIMESTAMP NOT NULL,
    -- Certificate not after (expiration) timestamp
    not_after TIMESTAMP NOT NULL,

    -- ========== Certificate Type & Status ==========
    -- Current validation status (VALID, EXPIRED, NOT_YET_VALID, REVOKED, INVALID)
    status VARCHAR(30) NOT NULL,
    -- Signature algorithm (e.g., SHA256WithRSA)
    signature_algorithm VARCHAR(50),

    -- ========== ValidationResult Value Object ==========
    -- Overall validation status
    validation_overall_status VARCHAR(30),
    -- Signature validation result
    validation_signature_valid BOOLEAN,
    -- Chain validation result
    validation_chain_valid BOOLEAN,
    -- Revocation check result (not revoked?)
    validation_not_revoked BOOLEAN,
    -- Validity period check result
    validation_validity_valid BOOLEAN,
    -- Constraints validation result
    validation_constraints_valid BOOLEAN,
    -- When was validation performed
    validation_validated_at TIMESTAMP,
    -- Validation duration in milliseconds
    validation_duration_millis BIGINT,

    -- ========== LDAP Integration ==========
    -- Has this certificate been uploaded to LDAP?
    uploaded_to_ldap BOOLEAN NOT NULL DEFAULT FALSE,
    -- When was it uploaded to LDAP?
    uploaded_to_ldap_at TIMESTAMP,

    -- ========== All Attributes (LDIF parsing) ==========
    all_attributes JSONB,

    -- ========== Metadata ==========
    -- Certificate creation timestamp
    created_at TIMESTAMP NOT NULL,
    -- Certificate last update timestamp
    updated_at TIMESTAMP,

    -- ========== Constraints ==========
    CONSTRAINT check_cert_type CHECK (certificate_type IN ('CSCA', 'DSC', 'DSC_NC', 'DS', 'UNKNOWN')),
    CONSTRAINT check_cert_status CHECK (status IN ('VALID', 'EXPIRED', 'NOT_YET_VALID', 'REVOKED', 'INVALID')),
    CONSTRAINT check_cert_source_type CHECK (source_type IN ('MASTER_LIST', 'LDIF_DSC', 'LDIF_CSCA'))
);

CREATE INDEX idx_certificate_upload_id ON certificate(upload_id);
CREATE INDEX idx_certificate_type ON certificate(certificate_type);
CREATE INDEX idx_certificate_source_type ON certificate(source_type);
CREATE INDEX idx_certificate_master_list_id ON certificate(master_list_id) WHERE master_list_id IS NOT NULL;
CREATE INDEX idx_certificate_subject_country ON certificate(subject_country_code);
CREATE INDEX idx_certificate_issuer_country ON certificate(issuer_country_code);
CREATE INDEX idx_certificate_fingerprint ON certificate(x509_fingerprint_sha256);
CREATE INDEX idx_certificate_status ON certificate(status);
CREATE INDEX idx_certificate_validated_at ON certificate(validation_validated_at DESC) WHERE validation_validated_at IS NOT NULL;
CREATE INDEX idx_certificate_validity_period ON certificate(not_before, not_after);
CREATE INDEX idx_certificate_upload_type_status ON certificate(upload_id, source_type, validation_overall_status);

COMMENT ON TABLE certificate IS 'Certificate Aggregate Root (globally unique by x509_fingerprint_sha256)';

-- ============================================================================
-- Certificate Validation Error (ElementCollection)
-- ============================================================================

CREATE TABLE certificate_validation_error (
    -- Foreign Key
    certificate_id UUID NOT NULL REFERENCES certificate(id) ON DELETE CASCADE,

    -- Error Data
    error_code VARCHAR(50) NOT NULL,
    error_message VARCHAR(500) NOT NULL,
    error_severity VARCHAR(20) NOT NULL,
    error_details JSONB,
    error_occurred_at TIMESTAMP NOT NULL,

    -- Primary Key
    PRIMARY KEY (certificate_id, error_code, error_occurred_at)
);

CREATE INDEX idx_cert_validation_error_severity ON certificate_validation_error(error_severity);
CREATE INDEX idx_cert_validation_error_occurred_at ON certificate_validation_error(error_occurred_at DESC);

COMMENT ON TABLE certificate_validation_error IS 'Validation errors for certificates';

-- ============================================================================
-- Certificate Revocation List
-- ============================================================================

CREATE TABLE certificate_revocation_list (
    -- Primary Key
    id UUID PRIMARY KEY,

    -- Foreign Key
    upload_id UUID NOT NULL REFERENCES uploaded_file(id) ON DELETE CASCADE,

    -- CRL Metadata
    country_code VARCHAR(3),
    issuer_name VARCHAR(500) NOT NULL,
    crl_number VARCHAR(50),

    -- Validity Period
    this_update TIMESTAMP NOT NULL,
    next_update TIMESTAMP,

    -- ========== X509CrlData Value Object ==========
    -- CRL Binary Data (DER-encoded)
    crl_binary BYTEA NOT NULL,
    -- Revoked Certificates Count
    revoked_count INT NOT NULL DEFAULT 0,

    -- ========== RevokedCertificates Value Object ==========
    -- Revoked Serial Numbers (semicolon-separated)
    revoked_serial_numbers TEXT NOT NULL DEFAULT '',

    -- Status
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,

    -- ========== Metadata ==========
    -- CRL creation timestamp
    created_at TIMESTAMP NOT NULL,
    -- CRL last update timestamp
    updated_at TIMESTAMP NOT NULL,

    -- Constraints
    CONSTRAINT check_crl_country_code CHECK (country_code ~ '^[A-Z]{2,3}$' OR country_code IS NULL),
    CONSTRAINT check_crl_revoked_count CHECK (revoked_count >= 0)
);

CREATE INDEX idx_crl_upload_id ON certificate_revocation_list(upload_id);
CREATE INDEX idx_crl_country_code ON certificate_revocation_list(country_code);
CREATE INDEX idx_crl_issuer_name ON certificate_revocation_list(issuer_name);
CREATE INDEX idx_crl_next_update ON certificate_revocation_list(next_update DESC) WHERE is_valid = TRUE;
CREATE INDEX idx_crl_created_at ON certificate_revocation_list(created_at DESC);

COMMENT ON TABLE certificate_revocation_list IS 'Certificate Revocation Lists';

-- ============================================================================
-- Performance Views
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

CREATE OR REPLACE VIEW v_certificate_validation_summary AS
SELECT
    c.upload_id,
    c.source_type,
    c.certificate_type,
    c.validation_overall_status,
    COUNT(*) AS certificate_count,
    COUNT(DISTINCT c.subject_country_code) AS country_count
FROM certificate c
GROUP BY c.upload_id, c.source_type, c.certificate_type, c.validation_overall_status
ORDER BY c.upload_id, c.source_type, c.validation_overall_status;

COMMENT ON VIEW v_certificate_validation_summary IS 'Summary of certificate validation results';

CREATE OR REPLACE VIEW v_upload_summary AS
SELECT
    uf.id AS upload_id,
    uf.file_name,
    uf.file_format,
    uf.status AS upload_status,
    uf.uploaded_at,
    uf.processing_mode,
    pf.certificate_count AS parsed_cert_count,
    pf.crl_count AS parsed_crl_count,
    COUNT(DISTINCT c.id) AS stored_cert_count,
    COUNT(DISTINCT crl.id) AS stored_crl_count,
    COUNT(DISTINCT ml.id) AS master_list_count
FROM uploaded_file uf
LEFT JOIN parsed_file pf ON pf.upload_id = uf.id
LEFT JOIN certificate c ON c.upload_id = uf.id
LEFT JOIN certificate_revocation_list crl ON crl.upload_id = uf.id
LEFT JOIN master_list ml ON ml.upload_id = uf.id
GROUP BY uf.id, uf.file_name, uf.file_format, uf.status, uf.uploaded_at, uf.processing_mode, pf.certificate_count, pf.crl_count
ORDER BY uf.uploaded_at DESC;

COMMENT ON VIEW v_upload_summary IS 'Summary of uploads with parsing and storage statistics';

-- ============================================================================
-- Legacy Compatibility (from V1)
-- ============================================================================

CREATE TABLE file_upload_history (
    id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    collection_number VARCHAR(10),
    version VARCHAR(50),
    file_format VARCHAR(50) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    file_size_display VARCHAR(20),
    uploaded_at TIMESTAMP NOT NULL,
    local_file_path VARCHAR(500),
    file_hash VARCHAR(64),
    expected_checksum VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    is_duplicate BOOLEAN DEFAULT FALSE,
    is_newer_version BOOLEAN DEFAULT FALSE,
    error_message TEXT
);

CREATE INDEX idx_upload_status ON file_upload_history(status);
CREATE INDEX idx_upload_date ON file_upload_history(uploaded_at DESC);
CREATE INDEX idx_file_hash ON file_upload_history(file_hash);

COMMENT ON TABLE file_upload_history IS 'Legacy upload history table (backward compatibility)';

-- ============================================================================
-- End of Initial Schema
-- ============================================================================

-- ============================================================================
-- V3: File Parsing Context (Refactored)
-- ============================================================================
-- Purpose: File parsing results storage (LDIF and Master List parsing)
-- Domain: File Parsing Context (ParsedFile Aggregate Root)
-- Date: 2025-10-31
-- ============================================================================

-- ============================================================================
-- parsed_file: File Parsing Aggregate Root
-- ============================================================================
CREATE TABLE IF NOT EXISTS parsed_file (
    -- Primary Key (JPearl UUID)
    id UUID PRIMARY KEY,

    -- Foreign Key to uploaded_file (relationship to File Upload Context)
    upload_id UUID NOT NULL REFERENCES uploaded_file(id) ON DELETE CASCADE,

    -- File Format (LDIF, ML, etc.)
    file_format VARCHAR(50) NOT NULL,

    -- Parsing Status (RECEIVED, PARSING, PARSED, FAILED)
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',

    -- ========== ParsingStatistics Embedded Value Object ==========
    -- Total entries in file
    total_entries INT DEFAULT 0,

    -- Total entries processed (success + failure)
    total_processed INT DEFAULT 0,

    -- Extracted certificates count
    certificate_count INT DEFAULT 0,

    -- Extracted CRLs count
    crl_count INT DEFAULT 0,

    -- Valid certificates count
    valid_count INT DEFAULT 0,

    -- Invalid certificates count
    invalid_count INT DEFAULT 0,

    -- Errors count
    error_count INT DEFAULT 0,

    -- Parsing duration (milliseconds)
    duration_millis BIGINT DEFAULT 0,

    -- Timestamps
    parsing_started_at TIMESTAMP,
    parsing_completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_parsed_file_upload_id ON parsed_file(upload_id);
CREATE INDEX IF NOT EXISTS idx_parsed_file_status ON parsed_file(status);
CREATE INDEX IF NOT EXISTS idx_parsed_file_parsing_completed_at ON parsed_file(parsing_completed_at DESC);

-- ============================================================================
-- parsed_certificate: Certificates extracted from parsed file (ElementCollection)
-- ============================================================================
-- This table stores CertificateData value objects extracted from the parsed file
-- Part of the ParsedFile Aggregate Root (File Parsing Context)
-- Hibernate automatically generates this table for @ElementCollection(fetch = FetchType.LAZY)
-- ============================================================================
CREATE TABLE IF NOT EXISTS parsed_certificate (
    -- Foreign Key to parsed_file (ElementCollection join column)
    parsed_file_id UUID NOT NULL REFERENCES parsed_file(id) ON DELETE CASCADE,

    -- ========== CertificateData Value Object Fields ==========
    -- Certificate Type (CSCA, DSC, DSC_NC)
    cert_type VARCHAR(20) NOT NULL,

    -- Issuing Country Code (ISO 3166-1 alpha-2) - ✅ KEY FIELD (was missing, causing error)
    country_code VARCHAR(2),

    -- Subject Distinguished Name
    subject_dn VARCHAR(500) NOT NULL,

    -- Issuer Distinguished Name
    issuer_dn VARCHAR(500) NOT NULL,

    -- Certificate Serial Number (hex string)
    serial_number VARCHAR(100) NOT NULL,

    -- Certificate Validity Period
    not_before TIMESTAMP NOT NULL,
    not_after TIMESTAMP NOT NULL,

    -- DER-encoded X.509 certificate binary
    certificate_binary BYTEA NOT NULL,

    -- SHA-256 Fingerprint (64-char hex)
    fingerprint_sha256 VARCHAR(64),

    -- Validity Status
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,

    -- ========== Composite Primary Key ==========
    -- ElementCollection requires FK + unique key(s)
    PRIMARY KEY (parsed_file_id, serial_number)
);

CREATE INDEX IF NOT EXISTS idx_parsed_certificate_cert_type ON parsed_certificate(cert_type);
CREATE INDEX IF NOT EXISTS idx_parsed_certificate_fingerprint ON parsed_certificate(fingerprint_sha256);
CREATE INDEX IF NOT EXISTS idx_parsed_certificate_validity ON parsed_certificate(not_after) WHERE is_valid = TRUE;

-- ============================================================================
-- parsed_crl: CRL entries extracted from parsed file (ElementCollection)
-- ============================================================================
-- This table stores CrlData value objects extracted from the parsed file
-- Part of the ParsedFile Aggregate Root (File Parsing Context)
-- Hibernate automatically generates this table for @ElementCollection(fetch = FetchType.LAZY)
-- ============================================================================
CREATE TABLE IF NOT EXISTS parsed_crl (
    -- Foreign Key to parsed_file (ElementCollection join column)
    parsed_file_id UUID NOT NULL REFERENCES parsed_file(id) ON DELETE CASCADE,

    -- ========== CrlData Value Object Fields ==========
    -- CRL Country Code (ISO 3166-1 alpha-2)
    crl_country_code VARCHAR(2),

    -- CRL Issuer Distinguished Name
    crl_issuer_dn VARCHAR(500) NOT NULL,

    -- CRL Number
    crl_number VARCHAR(50),

    -- CRL Validity Period
    crl_this_update TIMESTAMP NOT NULL,
    crl_next_update TIMESTAMP,

    -- DER-encoded X.509 CRL binary
    crl_binary BYTEA NOT NULL,

    -- Revoked Certificates Count
    revoked_certs_count INT,

    -- CRL Validity Status
    crl_is_valid BOOLEAN NOT NULL DEFAULT TRUE,

    -- ========== Composite Primary Key ==========
    -- ElementCollection requires FK + unique key(s)
    PRIMARY KEY (parsed_file_id, crl_issuer_dn)
);

CREATE INDEX IF NOT EXISTS idx_parsed_crl_validity ON parsed_crl(crl_next_update DESC) WHERE crl_is_valid = TRUE;

-- ============================================================================
-- parsing_error: Parsing errors extracted from parsed file (ElementCollection)
-- ============================================================================
-- This table stores ParsingError value objects extracted during file parsing
-- Part of the ParsedFile Aggregate Root (File Parsing Context)
-- Hibernate automatically generates this table for @ElementCollection(fetch = FetchType.LAZY)
-- ============================================================================
CREATE TABLE IF NOT EXISTS parsing_error (
    -- Foreign Key to parsed_file (ElementCollection join column)
    parsed_file_id UUID NOT NULL REFERENCES parsed_file(id) ON DELETE CASCADE,

    -- ========== ParsingError Value Object Fields ==========
    -- Error Type (ENTRY_ERROR, CERTIFICATE_ERROR, CRL_ERROR, VALIDATION_ERROR, PARSE_ERROR)
    error_type VARCHAR(50) NOT NULL,

    -- Error Location (Entry DN, Certificate fingerprint, Line number, etc.)
    error_location VARCHAR(500),

    -- Error Message
    error_message VARCHAR(1000) NOT NULL,

    -- Error Occurred Time
    error_occurred_at TIMESTAMP NOT NULL,

    -- ========== Composite Primary Key ==========
    -- ElementCollection requires FK + unique key(s)
    PRIMARY KEY (parsed_file_id, error_occurred_at)
);

CREATE INDEX IF NOT EXISTS idx_parsing_error_type ON parsing_error(error_type);
CREATE INDEX IF NOT EXISTS idx_parsing_error_occurred_at ON parsing_error(error_occurred_at DESC);

-- ============================================================================
-- Comments for Documentation
-- ============================================================================
COMMENT ON TABLE parsed_file IS 'File Parsing Aggregate Root - LDIF/ML parsing results';
COMMENT ON TABLE parsed_certificate IS 'Certificates extracted from parsed files';
COMMENT ON TABLE parsed_crl IS 'CRL entries extracted from parsed files';
COMMENT ON TABLE parsing_error IS 'Parsing errors extracted from parsed files';

-- ============================================================================
-- End of V3: File Parsing Context
-- ============================================================================

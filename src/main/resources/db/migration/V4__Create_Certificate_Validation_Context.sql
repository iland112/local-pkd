-- ============================================================================
-- V4: Certificate Validation Context
-- ============================================================================
-- Purpose: X.509 Certificate and CRL storage with validation support
-- Domain: Certificate Validation Context
-- Date: 2025-10-31
-- ============================================================================

-- ============================================================================
-- certificate: Certificate Aggregate Root
-- ============================================================================
-- Aggregate Root for X.509 certificate management
-- Contains: X509Data (embedded), SubjectInfo (embedded), IssuerInfo (embedded),
--           ValidityPeriod (embedded), ValidationResult (embedded)
-- ============================================================================
CREATE TABLE IF NOT EXISTS certificate (
    -- ========== Primary Key ==========
    -- JPearl Type-Safe Entity ID
    id UUID PRIMARY KEY,

    -- ========== Foreign Keys ==========
    -- Cross-Context Reference: File Upload Context
    upload_id UUID NOT NULL REFERENCES uploaded_file(id) ON DELETE CASCADE,

    -- ========== X509Data Value Object ==========
    -- DER-encoded X.509 certificate binary
    x509_certificate_binary BYTEA NOT NULL,
    -- Certificate serial number (hex string)
    x509_serial_number VARCHAR(100) NOT NULL,
    -- SHA-256 fingerprint (64-char hex)
    x509_fingerprint_sha256 VARCHAR(64) NOT NULL UNIQUE,

    -- ========== SubjectInfo Value Object ==========
    -- Subject Distinguished Name
    subject_dn VARCHAR(500) NOT NULL,
    -- Subject country code (ISO 3166-1 alpha-2)
    subject_country_code VARCHAR(2),
    -- Subject organization
    subject_organization VARCHAR(255),
    -- Subject organizational unit
    subject_organizational_unit VARCHAR(255),
    -- Subject common name
    subject_common_name VARCHAR(255),

    -- ========== IssuerInfo Value Object ==========
    -- Issuer Distinguished Name
    issuer_dn VARCHAR(500) NOT NULL,
    -- Issuer country code (ISO 3166-1 alpha-2)
    issuer_country_code VARCHAR(2) NOT NULL,
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
    -- Certificate type (CSCA, DSC, DSC_NC, DS, UNKNOWN)
    certificate_type VARCHAR(30) NOT NULL,
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

    -- ========== Metadata ==========
    -- Certificate creation timestamp
    created_at TIMESTAMP NOT NULL,
    -- Certificate last update timestamp
    updated_at TIMESTAMP,

    -- ========== Constraints ==========
    CONSTRAINT chk_certificate_type CHECK (
        certificate_type IN ('CSCA', 'DSC', 'DSC_NC', 'DS', 'UNKNOWN')
    ),
    CONSTRAINT chk_certificate_status CHECK (
        status IN ('VALID', 'EXPIRED', 'NOT_YET_VALID', 'REVOKED', 'INVALID')
    ),
    CONSTRAINT chk_validity_period CHECK (not_before < not_after)
);

-- ========== Indexes for Certificate ==========
CREATE INDEX IF NOT EXISTS idx_certificate_upload_id ON certificate(upload_id);
CREATE INDEX IF NOT EXISTS idx_certificate_fingerprint ON certificate(x509_fingerprint_sha256);
CREATE INDEX IF NOT EXISTS idx_certificate_serial_number ON certificate(x509_serial_number);
CREATE INDEX IF NOT EXISTS idx_certificate_status ON certificate(status);
CREATE INDEX IF NOT EXISTS idx_certificate_type ON certificate(certificate_type);
CREATE INDEX IF NOT EXISTS idx_certificate_issuer_country ON certificate(issuer_country_code);
CREATE INDEX IF NOT EXISTS idx_certificate_subject_country ON certificate(subject_country_code);
CREATE INDEX IF NOT EXISTS idx_certificate_not_after ON certificate(not_after);
CREATE INDEX IF NOT EXISTS idx_certificate_created_at ON certificate(created_at DESC);

-- ============================================================================
-- certificate_validation_error: Validation error tracking
-- ============================================================================
-- ElementCollection table for Certificate aggregate
-- Many-to-One relationship with certificate
-- ============================================================================
CREATE TABLE IF NOT EXISTS certificate_validation_error (
    -- Primary Key: Certificate ID (foreign key) + array index
    certificate_id UUID NOT NULL REFERENCES certificate(id) ON DELETE CASCADE,
    -- Error code (ValidationError.errorCode)
    error_code VARCHAR(50) NOT NULL,
    -- Error message (ValidationError.errorMessage)
    error_message TEXT NOT NULL,
    -- Error severity (ERROR, WARNING)
    error_severity VARCHAR(20) NOT NULL,
    -- When the error occurred
    error_occurred_at TIMESTAMP NOT NULL,

    PRIMARY KEY (certificate_id, error_code, error_occurred_at)
);

CREATE INDEX IF NOT EXISTS idx_cert_validation_error_certificate_severity
    ON certificate_validation_error(certificate_id, error_severity);

-- ============================================================================
-- certificate_revocation_list: CRL Aggregate Root
-- ============================================================================
-- Aggregate Root for X.509 CRL (Certificate Revocation List) management
-- Contains: IssuerName (embedded), CountryCode (embedded), ValidityPeriod (embedded),
--           X509CrlData (embedded), RevokedCertificates (embedded)
-- ============================================================================
CREATE TABLE IF NOT EXISTS certificate_revocation_list (
    -- ========== Primary Key ==========
    -- JPearl Type-Safe Entity ID
    id UUID PRIMARY KEY,

    -- ========== Foreign Keys ==========
    -- Cross-Context Reference: File Upload Context
    upload_id UUID NOT NULL REFERENCES uploaded_file(id) ON DELETE CASCADE,

    -- ========== IssuerName Value Object ==========
    -- CSCA (Country Signing CA) issuer name
    issuer_name VARCHAR(255) NOT NULL,

    -- ========== CountryCode Value Object ==========
    -- ISO 3166-1 alpha-2 country code
    country_code VARCHAR(2) NOT NULL,

    -- ========== ValidityPeriod Value Object ==========
    -- When CRL was issued (thisUpdate)
    this_update TIMESTAMP NOT NULL,
    -- When next CRL update is expected (nextUpdate)
    next_update TIMESTAMP NOT NULL,

    -- ========== X509CrlData Value Object ==========
    -- DER-encoded X.509 CRL binary
    crl_binary BYTEA NOT NULL,

    -- ========== RevokedCertificates Value Object ==========
    -- Semicolon-separated list of revoked certificate serial numbers (hex)
    revoked_serial_numbers TEXT NOT NULL DEFAULT '',
    -- Count of revoked certificates
    revoked_count INT NOT NULL DEFAULT 0,

    -- ========== Metadata ==========
    -- CRL creation timestamp
    created_at TIMESTAMP NOT NULL,
    -- CRL last update timestamp
    updated_at TIMESTAMP NOT NULL,

    -- ========== Constraints ==========
    CONSTRAINT chk_crl_country_code_format CHECK (length(country_code) = 2),
    CONSTRAINT chk_crl_next_after_this CHECK (next_update > this_update),
    CONSTRAINT chk_crl_revoked_count CHECK (revoked_count >= 0)
);

-- ========== Indexes for CRL ==========
CREATE INDEX IF NOT EXISTS idx_crl_issuer_country ON certificate_revocation_list(issuer_name, country_code);
CREATE INDEX IF NOT EXISTS idx_crl_issuer ON certificate_revocation_list(issuer_name);
CREATE INDEX IF NOT EXISTS idx_crl_country ON certificate_revocation_list(country_code);
CREATE INDEX IF NOT EXISTS idx_crl_next_update ON certificate_revocation_list(next_update DESC);
-- Note: Removed WHERE next_update > CURRENT_TIMESTAMP (PostgreSQL immutability requirement)
--       Use application logic for filtering valid CRLs
CREATE INDEX IF NOT EXISTS idx_crl_created_at ON certificate_revocation_list(created_at DESC);

-- ============================================================================
-- Comments for Documentation
-- ============================================================================
COMMENT ON TABLE certificate IS 'X.509 Certificate Aggregate Root - Certificate Validation Context';
COMMENT ON COLUMN certificate.id IS 'Certificate unique identifier (JPearl UUID)';
COMMENT ON COLUMN certificate.x509_certificate_binary IS 'DER-encoded X.509 certificate binary';
COMMENT ON COLUMN certificate.x509_fingerprint_sha256 IS 'SHA-256 fingerprint (64-char hex, unique)';
COMMENT ON COLUMN certificate.subject_dn IS 'Certificate subject Distinguished Name';
COMMENT ON COLUMN certificate.issuer_dn IS 'Certificate issuer Distinguished Name';
COMMENT ON COLUMN certificate.not_before IS 'Certificate validity start time';
COMMENT ON COLUMN certificate.not_after IS 'Certificate validity end time (expiration)';
COMMENT ON COLUMN certificate.certificate_type IS 'ICAO PKD certificate type (CSCA, DSC, DSC_NC, DS, UNKNOWN)';
COMMENT ON COLUMN certificate.status IS 'Validation status (VALID, EXPIRED, NOT_YET_VALID, REVOKED, INVALID)';
COMMENT ON COLUMN certificate.uploaded_to_ldap IS 'Has this certificate been uploaded to LDAP?';

COMMENT ON TABLE certificate_validation_error IS 'Certificate validation errors - ElementCollection of Certificate';
COMMENT ON TABLE certificate_revocation_list IS 'X.509 CRL (Certificate Revocation List) Aggregate Root - Certificate Validation Context';
COMMENT ON COLUMN certificate_revocation_list.id IS 'CRL unique identifier (JPearl UUID)';
COMMENT ON COLUMN certificate_revocation_list.issuer_name IS 'CSCA issuer name (e.g., CSCA-QA)';
COMMENT ON COLUMN certificate_revocation_list.country_code IS 'ISO 3166-1 alpha-2 country code';
COMMENT ON COLUMN certificate_revocation_list.this_update IS 'CRL issue timestamp';
COMMENT ON COLUMN certificate_revocation_list.next_update IS 'Expected next CRL update timestamp';
COMMENT ON COLUMN certificate_revocation_list.crl_binary IS 'DER-encoded X.509 CRL binary';
COMMENT ON COLUMN certificate_revocation_list.revoked_serial_numbers IS 'Semicolon-separated revoked cert serial numbers (hex)';

-- ============================================================================
-- End of V4: Certificate Validation Context
-- ============================================================================

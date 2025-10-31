-- V8: Create Certificate Table
-- Phase 11: Certificate Validation Context
-- Author: SmartCore Inc.
-- Date: 2025-10-24

-- ============================================================
-- Certificate Table (Aggregate Root)
-- ============================================================
CREATE TABLE IF NOT EXISTS certificate (
    -- Primary Key (JPearl UUID)
    id UUID PRIMARY KEY,

    -- X509Data (Embedded)
    x509_certificate_binary BYTEA NOT NULL,
    x509_serial_number VARCHAR(100),
    x509_fingerprint_sha256 VARCHAR(64) NOT NULL UNIQUE,

    -- SubjectInfo (Embedded)
    subject_dn VARCHAR(500) NOT NULL,
    subject_country_code VARCHAR(2),
    subject_organization VARCHAR(255),
    subject_organizational_unit VARCHAR(255),
    subject_common_name VARCHAR(255),

    -- IssuerInfo (Embedded)
    issuer_dn VARCHAR(500) NOT NULL,
    issuer_country_code VARCHAR(2),
    issuer_organization VARCHAR(255),
    issuer_organizational_unit VARCHAR(255),
    issuer_common_name VARCHAR(255),
    issuer_is_ca BOOLEAN,

    -- ValidityPeriod (Embedded)
    not_before TIMESTAMP NOT NULL,
    not_after TIMESTAMP NOT NULL,

    -- Certificate Type & Status
    certificate_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    signature_algorithm VARCHAR(50),

    -- ValidationResult (Embedded)
    validation_overall_status VARCHAR(30),
    validation_signature_valid BOOLEAN,
    validation_chain_valid BOOLEAN,
    validation_not_revoked BOOLEAN,
    validation_validity_valid BOOLEAN,
    validation_constraints_valid BOOLEAN,
    validation_validated_at TIMESTAMP,
    validation_duration_millis BIGINT,

    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    uploaded_to_ldap BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_to_ldap_at TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_certificate_type CHECK (certificate_type IN ('CSCA', 'DSC', 'DSC_NC', 'DS', 'UNKNOWN')),
    CONSTRAINT chk_certificate_status CHECK (status IN ('VALID', 'EXPIRED', 'NOT_YET_VALID', 'REVOKED', 'INVALID')),
    CONSTRAINT chk_validity_period CHECK (not_before < not_after)
);

-- ============================================================
-- Indexes for Performance
-- ============================================================

-- Primary lookup: by fingerprint (unique)
CREATE UNIQUE INDEX IF NOT EXISTS idx_certificate_fingerprint_unique
    ON certificate(x509_fingerprint_sha256);

-- Lookup: by serial number
CREATE INDEX IF NOT EXISTS idx_certificate_serial_number
    ON certificate(x509_serial_number);

-- Filter: by status
CREATE INDEX IF NOT EXISTS idx_certificate_status
    ON certificate(status);

-- Filter: by type
CREATE INDEX IF NOT EXISTS idx_certificate_type
    ON certificate(certificate_type);

-- Filter: by country code (subject)
CREATE INDEX IF NOT EXISTS idx_certificate_subject_country
    ON certificate(subject_country_code);

-- Filter: by country code (issuer)
CREATE INDEX IF NOT EXISTS idx_certificate_issuer_country
    ON certificate(issuer_country_code);

-- Filter: by LDAP upload status
CREATE INDEX IF NOT EXISTS idx_certificate_ldap_upload
    ON certificate(uploaded_to_ldap) WHERE uploaded_to_ldap = FALSE;

-- Sort: by creation time
CREATE INDEX IF NOT EXISTS idx_certificate_created_at
    ON certificate(created_at DESC);

-- Sort: by expiration time (for finding expiring soon)
CREATE INDEX IF NOT EXISTS idx_certificate_not_after
    ON certificate(not_after);

-- Composite: status + type (common query pattern)
CREATE INDEX IF NOT EXISTS idx_certificate_status_type
    ON certificate(status, certificate_type);

-- ============================================================
-- Statistics View (for monitoring)
-- ============================================================
CREATE OR REPLACE VIEW v_certificate_stats AS
SELECT
    COUNT(*) AS total_certificates,
    COUNT(*) FILTER (WHERE status = 'VALID') AS valid_count,
    COUNT(*) FILTER (WHERE status = 'EXPIRED') AS expired_count,
    COUNT(*) FILTER (WHERE status = 'REVOKED') AS revoked_count,
    COUNT(*) FILTER (WHERE status = 'INVALID') AS invalid_count,
    COUNT(*) FILTER (WHERE certificate_type = 'CSCA') AS csca_count,
    COUNT(*) FILTER (WHERE certificate_type = 'DSC') AS dsc_count,
    COUNT(*) FILTER (WHERE certificate_type = 'DSC_NC') AS dsc_nc_count,
    COUNT(*) FILTER (WHERE uploaded_to_ldap = TRUE) AS uploaded_to_ldap_count,
    COUNT(*) FILTER (WHERE uploaded_to_ldap = FALSE AND status = 'VALID') AS pending_ldap_upload_count,
    COUNT(*) FILTER (WHERE not_after < CURRENT_TIMESTAMP) AS already_expired_count,
    COUNT(*) FILTER (WHERE not_after BETWEEN CURRENT_TIMESTAMP AND CURRENT_TIMESTAMP + INTERVAL '30 days') AS expiring_soon_count
FROM certificate;

-- ============================================================
-- Comments for Documentation
-- ============================================================
COMMENT ON TABLE certificate IS 'X.509 인증서 Aggregate Root - Certificate Validation Context';
COMMENT ON COLUMN certificate.id IS 'Certificate 고유 식별자 (JPearl UUID)';
COMMENT ON COLUMN certificate.x509_certificate_binary IS 'DER-encoded X.509 인증서 바이너리 데이터';
COMMENT ON COLUMN certificate.x509_fingerprint_sha256 IS 'SHA-256 지문 (64자 16진수, 중복 검사용)';
COMMENT ON COLUMN certificate.subject_dn IS '인증서 주체(Subject) Distinguished Name';
COMMENT ON COLUMN certificate.issuer_dn IS '인증서 발급자(Issuer) Distinguished Name';
COMMENT ON COLUMN certificate.not_before IS '인증서 유효 시작 시간';
COMMENT ON COLUMN certificate.not_after IS '인증서 유효 종료 시간';
COMMENT ON COLUMN certificate.certificate_type IS 'ICAO PKD 인증서 타입 (CSCA, DSC, DSC_NC, DS, UNKNOWN)';
COMMENT ON COLUMN certificate.status IS '검증 상태 (VALID, EXPIRED, NOT_YET_VALID, REVOKED, INVALID)';
COMMENT ON COLUMN certificate.uploaded_to_ldap IS 'LDAP 디렉토리 업로드 여부';

COMMENT ON VIEW v_certificate_stats IS '인증서 통계 뷰 - 상태별/타입별 집계';

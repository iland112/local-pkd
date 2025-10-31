-- V9: Create Certificate Validation Error Table
-- Phase 11: Certificate Validation Context
-- Author: SmartCore Inc.
-- Date: 2025-10-24

-- ============================================================
-- Certificate Validation Error Table (@ElementCollection)
-- ============================================================
-- JPA @ElementCollection으로 정의된 ValidationError 목록을 저장합니다.
-- 각 Certificate는 여러 개의 ValidationError를 가질 수 있습니다.

CREATE TABLE IF NOT EXISTS certificate_validation_error (
    -- Foreign Key to Certificate
    certificate_id UUID NOT NULL,

    -- ValidationError fields (Embedded)
    error_code VARCHAR(50) NOT NULL,
    error_message VARCHAR(500) NOT NULL,
    error_severity VARCHAR(20) NOT NULL,
    error_occurred_at TIMESTAMP NOT NULL,

    -- Constraints
    CONSTRAINT fk_certificate_validation_error_certificate
        FOREIGN KEY (certificate_id)
        REFERENCES certificate(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_error_severity
        CHECK (error_severity IN ('ERROR', 'WARNING'))
);

-- ============================================================
-- Indexes for Performance
-- ============================================================

-- Lookup: by certificate_id (FK)
CREATE INDEX IF NOT EXISTS idx_cert_validation_error_cert_id
    ON certificate_validation_error(certificate_id);

-- Filter: by error code
CREATE INDEX IF NOT EXISTS idx_cert_validation_error_code
    ON certificate_validation_error(error_code);

-- Filter: by severity
CREATE INDEX IF NOT EXISTS idx_cert_validation_error_severity
    ON certificate_validation_error(error_severity);

-- Sort: by occurred time
CREATE INDEX IF NOT EXISTS idx_cert_validation_error_occurred_at
    ON certificate_validation_error(error_occurred_at DESC);

-- Composite: certificate_id + severity (common query pattern)
CREATE INDEX IF NOT EXISTS idx_cert_validation_error_cert_severity
    ON certificate_validation_error(certificate_id, error_severity);

-- ============================================================
-- Error Statistics View
-- ============================================================
CREATE OR REPLACE VIEW v_certificate_validation_error_stats AS
SELECT
    COUNT(*) AS total_errors,
    COUNT(*) FILTER (WHERE error_severity = 'ERROR') AS critical_errors,
    COUNT(*) FILTER (WHERE error_severity = 'WARNING') AS warnings,
    COUNT(DISTINCT certificate_id) AS certificates_with_errors,
    COUNT(DISTINCT error_code) AS unique_error_codes,
    error_code,
    COUNT(*) AS error_count
FROM certificate_validation_error
GROUP BY error_code
ORDER BY error_count DESC;

-- ============================================================
-- Common Error Codes View
-- ============================================================
CREATE OR REPLACE VIEW v_common_validation_errors AS
SELECT
    error_code,
    error_severity,
    COUNT(*) AS occurrence_count,
    COUNT(DISTINCT certificate_id) AS affected_certificates,
    MIN(error_occurred_at) AS first_occurrence,
    MAX(error_occurred_at) AS last_occurrence
FROM certificate_validation_error
GROUP BY error_code, error_severity
ORDER BY occurrence_count DESC
LIMIT 20;

-- ============================================================
-- Certificates with Critical Errors View
-- ============================================================
CREATE OR REPLACE VIEW v_certificates_with_critical_errors AS
SELECT
    c.id AS certificate_id,
    c.subject_dn,
    c.subject_country_code,
    c.certificate_type,
    c.status,
    COUNT(e.error_code) AS critical_error_count,
    STRING_AGG(DISTINCT e.error_code, ', ' ORDER BY e.error_code) AS error_codes
FROM certificate c
INNER JOIN certificate_validation_error e ON c.id = e.certificate_id
WHERE e.error_severity = 'ERROR'
GROUP BY c.id, c.subject_dn, c.subject_country_code, c.certificate_type, c.status
ORDER BY critical_error_count DESC;

-- ============================================================
-- Comments for Documentation
-- ============================================================
COMMENT ON TABLE certificate_validation_error IS 'Certificate 검증 오류 목록 (ElementCollection)';
COMMENT ON COLUMN certificate_validation_error.certificate_id IS 'Certificate FK (ON DELETE CASCADE)';
COMMENT ON COLUMN certificate_validation_error.error_code IS '오류 코드 (예: SIGNATURE_INVALID, CHAIN_INCOMPLETE)';
COMMENT ON COLUMN certificate_validation_error.error_message IS '상세 오류 메시지';
COMMENT ON COLUMN certificate_validation_error.error_severity IS '심각도 (ERROR: 치명적, WARNING: 경고)';
COMMENT ON COLUMN certificate_validation_error.error_occurred_at IS '오류 발생 시간';

COMMENT ON VIEW v_certificate_validation_error_stats IS '검증 오류 통계 뷰 - 오류 코드별 집계';
COMMENT ON VIEW v_common_validation_errors IS '일반적인 검증 오류 Top 20';
COMMENT ON VIEW v_certificates_with_critical_errors IS '치명적 오류가 있는 인증서 목록';

-- V7: Create Parsed File Tables for File Parsing Context
-- Date: 2025-10-23
-- Description: ParsedFile Aggregate Root 및 관련 ElementCollection 테이블 생성

-- ========================================
-- 1. parsed_file (Aggregate Root)
-- ========================================
CREATE TABLE parsed_file (
    -- Primary Key (UUID)
    id UUID PRIMARY KEY,

    -- 외부 참조 (File Upload Context)
    upload_id UUID NOT NULL,

    -- 파일 포맷
    file_format VARCHAR(50) NOT NULL,

    -- 파싱 상태
    status VARCHAR(20) NOT NULL,

    -- 파싱 시작/완료 시각
    parsing_started_at TIMESTAMP,
    parsing_completed_at TIMESTAMP,

    -- 파싱 통계 (Embedded ParsingStatistics)
    total_entries INTEGER DEFAULT 0,
    total_processed INTEGER DEFAULT 0,
    certificate_count INTEGER DEFAULT 0,
    crl_count INTEGER DEFAULT 0,
    valid_count INTEGER DEFAULT 0,
    invalid_count INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    duration_millis BIGINT DEFAULT 0,

    -- Constraints
    CONSTRAINT chk_status CHECK (status IN ('RECEIVED', 'PARSING', 'PARSED', 'FAILED')),
    CONSTRAINT chk_total_entries_positive CHECK (total_entries >= 0),
    CONSTRAINT chk_duration_positive CHECK (duration_millis >= 0)
);

-- Indexes
CREATE INDEX idx_parsed_file_upload_id ON parsed_file(upload_id);
CREATE INDEX idx_parsed_file_status ON parsed_file(status);
CREATE INDEX idx_parsed_file_started_at ON parsed_file(parsing_started_at DESC);

-- Comments
COMMENT ON TABLE parsed_file IS 'ParsedFile Aggregate Root - 파싱된 파일 메타데이터';
COMMENT ON COLUMN parsed_file.id IS 'ParsedFileId (UUID)';
COMMENT ON COLUMN parsed_file.upload_id IS 'UploadId - File Upload Context 참조';
COMMENT ON COLUMN parsed_file.file_format IS 'FileFormat - LDIF, Master List 등';
COMMENT ON COLUMN parsed_file.status IS 'ParsingStatus - RECEIVED, PARSING, PARSED, FAILED';

-- ========================================
-- 2. parsed_certificate (ElementCollection)
-- ========================================
CREATE TABLE parsed_certificate (
    -- Foreign Key to parsed_file
    parsed_file_id UUID NOT NULL,

    -- CertificateData Value Object fields
    cert_type VARCHAR(20) NOT NULL,
    country_code VARCHAR(2),
    subject_dn VARCHAR(500) NOT NULL,
    issuer_dn VARCHAR(500) NOT NULL,
    serial_number VARCHAR(100) NOT NULL,
    not_before TIMESTAMP NOT NULL,
    not_after TIMESTAMP NOT NULL,
    certificate_binary BYTEA NOT NULL,
    fingerprint_sha256 VARCHAR(64),
    valid BOOLEAN NOT NULL DEFAULT TRUE,

    -- Primary Key (composite)
    PRIMARY KEY (parsed_file_id, fingerprint_sha256),

    -- Foreign Key
    CONSTRAINT fk_parsed_certificate_file
        FOREIGN KEY (parsed_file_id)
        REFERENCES parsed_file(id)
        ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_parsed_cert_file_id ON parsed_certificate(parsed_file_id);
CREATE INDEX idx_parsed_cert_country ON parsed_certificate(country_code);
CREATE INDEX idx_parsed_cert_type ON parsed_certificate(cert_type);
CREATE INDEX idx_parsed_cert_valid ON parsed_certificate(valid);

-- Comments
COMMENT ON TABLE parsed_certificate IS 'CertificateData ElementCollection - 추출된 인증서 데이터';
COMMENT ON COLUMN parsed_certificate.cert_type IS '인증서 타입 (CSCA, DSC 등)';
COMMENT ON COLUMN parsed_certificate.country_code IS '국가 코드 (ISO 3166-1 alpha-2)';
COMMENT ON COLUMN parsed_certificate.fingerprint_sha256 IS 'SHA-256 지문 (인증서 고유 식별)';

-- ========================================
-- 3. parsed_crl (ElementCollection)
-- ========================================
CREATE TABLE parsed_crl (
    -- Foreign Key to parsed_file
    parsed_file_id UUID NOT NULL,

    -- CrlData Value Object fields
    crl_issuer_dn VARCHAR(500) NOT NULL,
    crl_this_update TIMESTAMP NOT NULL,
    crl_next_update TIMESTAMP,
    crl_binary BYTEA NOT NULL,
    revoked_certs_count INTEGER DEFAULT 0,
    valid BOOLEAN NOT NULL DEFAULT TRUE,

    -- Primary Key (composite)
    PRIMARY KEY (parsed_file_id, crl_issuer_dn, crl_this_update),

    -- Foreign Key
    CONSTRAINT fk_parsed_crl_file
        FOREIGN KEY (parsed_file_id)
        REFERENCES parsed_file(id)
        ON DELETE CASCADE,

    -- Constraints
    CONSTRAINT chk_revoked_count_positive CHECK (revoked_certs_count >= 0)
);

-- Indexes
CREATE INDEX idx_parsed_crl_file_id ON parsed_crl(parsed_file_id);
CREATE INDEX idx_parsed_crl_next_update ON parsed_crl(crl_next_update);
CREATE INDEX idx_parsed_crl_valid ON parsed_crl(valid);

-- Comments
COMMENT ON TABLE parsed_crl IS 'CrlData ElementCollection - 추출된 CRL 데이터';
COMMENT ON COLUMN parsed_crl.crl_issuer_dn IS 'CRL 발행자 DN';
COMMENT ON COLUMN parsed_crl.crl_this_update IS 'CRL 발행 시각';
COMMENT ON COLUMN parsed_crl.crl_next_update IS 'CRL 다음 업데이트 예정 시각';

-- ========================================
-- 4. parsing_error (ElementCollection)
-- ========================================
CREATE TABLE parsing_error (
    -- Foreign Key to parsed_file
    parsed_file_id UUID NOT NULL,

    -- ParsingError Value Object fields
    error_type VARCHAR(50) NOT NULL,
    error_location VARCHAR(500),
    error_message VARCHAR(1000) NOT NULL,
    error_occurred_at TIMESTAMP NOT NULL,

    -- Primary Key (composite)
    PRIMARY KEY (parsed_file_id, error_occurred_at, error_type),

    -- Foreign Key
    CONSTRAINT fk_parsing_error_file
        FOREIGN KEY (parsed_file_id)
        REFERENCES parsed_file(id)
        ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_parsing_error_file_id ON parsing_error(parsed_file_id);
CREATE INDEX idx_parsing_error_type ON parsing_error(error_type);
CREATE INDEX idx_parsing_error_occurred_at ON parsing_error(error_occurred_at DESC);

-- Comments
COMMENT ON TABLE parsing_error IS 'ParsingError ElementCollection - 파싱 오류 목록';
COMMENT ON COLUMN parsing_error.error_type IS '오류 타입 (ENTRY_ERROR, CERTIFICATE_ERROR, PARSE_ERROR 등)';
COMMENT ON COLUMN parsing_error.error_location IS '오류 발생 위치 (DN, entry ID 등)';

-- ========================================
-- 5. Statistics View (Optional)
-- ========================================
CREATE OR REPLACE VIEW v_parsed_file_summary AS
SELECT
    pf.id,
    pf.upload_id,
    pf.file_format,
    pf.status,
    pf.parsing_started_at,
    pf.parsing_completed_at,
    pf.total_processed,
    pf.certificate_count,
    pf.crl_count,
    pf.error_count,
    pf.duration_millis,
    COUNT(DISTINCT pc.fingerprint_sha256) AS actual_cert_count,
    COUNT(DISTINCT pcrl.crl_issuer_dn) AS actual_crl_count,
    COUNT(DISTINCT pe.error_type) AS actual_error_count,
    CASE
        WHEN pf.status = 'PARSED' THEN 'SUCCESS'
        WHEN pf.status = 'FAILED' THEN 'FAILED'
        ELSE 'PROCESSING'
    END AS result_status
FROM parsed_file pf
LEFT JOIN parsed_certificate pc ON pf.id = pc.parsed_file_id
LEFT JOIN parsed_crl pcrl ON pf.id = pcrl.parsed_file_id
LEFT JOIN parsing_error pe ON pf.id = pe.parsed_file_id
GROUP BY
    pf.id,
    pf.upload_id,
    pf.file_format,
    pf.status,
    pf.parsing_started_at,
    pf.parsing_completed_at,
    pf.total_processed,
    pf.certificate_count,
    pf.crl_count,
    pf.error_count,
    pf.duration_millis;

COMMENT ON VIEW v_parsed_file_summary IS 'ParsedFile 요약 뷰 - 통계 및 실제 카운트';

-- ========================================
-- Migration Complete
-- ========================================

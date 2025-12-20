-- ================================================================
-- ICAO PKD Database Schema - Initial Migration
-- Version: 2.0
-- Description: 통합 PKD 파일 관리 및 인증서 추적 시스템
-- ================================================================

-- ================================================================
-- 1. 파일 업로드 이력 테이블 (file_upload_history)
-- ================================================================
-- 이전 pkd_files 테이블의 기능을 통합하여 파일 업로드 및 처리 이력 관리
CREATE TABLE IF NOT EXISTS file_upload_history (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,

    -- ========================================
    -- 파일 정보
    -- ========================================

    -- 원본 파일명 (예: icaopkd-001-complete-009410.ldif)
    filename VARCHAR(255) NOT NULL,

    -- Collection 번호 (001: eMRTD, 002: CSCA, 003: Non-Conformant)
    collection_number VARCHAR(3),

    -- 파일 버전 (LDIF: 숫자, ML: Month/Year)
    version VARCHAR(50),

    -- 파일 포맷 (FileFormat enum)
    file_format VARCHAR(50),

    -- 파일 크기 (bytes)
    file_size_bytes BIGINT,

    -- 파일 크기 (human-readable, 예: "74.3 MiB")
    file_size_display VARCHAR(20),

    -- ========================================
    -- 업로드 정보
    -- ========================================

    -- 업로드 일시
    uploaded_at TIMESTAMP NOT NULL,

    -- 업로드 사용자 ID
    uploaded_by VARCHAR(100),

    -- 로컬 파일 경로
    local_file_path VARCHAR(500),

    -- ========================================
    -- 체크섬 검증 정보
    -- ========================================

    -- 계산된 SHA-1 체크섬
    calculated_checksum VARCHAR(40),

    -- 기대되는 체크섬 (ICAO 공식, 사용자 입력)
    expected_checksum VARCHAR(40),

    -- 체크섬 검증 수행 여부
    checksum_validated BOOLEAN,

    -- 체크섬 일치 여부
    checksum_valid BOOLEAN,

    -- 체크섬 계산 소요 시간 (밀리초)
    checksum_elapsed_time_ms BIGINT,

    -- ========================================
    -- 처리 상태 정보
    -- ========================================

    -- 업로드 상태 (UploadStatus enum)
    status VARCHAR(50) NOT NULL DEFAULT 'RECEIVED',

    -- 오류 메시지
    error_message VARCHAR(1000),

    -- 처리된 엔트리 수
    entries_processed INTEGER,

    -- 실패한 엔트리 수
    entries_failed INTEGER,

    -- 처리 시작 시간
    processing_started_at TIMESTAMP,

    -- 처리 완료 시간
    processing_completed_at TIMESTAMP,

    -- 총 처리 시간 (초)
    total_processing_time_seconds BIGINT,

    -- ========================================
    -- 중복 체크 정보
    -- ========================================

    -- 중복 파일 여부
    is_duplicate BOOLEAN DEFAULT FALSE,

    -- 새로운 버전 여부
    is_newer_version BOOLEAN DEFAULT FALSE,

    -- 대체된 이전 파일 ID
    replaced_file_id BIGINT,

    -- ========================================
    -- 추가 메타데이터
    -- ========================================

    -- ICAO 공식 설명
    description VARCHAR(1000),

    -- Deprecated 여부
    is_deprecated BOOLEAN DEFAULT FALSE,

    -- 비고 (관리자 메모)
    remarks VARCHAR(1000),

    -- ========================================
    -- Audit Fields
    -- ========================================

    -- 생성 일시
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 수정 일시
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- ========================================
    -- 제약 조건
    -- ========================================

    -- Collection 번호는 001, 002, 003 중 하나
    CONSTRAINT chk_collection_number CHECK (
        collection_number IS NULL OR
        collection_number IN ('001', '002', '003')
    ),

    -- 파일 크기는 양수
    CONSTRAINT chk_file_size_positive CHECK (
        file_size_bytes IS NULL OR file_size_bytes > 0
    ),

    -- 체크섬은 40자 (SHA-1) 또는 NULL
    CONSTRAINT chk_checksum_length CHECK (
        (calculated_checksum IS NULL OR length(calculated_checksum) = 40) AND
        (expected_checksum IS NULL OR length(expected_checksum) = 40)
    ),

    -- 엔트리 수는 0 이상
    CONSTRAINT chk_entries_non_negative CHECK (
        entries_processed IS NULL OR entries_processed >= 0
    ),

    -- 실패한 엔트리는 처리된 엔트리보다 작거나 같음
    CONSTRAINT chk_entries_failed_valid CHECK (
        entries_failed IS NULL OR
        entries_processed IS NULL OR
        entries_failed <= entries_processed
    ),

    -- 업로드 상태 값 (UploadStatus enum과 일치)
    CONSTRAINT chk_upload_status CHECK (status IN (
        'RECEIVED',
        'VALIDATING',
        'CHECKSUM_VALIDATING',
        'CHECKSUM_INVALID',
        'DUPLICATE_DETECTED',
        'OLDER_VERSION',
        'PARSING',
        'STORING',
        'SUCCESS',
        'FAILED',
        'ROLLBACK',
        'PARTIAL_SUCCESS'
    )),

    -- 파일 포맷 값 (FileFormat enum과 일치)
    CONSTRAINT chk_file_format CHECK (file_format IS NULL OR file_format IN (
        'ML_SIGNED_CMS',
        'CSCA_COMPLETE_LDIF',
        'CSCA_DELTA_LDIF',
        'EMRTD_COMPLETE_LDIF',
        'EMRTD_DELTA_LDIF',
        'NON_CONFORMANT_COMPLETE_LDIF',
        'NON_CONFORMANT_DELTA_LDIF'
    )),

    -- replaced_file_id는 같은 테이블의 id를 참조
    CONSTRAINT fk_replaced_file
        FOREIGN KEY (replaced_file_id)
        REFERENCES file_upload_history(id)
        ON DELETE SET NULL
);

-- 인덱스
CREATE INDEX idx_upload_status ON file_upload_history(status);
CREATE INDEX idx_upload_date ON file_upload_history(uploaded_at DESC);
CREATE INDEX idx_collection_version ON file_upload_history(collection_number, version);
CREATE INDEX idx_checksum ON file_upload_history(calculated_checksum);
CREATE INDEX idx_filename ON file_upload_history(filename);
CREATE INDEX idx_duplicate ON file_upload_history(is_duplicate) WHERE is_duplicate = TRUE;
CREATE INDEX idx_newer_version ON file_upload_history(collection_number, version DESC, uploaded_at DESC);
CREATE INDEX idx_in_progress ON file_upload_history(status) WHERE status NOT IN ('SUCCESS', 'FAILED', 'ROLLBACK', 'CHECKSUM_INVALID', 'DUPLICATE_DETECTED', 'OLDER_VERSION', 'PARTIAL_SUCCESS');

-- 코멘트
COMMENT ON TABLE file_upload_history IS 'ICAO PKD 파일 업로드 및 처리 이력';
COMMENT ON COLUMN file_upload_history.collection_number IS '001=eMRTD PKI Objects, 002=CSCA Master Lists, 003=Non-Conformant (Deprecated)';
COMMENT ON COLUMN file_upload_history.version IS 'LDIF: 숫자 버전, ML: Month/Year';
COMMENT ON COLUMN file_upload_history.file_format IS 'FileFormat enum 값';
COMMENT ON COLUMN file_upload_history.status IS 'UploadStatus enum 값 (12개 상태)';

-- ================================================================
-- 2. 인증서 통합 테이블 (CSCA + DSC)
-- ================================================================
CREATE TABLE IF NOT EXISTS certificates (
    id BIGSERIAL PRIMARY KEY,
    cert_id VARCHAR(36) UNIQUE NOT NULL,

    -- 인증서 분류
    cert_type VARCHAR(10) NOT NULL,
    country_code VARCHAR(2) NOT NULL,

    -- 인증서 식별 정보
    subject_dn TEXT NOT NULL,
    issuer_dn TEXT NOT NULL,
    serial_number VARCHAR(100) NOT NULL,

    -- Fingerprints
    fingerprint_sha1 VARCHAR(40) NOT NULL,
    fingerprint_sha256 VARCHAR(64) NOT NULL UNIQUE,

    -- 유효기간
    not_before TIMESTAMP NOT NULL,
    not_after TIMESTAMP NOT NULL,

    -- 상태
    status VARCHAR(20) NOT NULL DEFAULT 'VALID',

    -- 폐기 정보
    revoked_at TIMESTAMP,
    revocation_reason VARCHAR(100),
    revocation_source VARCHAR(36),

    -- 검증 정보
    signature_verified BOOLEAN DEFAULT FALSE,
    trust_chain_verified BOOLEAN DEFAULT FALSE,
    crl_checked BOOLEAN DEFAULT FALSE,
    last_verified_at TIMESTAMP,

    -- 원본 데이터
    certificate_der BYTEA NOT NULL,
    certificate_pem TEXT,

    -- 출처 (file_upload_history 참조)
    source_file_id BIGINT,

    -- LDAP 동기화
    ldap_synced BOOLEAN DEFAULT FALSE,
    ldap_dn TEXT,
    ldap_synced_at TIMESTAMP,

    -- 버전 관리
    version INTEGER DEFAULT 1,
    is_latest BOOLEAN DEFAULT TRUE,
    replaced_by VARCHAR(36),

    -- 감사
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 제약 조건
    CONSTRAINT chk_cert_type CHECK (cert_type IN ('CSCA', 'DSC', 'BCSC', 'BCSC_NC')),
    CONSTRAINT chk_cert_status CHECK (status IN ('VALID', 'EXPIRED', 'REVOKED', 'SUSPENDED')),
    CONSTRAINT chk_cert_validity_period CHECK (not_before < not_after),
    CONSTRAINT chk_cert_version_positive CHECK (version > 0),
    CONSTRAINT chk_fingerprint_sha1_length CHECK (LENGTH(fingerprint_sha1) = 40),
    CONSTRAINT chk_fingerprint_sha256_length CHECK (LENGTH(fingerprint_sha256) = 64),
    CONSTRAINT chk_serial_number_not_empty CHECK (serial_number IS NOT NULL AND serial_number <> ''),
    CONSTRAINT fk_cert_source_file FOREIGN KEY(source_file_id) REFERENCES file_upload_history(id) ON DELETE SET NULL,
    CONSTRAINT fk_cert_replaced_by FOREIGN KEY(replaced_by) REFERENCES certificates(cert_id) ON DELETE SET NULL
);

-- 인덱스
CREATE INDEX idx_cert_type ON certificates(cert_type);
CREATE INDEX idx_cert_country ON certificates(country_code);
CREATE INDEX idx_cert_status ON certificates(status);
CREATE INDEX idx_cert_serial ON certificates(country_code, serial_number);
CREATE INDEX idx_cert_latest ON certificates(is_latest) WHERE is_latest = TRUE;
CREATE INDEX idx_cert_ldap_synced ON certificates(ldap_synced) WHERE ldap_synced = FALSE;
CREATE INDEX idx_cert_fingerprint_sha1 ON certificates(fingerprint_sha1);
CREATE INDEX idx_cert_not_after ON certificates(not_after);
CREATE INDEX idx_cert_country_type_status ON certificates(country_code, cert_type, status, is_latest) WHERE is_latest = TRUE;
CREATE INDEX idx_cert_expiring ON certificates(not_after, status) WHERE status = 'VALID' AND is_latest = TRUE;
CREATE INDEX idx_cert_ldap_pending ON certificates(ldap_synced, cert_type, country_code) WHERE ldap_synced = FALSE;
CREATE INDEX idx_cert_valid_only ON certificates(country_code, cert_type, not_after) WHERE status = 'VALID' AND is_latest = TRUE;
CREATE INDEX idx_cert_revoked_only ON certificates(country_code, revoked_at DESC) WHERE status = 'REVOKED' AND is_latest = TRUE;

-- 전문 검색 인덱스
CREATE INDEX idx_cert_subject_dn_gin ON certificates USING gin(to_tsvector('english', subject_dn));
CREATE INDEX idx_cert_issuer_dn_gin ON certificates USING gin(to_tsvector('english', issuer_dn));

-- 코멘트
COMMENT ON TABLE certificates IS 'CSCA, DSC, BCSC 인증서 통합 테이블';

-- ================================================================
-- 3. CRL 테이블
-- ================================================================

-- CRL 메타 정보
CREATE TABLE IF NOT EXISTS crl_lists (
    id BIGSERIAL PRIMARY KEY,
    crl_id VARCHAR(36) UNIQUE NOT NULL,

    country_code VARCHAR(2) NOT NULL,
    issuer_dn TEXT NOT NULL,

    this_update TIMESTAMP NOT NULL,
    next_update TIMESTAMP NOT NULL,

    total_revocations INTEGER DEFAULT 0,

    source_file_id BIGINT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_crl_update_order CHECK (this_update <= next_update),
    CONSTRAINT chk_crl_revocations_non_negative CHECK (total_revocations >= 0),
    CONSTRAINT fk_crl_file FOREIGN KEY(source_file_id) REFERENCES file_upload_history(id) ON DELETE SET NULL
);

-- 인덱스
CREATE INDEX idx_crl_country ON crl_lists(country_code);
CREATE INDEX idx_crl_this_update ON crl_lists(this_update DESC);

-- CRL 개별 항목
CREATE TABLE IF NOT EXISTS crl_entries (
    id BIGSERIAL PRIMARY KEY,

    crl_list_id BIGINT NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    serial_number VARCHAR(100) NOT NULL,

    revocation_date TIMESTAMP NOT NULL,
    revocation_reason VARCHAR(100),

    affected_cert_id VARCHAR(36),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_crl_entry UNIQUE(crl_list_id, serial_number),
    CONSTRAINT fk_crl_list FOREIGN KEY(crl_list_id) REFERENCES crl_lists(id) ON DELETE CASCADE,
    CONSTRAINT fk_affected_cert FOREIGN KEY(affected_cert_id) REFERENCES certificates(cert_id) ON DELETE SET NULL
);

-- 인덱스
CREATE INDEX idx_crl_entry_country ON crl_entries(country_code);
CREATE INDEX idx_crl_entry_serial ON crl_entries(country_code, serial_number);
CREATE INDEX idx_crl_entry_affected ON crl_entries(affected_cert_id);
CREATE INDEX idx_crl_entries_revocation_date ON crl_entries(revocation_date DESC);
CREATE INDEX idx_crl_entries_affected_cert ON crl_entries(affected_cert_id) WHERE affected_cert_id IS NOT NULL;

-- 코멘트
COMMENT ON TABLE crl_lists IS 'CRL 메타 정보';
COMMENT ON TABLE crl_entries IS 'CRL 폐기 항목';

-- ================================================================
-- 4. 편차(Deviation) 테이블
-- ================================================================
CREATE TABLE IF NOT EXISTS deviations (
    id BIGSERIAL PRIMARY KEY,
    deviation_id VARCHAR(36) UNIQUE NOT NULL,

    country_code VARCHAR(2) NOT NULL,
    deviation_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,

    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    workaround TEXT,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    reported_date TIMESTAMP,
    resolved_date TIMESTAMP,
    acknowledged_by VARCHAR(100),

    -- 적용 규칙
    rule_applied BOOLEAN DEFAULT FALSE,
    rule_config JSONB,

    source_file_id BIGINT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_deviation_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_deviation_status CHECK (status IN ('ACTIVE', 'RESOLVED', 'ACKNOWLEDGED', 'IGNORED')),
    CONSTRAINT fk_deviation_file FOREIGN KEY(source_file_id) REFERENCES file_upload_history(id) ON DELETE SET NULL
);

-- 인덱스
CREATE INDEX idx_deviation_country ON deviations(country_code);
CREATE INDEX idx_deviation_status ON deviations(status);
CREATE INDEX idx_deviation_severity ON deviations(severity);
CREATE INDEX idx_deviations_active ON deviations(status, severity, country_code) WHERE status = 'ACTIVE';

-- 전문 검색 인덱스
CREATE INDEX idx_deviation_desc_gin ON deviations USING gin(to_tsvector('english', description));

-- 코멘트
COMMENT ON TABLE deviations IS '표준 편차 관리 테이블';

-- ================================================================
-- 5. 통계 테이블
-- ================================================================
CREATE TABLE IF NOT EXISTS country_statistics (
    id BIGSERIAL PRIMARY KEY,

    country_code VARCHAR(2) NOT NULL,
    stats_date DATE NOT NULL DEFAULT CURRENT_DATE,

    -- CSCA 통계
    total_csca INTEGER DEFAULT 0,
    valid_csca INTEGER DEFAULT 0,
    expired_csca INTEGER DEFAULT 0,
    revoked_csca INTEGER DEFAULT 0,

    -- DSC 통계
    total_dsc INTEGER DEFAULT 0,
    valid_dsc INTEGER DEFAULT 0,
    expired_dsc INTEGER DEFAULT 0,
    revoked_dsc INTEGER DEFAULT 0,

    -- CRL 통계
    total_revocations INTEGER DEFAULT 0,
    last_crl_update TIMESTAMP,

    -- Deviation 통계
    total_deviations INTEGER DEFAULT 0,
    critical_deviations INTEGER DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_country_stats UNIQUE(country_code, stats_date),
    CONSTRAINT chk_stats_non_negative CHECK (
        total_csca >= 0 AND valid_csca >= 0 AND expired_csca >= 0 AND revoked_csca >= 0 AND
        total_dsc >= 0 AND valid_dsc >= 0 AND expired_dsc >= 0 AND revoked_dsc >= 0 AND
        total_revocations >= 0 AND total_deviations >= 0 AND critical_deviations >= 0
    ),
    CONSTRAINT chk_stats_csca_sum CHECK (valid_csca + expired_csca + revoked_csca <= total_csca),
    CONSTRAINT chk_stats_dsc_sum CHECK (valid_dsc + expired_dsc + revoked_dsc <= total_dsc)
);

-- 인덱스
CREATE INDEX idx_stats_country ON country_statistics(country_code);
CREATE INDEX idx_stats_date ON country_statistics(stats_date DESC);
CREATE INDEX idx_country_stats_latest ON country_statistics(country_code, stats_date DESC);

-- 코멘트
COMMENT ON TABLE country_statistics IS '국가별 통계 집계 테이블';

-- ================================================================
-- 6. 뷰(Views)
-- ================================================================

-- 업로드 이력 요약 뷰
CREATE OR REPLACE VIEW v_upload_summary AS
SELECT
    fuh.collection_number,
    fuh.file_format,
    MAX(fuh.version) as latest_version,
    COUNT(*) FILTER (WHERE fuh.status = 'SUCCESS') as success_count,
    COUNT(*) FILTER (WHERE fuh.status IN ('FAILED', 'ROLLBACK', 'CHECKSUM_INVALID')) as failed_count,
    MAX(fuh.uploaded_at) as last_upload,
    SUM(fuh.entries_processed) as total_entries_processed
FROM file_upload_history fuh
GROUP BY fuh.collection_number, fuh.file_format
ORDER BY fuh.collection_number;

COMMENT ON VIEW v_upload_summary IS '파일 업로드 이력 요약';

-- 인증서 현황 뷰
CREATE OR REPLACE VIEW v_certificate_status AS
SELECT
    c.country_code,
    c.cert_type,
    c.status,
    COUNT(*) as count,
    MIN(c.not_after) as earliest_expiry,
    MAX(c.not_after) as latest_expiry
FROM certificates c
WHERE c.is_latest = TRUE
GROUP BY c.country_code, c.cert_type, c.status
ORDER BY c.country_code, c.cert_type;

COMMENT ON VIEW v_certificate_status IS '인증서 현황 요약';

-- 만료 예정 인증서 뷰
CREATE OR REPLACE VIEW v_expiring_certificates AS
SELECT
    c.cert_id,
    c.cert_type,
    c.country_code,
    c.subject_dn,
    c.not_after,
    EXTRACT(DAY FROM (c.not_after - CURRENT_TIMESTAMP)) as days_until_expiry
FROM certificates c
WHERE c.is_latest = TRUE
  AND c.status = 'VALID'
  AND c.not_after BETWEEN CURRENT_TIMESTAMP AND CURRENT_TIMESTAMP + INTERVAL '90 days'
ORDER BY c.not_after;

COMMENT ON VIEW v_expiring_certificates IS '90일 내 만료 예정 인증서';

-- ================================================================
-- 7. 트리거 함수
-- ================================================================

-- updated_at 자동 갱신 함수
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- updated_at 트리거 적용
CREATE TRIGGER update_file_upload_history_updated_at
    BEFORE UPDATE ON file_upload_history
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_certificates_updated_at
    BEFORE UPDATE ON certificates
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_deviations_updated_at
    BEFORE UPDATE ON deviations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_country_statistics_updated_at
    BEFORE UPDATE ON country_statistics
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ================================================================
-- 8. 통계 정보 갱신
-- ================================================================

-- PostgreSQL 통계 정보 업데이트 (쿼리 최적화에 중요)
ANALYZE file_upload_history;
ANALYZE certificates;
ANALYZE crl_lists;
ANALYZE crl_entries;
ANALYZE deviations;
ANALYZE country_statistics;

-- ================================================================
-- End of Migration
-- ================================================================

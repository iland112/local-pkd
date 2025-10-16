-- ================================================================
-- ICAO PKD Database Schema - Initial Migration
-- Version: 1.0
-- Description: 통합 PKD 파일 관리 및 인증서 추적 시스템
-- ================================================================

-- ================================================================
-- 1. 통합 PKD 파일 테이블
-- ================================================================
CREATE TABLE IF NOT EXISTS pkd_files (
    id BIGSERIAL PRIMARY KEY,
    file_id VARCHAR(36) UNIQUE NOT NULL,
    
    -- 파일 분류
    file_type VARCHAR(30) NOT NULL,  -- CSCA_MASTER_LIST, EMRTD_PKI_OBJECTS, NON_CONFORMANT
    file_format VARCHAR(30) NOT NULL, -- ML_SIGNED_CMS, CSCA_COMPLETE_LDIF, DSC_DELTA_LDIF 등
    
    -- ICAO PKD 메타데이터
    collection_number VARCHAR(3),     -- '001', '002', '003'
    version_number VARCHAR(10),       -- '000325', '009398'
    is_delta BOOLEAN,                 -- TRUE=Delta, FALSE=Complete
    delta_type VARCHAR(10),           -- ml, dscs, bcscs, crls (Delta인 경우)
    
    -- 파일 정보
    original_filename VARCHAR(255) NOT NULL,
    file_extension VARCHAR(10),       -- 'ml', 'ldif'
    stored_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_hash_sha256 VARCHAR(64),
    
    -- Delta 전용 정보
    base_version VARCHAR(10),         -- Delta 적용 기준 버전
    delta_entries_added INTEGER,      -- changetype: add
    delta_entries_modified INTEGER,   -- changetype: modify
    delta_entries_deleted INTEGER,    -- changetype: delete
    
    -- 메타데이터
    country_code VARCHAR(2),
    issue_date TIMESTAMP,
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    
    -- 처리 상태
    upload_status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    parse_status VARCHAR(20),
    verify_status VARCHAR(20),
    ldap_status VARCHAR(20),
    
    -- 처리 시간
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    parse_started_at TIMESTAMP,
    parse_completed_at TIMESTAMP,
    verify_started_at TIMESTAMP,
    verify_completed_at TIMESTAMP,
    ldap_started_at TIMESTAMP,
    ldap_completed_at TIMESTAMP,
    
    -- 처리 결과
    total_entries INTEGER DEFAULT 0,
    valid_entries INTEGER DEFAULT 0,
    invalid_entries INTEGER DEFAULT 0,
    duplicate_entries INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    
    -- 에러 정보
    error_message TEXT,
    
    -- 다운로드 정보
    download_source VARCHAR(255),
    download_method VARCHAR(20),
    
    -- 감사
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 제약 조건
    CONSTRAINT chk_file_type CHECK (file_type IN ('CSCA', 'DSC_CRL', 'ML_LEGACY', 'NON_CONFORMANT')),
    CONSTRAINT chk_file_format CHECK (file_format IN (
        'CSCA_COMPLETE_LDIF', 
        'CSCA_DELTA_LDIF', 
        'DSC_COMPLETE_LDIF', 
        'DSC_DELTA_LDIF',
        'ML_CMS', 
        'NON_CONFORMANT_LDIF'
    )),
    CONSTRAINT chk_upload_status CHECK (upload_status IN ('UPLOADED', 'PARSING', 'PARSED', 'VERIFYING', 'VERIFIED', 'APPLYING', 'APPLIED', 'FAILED'))
);

-- 인덱스
CREATE INDEX idx_pkd_file_type ON pkd_files(file_type);
CREATE INDEX idx_pkd_file_format ON pkd_files(file_format);
CREATE INDEX idx_pkd_country ON pkd_files(country_code);
CREATE INDEX idx_pkd_status ON pkd_files(upload_status, parse_status);
CREATE INDEX idx_pkd_uploaded ON pkd_files(uploaded_at DESC);
CREATE INDEX idx_pkd_collection ON pkd_files(collection_number);
CREATE INDEX idx_pkd_version ON pkd_files(version_number);
CREATE INDEX idx_pkd_delta ON pkd_files(is_delta);

-- 코멘트
COMMENT ON TABLE pkd_files IS 'ICAO PKD 파일 통합 관리 테이블';
COMMENT ON COLUMN pkd_files.collection_number IS '001=CSCA, 002=DSC/CRL, 003=Non-Conformant';
COMMENT ON COLUMN pkd_files.version_number IS 'ICAO PKD version number';
COMMENT ON COLUMN pkd_files.is_delta IS 'TRUE if delta file, FALSE if complete file';

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
    
    -- 출처
    source_file_id VARCHAR(36),
    
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
    CONSTRAINT chk_cert_type CHECK (cert_type IN ('CSCA', 'DSC')),
    CONSTRAINT chk_cert_status CHECK (status IN ('VALID', 'EXPIRED', 'REVOKED', 'SUSPENDED')),
    CONSTRAINT fk_source_file FOREIGN KEY(source_file_id) REFERENCES pkd_files(file_id) ON DELETE SET NULL,
    CONSTRAINT fk_replaced_by FOREIGN KEY(replaced_by) REFERENCES certificates(cert_id) ON DELETE SET NULL
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

-- 코멘트
COMMENT ON TABLE certificates IS 'CSCA 및 DSC 인증서 통합 테이블';

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
    
    source_file_id VARCHAR(36),
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_crl_file FOREIGN KEY(source_file_id) REFERENCES pkd_files(file_id) ON DELETE SET NULL
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
    
    source_file_id VARCHAR(36),
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_deviation_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_deviation_status CHECK (status IN ('ACTIVE', 'RESOLVED', 'ACKNOWLEDGED', 'IGNORED')),
    CONSTRAINT fk_deviation_file FOREIGN KEY(source_file_id) REFERENCES pkd_files(file_id) ON DELETE SET NULL
);

-- 인덱스
CREATE INDEX idx_deviation_country ON deviations(country_code);
CREATE INDEX idx_deviation_status ON deviations(status);
CREATE INDEX idx_deviation_severity ON deviations(severity);

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
    
    CONSTRAINT uk_country_stats UNIQUE(country_code, stats_date)
);

-- 인덱스
CREATE INDEX idx_stats_country ON country_statistics(country_code);
CREATE INDEX idx_stats_date ON country_statistics(stats_date DESC);

-- 코멘트
COMMENT ON TABLE country_statistics IS '국가별 통계 집계 테이블';

-- ================================================================
-- 6. 뷰(Views)
-- ================================================================

-- Delta 적용 추적 뷰
CREATE OR REPLACE VIEW v_delta_tracking AS
SELECT 
    f.collection_number,
    f.file_type,
    MAX(f.version_number) as latest_version,
    COUNT(*) FILTER (WHERE f.is_delta = FALSE) as complete_count,
    COUNT(*) FILTER (WHERE f.is_delta = TRUE) as delta_count,
    MAX(f.uploaded_at) as last_update,
    SUM(f.total_entries) as total_entries_processed
FROM pkd_files f
WHERE f.upload_status = 'APPLIED'
GROUP BY f.collection_number, f.file_type
ORDER BY f.collection_number;

COMMENT ON VIEW v_delta_tracking IS 'Delta 파일 적용 추적';

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
CREATE TRIGGER update_pkd_files_updated_at
    BEFORE UPDATE ON pkd_files
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
-- 8. 초기 데이터
-- ================================================================

-- 시스템 사용자 (필요시)
-- INSERT INTO users (username, role) VALUES ('system', 'SYSTEM') ON CONFLICT DO NOTHING;

-- ================================================================
-- End of Migration
-- ================================================================